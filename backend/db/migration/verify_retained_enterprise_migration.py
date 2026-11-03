"""Independently compare migrated business against the untouched legacy snapshot.

This verifier does not trust generated insert rows. It reconstructs destinations
from the original wide workorder columns, checks each historical field, verifies
every direct child reference and validates the explicitly approved exceptions.
It performs only reads; an optional clone-only probe tests rollback and uniqueness.
"""
from collections import Counter
from datetime import datetime
from decimal import Decimal
import importlib.util
import json
from pathlib import Path
import sys

SPEC=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate_into_retained_enterprise.py'))
M=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)
H=M.H


def equivalent(actual, expected, column_type):
    """Compare source semantics exactly, allowing only storage-format normalization."""
    if actual is None or expected is None:
        return actual is None and expected is None
    if column_type=='json':
        expected=json.loads(expected) if isinstance(expected,str) else expected
        return actual==expected
    if column_type.startswith('decimal'):
        return Decimal(str(actual))==Decimal(str(expected))
    if column_type.startswith(('date','timestamp')):
        return datetime.fromisoformat(str(actual))==datetime.fromisoformat(str(expected))
    return actual==expected


def verify(c, folder, database):
    """Read current business and check every legacy row/column through audit maps.

    Missing mappings, duplicate aggregate rows, unknown source fields, changed
    money/text, dangling references and unauthorized creator assignments all fail.
    The report records expected exclusions rather than calling them data loss.
    """
    snapshot=json.loads((folder/'snapshot.json').read_text(encoding='utf-8'))
    plan=json.loads((folder/'plan.json').read_text(encoding='utf-8'))
    source=snapshot['rows']['insurance']; schema=snapshot['schema']['insurance_saas']
    audit=plan['audit']; maps={base:{int(k):v for k,v in values.items()} for base,values in audit['id_maps'].items()}
    changed=set(plan['rows'])
    rows={t:[] for t in changed}
    queries=["SET SESSION time_zone='+08:00';",'SET TRANSACTION READ ONLY;','START TRANSACTION WITH CONSISTENT SNAPSHOT;']
    for table in sorted(changed):
        queries.append("SELECT JSON_OBJECT('table',"+H.literal(table)+",'row',"+H.row_expr(schema[table])+') FROM '+H.ident(database)+'.'+H.ident(table)+' ORDER BY id;')
    queries.append('COMMIT;')
    for record in c.query('\n'.join(queries)):
        rows[record['table']].append(record['row'])
    ids={t:{r['id']:r for r in values} for t,values in rows.items()}
    checks=Counter(); coverage={}

    def check(table, row, field, expected, label):
        """Count a source-to-destination field assertion and fail with its identity."""
        assert equivalent(row[field],expected,schema[table][field]['type']), (label,table,row['id'],field,row[field],expected)
        checks[table]+=1

    parts={}
    for table in M.PARTS:
        key=lambda r:(r['workorder_id'],r.get('side'))
        parts[table]={key(r):r for r in rows.get(table,[])}
        assert len(parts[table])==len(rows.get(table,[])), ('duplicate_part',table)
    workorder_fields=set()
    for old in source['workorder']:
        if old['id'] in M.DROP_WORKORDERS:
            assert old['id'] not in maps['workorder']
            continue
        wid=maps['workorder'][old['id']]; main=ids['biz_workorder'][wid]
        for field,value in old.items():
            workorder_fields.add(field)
            table='biz_workorder'; row=main; dest=field
            if field=='id':
                value=wid
            elif field=='create_by':
                # Original submitting identity is retained separately; the current
                # handler-to-creator inference was explicitly accepted by the user.
                field_users={u['id']:u for suffix in ['', '_archive'] for u in source['user'+suffix]}
                if value is not None:
                    check(table,row,'submitter_name',field_users[value]['name'],'legacy submitter')
                dest='created_by'; value=maps['user'][old['handle_by']]
            elif field in ['handle_by','update_by']:
                dest='updated_by' if field=='update_by' else field
                value=maps['user'].get(value)
            elif field in ['create_merchant_id','handle_merchant_id','insurance_merchant_id']:
                value=None if value is None else maps['merchant'][value]
            elif field in ['create_time','update_time']:
                dest='created_at' if field=='create_time' else 'updated_at';value=H.unix_time(value)
            elif field=='is_delete':
                dest='deleted'
            elif field=='remind_status':
                value=0
            elif field.startswith(('upstream_','downstream_')):
                side,part_field=field.split('_',1)
                table='biz_workorder_commission';row=parts[table][(wid,side.upper())]
                dest=part_field.replace('vehicle_and_vessel_tax','vehicle_tax')
            elif field not in schema['biz_workorder']:
                if field in ['pay_name','pay_id_num','pay_bank','pay_bank_card_num','required_pay_amount','pay_remark','pay_failed_remark']:
                    table='biz_workorder_payment'
                    dest={'pay_name':'payee_name','pay_id_num':'payee_phone','pay_bank':'merchant_bank','pay_bank_card_num':'merchant_bank_card_num'}.get(field,field)
                elif field in ['quotation_time','quotation_no','commercial_amount','compulsory_amount','vehicle_and_tax_amount','non_motor_amount','non_motor_insurance_name','non_motor_coverage_amount','quotation_remark','quotation_failed_remark']:
                    table='biz_workorder_quote'
                elif field in ['tracking_num','logistics_company']:
                    table='biz_workorder_logistics'
                else:
                    table='biz_workorder_underwriting'
                row=parts[table][(wid,None)]
                if field in ['quotation_time','underwriting_time','finish_time']:
                    value=H.unix_time(value)
                assert dest in schema[table], ('uncovered_source_workorder_field',field)
            check(table,row,dest,value,'legacy workorder field '+field)
        for field in ['remind_status','renewal_status_cycle','renewal_reminder_disabled','commercial_renewal_status','commercial_renewal_cycle','compulsory_renewal_status','compulsory_renewal_cycle']:
            check('biz_workorder',main,field,0,'renewal reset')
        check('biz_workorder',main,'enterprise_id',5,'tenant')
    coverage['workorder']=sorted(workorder_fields)

    # Children and independent catalogs use a separate reconstruction of field
    # names and references, not the converter's generated row dictionaries.
    parent_index={base:{r['id']:r for suffix in ['', '_archive'] for r in source.get(base+suffix,[])} for base in ['merchant','workorder']}
    for base,target in M.BASES.items():
        if base in ['workorder','merchant']:
            continue
        covered=set()
        for suffix in ['', '_archive']:
            table=target+suffix
            for old in source.get(base+suffix,[]):
                if old['id'] not in maps[base]:
                    assert old.get('workorder_id') in M.DROP_WORKORDERS
                    continue
                row=ids[table][maps[base][old['id']]]
                for field,value in old.items():
                    covered.add(field);dest=field
                    if field=='id':value=maps[base][value]
                    elif field=='is_delete':dest='deleted'
                    elif field=='update_by':dest='updated_by';value=maps['user'].get(value)
                    elif field in H.TIMES and H.TIMES[field] in schema[table]:
                        dest=H.TIMES[field]
                        if value is None and field in ['create_time','update_time']:
                            parent=parent_index['workorder'].get(old.get('workorder_id')) if 'workorder_id' in old else parent_index['merchant'].get(old.get('merchant_id'))
                            value=parent.get(field) if parent else None
                        value=H.unix_time(value)
                    elif field in ['merchant_id','workorder_id','insurance_id','file_id']:
                        entity={'merchant_id':'merchant','workorder_id':'workorder','insurance_id':'insurance','file_id':'system_file'}[field]
                        value=None if value is None else maps[entity][value]
                    elif base=='workorder_file' and field=='type':dest='file_type'
                    assert dest in schema[table],('uncovered_source_field',base,field)
                    check(table,row,dest,value,'legacy child '+base)
                check(table,row,'enterprise_id',5,'tenant')
        coverage[base]=sorted(covered)
    original_merchants=parent_index['merchant']
    regenerated={x['old_id']:x['new_code'] for x in audit['regenerated_codes']}
    selected={x['old_merchant_id']:x['old_person_id'] for x in audit['selected_contacts']}
    people={u['id']:u for suffix in ['', '_archive'] for u in source['user'+suffix]}
    merged={x['old_id']:x['kept_old_id'] for x in audit['merchant_merges']}
    for old_id,new_id in maps['merchant'].items():
        if old_id in merged:
            assert new_id==maps['merchant'][merged[old_id]]
            continue
        old=original_merchants[old_id]
        table='biz_merchant' if new_id in ids['biz_merchant'] else 'biz_merchant_archive';row=ids[table][new_id]
        for field,value in old.items():
            dest=field
            if field=='id':value=new_id
            elif field=='type':
                dest='direction';value='UPSTREAM' if value in ['机构','保司'] else 'DOWNSTREAM'
            elif field=='is_delete':dest='deleted'
            elif field=='update_by':dest='updated_by';value=maps['user'].get(value)
            elif field in ['create_time','update_time']:dest='created_at' if field=='create_time' else 'updated_at';value=H.unix_time(value)
            elif field=='code':value=regenerated.get(old_id,value)
            elif field in ['contact','phone'] and row['direction']=='DOWNSTREAM' and old_id in selected:
                value=people[selected[old_id]]['name' if field=='contact' else 'username']
            check(table,row,dest,value,'first merchant information')
    summary={'legacy_business_verified':True,'source_workorder_columns_covered':len(workorder_fields),
             'field_assertions':sum(checks.values()),'assertions_by_table':dict(checks),
             'workorders':len(rows['biz_workorder']),'merchant_merges':len(merged),
             'workorder_count_by_mapped_user':dict(Counter(str(r['handle_by']) for r in rows['biz_workorder'])),
             'source_field_coverage':coverage}
    M.AUDIT.save_json(folder/('legacy_business_verification_'+database+'.json'),summary)
    print(json.dumps({k:v for k,v in summary.items() if k not in ['source_field_coverage','assertions_by_table']},ensure_ascii=False),flush=True)
    return summary


def clone_probes(c, snapshot, database):
    """Prove transaction rollback and enterprise-code uniqueness on the clone only."""
    assert database.startswith('insurance_saas_check_')
    schema=snapshot['schema']
    before=M.fingerprints(c,schema,{'insurance_saas':['biz_workorder','biz_merchant']}) if database=='insurance_saas' else None
    get_hash=lambda: c.query("SELECT JSON_OBJECT('hash',SHA2("+H.row_expr(schema['insurance_saas']['biz_workorder'])+",256)) FROM "+H.ident(database)+'.biz_workorder ORDER BY id LIMIT 1;')[0]['hash']
    original=get_hash()
    try:
        c.run('USE '+H.ident(database)+"; CREATE TEMPORARY TABLE probe_guard(v INT CHECK(v=1)); SET autocommit=0; UPDATE biz_workorder SET remark='rollback probe' ORDER BY id LIMIT 1; INSERT INTO probe_guard VALUES(0); COMMIT;")
        raise AssertionError('Expected assertion failure')
    except RuntimeError as error:
        assert '3819' in str(error) or 'CHECK' in str(error)
    assert get_hash()==original,'Failed DML did not roll back'
    merchant=c.query("SELECT JSON_OBJECT('id',id,'code',code,'category_id',category_id,'direction',direction) FROM "+H.ident(database)+'.biz_merchant ORDER BY id LIMIT 1;')[0]
    next_id=c.query("SELECT JSON_OBJECT('id',MAX(id)+1) FROM "+H.ident(database)+'.biz_merchant;')[0]['id']
    try:
        c.run('USE '+H.ident(database)+'; START TRANSACTION; INSERT INTO biz_merchant(id,enterprise_id,code,name,category_id,direction,deleted) VALUES('+str(next_id)+',5,'+H.literal(merchant['code'])+",'unique-code probe',"+str(merchant['category_id'])+','+H.literal(merchant['direction'])+',0); COMMIT;')
        raise AssertionError('Expected duplicate-code rejection')
    except RuntimeError as error:
        assert '1062' in str(error) and 'uk_biz_merchant_enterprise_code' in str(error)
    print('PASS clone rollback and enterprise-code unique constraint',flush=True)


if __name__=='__main__':
    folder=Path(sys.argv[1]).resolve();database=sys.argv[2];c=M.client()
    verify(c,folder,database)
    if len(sys.argv)>3 and sys.argv[3]=='--clone-probes':
        clone_probes(c,json.loads((folder/'snapshot.json').read_text(encoding='utf-8')),database)
