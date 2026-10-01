#!/usr/bin/env python3
"""将旧车险库合并进已有 SaaS 库，先生成可审阅计划，再显式执行。

仅使用 Python 标准库及 MySQL 8.0.16+ 客户端。预览以一致性快照读取全部旧表；
生成独立 SQL、源数据快照和 ID/账号对照表。执行在表锁内核对快照并原子提交，
不删除既有数据、不修改公共角色权限、不在目标库创建永久迁移表。
"""

import argparse
from collections import defaultdict
from datetime import datetime, timedelta
from decimal import Decimal
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import unicodedata


DIRECT = {
    'merchant': 'biz_merchant', 'merchant_area': 'biz_merchant_area',
    'insurance': 'biz_insurance_product', 'system_file': 'sys_file',
    'workorder': 'biz_workorder', 'workorder_insurance': 'biz_workorder_insurance',
    'workorder_file': 'biz_workorder_file', 'vehicle_license': 'biz_vehicle_license',
    'vehicle_invoice': 'biz_vehicle_invoice', 'vehicle_certificate': 'biz_vehicle_certificate',
    'user': 'tenant_user',
}
EXTRA = ['tenant_enterprise', 'tenant_member', 'tenant_member_change_log',
         'saas_wallet', 'saas_subscription', 'biz_merchant_staff', 'biz_merchant_staff_role',
         'biz_workorder_quote', 'biz_workorder_commission', 'biz_workorder_payment',
         'biz_workorder_underwriting', 'biz_workorder_logistics']
CONFIG = ['biz_merchant_category', 'auth_role', 'auth_permission', 'auth_role_permission']
TIMES = {'create_time': 'created_at', 'update_time': 'updated_at',
         'registration_date': 'registration_date', 'issue_date': 'issue_date',
         'transfer_date': 'transfer_date', 'quotation_time': 'quotation_time',
         'underwriting_time': 'underwriting_time', 'finish_time': 'finish_time'}
REFS = {'merchant_id': 'merchant', 'create_merchant_id': 'merchant',
        'handle_merchant_id': 'merchant', 'insurance_merchant_id': 'merchant',
        'workorder_id': 'workorder', 'insurance_id': 'insurance', 'file_id': 'system_file',
        'update_by': 'user', 'handle_by': 'user', 'create_by': 'user'}
ROLE_CODES = {'admin': 'ADMIN', '出单员': 'ISSUER', '联系人': 'CONTACT',
              '收款人': 'PAYEE', '店员': 'CLERK'}


def ident(value):
    """验证命令行或元数据中的库表标识符，拒绝 SQL 注入及跨库误指向。

    输入为库名、表名或列名，返回带反引号的 SQL 标识符；非法名称抛出 ValueError，
    不进行任何数据库操作，也不允许用户输入改变查询范围。
    """
    if not re.fullmatch(r'[A-Za-z0-9_]+', value):
        raise ValueError('非法数据库/表标识符: ' + value)
    return '`' + value + '`'


def literal(value):
    """将业务值序列化为不依赖反斜杠转义的 SQL 字面量。

    NULL 和精确数字保留原义，文本用 UTF-8 十六进制保留换行和引号；返回的片段
    用于计划 SQL，不修改数据，避免业务文本被解释为 SQL 控制字符。
    """
    if value is None:
        return 'NULL'
    if isinstance(value, (int, float, Decimal)):
        return str(value)
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    return "CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4)"


def dump(value):
    """序列化计划或源快照为便于审阅的 JSON。

    输入为结构化记录，Decimal 以精确字符串输出，避免金额精度丢失；返回文本，
    文件路径及写入行为由调用者决定，不向控制台输出凭据。
    """
    return json.dumps(value, ensure_ascii=False, indent=2, default=str)


def login_key(value):
    """为内存测试和业务编号预检查生成 Unicode 比较键。

    输入原值，返回去重音、尾空格及大小写差异的键；不改变原始业务值。正式账号
    预览使用 MySQL 权重键，数据库实际比较及唯一约束始终是最终依据。
    """
    text = unicodedata.normalize('NFKD', str(value or '').rstrip()).casefold()
    return ''.join(c for c in text if not unicodedata.combining(c))


def unix_time(value):
    """转换旧库的秒时间戳，保持项目使用的 Asia/Shanghai 时间含义。

    输入时间戳或 NULL，返回 +08:00 下的 DATETIME 文本或 NULL；非法和越界值
    抛出异常，禁止截断或替换历史时间。原 bigint 保险起期不调用该转换。
    """
    if value is None:
        return None
    return (datetime(1970, 1, 1) + timedelta(seconds=int(value), hours=8)).isoformat(' ')


class Mysql:
    """通过现有 mysql 客户端建立短连接，密码只放子进程环境，不写入命令或产物。

    读取阶段仅执行 SELECT/SHOW 和只读快照事务；执行阶段使用一个连接接收完整
    SQL，客户端遇错退出并断开连接，InnoDB 自动回滚，禁止使用 --force。
    """

    def __init__(self, args):
        """由命令行参数建立客户端配置，不在此阶段连接数据库。

        login-path 使用本机凭据，其他方式使用 MYSQL_PWD 或交互密码；密码仅存
        子进程环境，生命周期限于本次执行，返回已配置的客户端对象。
        """
        self.command = [args.mysql]
        if args.login_path:
            self.command += ['--login-path=' + args.login_path]
        self.command += ['--host=' + args.host, '--port=' + str(args.port), '--user=' + args.user,
                         '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names',
                         '--binary-mode', '--max-allowed-packet=256M']
        self.env = os.environ.copy()
        if args.password_prompt:
            self.env['MYSQL_PWD'] = getpass.getpass('MySQL 密码: ')

    def run(self, sql):
        """在同一个 mysql 连接中执行调用者提供的完整 SQL。

        成功返回客户端输出，非零退出抛出 RuntimeError；不使用 shell 或 --force。
        写入事务遇错时连接退出，由 InnoDB 回滚未提交变化，调用者不能继续提交。
        """
        result = subprocess.run(self.command, input=sql.encode('utf-8'), capture_output=True, env=self.env)
        if result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8')

    def query(self, sql):
        """读取快照和预检查 SELECT 的逐行 JSON 输出。

        输入必须是每行输出一个 JSON 对象的查询，返回记录列表；按 Decimal 解析
        金额，避免二进制浮点误差。查询或 JSON 格式错误向上抛出以中止计划。
        """
        return [json.loads(line, parse_float=Decimal) for line in self.run(sql).splitlines() if line]


def row_expr(columns):
    """根据有序列元数据组装完整行的 JSON 表达式。

    输入实时列名集合，返回 JSON_OBJECT SQL，用于源快照及相同规则的指纹计算；
    覆盖无法直接映射的历史字段，不执行查询，也不只选择可迁移字段。
    """
    return 'JSON_OBJECT(' + ','.join(literal(c) + ',' + ident(c) for c in columns) + ')'


def fingerprint_sql(database, table, columns):
    """生成执行前核验源数据和公共配置变化的只读 SQL。

    输入库、表与有序列，返回聚合行数及完整行 SHA256 前 64 位异或的 SELECT；
    空表指纹为零，任一结果与预览不一致都会使后续事务中止。
    """
    expr = row_expr(columns)
    return ('SELECT COUNT(*) n, COALESCE(BIT_XOR(CAST(CONV(SUBSTRING(SHA2(' + expr +
            ',256),1,16),16,10) AS UNSIGNED)),0) h FROM ' + ident(database) + '.' + ident(table))


def snapshot(client, source, target):
    """一次只读一致性快照读取源库全部表、目标结构、主键高水位和账号/公共配置。

    目标库必须显式存在，所有涉及的表必须使用 InnoDB。源库出现未知非空表时后续
    构建阶段中止，防止数据库演进后脚本静默遗漏新业务；归档表也在覆盖范围内。
    """
    version = client.query("SELECT JSON_OBJECT('version',VERSION());")[0]['version']
    parts = re.match(r'(\d+)\.(\d+)\.(\d+)', version)
    if not parts or 'MariaDB' in version or tuple(map(int, parts.groups())) < (8, 0, 16):
        raise ValueError('需要 MySQL 8.0.16+（临时 CHECK 约束必须实际生效），当前: ' + version)
    metadata = client.query("SELECT JSON_OBJECT('db',TABLE_SCHEMA,'table',TABLE_NAME,'name',COLUMN_NAME,"
                            "'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,"
                            "'extra',EXTRA,'collation',COLLATION_NAME) FROM information_schema.columns "
                            'WHERE TABLE_SCHEMA IN (' + literal(source) + ',' + literal(target) +
                            ') ORDER BY TABLE_SCHEMA,TABLE_NAME,ORDINAL_POSITION;')
    schema = {source: defaultdict(dict), target: defaultdict(dict)}
    for col in metadata:
        schema[col['db']][col['table']][col['name']] = col
    if not schema[source] or not schema[target]:
        raise ValueError('源库或目标库不存在，请核对 --source/--target，脚本不会自动猜测库名')
    required = set(DIRECT.values()) | set(EXTRA) | set(CONFIG)
    for name in list(required):
        if name + '_archive' in schema[target]:
            required.add(name + '_archive')
    if not required <= set(schema[target]):
        raise ValueError('目标库缺少表: ' + str(sorted(required - set(schema[target]))))
    engines = client.query("SELECT JSON_OBJECT('db',TABLE_SCHEMA,'table',TABLE_NAME,'engine',ENGINE) "
                           'FROM information_schema.tables WHERE TABLE_SCHEMA IN (' + literal(source) +
                           ',' + literal(target) + ');')
    for item in engines:
        if (item['db'] == source or item['table'] in required) and item['engine'] != 'InnoDB':
            raise ValueError('不支持非 InnoDB 表: ' + item['db'] + '.' + item['table'])
    triggers = client.query("SELECT JSON_OBJECT('name',TRIGGER_NAME,'timing',ACTION_TIMING,"
                            "'event',EVENT_MANIPULATION,'statement',ACTION_STATEMENT) FROM information_schema.triggers "
                            'WHERE TRIGGER_SCHEMA=' + literal(target) + ' AND EVENT_OBJECT_TABLE IN (' +
                            ','.join(literal(t) for t in required) + ');')
    for trigger in triggers:
        if trigger['event'] != 'INSERT':
            continue
        normalized = re.sub(r'\s+', '', trigger['statement']).rstrip(';').upper()
        expected = ('SETNEW.`CREATED_AT`=COALESCE(NEW.`CREATED_AT`,CURRENT_TIMESTAMP),'
                    'NEW.`UPDATED_AT`=COALESCE(NEW.`UPDATED_AT`,CURRENT_TIMESTAMP)')
        if trigger['timing'] != 'BEFORE' or normalized != expected:
            raise ValueError('未审阅的插入触发器: ' + trigger['name'])
    queries = ['SET SESSION time_zone=\'+08:00\';',
               'SET TRANSACTION READ ONLY;', 'START TRANSACTION WITH CONSISTENT SNAPSHOT;']
    reads = set(CONFIG) | {'tenant_user', 'tenant_user_archive', 'tenant_enterprise', 'tenant_enterprise_archive'}
    reads &= required
    for database, tables in [(source, set(schema[source])), (target, required)]:
        for table in sorted(tables):
            full = ident(database) + '.' + ident(table)
            if database == target:
                queries.append("SELECT JSON_OBJECT('kind','stat','table'," + literal(table) +
                               ",'n',COUNT(*),'max',COALESCE(MAX(id),0)) FROM " + full + ';')
            if database == source or table in reads:
                expr = row_expr(schema[database][table])
                queries.append("SELECT JSON_OBJECT('kind','row','db'," + literal(database) +
                               ",'table'," + literal(table) + ",'row'," + expr +
                               ",'hash',SUBSTRING(SHA2(" + expr + ',256),1,16)) FROM ' + full + ' ORDER BY id;')
    queries.append('COMMIT;')
    data = {'source': source, 'target': target, 'schema': schema, 'rows': {source: {}, target: {}},
            'stats': {}, 'fingerprints': {source: {}, target: {}}, 'triggers': triggers}
    for database, tables in [(source, schema[source]), (target, reads)]:
        for table in tables:
            data['rows'][database][table] = []
            data['fingerprints'][database][table] = {'n': 0, 'h': 0}
    for item in client.query('\n'.join(queries)):
        if item['kind'] == 'stat':
            data['stats'][item['table']] = {'n': item['n'], 'max': item['max']}
        else:
            database, table = item['db'], item['table']
            data['rows'][database][table].append(item['row'])
            fp = data['fingerprints'][database][table]
            fp['n'] += 1
            fp['h'] ^= int(item['hash'], 16)
    return data


class Plan:
    """在内存构建企业及业务聚合，不修改源库或目标库。

    每类实体在正式/归档表共用新的 ID 空间，关系按旧实体 ID 映射。遇到源库悬空
    外键、同 ID 不同实体、字段截断或不可解释的角色时中止，绝不静默丢弃数据。
    """

    def __init__(self, data, enterprise_code, enterprise_name, owner_id=None, account_key=login_key):
        """根据一致性快照和企业参数初始化迁移计划状态。

        输入含实时结构与行数据，分配企业 ID 并建立审计容器；可指定拥有者旧 ID
        及账号比较器。对象只维护本次内存计划，不写数据库，编码用于重跑检查。
        """
        self.data = data
        self.source, self.target = data['source'], data['target']
        self.src = data['rows'][self.source]
        self.existing = data['rows'][self.target]
        self.schema = data['schema'][self.target]
        self.next_ids = {}
        self.output = defaultdict(list)
        self.maps = defaultdict(dict)
        self.audit = {'enterprise_code': enterprise_code, 'warnings': [], 'accounts': [], 'relations': []}
        self.now = datetime.now().isoformat(' ', timespec='seconds')
        self.code, self.name, self.owner_id = enterprise_code, enterprise_name, owner_id
        self.account_key = account_key
        self.enterprise_id = self.allocate('tenant_enterprise')

    def allocate(self, table):
        """为目标实体从正式与归档的最高主键之后分配新 ID。

        输入目标表名，返回新 BIGINT，同族表共享本次序列并递增内存状态；超界
        则中止，避免归档恢复时碰撞。真正写入前还会在表锁内检查目标未变化。
        """
        base = table.removesuffix('_archive')
        if base not in self.next_ids:
            self.next_ids[base] = max(self.data['stats'].get(base, {}).get('max', 0),
                                      self.data['stats'].get(base + '_archive', {}).get('max', 0))
        self.next_ids[base] += 1
        if self.next_ids[base] > 9223372036854775807:
            raise ValueError('主键超出 BIGINT: ' + base)
        return self.next_ids[base]

    def records(self, base):
        """按正式、归档顺序枚举一类旧实体，用于统一映射父子关联。

        输入源基础表名，逐项返回后缀和原始行；不改变记录。相同旧 ID 在两处
        出现时抛出 ValueError，禁止把不同历史版本猜测为同一实体。
        """
        seen = set()
        for suffix in ['', '_archive']:
            for row in self.src.get(base + suffix, []):
                if row['id'] in seen:
                    raise ValueError('源正式/归档 ID 重叠，需先确认实体版本: ' + base + ':' + str(row['id']))
                seen.add(row['id'])
                yield suffix, row

    def ref(self, base, old_id):
        """将旧外键转换为已分配的新实体主键。

        输入实体类别与旧 ID，NULL 原样返回；非空引用必须存在于正式/归档联合
        映射，否则抛出异常并拒绝整批计划，绝不借用目标库同号记录。
        """
        if old_id is None:
            return None
        if old_id not in self.maps[base]:
            raise ValueError('源库存在悬空关联: ' + base + ':' + str(old_id))
        return self.maps[base][old_id]

    def add(self, table, values):
        """按实时结构校验待插入行，拒绝未知列、非空违约、文本超长及无效 JSON。

        不复制 generated 列；该类唯一键由 MySQL 自动生成。没有目标字段的源内容保留
        在 source-snapshot.json，不用猜测字段用途，也不擅自改变目标结构。
        """
        if table not in self.schema:
            raise ValueError('目标缺少归档或业务表: ' + table)
        cols = self.schema[table]
        values = {k: v for k, v in values.items() if k not in cols or 'GENERATED' not in cols[k]['extra']}
        if set(values) - set(cols):
            raise ValueError('目标字段不存在: ' + table + ' ' + str(set(values) - set(cols)))
        for key, col in cols.items():
            if 'GENERATED' in col['extra'] or 'auto_increment' in col['extra']:
                continue
            if col['nullable'] == 'NO' and col['default'] is None and values.get(key) is None:
                raise ValueError('必填字段缺失: ' + table + '.' + key)
            if key not in values:
                continue
            value = values[key]
            if value is None and col['nullable'] == 'NO':
                raise ValueError('非空字段为 NULL: ' + table + '.' + key)
            length = re.match(r'(?:var)?char\((\d+)\)', col['type'])
            if value is not None and length and len(str(value)) > int(length[1]):
                raise ValueError('拒绝截断超长字段: ' + table + '.' + key)
            if value is not None and col['type'] == 'json' and isinstance(value, str):
                json.loads(value)
        self.output[table].append(values)
        return values['id']

    def common(self, table, row):
        """为一个目标业务行建立共同的历史字段和企业隔离字段。

        输入目标表与旧行，返回同名字段、软删除、转换后的时间以及新更新人 ID；
        只构造字典，不修改旧行。专有字段和外键由对应业务阶段继续转换。
        """
        values = {k: v for k, v in row.items() if k in self.schema[table] and k != 'id'}
        values.update(enterprise_id=self.enterprise_id, deleted=row.get('is_delete', 0))
        for old, new in TIMES.items():
            if old in row and new in self.schema[table]:
                values[new] = unix_time(row[old])
        if 'updated_by' in self.schema[table]:
            values['updated_by'] = self.ref('user', row.get('update_by'))
        return values

    def build(self):
        """完成从一致性快照到待插入业务行及审计对照的完整转换。

        依次映射基础实体、人员、企业、商户和工单聚合，最后核验源表覆盖并返回
        本计划；未知非空表、重复企业编码或关系错误会中止，不产生数据库变化。
        """
        for base, target in DIRECT.items():
            for suffix, row in self.records(base):
                self.maps[base][row['id']] = self.allocate(target)
        existing_codes = {login_key(r['code']) for t in ['tenant_enterprise', 'tenant_enterprise_archive']
                          for r in self.existing.get(t, [])}
        if login_key(self.code) in existing_codes:
            raise ValueError('企业编码已存在（包括归档），禁止重复导入: ' + self.code)
        self.users()
        self.configuration()
        self.merchants()
        self.members()
        for base in ['insurance', 'system_file', 'merchant_area', 'workorder_insurance',
                     'workorder_file', 'vehicle_license', 'vehicle_invoice', 'vehicle_certificate']:
            self.direct(base)
        self.workorders()
        allowed = set(DIRECT) | {t + '_archive' for t in DIRECT} | {'role', 'menu', 'user_role', 'user_role_archive', 'role_menu'}
        unknown = [t for t, rows in self.src.items() if rows and t not in allowed]
        if unknown:
            raise ValueError('存在未定义迁移契约的非空源表: ' + str(unknown))
        self.audit['source_counts'] = {t: len(rows) for t, rows in self.src.items()}
        self.audit['target_insert_counts'] = {t: len(rows) for t, rows in self.output.items()}
        self.audit['enterprise_id'] = self.enterprise_id
        self.audit['id_maps'] = dict(self.maps)
        for relation in self.audit['relations']:
            if relation['type'] == 'user_role':
                relation['new_user_id'] = self.ref('user', relation['old_user_id'])
                relation['new_staff_id'] = self.maps['staff'].get(relation['old_user_id'])
        return self

    def users(self):
        """保留全部旧用户的账号资料及 BCrypt 密码，不以相同手机号自动合并身份。

        目标登录采用 username OR phone，所以同时对两个命名空间查重。冲突账号
        追加迁移企业及旧 ID 后缀；冲突手机号置 NULL 并记入对照表，防止短信重置
        覆盖另一人的密码。商户身份另拆人员，只有原管理/出单角色建立企业成员。
        """
        used = {self.account_key(r.get(k)) for t in ['tenant_user', 'tenant_user_archive']
                for r in self.existing.get(t, []) for k in ['username', 'phone'] if r.get(k)}
        for suffix, row in self.records('user'):
            table = 'tenant_user' + suffix
            values = self.common(table, row)
            original = str(row.get('username') or '')
            username = original or 'legacy_' + str(row['id'])
            phone = original if re.fullmatch(r'1\d{10}', original) else None
            conflict = self.account_key(username) in used
            phone_conflict = phone is not None and self.account_key(phone) in used
            if conflict:
                tail = '_e' + str(self.enterprise_id) + '_u' + str(row['id'])
                username = username[:100 - len(tail)] + tail
                i = 0
                while self.account_key(username) in used:
                    i += 1
                    tail = '_e' + str(self.enterprise_id) + '_u' + str(row['id']) + '_' + str(i)
                    username = original[:100 - len(tail)] + tail
            if phone_conflict:
                phone = None
            used.add(self.account_key(username))
            if phone:
                used.add(self.account_key(phone))
            values.update(id=self.maps['user'][row['id']], username=username, phone=phone,
                          real_name=row.get('name'), status=row.get('status') if row.get('status') is not None else 0)
            values.pop('enterprise_id', None)
            self.add(table, values)
            self.audit['accounts'].append({'old_id': row['id'], 'new_id': values['id'], 'archive': bool(suffix),
                                          'old_username': original, 'username': username, 'phone': phone,
                                          'renamed': conflict, 'phone_conflict': phone_conflict,
                                          'old_merchant_id': row.get('merchant_id'),
                                          'new_merchant_id': self.ref('merchant', row.get('merchant_id'))})

    def configuration(self):
        """复用 SaaS 公共角色与权限，不导入旧全局主键，也不扩大既有企业的授权范围。

        旧 admin/出单员对应 ADMIN/ISSUER，商户角色对应人员角色；menu 按权限码匹配。
        旧 user_role/role_menu 原始历史全部保留在快照及关系审计，重复关系不重复建成员。
        """
        roles = {r['code']: r for r in self.existing['auth_role'] if not r['deleted'] and r['status'] == 1}
        if not {'OWNER', 'ADMIN', 'ISSUER'} <= roles.keys():
            raise ValueError('目标缺少启用的 OWNER/ADMIN/ISSUER 角色')
        self.role_names = {}
        self.role_rows = {}
        for row in self.src['role']:
            if row['name'] not in ROLE_CODES:
                raise ValueError('未知旧角色: ' + str(row['name']))
            self.role_names[row['id']] = row['name']
            self.role_rows[row['id']] = row
            self.audit['relations'].append({'type': 'role', 'old_id': row['id'],
                                            'role_code': ROLE_CODES[row['name']]})
        permissions = {r['code']: r['id'] for r in self.existing['auth_permission']
                       if r['system_code'] == 'INSURANCE' and not r['deleted'] and r['status'] == 1}
        grants = {(r['role_id'], r['permission_id']) for r in self.existing['auth_role_permission'] if not r['deleted']}
        menus = {r['id']: r for r in self.src['menu']}
        for row in self.src['menu']:
            if row.get('perms') and row['perms'] not in permissions:
                raise ValueError('目标缺少旧权限: ' + row['perms'])
            self.audit['relations'].append({'type': 'menu', 'old_id': row['id'],
                                            'permission_id': permissions.get(row.get('perms'))})
        for row in self.src['role_menu']:
            name = self.role_names[row['role_id']]
            code = menus[row['menu_id']]['perms']
            if name in ['admin', '出单员'] and not row['is_delete'] and code:
                if (roles[ROLE_CODES[name]]['id'], permissions[code]) not in grants:
                    raise ValueError('目标角色未授权旧权限: ' + name + '/' + code)
            self.audit['relations'].append({'type': 'role_menu', 'old_id': row['id'], 'permission': code,
                                            'role_code': ROLE_CODES[name], 'deleted': row['is_delete']})
        self.user_roles = defaultdict(list)
        for suffix, row in self.records('user_role'):
            self.ref('user', row['user_id'])
            if row['role_id'] not in self.role_names:
                raise ValueError('不存在的旧角色: ' + str(row['role_id']))
            self.user_roles[row['user_id']].append((suffix, row, self.role_names[row['role_id']]))
            self.audit['relations'].append({'type': 'user_role', 'old_id': row['id'], 'archive': bool(suffix),
                                            'old_user_id': row['user_id'], 'role': self.role_names[row['role_id']],
                                            'deleted': row['is_delete']})
        if not any((roles['OWNER']['id'], p) in grants for p in permissions.values()):
            raise ValueError('OWNER 未配置车险权限，创建企业后无法使用车险系统')

    def merchants(self):
        """按现有分类迁入上下游商户；重复/空编号生成确定性新编号，关联 ID 不依赖编号。

        未标注类型的历史商户采用原脚本 DEALER_STORE 兼容规则并告警。
        上游联系人字段保留；下游的联系人来自后续人员角色，避免两套身份混用。
        """
        categories = {r['code']: r for r in self.existing['biz_merchant_category']
                      if not r['deleted'] and r['status'] == 1}
        self.merchant_rows = {}
        codes = set()
        for suffix, row in self.records('merchant'):
            category = {'机构': 'INSURANCE_ORG', '保司': 'INSURANCE_ORG', '汽修厂': 'AUTO_REPAIR',
                        '代理人': 'AGENT', '车商店铺': 'DEALER_STORE'}.get(row['type'], 'DEALER_STORE')
            if category not in categories:
                raise ValueError('缺少商户分类: ' + category)
            if row['type'] not in ['机构', '保司', '汽修厂', '代理人', '车商店铺']:
                self.audit['warnings'].append('商户 ' + str(row['id']) + ' 未知类型按 DEALER_STORE 兼容')
            values = self.common('biz_merchant' + suffix, row)
            values.update(id=self.maps['merchant'][row['id']], category_id=categories[category]['id'],
                          service_phone=row.get('phone'), code=self.unique_code(row.get('code'), row['id'], codes, 'M'))
            if categories[category]['direction'] != 'UPSTREAM':
                values['contact'] = values['phone'] = None
            self.merchant_rows[row['id']] = (suffix, row, categories[category]['direction'])
            self.add('biz_merchant' + suffix, values)

    def unique_code(self, original, old_id, used, prefix):
        """为新企业内的商户或工单生成可追溯的唯一业务编号。

        输入原编号、旧 ID、已用集合和对象类型，保留唯一原值；空值或大小写/
        重音重复时加旧 ID 后缀，返回候选并更新集合及审计告警，数据库仍不写入。
        """
        code = str(original or '').strip()
        if not code or login_key(code) in used:
            i = 0
            while True:
                tail = '_legacy_' + str(old_id) + ('_' + str(i) if i else '')
                candidate = (code or prefix)[:100 - len(tail)] + tail
                if login_key(candidate) not in used:
                    break
                i += 1
            self.audit['warnings'].append(prefix + ' 编号 ' + repr(original) + ' -> ' + candidate)
            code = candidate
        used.add(login_key(code))
        return code

    def members(self):
        """建立单一企业及所有旧管理/出单人员成员关系，同时拆分下游商户人员。

        自动拥有者优先启用且已审批的 admin，否则保留候选人的待审批状态并告警。
        一个用户的多角色折叠为最高企业角色；多个联系人选旧 ID 最小者为 CONTACT，
        其余为 CLERK；多个收款人全部保留 PAYEE，但仅旧 ID 最小者默认收款。
        """
        users = list(self.records('user'))
        candidates = [(suffix, row) for suffix, row in users if not suffix and not row['is_delete']
                      and row.get('status') == 1 and any(name == 'admin' and not rr['is_delete'] and not rs
                      and self.role_rows[rr['role_id']].get('status', 1) == 1
                      and not self.role_rows[rr['role_id']].get('is_delete', 0)
                      for rs, rr, name in self.user_roles[row['id']])]
        if self.owner_id is None:
            candidates.sort(key=lambda pair: (pair[1].get('is_approval') != 1, pair[1]['id']))
            if not candidates:
                raise ValueError('没有启用的旧管理员，请指定合法的 --owner-old-id')
            owner = candidates[0][1]
        else:
            matching = [row for suffix, row in users if not suffix and row['id'] == self.owner_id
                        and not row['is_delete'] and row.get('status') == 1
                        and any(name in ['admin', '出单员'] and not rr['is_delete'] and not rs
                                and self.role_rows[rr['role_id']].get('status', 1) == 1
                                and not self.role_rows[rr['role_id']].get('is_delete', 0)
                                for rs, rr, name in self.user_roles[row['id']])]
            if not matching:
                raise ValueError('指定拥有者必须是未删除、启用的旧管理/出单人员')
            owner = matching[0]
        self.owner_id = owner['id']
        if owner.get('is_approval') != 1:
            self.audit['warnings'].append('拥有者旧 ID ' + str(self.owner_id) + ' 尚未审批，保留成员待审批状态 2')
        self.audit['owner_old_id'] = self.owner_id
        owner_account = next(a for a in self.audit['accounts'] if a['old_id'] == self.owner_id)
        self.add('tenant_enterprise', {'id': self.enterprise_id, 'name': self.name, 'code': self.code,
                 'owner_user_id': self.ref('user', self.owner_id), 'contact_name': owner.get('name'),
                 'contact_phone': owner_account['phone'], 'status': 1, 'source': 2,
                 'data_retention_enabled': 0, 'created_at': self.now, 'updated_at': self.now, 'deleted': 0})
        for table in ['saas_wallet', 'saas_subscription']:
            values = {'id': self.allocate(table), 'enterprise_id': self.enterprise_id,
                      'created_at': self.now, 'updated_at': self.now}
            if table == 'saas_wallet':
                values.update(balance_amount=0, frozen_amount=0, currency='CNY', status=1, deleted=0,
                              updated_by=self.ref('user', self.owner_id))
            else:
                values.update(status=0, user_limit=0, workorder_limit=0, ocr_quota=0, request_quota=0,
                              auto_renew_enabled=0)
            self.add(table, values)
        self.audit['warnings'].append('订阅按新建企业初始化为未开通，钱包余额为 0；不伪造充值或付费套餐')
        contact_selected, payee_selected = {}, set()
        for suffix, row in users:
            roles = self.user_roles[row['id']]
            live_names = {name for rs, rr, name in roles if not rs and not rr['is_delete']
                          and self.role_rows[rr['role_id']].get('status', 1) == 1
                          and not self.role_rows[rr['role_id']].get('is_delete', 0)}
            historical_names = {name for rs, rr, name in roles}
            enterprise_names = (live_names if not suffix and not row['is_delete'] else historical_names) & {'admin', '出单员'}
            if enterprise_names:
                code = 'OWNER' if row['id'] == self.owner_id else ('ADMIN' if 'admin' in enterprise_names else 'ISSUER')
                table = 'tenant_member' + suffix
                values = self.common(table, row)
                values.update(id=self.allocate(table), user_id=self.ref('user', row['id']), role_code=code,
                              status=0 if row.get('status') != 1 else (1 if row.get('is_approval') == 1 else 2),
                              joined_at=unix_time(row.get('create_time')))
                self.add(table, values)
                if not suffix and not row['is_delete']:
                    self.add('tenant_member_change_log', {'id': self.allocate('tenant_member_change_log'),
                        'enterprise_id': self.enterprise_id, 'event_type': 'JOIN',
                        'operator_user_id': self.ref('user', self.owner_id), 'target_user_id': values['user_id'],
                        'target_name_snapshot': row.get('name'), 'after_role_code': code,
                        'occurred_at': self.now, 'remark': 'insurance 存量业务迁移'})
            merchant_id = row.get('merchant_id')
            if merchant_id is None:
                continue
            self.ref('merchant', merchant_id)
            if self.merchant_rows[merchant_id][2] != 'DOWNSTREAM':
                continue
            table = 'biz_merchant_staff' + suffix
            staff_id = self.allocate(table)
            self.maps['staff'][row['id']] = staff_id
            values = self.common(table, row)
            values.update(id=staff_id, merchant_id=self.ref('merchant', merchant_id), name=row.get('name'),
                          phone=row.get('username') if re.fullmatch(r'1\d{10}', str(row.get('username') or '')) else None,
                          status=row.get('status') if row.get('status') is not None else 0, remark='由旧 user 表拆分')
            self.add(table, values)
            seen_roles = set()
            for rs, rr, name in roles:
                if name not in ['联系人', '收款人', '店员']:
                    continue
                deleted = max(row['is_delete'], rr['is_delete'])
                code = ROLE_CODES[name]
                active = not suffix and not rs and not deleted
                if code == 'CONTACT' and active:
                    if merchant_id in contact_selected and contact_selected[merchant_id] != row['id']:
                        code = 'CLERK'
                        self.audit['warnings'].append('商户 ' + str(merchant_id) + ' 重复联系人 ' + str(row['id']) + ' 转为 CLERK')
                    else:
                        contact_selected[merchant_id] = row['id']
                default = int(code == 'PAYEE' and active and merchant_id not in payee_selected)
                if default:
                    payee_selected.add(merchant_id)
                role_suffix = '_archive' if suffix or rs else ''
                dedup = (role_suffix, code, deleted)
                if dedup in seen_roles:
                    continue
                seen_roles.add(dedup)
                role_table = 'biz_merchant_staff_role' + role_suffix
                role_values = self.common(role_table, rr)
                role_values.update(id=self.allocate(role_table), merchant_id=self.ref('merchant', merchant_id),
                                   staff_id=staff_id, role_code=code, is_default=default, deleted=deleted)
                self.add(role_table, role_values)

    def direct(self, base):
        """迁移险种、文件、区域、车辆及明细，所有外键按基础实体映射统一替换。

        归档明细允许指向正式父实体，正式明细也可能保留归档文件关联，不能只在
        同一后缀内找父记录。文件仅保留存储路径/元数据，不复制外部文件二进制。
        """
        for suffix, row in self.records(base):
            table = DIRECT[base] + suffix
            values = self.common(table, row)
            values['id'] = self.maps[base][row['id']]
            for key, entity in REFS.items():
                if key in values:
                    values[key] = self.ref(entity, row[key])
            if base == 'workorder_file':
                values['file_type'] = row['type']
            self.add(table, values)

    def workorders(self):
        """把旧宽表拆为工单、报价、双侧佣金、支付、核保和物流，保留金额与状态。

        created_by/handle_by/updated_by 指向新用户，source_staff_id 指向新商户人员。
        支付证件字段按旧 pay_id_num 语义写入 payee_id_num，不能误写成手机号；
        收款人员仅在商户、姓名和证件匹配唯一时绑定，历史快照始终保留。
        """
        codes = set()
        staff = [(row, self.maps['staff'][row['id']]) for suffix, row in self.records('user')
                 if row['id'] in self.maps['staff']]
        for suffix, row in self.records('workorder'):
            table = 'biz_workorder' + suffix
            values = self.common(table, row)
            values.update(id=self.maps['workorder'][row['id']], code=self.unique_code(row.get('code'), row['id'], codes, 'W'),
                          created_by=self.ref('user', row.get('create_by')),
                          source_staff_id=self.maps['staff'].get(row.get('create_by')),
                          handle_by=self.ref('user', row.get('handle_by')))
            for key in ['create_merchant_id', 'handle_merchant_id', 'insurance_merchant_id']:
                values[key] = self.ref('merchant', row.get(key))
            self.add(table, values)
            for part in ['quote', 'payment', 'underwriting', 'logistics']:
                part_table = 'biz_workorder_' + part + suffix
                details = self.common(part_table, row)
                details.update(id=self.allocate(part_table), workorder_id=values['id'])
                if part == 'payment':
                    candidates = [sid for person, sid in staff if person.get('merchant_id') == row.get('create_merchant_id')
                                  and person.get('name') == row.get('pay_name') and row.get('pay_id_num')
                                  and person.get('id_num') == row['pay_id_num']]
                    details.update(payee_staff_id=candidates[0] if len(candidates) == 1 else None,
                                   payee_name=row.get('pay_name'), payee_id_num=row.get('pay_id_num'),
                                   merchant_bank=row.get('pay_bank'), merchant_bank_card_num=row.get('pay_bank_card_num'))
                    if len(candidates) == 1:
                        phone = next(person.get('username') for person, sid in staff if sid == candidates[0])
                        details['payee_phone'] = phone if re.fullmatch(r'1\d{10}', str(phone or '')) else None
                self.add(part_table, details)
            for side in ['upstream', 'downstream']:
                part_table = 'biz_workorder_commission' + suffix
                details = self.common(part_table, row)
                details.update(id=self.allocate(part_table), workorder_id=values['id'], side=side.upper())
                for field in ['compute_type', 'commercial_percentage', 'compulsory_percentage', 'vehicle_tax_percentage',
                              'non_motor_percentage', 'commercial_amount', 'compulsory_amount', 'vehicle_tax_amount', 'non_motor_amount']:
                    old = field.replace('vehicle_tax', 'vehicle_and_vessel_tax')
                    details[field] = row.get(side + '_' + old)
                self.add(part_table, details)


def guard(condition, label):
    """将执行前或数量条件转为临时 CHECK 断言 SQL。

    输入布尔 SQL 与审阅标签，返回校验 INSERT；条件不成立写入 0 触发约束错误，
    让未使用 --force 的客户端断开并回滚，而不是继续导入或静默跳过异常。
    """
    return '-- ' + label + '\nINSERT INTO migration_guard(v) SELECT IF((' + condition + '),1,0);'


def database_account_key(client, schema):
    """用目标真实排序规则计算登录比较键，覆盖大小写、重音及 Unicode 等价冲突。

    username 与 phone 的排序规则必须一致；否则登录 OR 查询的比较语义需人工确认。
    结果只缓存于预览内存，不改变数据库。别名候选也使用相同规则验证。
    """
    collations = {schema[t][c]['collation'] for t in ['tenant_user', 'tenant_user_archive']
                  if t in schema for c in ['username', 'phone']}
    if len(collations) != 1:
        raise ValueError('用户账号/手机号排序规则不一致，请先确认登录比较语义')
    collation = collations.pop()
    ident(collation)
    cache = {}

    def key(value):
        """读取单个账号在目标排序规则下的比较权重并缓存。

        输入账号候选，返回 MySQL 权重字符串；仅执行 SELECT，不改变身份数据，
        相同输入复用内存结果，不向控制台输出账号或凭据。
        """
        value = str(value or '')
        if value not in cache:
            cache[value] = client.query("SELECT JSON_OBJECT('key',HEX(WEIGHT_STRING(" + literal(value) +
                                         ' COLLATE ' + collation + ')));')[0]['key']
        return cache[value]

    return key


def render_sql(plan):
    """生成完整可审阅事务：先表锁核验计划，再分批插入并校验数量，最后一次提交。

    LOCK TABLES 在 autocommit=0 之后执行，之后不再 START TRANSACTION，避免释放锁。
    锁覆盖源表、目标写入表及读取的归档/公共配置，目标变化时中止并要求重新预览。
    """
    data = plan.data
    source, target = plan.source, plan.target
    lines = ['-- Generated plan: execute using this script only; do not use mysql --force.',
             '-- A mysql client error disconnects and rolls back all InnoDB inserts.',
             'USE ' + ident(target) + ';',
             'SET SESSION time_zone=\'+08:00\';', "SET SESSION sql_mode='STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';",
             'CREATE TEMPORARY TABLE migration_guard(v TINYINT NOT NULL CHECK (v=1)) ENGINE=InnoDB;',
             'SET autocommit=0;']
    locks = [ident(source) + '.' + ident(t) + ' READ' for t in sorted(plan.src)]
    locks += [ident(target) + '.' + ident(t) + (' WRITE' if t in plan.output else ' READ') for t in sorted(data['stats'])]
    lines.append('LOCK TABLES ' + ',\n'.join(locks) + ';')
    trigger_tables = ','.join(literal(t) for t in data['stats'])
    lines.append(guard('(SELECT COUNT(*) FROM information_schema.triggers WHERE TRIGGER_SCHEMA=' +
                       literal(target) + ' AND EVENT_OBJECT_TABLE IN (' + trigger_tables + '))=' +
                       str(len(data['triggers'])), '触发器数量未变化'))
    for trigger in data['triggers']:
        lines.append(guard('EXISTS(SELECT 1 FROM information_schema.triggers WHERE TRIGGER_SCHEMA=' + literal(target) +
                           ' AND TRIGGER_NAME=' + literal(trigger['name']) + ' AND BINARY ACTION_STATEMENT=BINARY ' +
                           literal(trigger['statement']) + ' AND ACTION_TIMING=' + literal(trigger['timing']) +
                           ' AND EVENT_MANIPULATION=' + literal(trigger['event']) + ')', '触发器定义未变化: ' + trigger['name']))
    for table, stat in data['stats'].items():
        full = ident(target) + '.' + ident(table)
        lines.append(guard('(SELECT COUNT(*) FROM ' + full + ')=' + str(stat['n']) +
                           ' AND (SELECT COALESCE(MAX(id),0) FROM ' + full + ')=' + str(stat['max']),
                           '目标主键及行数未变化: ' + table))
    for database in [source, target]:
        for table, fp in data['fingerprints'][database].items():
            check = fingerprint_sql(database, table, data['schema'][database][table])
            lines.append(guard('(SELECT n=' + str(fp['n']) + ' AND h=' + str(fp['h']) + ' FROM (' + check + ') snapshot_check)',
                               '源数据/账号/公共配置未变化: ' + database + '.' + table))
    for account in plan.audit['accounts']:
        username, phone = literal(account['username']), literal(account['phone'])
        for table in ['tenant_user', 'tenant_user_archive']:
            if table not in data['stats']:
                continue
            full = ident(target) + '.' + ident(table)
            condition = ('NOT EXISTS(SELECT 1 FROM ' + full + ' WHERE username=' + username + ' OR phone=' + username + ')')
            if account['phone']:
                condition += ' AND NOT EXISTS(SELECT 1 FROM ' + full + ' WHERE username=' + phone + ' OR phone=' + phone + ')'
            lines.append(guard(condition, '按目标排序规则再次检查账号: ' + str(account['old_id'])))
    priority = ['tenant_user', 'tenant_enterprise', 'saas_wallet', 'saas_subscription',
                'biz_insurance_product', 'biz_merchant', 'sys_file', 'tenant_member',
                'tenant_member_change_log', 'biz_merchant_staff', 'biz_merchant_staff_role', 'biz_workorder']
    tables = sorted(plan.output, key=lambda t: (priority.index(t.removesuffix('_archive'))
                    if t.removesuffix('_archive') in priority else len(priority), t))
    for table in tables:
        rows = plan.output[table]
        full = ident(target) + '.' + ident(table)
        groups = defaultdict(list)
        for row in rows:
            groups[tuple(row)].append(row)
        for cols, batch in groups.items():
            for start in range(0, len(batch), 200):
                chunk = batch[start:start + 200]
                lines.append('INSERT INTO ' + full + '(' + ','.join(ident(c) for c in cols) + ') VALUES\n' +
                             ',\n'.join('(' + ','.join(literal(row[c]) for c in cols) + ')' for row in chunk) + ';')
        expected = data['stats'][table]['n'] + len(rows)
        lines.append(guard('(SELECT COUNT(*) FROM ' + full + ')=' + str(expected), '迁入数量一致: ' + table))
    # 迁入账号之间也必须按真实排序规则比较，补足 Python 的 Unicode 预检查差异。
    selects = []
    for suffix in ['', '_archive']:
        ids = ','.join(str(a['new_id']) for a in plan.audit['accounts'] if a['archive'] == bool(suffix)) or 'NULL'
        if 'tenant_user' + suffix in data['stats']:
            selects.append('SELECT id,username,phone FROM ' + ident(target) + '.tenant_user' + suffix + ' WHERE id IN (' + ids + ')')
    # LOCK TABLES 使用同一基表别名需要额外锁，故采用派生临时表进行账号间比较。
    lines.append('CREATE TEMPORARY TABLE migration_accounts AS ' + ' UNION ALL '.join(selects) + ';')
    lines.append('CREATE TEMPORARY TABLE migration_accounts_copy AS SELECT * FROM migration_accounts;')
    lines.append(guard('NOT EXISTS(SELECT 1 FROM migration_accounts a JOIN migration_accounts_copy b ON a.id<>b.id '
                       'AND (a.username=b.username OR a.username=b.phone OR a.phone=b.username OR a.phone=b.phone))',
                       '迁入账号间无登录歧义'))
    lines += ['COMMIT;', 'UNLOCK TABLES;', 'SELECT \'migration committed\';']
    return '\n'.join(lines) + '\n'


def validate_sql(client, sql):
    """只读核验审阅计划：执行写入前的全部快照判断，并 EXPLAIN 每批业务 INSERT。

    快照判断在 READ ONLY 一致性事务内运行；EXPLAIN 独立只分析、不执行写入，
    因而此验证不能证明触发器、唯一键、磁盘空间及提交一定成功；实际执行仍靠锁内
    校验和整批事务保护。计划过期则返回失败，要求重新预览，而不是继续执行旧计划。
    """
    statements = '\n'.join(line for line in sql.splitlines() if not line.startswith('--')).split(';')
    explains, checks = [], []
    encountered_write = False
    for statement in statements:
        statement = statement.strip()
        if statement.startswith('INSERT INTO `'):
            encountered_write = True
            explains.append('EXPLAIN ' + statement + ';')
        elif statement.startswith('INSERT INTO migration_guard') and not encountered_write:
            expression = statement[statement.index('SELECT IF') + len('SELECT '):]
            checks.append("SELECT JSON_OBJECT('check'," + str(len(checks) + 1) + ",'ok'," + expression + ');')
    begin = "SET SESSION time_zone='+08:00'; SET TRANSACTION READ ONLY; START TRANSACTION WITH CONSISTENT SNAPSHOT;\n"
    results = client.query(begin + '\n'.join(checks) + '\nROLLBACK;')
    failed = [r['check'] for r in results if r['ok'] != 1]
    if failed:
        raise ValueError('计划已过期或账号发生冲突，重新预览。失败校验序号: ' + str(failed))
    # MySQL 将 EXPLAIN INSERT 分类为写语句而拒绝放入 READ ONLY 事务，但 EXPLAIN
    # 自身不会执行 INSERT；因此单独解析这些语句，不改变任何表或启动写入事务。
    client.run("SET SESSION time_zone='+08:00';\n" + '\n'.join(explains))
    return {'preflight_checks': len(checks), 'explain_insert_batches': len(explains)}


def main():
    """提供 preview/apply 两阶段入口；apply 只能执行已审阅且哈希未变的计划。

    默认只读预览。执行必须传 --confirm-code，确认企业编码并要求停写及备份；
    执行完成后写本地回执。重复执行即使回执丢失，也由数据库指纹变化拒绝。
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['preview', 'validate', 'apply'])
    parser.add_argument('--source', default='insurance')
    parser.add_argument('--target', required=True)
    parser.add_argument('--enterprise-code', default='LEGACY_INSURANCE_20260928')
    parser.add_argument('--enterprise-name', default='小马e保存量业务')
    parser.add_argument('--owner-old-id', type=int)
    parser.add_argument('--plan-dir', required=True)
    parser.add_argument('--confirm-code')
    parser.add_argument('--mysql', default='mysql')
    parser.add_argument('--host', default='localhost')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--login-path')
    parser.add_argument('--password-prompt', action='store_true')
    args = parser.parse_args()
    ident(args.source)
    ident(args.target)
    if args.source == args.target:
        raise ValueError('源库与目标库不能相同')
    client = Mysql(args)
    folder = Path(args.plan_dir).resolve()
    if args.mode == 'preview':
        if folder.exists() and any(folder.iterdir()):
            raise ValueError('预览目录非空，请使用新目录以保留此前审阅结果')
        data = snapshot(client, args.source, args.target)
        plan = Plan(data, args.enterprise_code, args.enterprise_name, args.owner_old_id,
                    database_account_key(client, data['schema'][args.target])).build()
        sql = render_sql(plan)
        folder.mkdir(parents=True, exist_ok=True)
        (folder / '.gitignore').write_text('*\n!.gitignore\n', encoding='utf-8')
        (folder / 'source-snapshot.json').write_text(dump(data['rows'][args.source]), encoding='utf-8')
        (folder / 'migration.sql').write_text(sql, encoding='utf-8')
        plan.audit.update(source=args.source, target=args.target,
                          sql_sha256=hashlib.sha256(sql.encode('utf-8')).hexdigest())
        (folder / 'manifest.json').write_text(dump(plan.audit), encoding='utf-8')
        print(dump({k: v for k, v in plan.audit.items() if k not in ['accounts', 'id_maps', 'relations']}))
        print('只读预览完成: ' + str(folder))
    else:
        manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
        sql = (folder / 'migration.sql').read_text(encoding='utf-8')
        if args.target != manifest['target'] or args.source != manifest['source']:
            raise ValueError('执行库名与预览计划不一致')
        if hashlib.sha256(sql.encode('utf-8')).hexdigest() != manifest['sql_sha256']:
            raise ValueError('SQL 已被修改，请重新生成预览，禁止执行未审阅计划')
        if (folder / 'committed.json').exists():
            raise ValueError('该计划已经执行，禁止重复导入')
        if args.mode == 'validate':
            result = validate_sql(client, sql)
            result['sql_sha256'] = manifest['sql_sha256']
            (folder / 'validated.json').write_text(dump(result), encoding='utf-8')
            print(dump(result))
            return
        if args.confirm_code != manifest['enterprise_code']:
            raise ValueError('执行必须用 --confirm-code 明确确认预览计划中的企业编码')
        validate_sql(client, sql)
        print(client.run(sql))
        (folder / 'committed.json').write_text(dump({'enterprise_id': manifest['enterprise_id'],
                  'committed_at': datetime.now().isoformat(), 'sql_sha256': manifest['sql_sha256']}), encoding='utf-8')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, KeyError) as error:
        print('迁移中止: ' + str(error), file=sys.stderr)
        sys.exit(1)
