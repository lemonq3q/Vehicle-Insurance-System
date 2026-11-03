"""Read-only audit of tenant scope, identities and legacy business references.

Scans a consistent snapshot of insurance_saas, distinguishes platform-wide data
from tenant business, and saves counts and record IDs without passwords or payment
payloads. Findings are evidence for review; this command cannot delete any rows.
"""
from collections import Counter, defaultdict
from datetime import datetime
import importlib.util
import json
from pathlib import Path

SPEC=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate_into_retained_enterprise.py'))
M=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)
H=M.H


def snapshot(c):
    """Read schema and all rows of the requested local database without writes."""
    columns=c.query("SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='insurance_saas' ORDER BY TABLE_NAME,ORDINAL_POSITION;")
    schema=defaultdict(list)
    for col in columns:
        schema[col['table']].append(col['column'])
    sql=["SET SESSION time_zone='+08:00';",'SET TRANSACTION READ ONLY;','START TRANSACTION WITH CONSISTENT SNAPSHOT;']
    for table,fields in schema.items():
        sql.append("SELECT JSON_OBJECT('table',"+H.literal(table)+",'row',"+H.row_expr(fields)+') FROM insurance_saas.'+H.ident(table)+' ORDER BY id;')
    sql.append('COMMIT;')
    data={table:[] for table in schema}
    for record in c.query('\n'.join(sql)):
        data[record['table']].append(record['row'])
    return data


def audit(data):
    """Classify orphan records while respecting archive and identity domains.

    Platform monitor users are separate from enterprise members. File references
    include archived links and avatars; historical actor snapshots are reported
    separately from broken current business relations. Unused merchant catalogs
    are counted, but are not treated as meaningless data.
    """
    indexes={t:{r['id']:r for r in rows} for t,rows in data.items()}
    findings=[]
    report={'database':'insurance_saas','read_only':True,'table_counts':{t:len(r) for t,r in data.items()},'findings':findings}

    def finding(kind,table,records,description,level='review'):
        """Store the complete record-ID list and a non-sensitive explanation."""
        if records:
            findings.append({'kind':kind,'table':table,'count':len(records),'ids':[str(r['id']) for r in records],
                             'description':description,'level':level})

    def parent(base,value):
        """Resolve both online and archived historical entities by their exact ID."""
        return indexes.get(base,{}).get(value) or indexes.get(base+'_archive',{}).get(value)

    enterprises=indexes['tenant_enterprise']
    scope=[]
    for table,rows in data.items():
        if rows and 'enterprise_id' in rows[0]:
            bad=[r for r in rows if r['enterprise_id'] is not None and r['enterprise_id'] not in enterprises]
            finding('missing_enterprise',table,bad,'所属企业不存在','error')
            nulls=[r for r in rows if r['enterprise_id'] is None]
            scope.append({'table':table,'null_enterprise':len(nulls),'missing_enterprise':len(bad)})
            if nulls and table not in ['biz_insurance_product','biz_insurance_product_archive','sys_file','sys_file_archive','monitor_system_log']:
                finding('unscoped_business',table,nulls,'企业业务缺少enterprise_id','error')
    report['enterprise_scope']=scope
    users=indexes['tenant_user'];members=data['tenant_member']
    finding('user_without_membership','tenant_user',[u for u in users.values() if not any(m['user_id']==u['id'] and m['deleted']==0 for m in members)],'没有有效企业成员关系的账号','error')
    role_codes={r['code'] for r in data['auth_role'] if not r['deleted'] and r['status']==1}
    finding('invalid_member_role','tenant_member',[m for m in members if m['role_code'] not in role_codes or m['user_id'] not in users or m['enterprise_id'] not in enterprises],'成员角色或关联失效','error')
    finding('disabled_or_deleted_enterprise_user','tenant_user',[u for u in users.values() if u['deleted'] or u['status']!=1],'需要确认是否仍应保留的停用/删除账号')
    finding('no_login_identity','tenant_user',[u for u in users.values() if not (u.get('username') or u.get('phone') or u.get('email') or u.get('wechat_open_id'))],'账号没有可用登录身份','error')
    finding('no_login_credential','tenant_user',[u for u in users.values() if not (u.get('password') or u.get('wechat_open_id'))],'无密码且未绑定微信的账号','error')
    for enterprise in enterprises.values():
        owner=[m for m in members if m['enterprise_id']==enterprise['id'] and m['role_code']=='OWNER' and not m['deleted'] and m['status']==1]
        if len(owner)!=1 or owner[0]['user_id']!=enterprise['owner_user_id']:
            finding('owner_inconsistent','tenant_enterprise',[enterprise],'拥有者账号与启用OWNER成员不一致','error')
    report['enterprise_accounts']=[{'id':str(u['id']),'name':u['real_name'],'status':u['status'],
                                   'roles':[m['role_code'] for m in members if m['user_id']==u['id'] and not m['deleted']]} for u in users.values()]
    report['platform_accounts']=[{'id':str(u['id']),'name':u['real_name'],'status':u['status'],'deleted':u['deleted'],
        'roles':[indexes['monitor_role'].get(r['role_id'],{}).get('code') for r in data['monitor_user_role'] if r['user_id']==u['id']]} for u in data['monitor_user']]
    relationships={'merchant_id':'biz_merchant','create_merchant_id':'biz_merchant','handle_merchant_id':'biz_merchant',
        'insurance_merchant_id':'biz_merchant','workorder_id':'biz_workorder','insurance_id':'biz_insurance_product',
        'file_id':'sys_file','avatar_file_id':'sys_file','wallet_id':'saas_wallet','plan_id':'saas_plan',
        'old_plan_id':'saas_plan','new_plan_id':'saas_plan','auto_renew_plan_id':'saas_plan',
        'original_subscription_id':'saas_subscription','related_subscription_id':'saas_subscription',
        'last_renew_order_id':'saas_order','related_order_id':'saas_order','related_recharge_order_id':'saas_recharge_order',
        'wallet_transaction_id':'saas_wallet_transaction','joined_by_invite_id':'tenant_invite_code','invite_id':'tenant_invite_code'}
    archived_references=Counter()
    for table,rows in data.items():
        if not rows:
            continue
        refs=dict(relationships)
        if table.startswith('saas_subscription'):
            refs['order_id']='saas_order'
        if table.startswith('biz_merchant') and 'category_id' in rows[0]:refs['category_id']='biz_merchant_category'
        if table.startswith('tenant_enterprise'):refs['owner_user_id']='tenant_user'
        if table.startswith('tenant_member') and 'user_id' in rows[0]:refs['user_id']='tenant_user'
        if table.startswith('saas_order'):refs['buyer_user_id']='tenant_user'
        if table.startswith(('saas_recharge_order','saas_wallet_transaction','biz_ocr_record')):refs['user_id']='tenant_user'
        if table=='monitor_user_role':refs.update(user_id='monitor_user',role_id='monitor_role')
        if table.startswith('auth_role_permission'):refs.update(role_id='auth_role',permission_id='auth_permission')
        if table=='sys_reminder_type':refs['category_id']='sys_reminder_category'
        if table.startswith('biz_workorder') and not table.startswith('biz_workorder_'):refs.update(created_by='tenant_user',handle_by='tenant_user')
        for field,base in refs.items():
            if field not in rows[0]:continue
            missing=[];cross=[]
            for row in rows:
                value=row[field]
                if value is None:continue
                p=parent(base,value)
                if p is None:missing.append(row);continue
                if row.get('enterprise_id') is not None and p.get('enterprise_id') is not None and row['enterprise_id']!=p['enterprise_id']:
                    cross.append(row)
                if value not in indexes.get(base,{}) and base+'_archive' in indexes:archived_references[table+'.'+field]+=1
            finding('missing_parent',table,missing,field+'引用不存在的'+base,'error')
            finding('cross_enterprise_parent',table,cross,field+'指向其他企业数据','error')
    report['valid_archive_references']=dict(archived_references)

    used_files=set()
    for table,rows in data.items():
        for row in rows:
            for field in ['file_id','avatar_file_id']:
                if row.get(field) is not None:used_files.add(row[field])
    report['file_orphans']={}
    for table in ['sys_file','sys_file_archive']:
        orphans=[r for r in data[table] if r['id'] not in used_files]
        report['file_orphans'][table]={'count':len(orphans),'ids':[str(r['id']) for r in orphans],
            'linked_flag_counts':dict(Counter(str(r['is_linked']) for r in orphans)),
            'deleted_flag_counts':dict(Counter(str(r['deleted']) for r in orphans))}
        finding('unreferenced_file',table,orphans,'包括在线/归档附件和头像在内，没有记录引用的文件元数据；不代表可直接删除OSS对象')
    historical=[]
    for row in data['tenant_member_change_log']:
        if any(row.get(f) is not None and row[f] not in users for f in ['operator_user_id','target_user_id']):historical.append(row)
    finding('historical_deleted_actor','tenant_member_change_log',historical,'历史成员日志引用已删除用户；有姓名快照的审计历史，不是可登录账号')
    finding('test_table','test',data['test'],'独立触发器测试残留，不属于任何企业业务','cleanup_candidate')
    recharge_orders=[r for t in ['saas_recharge_order','saas_recharge_order_archive'] for r in data[t]]
    checkout={r.get('stripe_checkout_session_id') for r in recharge_orders if r.get('stripe_checkout_session_id')}
    intents={r.get('stripe_payment_intent_id') for r in recharge_orders if r.get('stripe_payment_intent_id')}
    recharge_ids={str(r['id']) for r in recharge_orders}
    recharge_numbers={r['recharge_no'] for r in recharge_orders}
    webhooks=[];metadata_tenants=Counter()
    for row in data['saas_stripe_webhook_event']:
        payload=row['payload_json'];payload=json.loads(payload) if isinstance(payload,str) else payload
        obj=(payload or {}).get('data',{}).get('object',{}) or {}
        meta=obj.get('metadata',{}) or {}
        if meta.get('enterprise_id') is not None:metadata_tenants[str(meta['enterprise_id'])]+=1
        # Refund events use payment intents; internal refund-state events instead
        # store rechargeOrderId/paymentIntentId at the payload root. Checking only
        # checkout sessions would miss these legitimate audit relationships.
        matched=(row.get('checkout_session_id') in checkout or obj.get('id') in checkout
            or obj.get('id') in intents or obj.get('payment_intent') in intents
            or str(meta.get('order_id')) in recharge_ids or meta.get('recharge_no') in recharge_numbers
            or str((payload or {}).get('rechargeOrderId')) in recharge_ids
            or (payload or {}).get('paymentIntentId') in intents)
        if not matched:webhooks.append(row)
    finding('unmatched_payment_history','saas_stripe_webhook_event',webhooks,'核对会话、付款意向、充值单ID及编号后仍无法关联保留充值记录；支付/退款审计和幂等资料需单独决定是否清理')
    report['stripe_payload_enterprise_counts']=dict(metadata_tenants)
    report['platform_unscoped_data']={t:len(data[t]) for t in ['auth_role','auth_permission','auth_role_permission','biz_merchant_category','saas_plan','monitor_user','monitor_user_role','monitor_role','saas_visitor_lead','monitor_promotion_target','monitor_system_log','sys_reminder_category','sys_reminder_type']}
    used_merchants={r.get(f) for t in ['biz_workorder','biz_workorder_archive'] for r in data[t] for f in ['create_merchant_id','handle_merchant_id','insurance_merchant_id']}
    report['valid_unused_merchants']=dict(Counter(r['direction'] for r in data['biz_merchant'] if r['id'] not in used_merchants))
    for part in ['quote','payment','underwriting','logistics']:
        counts=Counter(r['workorder_id'] for r in data['biz_workorder_'+part] if not r['deleted'])
        bad=[w for w in data['biz_workorder'] if counts[w['id']]!=1]
        finding('missing_or_duplicate_stage','biz_workorder',bad,part+'有效阶段明细不为1条','error')
    commissions=Counter((r['workorder_id'],r['side']) for r in data['biz_workorder_commission'] if not r['deleted'])
    finding('missing_or_duplicate_commission','biz_workorder',[w for w in data['biz_workorder'] if any(commissions[(w['id'],side)]!=1 for side in ['UPSTREAM','DOWNSTREAM'])],'上下游佣金记录缺失或重复','error')
    report['errors']=sum(x['count'] for x in findings if x['level']=='error')
    return report


if __name__=='__main__':
    report=audit(snapshot(M.client()))
    folder=M.ROOT/'outputs/database-audit'/datetime.now().strftime('%Y%m%d-%H%M%S');folder.mkdir(parents=True)
    M.AUDIT.save_json(folder/'integrity_audit.json',report)
    summary={k:v for k,v in report.items() if k not in ['table_counts','enterprise_scope','file_orphans']}
    summary['findings']=[{k:v for k,v in r.items() if k!='ids'} for r in report['findings']]
    summary['file_orphans']={t:{k:v for k,v in value.items() if k!='ids'} for t,value in report['file_orphans'].items()}
    print(json.dumps({'folder':str(folder),'summary':summary},ensure_ascii=False),flush=True)
