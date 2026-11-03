"""Migrate local insurance into the retained enterprise using reviewed decisions.

Prepare captures fresh evidence and backups, builds an explicit conversion plan,
and rehearses on a separate database. Apply requires a successful rehearsal and
unchanged source/target fingerprints. All production DML and exact row comparison
run under table locks in one transaction. No source rows or cloud files are edited.
"""
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SPEC = importlib.util.spec_from_file_location('preflight', HERE / 'preflight_existing_enterprise_migration.py')
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)
H = AUDIT.HELPERS
BASES = {k: v for k, v in H.DIRECT.items() if k != 'user'}
PARTS = ['biz_workorder_quote', 'biz_workorder_commission', 'biz_workorder_payment',
         'biz_workorder_underwriting', 'biz_workorder_logistics']
RESET = ['biz_ocr_record', 'monitor_enterprise_daily_usage', 'monitor_enterprise_reminder',
         'saas_enterprise_reminder']
DROP_WORKORDERS = {7, 15, 17, 20, 23, 103}


def canonical(value):
    """Serialize exact business values deterministically for local audit hashes."""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, default=str, separators=(',', ':'))


def client():
    """Reuse the project client configuration without exposing credentials."""
    config = (ROOT / 'backend/insruance/src/main/resources/application.yml').read_text(encoding='utf-8')
    os.environ['MYSQL_PWD'] = re.search(r'^\s+password:\s*(.+)$', config, re.M)[1].strip().strip('\"\'')
    return H.Mysql(AUDIT.SimpleNamespace(mysql=r'D:\MySQL\file\bin\mysql.exe', login_path=None,
                                       host='127.0.0.1', port=3306, user='root', password_prompt=False))


def fingerprints(c, schema, names):
    """Read full-row 256-bit XOR fingerprints and counts for drift guards.

    All four 64-bit segments of SHA256 participate. SQL builds the canonical row
    JSON, so JSON storage normalization and DECIMAL formatting are consistent.
    """
    queries = []
    for db, tables in names.items():
        for table in sorted(tables):
            expr = H.row_expr(schema[db][table])
            fields = ["'db'," + H.literal(db), "'table'," + H.literal(table), "'n',COUNT(*)"]
            for i in range(4):
                fields.append("'h" + str(i) + "',COALESCE(BIT_XOR(CAST(CONV(SUBSTRING(SHA2(" + expr + ',256),' + str(1 + 16*i) + ',16),16,10) AS UNSIGNED)),0)')
            queries.append('SELECT JSON_OBJECT(' + ','.join(fields) + ') FROM ' + H.ident(db) + '.' + H.ident(table) + ';')
    return c.query('\n'.join(queries))


class Conversion:
    """Convert the reviewed legacy business into enterprise 5 with explicit maps.

    Source data stays immutable. State contains staged rows, shared online/archive
    ID sequences, merge mappings, creator assumptions and every discarded test row.
    No database writes occur in this class; schema validation rejects truncation.
    """

    def __init__(self, snapshot, groups):
        """Bind one read-only snapshot and MySQL-collation merchant groups."""
        self.s = snapshot
        self.src = snapshot['rows']['insurance']
        self.old_target = snapshot['rows']['insurance_saas']
        self.schema = snapshot['schema']['insurance_saas']
        self.groups = groups
        self.out = defaultdict(list)
        self.maps = defaultdict(dict)
        self.next = {}
        self.index = {base: {r['id']: r for suffix in ['', '_archive'] for r in self.src.get(base+suffix, [])}
                      for base in ['user', 'merchant', 'workorder', 'insurance', 'system_file']}
        self.log = {'approved_rules': {'enterprise_id': 5, 'creator': 'inferred_from_current_handler',
                    'contact_priority': ['联系人', '收款人', '店员'], 'same_priority': 'lowest_old_user_id',
                    'merchant_merge': 'same_direction_name_first_old_id', 'renewal': 'reset_all',
                    'missing_times': 'inherit_parent_history', 'payment_raw': 'copy_to_phone'},
                    'merchant_merges': [], 'regenerated_codes': [], 'selected_contacts': [],
                    'creator_inferences': [], 'null_editor_history': [], 'time_inheritance': [],
                    'removed_test_workorders': [], 'removed_children': Counter(), 'field_coverage': {}}
        for u in self.src['user']:
            if u['username'] in AUDIT.ACCOUNT_MAP:
                self.maps['user'][u['id']] = AUDIT.ACCOUNT_MAP[u['username']]
        assert set(self.maps['user']) == {1, 22, 120, 121, 122, 130}

    def allocate(self, table):
        """Allocate exact BIGINTs beyond both target online and archive maxima."""
        base = table.removesuffix('_archive')
        if base not in self.next:
            self.next[base] = max([r['id'] for suffix in ['', '_archive'] for r in self.old_target.get(base+suffix, [])] + [0])
        self.next[base] += 1
        assert self.next[base] <= 9223372036854775807
        return self.next[base]

    def actor(self, old_id, table, row_id):
        """Map known system actors; preserve unknown editor identity in audit only.

        Historic file editors may be deleted legacy users with no authorized new
        account. Returning NULL avoids falsely attributing edits to another person;
        the complete original identity remains in the snapshot and this audit.
        """
        if old_id is None:
            return None
        if old_id not in self.maps['user']:
            self.log['null_editor_history'].append({'table': table, 'old_id': row_id,
                'old_editor': old_id, 'name': self.index['user'][old_id]['name']})
        return self.maps['user'].get(old_id)

    def common(self, table, row, parent=None):
        """Preserve same-name fields, deletion flags, actors and Shanghai times.

        User-approved parent time inheritance applies only where history is absent.
        Archive NULLs otherwise remain NULL. New fields use current schema defaults.
        """
        values = {k: v for k, v in row.items() if k in self.schema[table] and k != 'id'
                  and 'GENERATED' not in self.schema[table][k]['extra']}
        values.update(enterprise_id=5, deleted=row.get('is_delete', 0))
        for old, new in H.TIMES.items():
            if old in row and new in self.schema[table]:
                value = row[old]
                if value is None and parent is not None and old in ['create_time', 'update_time']:
                    value = parent.get(old)
                    self.log['time_inheritance'].append({'table': table, 'old_id': row['id'], 'field': new, 'unix_seconds': value})
                values[new] = H.unix_time(value)
        if 'updated_by' in self.schema[table]:
            values['updated_by'] = self.actor(row.get('update_by'), table, row['id'])
        return values

    def add(self, table, row):
        """Validate a complete staged row against the live destination schema.

        Refuses unknown columns, text overflow, invalid JSON, numeric overflow and
        missing mandatory values. Values are never silently truncated or rounded.
        """
        fields = self.schema[table]
        assert not set(row) - set(fields), (table, set(row) - set(fields))
        for name, col in fields.items():
            if 'GENERATED' in col['extra'] or 'auto_increment' in col['extra']:
                continue
            if name not in row:
                if col['nullable'] == 'NO' and col['default'] is None:
                    raise ValueError(('missing_required', table, name))
                continue
            value = row[name]
            if value is None:
                assert col['nullable'] == 'YES', (table, name, 'NULL')
                continue
            length = re.match(r'(?:var)?char\((\d+)\)', col['type'])
            assert not length or len(str(value)) <= int(length[1]), (table, name, 'too_long')
            if col['type'] == 'json' and isinstance(value, str):
                json.loads(value)
            precision = re.match(r'decimal\((\d+),(\d+)\)', col['type'])
            if precision:
                number = Decimal(str(value)); p, scale = map(int, precision.groups())
                assert abs(number) < Decimal(10) ** (p-scale)
                assert number == number.quantize(Decimal(1).scaleb(-scale))
        self.out[table].append(row)

    def contacts(self):
        """Choose one person per original merchant by reviewed role and ID order."""
        roles = {r['id']: r['name'] for r in self.src['role']}
        rank = {'联系人': 0, '收款人': 1, '店员': 2}
        candidates = defaultdict(list)
        for relation in self.src['user_role']:
            person = self.index['user'][relation['user_id']]
            role = roles[relation['role_id']]
            if relation['is_delete'] or person['is_delete'] or role not in rank or person['merchant_id'] is None:
                continue
            candidates[person['merchant_id']].append((rank[role], person['id'], person, role))
        self.contact_people = {}
        for merchant_id, values in candidates.items():
            selected = sorted(values, key=lambda x: (x[0], x[1]))[0]
            self.contact_people[merchant_id] = selected[2]
            self.log['selected_contacts'].append({'old_merchant_id': merchant_id, 'old_person_id': selected[1],
                                                  'name': selected[2]['name'], 'role': selected[3]})

    def merchants(self):
        """Merge active same-direction names by first ID and redirect all references.

        The database supplies grouping keys under its real collation. Attributes
        come from the first original merchant; its selected person supplies the
        merged downstream contact. Archived merchant records remain historical.
        """
        representatives = {int(member): int(g['first_id']) for g in self.groups for member in g['old_ids']}
        assert set(representatives) == {r['id'] for r in self.src['merchant']}
        categories = {r['code']: r for r in self.old_target['biz_merchant_category'] if not r['deleted']}
        type_map = {'机构': 'INSURANCE_ORG', '保司': 'INSURANCE_ORG', '汽修厂': 'AUTO_REPAIR',
                    '代理人': 'AGENT', '车商店铺': 'DEALER_STORE'}
        self.merchant_values = {}
        used_codes = set()
        for suffix in ['', '_archive']:
            for old in sorted(self.src['merchant'+suffix], key=lambda x: x['id']):
                representative = representatives[old['id']] if not suffix else old['id']
                if not suffix and representative != old['id']:
                    self.maps['merchant'][old['id']] = self.maps['merchant'][representative]
                    self.log['merchant_merges'].append({'old_id': old['id'], 'kept_old_id': representative,
                        'new_id': self.maps['merchant'][representative], 'name': old['name']})
                    continue
                table = 'biz_merchant'+suffix
                values = self.common(table, old)
                category = categories[type_map[old['type']]]
                code = old['code']
                if not code or H.login_key(code) in used_codes:
                    while True:
                        candidate = uuid.uuid4().hex.upper()[:10]
                        if H.login_key(candidate) not in used_codes:
                            break
                    self.log['regenerated_codes'].append({'old_id': old['id'], 'old_code': code, 'new_code': candidate})
                    code = candidate
                used_codes.add(H.login_key(code))
                values.update(id=self.allocate(table), code=code, category_id=category['id'], direction=category['direction'],
                              service_phone=old.get('phone'))
                person = self.contact_people.get(old['id'])
                if category['direction'] == 'DOWNSTREAM' and person:
                    values.update(contact=person['name'], phone=person['username'])
                self.maps['merchant'][old['id']] = values['id']
                self.merchant_values[old['id']] = values
                self.add(table, values)
        self.representatives = representatives

    def build(self):
        """Build every business aggregate and audit the explicitly removed tests."""
        self.contacts()
        self.merchants()
        for base, target in BASES.items():
            if base == 'merchant':
                continue
            for suffix in ['', '_archive']:
                for row in self.src.get(base+suffix, []):
                    if row.get('workorder_id', row['id'] if base == 'workorder' else None) in DROP_WORKORDERS:
                        self.log['removed_children'][base+suffix] += 1
                        if base == 'workorder':
                            self.log['removed_test_workorders'].append({'old_id': row['id'], 'code': row['code'], 'old_handler': row['handle_by']})
                        continue
                    self.maps[base][row['id']] = self.allocate(target)
        for base, target in BASES.items():
            if base in ['merchant', 'workorder']:
                continue
            for suffix in ['', '_archive']:
                for old in self.src.get(base+suffix, []):
                    if old['id'] not in self.maps[base]:
                        continue
                    parent = self.index['workorder'].get(old.get('workorder_id')) if 'workorder_id' in old else self.index['merchant'].get(old.get('merchant_id'))
                    values = self.common(target+suffix, old, parent)
                    values['id'] = self.maps[base][old['id']]
                    for field, entity in H.REFS.items():
                        if field in values and field not in ['update_by', 'handle_by', 'create_by']:
                            values[field] = None if old[field] is None else self.maps[entity][old[field]]
                    if base == 'workorder_file':
                        values['file_type'] = old['type']
                    self.add(target+suffix, values)
        for suffix in ['', '_archive']:
            for old in self.src['workorder'+suffix]:
                if old['id'] in DROP_WORKORDERS:
                    continue
                handler = self.maps['user'][old['handle_by']]
                values = self.common('biz_workorder'+suffix, old)
                person = self.index['user'].get(old['create_by'])
                representative = self.representatives.get(old['create_merchant_id'], old['create_merchant_id'])
                merchant = self.merchant_values.get(representative)
                submitter = person['name'] if person else merchant.get('contact') if merchant else None
                values.update(id=self.maps['workorder'][old['id']], created_by=handler, handle_by=handler,
                              submitter_name=submitter, remind_status=0, renewal_status_cycle=0,
                              renewal_reminder_disabled=0, commercial_renewal_status=0, commercial_renewal_cycle=0,
                              compulsory_renewal_status=0, compulsory_renewal_cycle=0)
                for field in ['create_merchant_id', 'handle_merchant_id', 'insurance_merchant_id']:
                    values[field] = None if old[field] is None else self.maps['merchant'][old[field]]
                self.add('biz_workorder'+suffix, values)
                self.log['creator_inferences'].append({'old_workorder_id': old['id'], 'new_workorder_id': values['id'],
                    'old_handler_id': old['handle_by'], 'new_created_by': handler, 'submitter_name': submitter,
                    'submitter_source': 'legacy_person' if person else 'retained_merchant_contact'})
                for part in ['quote', 'payment', 'underwriting', 'logistics']:
                    table = 'biz_workorder_'+part+suffix
                    detail = self.common(table, old)
                    detail.update(id=self.allocate(table), workorder_id=values['id'])
                    if part == 'payment':
                        detail.update(payee_name=old['pay_name'], payee_phone=old['pay_id_num'],
                                      payee_id_num=old['pay_id_num'] if old.get('pay_id_num') and len(old['pay_id_num']) == 18 else None,
                                      merchant_bank=old['pay_bank'], merchant_bank_card_num=old['pay_bank_card_num'])
                    self.add(table, detail)
                for side in ['upstream', 'downstream']:
                    table = 'biz_workorder_commission'+suffix
                    detail = self.common(table, old)
                    detail.update(id=self.allocate(table), workorder_id=values['id'], side=side.upper())
                    for field in ['compute_type', 'commercial_percentage', 'compulsory_percentage', 'vehicle_tax_percentage',
                                  'non_motor_percentage', 'commercial_amount', 'compulsory_amount', 'vehicle_tax_amount', 'non_motor_amount']:
                        original = field.replace('vehicle_tax', 'vehicle_and_vessel_tax')
                        detail[field] = old[side+'_'+original]
                    self.add(table, detail)
        assert len(self.out['biz_workorder']) == 3675
        assert len(self.out['biz_merchant']) == 4246
        assert Counter(x['direction'] for x in self.out['biz_merchant']) == {'UPSTREAM': 706, 'DOWNSTREAM': 3540}
        self.log['target_insert_counts'] = {t: len(r) for t, r in self.out.items()}
        self.log['id_maps'] = dict(self.maps)
        return self


def insert_batches(table, rows, fields):
    """Serialize staged rows in bounded batches, preserving exact decimal/text values."""
    if not rows:
        return []
    lines = []
    for start in range(0, len(rows), 250):
        values = ['(' + ','.join(H.literal(r.get(col)) for col in fields) + ')' for r in rows[start:start+250]]
        lines.append('INSERT INTO ' + H.ident(table) + '(' + ','.join(map(H.ident, fields)) + ') VALUES\n' + ',\n'.join(values) + ';')
    return lines


def condition_guard(condition, label):
    """Generate a strict CHECK assertion that aborts the client before COMMIT."""
    return H.guard(condition, label)


def generate_sql(plan, database, baseline):
    """Stage complete expected rows and verify every column inside one locked DML.

    Temporary tables normalize JSON/defaults exactly as the destination does. Real
    tables are not modified until staging succeeds. Any mismatch, source drift or
    SQL error disconnects the client and rolls back all production DML.
    """
    schema = plan.schema
    replaced = set(BASES.values()) | set(PARTS)
    replaced |= {t+'_archive' for t in list(replaced) if t+'_archive' in schema}
    reset = set(RESET) | {'biz_ocr_record_archive'}
    changed = replaced | reset
    sql = ['USE '+H.ident(database)+';', "SET SESSION time_zone='+08:00';",
           "SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';",
           'CREATE TEMPORARY TABLE migration_guard(v TINYINT NOT NULL CHECK(v=1)) ENGINE=InnoDB;']
    full_expected = {}
    for table in sorted(changed):
        temporary = 'mig_expected_'+table
        sql.append('CREATE TEMPORARY TABLE '+H.ident(temporary)+' LIKE '+H.ident(table)+';')
        retained = [r for r in plan.old_target.get(table, []) if r.get('enterprise_id') != 5]
        if table in ['biz_merchant_area', 'biz_merchant_area_archive']:
            retained = [r for r in retained if r.get('enterprise_id') is not None]
        fields = [name for name, col in schema[table].items() if 'GENERATED' not in col['extra']]
        rows = []
        for r in retained:
            rows.append({k: v for k, v in r.items() if k in fields})
        # Expected rows must model SQL defaults for columns not explicitly mapped.
        for r in plan.out.get(table, []):
            expanded = dict(r)
            for field in fields:
                if field not in expanded:
                    default = schema[table][field]['default']
                    expanded[field] = default
            rows.append(expanded)
        full_expected[table] = rows
        sql += insert_batches(temporary, rows, fields)
    sql.append('SET autocommit=0;')
    locks = [H.ident('insurance')+'.'+H.ident(t)+' READ' for t in sorted(plan.src)]
    locks += [H.ident(database)+'.'+H.ident(t)+' WRITE' for t in sorted(schema)]
    sql.append('LOCK TABLES '+',\n'.join(locks)+';')
    for fp in baseline:
        db = 'insurance' if fp['db']=='insurance' else database
        fields = plan.s['schema'][fp['db']][fp['table']]
        expr = H.row_expr(fields)
        full = H.ident(db)+'.'+H.ident(fp['table'])
        checks = ['COUNT(*)='+str(fp['n'])]
        for i in range(4):
            checks.append('COALESCE(BIT_XOR(CAST(CONV(SUBSTRING(SHA2('+expr+',256),'+str(1+16*i)+',16),16,10) AS UNSIGNED)),0)='+str(fp['h'+str(i)]))
        sql.append(condition_guard('(SELECT '+' AND '.join(checks)+' FROM '+full+')', 'unchanged baseline '+fp['db']+'.'+fp['table']))
    # Replace only approved business scope; platform configuration and financial
    # subscriptions/accounts remain unchanged, and no cloud file is deleted.
    for table in sorted(changed):
        scope = 'enterprise_id=5'
        if table in ['biz_merchant_area', 'biz_merchant_area_archive']:
            scope += ' OR enterprise_id IS NULL'
        sql.append('DELETE FROM '+H.ident(database)+'.'+H.ident(table)+' WHERE '+scope+';')
        fields = [name for name, col in schema[table].items() if 'GENERATED' not in col['extra']]
        if plan.out.get(table):
            columns = ','.join(map(H.ident, fields))
            sql.append('INSERT INTO '+H.ident(database)+'.'+H.ident(table)+'('+columns+') SELECT '+columns+' FROM '+H.ident('mig_expected_'+table)+' WHERE enterprise_id=5;')
    for table in sorted(changed):
        full = H.ident(database)+'.'+H.ident(table)
        temporary = H.ident('mig_expected_'+table)
        same = ['BINARY '+full+'.'+H.ident(col)+' <=> BINARY '+temporary+'.'+H.ident(col) for col in schema[table]]
        sql.append(condition_guard('(SELECT COUNT(*) FROM '+full+')='+str(len(full_expected[table])), 'exact row count '+table))
        sql.append(condition_guard('(SELECT COUNT(*) FROM '+full+' JOIN '+temporary+' ON '+full+'.id='+temporary+'.id WHERE NOT ('+' AND '.join('('+x+')' for x in same)+'))=0', 'every column identical '+table))
    sql += ['COMMIT;', 'UNLOCK TABLES;', 'SET autocommit=1;', "SELECT 'MIGRATION_VERIFIED_AND_COMMITTED';"]
    return '\n'.join(sql), full_expected


def backup(c, folder, db):
    """Save a complete local recovery dump and require its completion marker."""
    path = folder/(db+'_before_migration.sql')
    args = [r'D:\MySQL\file\bin\mysqldump.exe','-h','127.0.0.1','-P','3306','-u','root',
            '--default-character-set=utf8mb4','--single-transaction','--quick','--hex-blob',
            '--routines','--events','--triggers','--no-tablespaces','--set-gtid-purged=OFF',
            '--databases',db,'--result-file='+str(path)]
    result = subprocess.run(args, env=c.env, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    assert b'-- Dump completed on ' in path.read_bytes()[-400:]
    print('BACKUP_OK', db, path.stat().st_size, flush=True)
    return path


def verify_independent(c, snapshot, expected, database):
    """Read committed target rows independently and compare full normalized data.

    Uses fresh reads rather than success messages. All unaffected account/finance/
    configuration tables must exactly equal the pre-migration snapshot. The SQL
    transaction already checked all changed rows including generated columns.
    """
    queries = []
    for table, fields in snapshot['schema']['insurance_saas'].items():
        queries.append("SELECT JSON_OBJECT('table',"+H.literal(table)+",'row',"+H.row_expr(fields)+') FROM '+H.ident(database)+'.'+H.ident(table)+' ORDER BY id;')
    actual = {t: [] for t in snapshot['schema']['insurance_saas']}
    for r in c.query('\n'.join(queries)):
        actual[r['table']].append(r['row'])
    for table in actual:
        if table not in expected:
            assert canonical(actual[table]) == canonical(snapshot['rows']['insurance_saas'][table]), ('unaffected_table_changed',table)
            continue
        wanted = {r['id']: r for r in expected[table]}
        assert len(actual[table]) == len(wanted), ('count_mismatch',table)
        for row in actual[table]:
            desired = wanted[row['id']]
            for field, value in desired.items():
                current = row[field]
                col = snapshot['schema']['insurance_saas'][table][field]
                if value is not None and col['type']=='json':
                    value = json.loads(value) if isinstance(value,str) else value
                if value is not None and re.match(r'(?:tinyint|int|bigint|smallint)',col['type']):
                    value = int(value)
                if value is not None and col['type'].startswith('decimal'):
                    value,current = Decimal(str(value)),Decimal(str(current))
                if value is not None and col['type'].startswith(('datetime','timestamp','date')):
                    value,current = datetime.fromisoformat(str(value)),datetime.fromisoformat(str(current))
                assert value == current, ('value_mismatch',table,row['id'],field,value,current)
    return {'all_columns_checked_in_transaction': True, 'independent_readback': True,
            'unchanged_nonbusiness_tables': len(actual)-len(expected),
            'counts': {t: len(r) for t,r in actual.items() if t in expected},
            'workorders': len(actual['biz_workorder']),
            'merchants': dict(Counter(r['direction'] for r in actual['biz_merchant']))}


def prepare(c):
    """Capture backups, build the plan, and rehearse without editing live business."""
    folder = ROOT/'outputs/database-migration'/('run-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
    folder.mkdir(parents=True)
    snapshot = AUDIT.read_snapshot(c)
    assert len(snapshot['rows']['insurance_saas']['tenant_enterprise']) == 1
    assert snapshot['rows']['insurance_saas']['tenant_enterprise'][0]['id']==5
    for db in ['insurance','insurance_saas']:
        backup(c,folder,db)
    AUDIT.save_json(folder/'snapshot.json',snapshot)
    groups = c.query("SELECT JSON_OBJECT('first_id',MIN(id),'old_ids',JSON_ARRAYAGG(CAST(id AS CHAR))) FROM insurance.merchant WHERE is_delete=0 GROUP BY CASE WHEN type IN ('机构','保司') THEN 'UPSTREAM' ELSE 'DOWNSTREAM' END,TRIM(name) COLLATE utf8mb4_0900_ai_ci;")
    plan = Conversion(snapshot,groups).build()
    baseline = fingerprints(c,snapshot['schema'],{db:snapshot['schema'][db] for db in snapshot['schema']})
    AUDIT.save_json(folder/'plan.json', {'rows':plan.out,'audit':plan.log,'groups':groups,'baseline':baseline})
    sql,expected = generate_sql(plan,'insurance_saas',baseline)
    (folder/'apply.sql').write_text(sql,encoding='utf-8')
    AUDIT.save_json(folder/'expected.json',expected)
    clone = 'insurance_saas_check_'+datetime.now().strftime('%Y%m%d_%H%M%S')
    c.run('CREATE DATABASE '+H.ident(clone)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    restore = (folder/'insurance_saas_before_migration.sql').read_text(encoding='utf-8')
    restore = restore.replace('`insurance_saas`','`'+clone+'`')
    restore = re.sub(r'(/\*!50003 TRIGGER `[^`]+`(?:(?!\*/)[\s\S])*?);(\s*\*/;;)',r'\1\2',restore)
    c.run(restore)
    print('REHEARSAL_START',clone,flush=True)
    rehearsal,_ = generate_sql(plan,clone,baseline)
    (folder/'rehearsal.sql').write_text(rehearsal,encoding='utf-8')
    result = c.run(rehearsal)
    assert 'MIGRATION_VERIFIED_AND_COMMITTED' in result
    verification = verify_independent(c,snapshot,expected,clone)
    verification['database']=clone
    AUDIT.save_json(folder/'rehearsal_verification.json',verification)
    print('REHEARSAL_OK',json.dumps(verification,ensure_ascii=False),flush=True)
    print('RUN_DIRECTORY',folder,flush=True)


def apply(c, folder):
    """Apply the already rehearsed SQL, stopping if current source or target drifted."""
    folder=folder.resolve()
    assert folder.is_relative_to(ROOT/'outputs/database-migration')
    rehearsal=json.loads((folder/'rehearsal_verification.json').read_text(encoding='utf-8'))
    assert rehearsal['independent_readback'] and rehearsal['workorders']==3675
    assert not (folder/'production_verification.json').exists(), 'Already applied; do not rerun'
    snapshot=json.loads((folder/'snapshot.json').read_text(encoding='utf-8'))
    plan=json.loads((folder/'plan.json').read_text(encoding='utf-8'))
    current=fingerprints(c,snapshot['schema'],{db:snapshot['schema'][db] for db in snapshot['schema']})
    assert canonical(current)==canonical(plan['baseline']), 'Source or target changed since rehearsal'
    # The source dump contains three UPDATE triggers owned by cloud root@%, which
    # does not exist locally. The separately rehearsed repair changes ownership
    # only; backup preserves the exact original definitions. Apply it before DML,
    # since MySQL trigger DDL commits implicitly and must not split our DML transaction.
    repair=folder/'local_trigger_definer_fix.sql'
    if repair.exists():
        assert (folder/'clone_probes_passed.json').exists(), 'Trigger repair not rehearsed'
        definitions=c.query("SELECT JSON_OBJECT('name',TRIGGER_NAME,'definer',DEFINER,'body',ACTION_STATEMENT) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='insurance_saas' AND TRIGGER_NAME IN ('trg_biz_merchant_bu','trg_biz_workorder_bu','trg_biz_workorder_payment_bu');")
        assert len(definitions)==3
        assert all(t['definer']=='root@%' and t['body']=='SET NEW.updated_at = CURRENT_TIMESTAMP' for t in definitions)
        c.run(repair.read_text(encoding='utf-8'))
    sql=(folder/'apply.sql').read_text(encoding='utf-8')
    result=c.run(sql)
    assert 'MIGRATION_VERIFIED_AND_COMMITTED' in result
    expected=json.loads((folder/'expected.json').read_text(encoding='utf-8'))
    verification=verify_independent(c,snapshot,expected,'insurance_saas')
    after=fingerprints(c,snapshot['schema'],{'insurance':snapshot['schema']['insurance']})
    assert canonical(after)==canonical([r for r in plan['baseline'] if r['db']=='insurance']), 'Source unexpectedly changed'
    verification['source_unchanged']=True
    AUDIT.save_json(folder/'production_verification.json',verification)
    print('PRODUCTION_VERIFIED',json.dumps(verification,ensure_ascii=False),flush=True)


if __name__=='__main__':
    connection=client()
    if sys.argv[1]=='prepare':
        prepare(connection)
    elif sys.argv[1]=='apply':
        apply(connection,Path(sys.argv[2]))
    else:
        raise SystemExit('Expected prepare or apply <run directory>')
