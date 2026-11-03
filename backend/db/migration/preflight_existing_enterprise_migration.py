"""Inspect the local legacy-to-existing-enterprise migration without writing SQL.

Reads both databases in one consistent read-only transaction and stores a complete
snapshot for migration review. BIGINT IDs and Decimal values retain their exact
values through the official client and Python JSON parser. This command only
produces local evidence; it cannot execute a migration or modify either database.
"""
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('legacy_migration', Path(__file__).with_name('migrate_insurance_to_saas.py'))
HELPERS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HELPERS)
ACCOUNT_MAP = {'15762502276': 1, '18601785588': 31, '13020176130': 36,
               '13818811250': 35, '15937123428': 38, '15601908394': 37}


def save_json(path, value):
    """Save evidence with exact decimal strings rather than binary floating point.

    The destination is an audit folder under the project; sensitive source values
    remain local and are not printed in the user-facing diagnostic summary.
    """
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, default=str), encoding='utf-8')


def read_snapshot(client):
    """Capture ordered schemas and all rows from both local databases atomically.

    This transaction explicitly forbids writes. The snapshot includes archives,
    configuration, and fields with no direct destination so they cannot disappear
    unnoticed during subsequent conversion planning.
    """
    columns = client.query("SELECT JSON_OBJECT('db',TABLE_SCHEMA,'table',TABLE_NAME,'name',COLUMN_NAME,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA IN ('insurance','insurance_saas') ORDER BY TABLE_SCHEMA,TABLE_NAME,ORDINAL_POSITION;")
    schema = defaultdict(lambda: defaultdict(dict))
    for column in columns:
        schema[column['db']][column['table']][column['name']] = column
    queries = ["SET SESSION time_zone='+08:00';", 'SET TRANSACTION READ ONLY;', 'START TRANSACTION WITH CONSISTENT SNAPSHOT;']
    for db in sorted(schema):
        for table, fields in sorted(schema[db].items()):
            queries.append("SELECT JSON_OBJECT('db'," + HELPERS.literal(db) + ",'table'," + HELPERS.literal(table) + ",'row'," + HELPERS.row_expr(fields) + ') FROM ' + HELPERS.ident(db) + '.' + HELPERS.ident(table) + ' ORDER BY id;')
    queries.append('COMMIT;')
    data = {db: {table: [] for table in schema[db]} for db in schema}
    for record in client.query('\n'.join(queries)):
        data[record['db']][record['table']].append(record['row'])
    return {'schema': schema, 'rows': data}


def analyze(snapshot):
    """Identify conversions requiring user decisions and structural blockers.

    Every source table is counted, source references are checked across live and
    archived entities, and unmapped historic actors are recorded instead of being
    assigned to a guessed account. Output contains summaries and actionable cases.
    """
    source = snapshot['rows']['insurance']
    target = snapshot['rows']['insurance_saas']
    report = {'source_counts': {t: len(r) for t, r in source.items()},
              'target_counts': {t: len(r) for t, r in target.items()},
              'account_mapping': [], 'overlapping_source_ids': {}, 'missing_references': [],
              'unmapped_user_references': [], 'same_priority_contacts': [],
              'invalid_json': [], 'out_of_range_times': [], 'duplicate_codes': {}}
    indexes = {}
    for base in ['user', 'merchant', 'workorder', 'insurance', 'system_file']:
        indexes[base] = {}
        for table in [base, base + '_archive']:
            for row in source.get(table, []):
                if row['id'] in indexes[base]:
                    report['overlapping_source_ids'].setdefault(base, []).append(row['id'])
                indexes[base][row['id']] = row
    targets = {u['id']: u for u in target['tenant_user']}
    mapped_ids = {}
    for username, destination in ACCOUNT_MAP.items():
        matches = [u for u in indexes['user'].values() if u['username'] == username]
        if len(matches) != 1 or destination not in targets:
            raise ValueError('Explicit account map is not unique or destination missing: ' + username)
        user = matches[0]
        mapped_ids[user['id']] = destination
        report['account_mapping'].append({'old_id': user['id'], 'old_username': username,
                                         'old_name': user['name'], 'new_id': destination,
                                         'new_name': targets[destination]['real_name']})
    references = {'merchant_id': 'merchant', 'create_merchant_id': 'merchant',
                  'handle_merchant_id': 'merchant', 'insurance_merchant_id': 'merchant',
                  'workorder_id': 'workorder', 'insurance_id': 'insurance',
                  'file_id': 'system_file', 'create_by': 'user', 'handle_by': 'user',
                  'update_by': 'user', 'user_id': 'user'}
    unresolved = Counter()
    for table, records in source.items():
        if table in ['menu', 'role', 'role_menu', 'page_test']:
            continue
        for row in records:
            for field, entity in references.items():
                value = row.get(field)
                if value is None:
                    continue
                if value not in indexes[entity]:
                    report['missing_references'].append({'table': table, 'id': row['id'], 'field': field, 'value': value})
                if entity == 'user' and value not in mapped_ids:
                    unresolved[(table, field, value)] += 1
            for field, value in row.items():
                if value is None:
                    continue
                # Only columns whose destination really uses MySQL JSON need JSON
                # parsing. Legacy option_json/default_option_json strings are
                # intentionally plain option labels in the current contract.
                destination = HELPERS.DIRECT.get(table.removesuffix('_archive'))
                if destination and table.endswith('_archive'):
                    destination += '_archive'
                destination_column = snapshot['schema']['insurance_saas'].get(destination, {}).get(field, {})
                if destination_column.get('type') == 'json':
                    try:
                        json.loads(value)
                    except (ValueError, TypeError):
                        report['invalid_json'].append({'table': table, 'id': row['id'], 'field': field})
                if field in HELPERS.TIMES:
                    try:
                        HELPERS.unix_time(value)
                    except (ValueError, OverflowError):
                        report['out_of_range_times'].append({'table': table, 'id': row['id'], 'field': field, 'value': value})
    for (table, field, user_id), count in sorted(unresolved.items()):
        user = indexes['user'].get(user_id, {})
        report['unmapped_user_references'].append({'table': table, 'field': field, 'old_user_id': user_id,
                                                  'old_name': user.get('name'), 'old_username': user.get('username'),
                                                  'merchant_id': user.get('merchant_id'), 'count': count})
    roles = {r['id']: r['name'] for r in source['role']}
    candidates = defaultdict(lambda: defaultdict(set))
    for relation in source['user_role']:
        user = indexes['user'].get(relation['user_id'], {})
        role = roles.get(relation['role_id'])
        if not relation['is_delete'] and not user.get('is_delete', 1) and user.get('merchant_id') and role in ['联系人', '收款人', '店员']:
            candidates[user['merchant_id']][role].add(user['id'])
    for merchant_id, by_role in sorted(candidates.items()):
        for role in ['联系人', '收款人', '店员']:
            if not by_role.get(role):
                continue
            if len(by_role[role]) > 1:
                report['same_priority_contacts'].append({'merchant_id': merchant_id,
                    'merchant_name': indexes['merchant'][merchant_id]['name'], 'role': role,
                    'candidates': [{'id': uid, 'name': indexes['user'][uid]['name'], 'phone': indexes['user'][uid]['username']} for uid in sorted(by_role[role])]})
            break
    for base in ['merchant', 'workorder']:
        codes = Counter(r.get('code') for r in indexes[base].values())
        report['duplicate_codes'][base] = {str(k): v for k, v in codes.items() if v > 1 or not k}
    report['legacy_payment_value_lengths'] = dict(Counter(len(r['pay_id_num']) for r in source['workorder'] if r.get('pay_id_num')))
    report['legacy_renewal_states'] = dict(Counter(str(r.get('remind_status')) for r in source['workorder']))
    return report


def main():
    """Configure the existing local client and write a read-only migration audit.

    Credentials come from the application configuration and only reach the child
    process environment. Prints counts and artifact paths, never user passwords.
    """
    config = (ROOT / 'backend/insruance/src/main/resources/application.yml').read_text(encoding='utf-8')
    os.environ['MYSQL_PWD'] = re.search(r'^\s+password:\s*(.+)$', config, re.M)[1].strip().strip('\"\'')
    client = HELPERS.Mysql(SimpleNamespace(mysql=r'D:\MySQL\file\bin\mysql.exe', login_path=None,
                                         host='127.0.0.1', port=3306, user='root', password_prompt=False))
    snapshot = read_snapshot(client)
    report = analyze(snapshot)
    folder = ROOT / 'outputs/database-migration' / ('preflight-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
    folder.mkdir(parents=True)
    save_json(folder / 'snapshot.json', snapshot)
    save_json(folder / 'preflight.json', report)
    print(json.dumps({'folder': str(folder), 'source_counts': report['source_counts'],
                      'missing_references': len(report['missing_references']),
                      'invalid_json': len(report['invalid_json']),
                      'overlapping_source_ids': report['overlapping_source_ids'],
                      'out_of_range_times': report['out_of_range_times'],
                      'same_priority_contacts': report['same_priority_contacts'],
                      'duplicate_codes': report['duplicate_codes']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
