"""Rehearse and apply the approved index-only workorder performance change.

Business SQL and PageHelper remain untouched. A full backup and all-table row
fingerprints protect data, while representative filters, pagination, and installed
PageHelper count queries are compared before/after on a private rehearsal schema.
Production application requires the successful rehearsal artifact and no drift.
"""
from datetime import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import time

SPEC=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate_into_retained_enterprise.py'))
M=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)
H=M.H
ROOT=M.ROOT
MIGRATION=Path(__file__).with_name('V20261103_01_add_workorder_relation_indexes.sql')
BASE_TABLES=['biz_workorder_quote','biz_workorder_underwriting','biz_workorder_logistics',
             'biz_vehicle_license','biz_vehicle_certificate','biz_vehicle_invoice',
             'biz_workorder_file','biz_workorder_insurance']
INDEX_TABLES=BASE_TABLES+[t+'_archive' for t in BASE_TABLES]+['biz_workorder_commission_archive']


def save(folder,name,value):
    """Write reproducible local evidence without printing business row contents."""
    M.AUDIT.save_json(folder/name,value)


def build_cases():
    """Keep actual mapper/PageHelper query shape and vary only ordinary filters.

    Permission-limited queries explicitly retain handle_by, and all cases retain
    the same enterprise predicates. Canonical full-result checks order by ID only
    for comparison; the actual measured page still uses existing creation ordering.
    """
    evidence=ROOT/'outputs/query-performance-20261103'
    listing=(evidence/'new_list.sql').read_text(encoding='utf-8')
    count=(evidence/'new_pagehelper_count.sql').read_text(encoding='utf-8')
    filters={
        'unfiltered_admin':'1=1',
        'issuer_only':'w.handle_by=35',
        'status_only':'w.status=10',
        'created_range':"w.created_at>='2026-09-01' AND w.created_at<'2026-11-03'",
        'finish_range':"uw.finish_time>='2026-09-01' AND uw.finish_time<'2026-11-03'",
        'keyword':"(w.code LIKE '%张%' OR w.organization_name LIKE '%张%' OR w.owner_name LIKE '%张%' OR vl.license_plate LIKE '%张%' OR vl.vehicle_code LIKE '%张%' OR vc.vehicle_code LIKE '%张%')",
        'combined':"w.handle_by=35 AND w.status=10 AND w.created_at>='2026-09-01' AND w.created_at<'2026-11-03'",
    }
    result={}
    for name,condition in filters.items():
        page=listing.replace('WHERE w.deleted = 0','WHERE w.deleted = 0 AND ('+condition+')')
        total=count.replace('WHERE w.deleted = 0','WHERE w.deleted = 0 AND ('+condition+')')
        result[name]={'page':page,'count':total}
    result['page_51']={'page':listing.replace('LIMIT 10','LIMIT 500,10'),'count':count}
    return result


def measure(c,database,cases,folder,label):
    """Measure unchanged count/page SQL and fingerprint all matching detail rows.

    EXPLAIN ANALYZE captures database-only time. Full row hashes catch lost joins,
    duplicated results, or changed amounts, while page IDs document boundary ties.
    Fifteen-second query limits keep a bad existing plan from running indefinitely.
    """
    results={};cache={}
    for name,case in cases.items():
        queries={k:v.replace('insurance_saas.',database+'.') for k,v in case.items()}
        outcome={}
        for kind in ['count','page']:
            query=queries[kind]
            if query in cache:
                outcome[kind]=cache[query]
                continue
            start=time.perf_counter()
            if kind=='count':
                returned=c.run('SET SESSION max_execution_time=15000; '+query+';').strip()
                extra={'total':int(returned)}
            else:
                returned=c.query('SET SESSION max_execution_time=15000; SELECT JSON_OBJECT(\'id\',p.id,\'created_time\',p.create_time) FROM ('+query+') p;')
                extra={'rows':len(returned),'ids':[str(r['id']) for r in returned],
                       'created_times':[r['created_time'] for r in returned]}
            elapsed=round((time.perf_counter()-start)*1000,3)
            explain=c.run('SET SESSION max_execution_time=15000; EXPLAIN ANALYZE '+query+';')
            (folder/(label+'_'+name+'_'+kind+'_explain.txt')).write_text(explain,encoding='utf-8')
            match=re.search(r'actual time=([\d.e+-]+)\.\.([\d.e+-]+)',explain)
            outcome[kind]={'wall_ms':elapsed,'database_ms':float(match[2]) if match else None,**extra}
            cache[query]=outcome[kind]
        full=re.sub(r' ORDER BY w\.created_at DESC LIMIT .*$',' ORDER BY w.id',queries['page'])
        assert full!=queries['page']
        start=time.perf_counter()
        raw=c.run('SET SESSION max_execution_time=15000; '+full+';')
        outcome['full_result_sha256']=hashlib.sha256(raw.encode('utf-8')).hexdigest()
        outcome['full_result_wall_ms']=round((time.perf_counter()-start)*1000,3)
        results[name]=outcome
        print(label,name,json.dumps({'count_ms':outcome['count']['database_ms'],'page_ms':outcome['page']['database_ms'],'total':outcome['count']['total']},ensure_ascii=False),flush=True)
    save(folder,label+'_benchmarks.json',results)
    return results


def compare(before,after):
    """Require identical complete query results and valid unchanged page boundaries.

    Existing SQL orders only by creation time, so tied rows can change physical
    order after an index change. A tie is recorded separately, never misreported
    as lost data; the full result hash must still match exactly in ID order.
    """
    ties=[]
    for name,old in before.items():
        new=after[name]
        assert old['count']['total']==new['count']['total'],('changed_count',name)
        assert old['full_result_sha256']==new['full_result_sha256'],('changed_rows',name)
        assert old['page']['rows']==new['page']['rows'],('changed_page_size',name)
        if old['page']['ids']!=new['page']['ids']:
            assert old['page']['created_times']==new['page']['created_times'],('changed_page_boundary',name)
            ties.append(name)
    return {'all_counts_identical':True,'all_full_row_hashes_identical':True,
            'page_boundaries_valid':True,'existing_creation_time_tie_cases':ties}


def indexes(c,database):
    """Read exact index layouts, so no original index can disappear unnoticed."""
    return c.query("SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',NON_UNIQUE,'sequence',SEQ_IN_INDEX,'column',COLUMN_NAME,'visible',IS_VISIBLE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA="+H.literal(database)+' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;')


def check_indexes(before,after):
    """Verify all original indexes and exactly seventeen reviewed additions."""
    b={M.canonical(r) for r in before};a={M.canonical(r) for r in after}
    assert b<=a,'Existing index changed or removed'
    added=[r for r in after if M.canonical(r) not in b]
    assert len(added)==17*3,len(added)
    assert {r['table'] for r in added}==set(INDEX_TABLES)
    for table in INDEX_TABLES:
        parts=[r for r in added if r['table']==table]
        assert [r['column'] for r in parts]==['enterprise_id','workorder_id','deleted']
        assert all(r['name']=='idx_20261103_workorder_link' and r['unique']==1 for r in parts)


def prepare(c):
    """Back up local target, rehearse migration and compare representative queries."""
    folder=ROOT/'outputs/index-optimization'/datetime.now().strftime('%Y%m%d-%H%M%S');folder.mkdir(parents=True)
    schema=json.loads((ROOT/'outputs/database-migration/run-20261102-232719/snapshot.json').read_text(encoding='utf-8'))['schema']
    baseline=M.fingerprints(c,schema,{'insurance_saas':schema['insurance_saas']})
    original_indexes=indexes(c,'insurance_saas')
    backup=M.backup(c,folder,'insurance_saas')
    clone='insurance_saas_indexcheck_'+datetime.now().strftime('%Y%m%d_%H%M%S')
    sql=backup.read_text(encoding='utf-8').replace('`insurance_saas`','`'+clone+'`')
    sql=re.sub(r'(/\*!50003 TRIGGER `[^`]+`(?:(?!\*/)[\s\S])*?);(\s*\*/;;)',r'\1\2',sql)
    c.run(sql)
    before=measure(c,clone,build_cases(),folder,'before')
    clone_indexes=indexes(c,clone)
    migration=MIGRATION.read_text(encoding='utf-8')
    c.run('USE '+H.ident(clone)+';\n'+migration)
    check_indexes(clone_indexes,indexes(c,clone))
    # Re-running must make no extra changes, proving the migration is idempotent.
    second=c.run('USE '+H.ident(clone)+';\n'+migration)
    assert second.count('already_covered')==17
    after=measure(c,clone,build_cases(),folder,'after')
    checked=compare(before,after)
    assert after['unfiltered_admin']['count']['database_ms']<before['unfiltered_admin']['count']['database_ms']/5
    assert M.canonical(baseline)==M.canonical(M.fingerprints(c,schema,{'insurance_saas':schema['insurance_saas']}))
    save(folder,'ready.json',{'clone':clone,'baseline':baseline,'original_indexes':original_indexes,
         'schema':schema,'comparison':checked,'sql_sha256':hashlib.sha256(migration.encode('utf-8')).hexdigest(),
         'approved_index_tables':INDEX_TABLES})
    print('REHEARSAL_READY',folder,json.dumps(checked),flush=True)


def apply(c,folder):
    """Apply reviewed index DDL locally and prove data/query semantics unchanged."""
    folder=folder.resolve();assert folder.is_relative_to(ROOT/'outputs/index-optimization')
    ready=json.loads((folder/'ready.json').read_text(encoding='utf-8'))
    assert not (folder/'production_verification.json').exists(),'Already applied'
    migration=MIGRATION.read_text(encoding='utf-8')
    assert hashlib.sha256(migration.encode('utf-8')).hexdigest()==ready['sql_sha256']
    assert M.canonical(ready['baseline'])==M.canonical(M.fingerprints(c,ready['schema'],{'insurance_saas':ready['schema']['insurance_saas']})),'Local data changed; rehearse again'
    assert M.canonical(ready['original_indexes'])==M.canonical(indexes(c,'insurance_saas')),'Local indexes changed'
    output=c.run('USE insurance_saas;\n'+migration)
    (folder/'production_ddl_output.txt').write_text(output,encoding='utf-8')
    check_indexes(ready['original_indexes'],indexes(c,'insurance_saas'))
    assert M.canonical(ready['baseline'])==M.canonical(M.fingerprints(c,ready['schema'],{'insurance_saas':ready['schema']['insurance_saas']})),'Row data changed'
    actual=measure(c,'insurance_saas',build_cases(),folder,'production')
    before=json.loads((folder/'before_benchmarks.json').read_text(encoding='utf-8'))
    check=compare(before,actual);check.update(all_72_table_data_fingerprints_unchanged=True,added_nonunique_indexes=17,original_indexes_unchanged=True)
    save(folder,'production_verification.json',check)
    print('PRODUCTION_VERIFIED',json.dumps(check),flush=True)


if __name__=='__main__':
    c=M.client()
    if sys.argv[1]=='prepare':prepare(c)
    elif sys.argv[1]=='apply':apply(c,Path(sys.argv[2]))
    else:raise SystemExit('Expected prepare or apply <evidence folder>')
