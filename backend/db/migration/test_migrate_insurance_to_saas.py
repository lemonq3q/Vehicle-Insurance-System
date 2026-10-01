"""验证迁移的身份隔离、归档关联、角色约束和失败保护，不向数据库写数据。"""

from collections import defaultdict
import unittest

from migrate_insurance_to_saas import CONFIG, DIRECT, EXTRA, Plan, render_sql


def fixture():
    """构造已有企业与旧业务并存的最小数据，覆盖人员多角色及跨正式/归档外键。"""
    common = 'id enterprise_id created_at updated_at updated_by deleted'
    fields = {
        'tenant_user': 'username phone email password real_name id_num status',
        'tenant_enterprise': 'name code owner_user_id contact_name contact_phone status source data_retention_enabled',
        'tenant_member': 'user_id role_code status joined_at',
        'tenant_member_change_log': 'event_type operator_user_id target_user_id target_name_snapshot after_role_code occurred_at remark',
        'saas_wallet': 'balance_amount frozen_amount currency status',
        'saas_subscription': 'status user_limit workorder_limit ocr_quota request_quota auto_renew_enabled',
        'biz_merchant': 'code name category_id contact phone service_phone',
        'biz_merchant_staff': 'merchant_id name phone email id_num status remark',
        'biz_merchant_staff_role': 'merchant_id staff_id role_code is_default',
        'biz_insurance_product': 'name type options_json',
        'sys_file': 'path file_name is_linked',
        'biz_merchant_area': 'merchant_id area_code',
        'biz_workorder': 'code create_merchant_id handle_merchant_id insurance_merchant_id created_by handle_by source_staff_id',
        'biz_workorder_file': 'workorder_id file_id file_type',
        'biz_workorder_insurance': 'workorder_id insurance_id option_json deductible_option_json',
        'biz_workorder_payment': 'workorder_id required_pay_amount payee_staff_id payee_name payee_phone payee_id_num merchant_bank merchant_bank_card_num',
        'biz_workorder_commission': 'workorder_id side compute_type commercial_percentage compulsory_percentage vehicle_tax_percentage non_motor_percentage commercial_amount compulsory_amount vehicle_tax_amount non_motor_amount',
    }
    tables = set(DIRECT.values()) | set(EXTRA) | set(CONFIG)
    schema, stats = {}, {}
    for table in tables:
        for suffix in ['', '_archive']:
            name = table + suffix
            names = (common + ' ' + fields.get(table, 'workorder_id')).split()
            schema[name] = {c: {'type': 'varchar(100)' if c in ['username', 'code'] else 'bigint',
                               'nullable': 'YES', 'default': None, 'extra': ''} for c in names}
            stats[name] = {'n': 0, 'max': 100 if suffix else 50}
    rows = {'insurance': defaultdict(list), 'insurance_saas': defaultdict(list)}
    for base in set(DIRECT) | {'role', 'menu', 'role_menu', 'user_role'}:
        rows['insurance'][base] = []
    users = []
    for uid in [1, 2, 3, 4]:
        users.append({'id': uid, 'username': '1380000000' + str(uid), 'password': '$2a$original',
                      'name': '人员' + str(uid), 'id_num': 'ID' + str(uid), 'status': 1,
                      'is_approval': 1, 'is_delete': 0, 'merchant_id': 1 if uid in [3, 4] else None,
                      'update_by': 1, 'create_time': 1, 'update_time': 2})
    rows['insurance']['user'] = users
    rows['insurance']['role'] = [{'id': i, 'name': n} for i, n in [(1, 'admin'), (2, '出单员'), (3, '联系人'), (4, '收款人')]]
    rows['insurance']['user_role'] = [{'id': i, 'user_id': u, 'role_id': r, 'is_delete': 0}
                                     for i, (u, r) in enumerate([(1, 1), (2, 2), (3, 3), (4, 3), (4, 4)], 1)]
    rows['insurance']['merchant'] = [{'id': 1, 'type': '车商店铺', 'code': 'M1', 'is_delete': 0}]
    rows['insurance']['workorder'] = [{'id': 7, 'code': 'W7', 'create_merchant_id': 1,
                                      'create_by': 3, 'handle_by': 2, 'update_by': 1,
                                      'pay_name': '人员4', 'pay_id_num': 'ID4', 'is_delete': 0}]
    rows['insurance']['system_file_archive'] = [{'id': 8, 'path': '/old/file', 'is_delete': 1}]
    rows['insurance']['workorder_file_archive'] = [{'id': 12, 'workorder_id': 7, 'file_id': 8,
                                                  'type': '行驶证', 'is_delete': 1}]
    rows['insurance_saas']['biz_merchant_category'] = [{'id': 1, 'code': 'DEALER_STORE',
                'direction': 'DOWNSTREAM', 'status': 1, 'deleted': 0}]
    rows['insurance_saas']['auth_role'] = [{'id': i, 'code': n, 'status': 1, 'deleted': 0}
                                         for i, n in [(1, 'OWNER'), (2, 'ADMIN'), (3, 'ISSUER')]]
    rows['insurance_saas']['auth_permission'] = [{'id': 1, 'code': 'all', 'system_code': 'INSURANCE', 'status': 1, 'deleted': 0}]
    rows['insurance_saas']['auth_role_permission'] = [{'id': i, 'role_id': i, 'permission_id': 1, 'deleted': 0} for i in [1, 2, 3]]
    return {'source': 'insurance', 'target': 'insurance_saas', 'schema': {'insurance': {}, 'insurance_saas': schema},
            'rows': rows, 'stats': stats, 'fingerprints': {'insurance': {}, 'insurance_saas': {}}, 'triggers': []}


class MigrationTest(unittest.TestCase):
    """覆盖迁移的业务风险，使用内存夹具，任何测试都不连接或写入本地数据库。"""

    def build(self, data=None):
        """构造一个可执行迁移计划，返回映射和拆分结果供业务断言使用。"""
        return Plan(data or fixture(), 'TEST_LEGACY', '测试迁移企业').build()

    def test_new_ids_use_live_and_archive_high_water_mark(self):
        """旧 ID 即使碰撞目标归档，也必须分配新 ID 并同步工单人员/商户关系。"""
        plan = self.build()
        workorder = plan.output['biz_workorder'][0]
        self.assertGreater(plan.maps['user'][1], 100)
        self.assertNotEqual(plan.maps['merchant'][1], 1)
        self.assertEqual(workorder['create_merchant_id'], plan.maps['merchant'][1])
        self.assertEqual(workorder['source_staff_id'], plan.maps['staff'][3])
        self.assertEqual(workorder['handle_by'], plan.maps['user'][2])

    def test_username_conflicts_with_existing_phone(self):
        """同手机号不能合并账号，避免旧登录或短信重置覆盖已有人的身份。"""
        data = fixture()
        data['rows']['insurance_saas']['tenant_user'] = [{'id': 50, 'username': 'existing', 'phone': '13800000001'}]
        plan = self.build(data)
        user = plan.output['tenant_user'][0]
        self.assertNotEqual(user['username'], '13800000001')
        self.assertIsNone(user['phone'])
        self.assertEqual(user['password'], '$2a$original')
        self.assertEqual(data['rows']['insurance_saas']['tenant_user'][0]['username'], 'existing')

    def test_archive_accounts_reserve_login_names(self):
        """归档身份也预留登录名，避免未来恢复记录时产生登录歧义。"""
        data = fixture()
        data['rows']['insurance_saas']['tenant_user_archive'] = [{'id': 99, 'username': '13800000001', 'phone': None}]
        plan = self.build(data)
        self.assertTrue(plan.audit['accounts'][0]['renamed'])

    def test_archived_detail_can_bind_live_workorder(self):
        """归档文件明细仍应绑定迁入的正式工单及归档文件，不能丢弃历史附件。"""
        plan = self.build()
        detail = plan.output['biz_workorder_file_archive'][0]
        self.assertEqual(detail['workorder_id'], plan.maps['workorder'][7])
        self.assertEqual(detail['file_id'], plan.maps['system_file'][8])
        self.assertEqual(detail['file_type'], '行驶证')

    def test_missing_parent_rejects_entire_plan(self):
        """悬空外键必须阻止整批迁移，不能写 NULL 或关联到目标库同号记录。"""
        data = fixture()
        data['rows']['insurance']['workorder_file_archive'][0]['file_id'] = 999
        with self.assertRaisesRegex(ValueError, '悬空关联'):
            self.build(data)

    def test_pending_owner_is_not_automatically_approved(self):
        """迁移可以建立企业但不能绕过原审批状态。"""
        data = fixture()
        data['rows']['insurance']['user'][0]['is_approval'] = 0
        plan = self.build(data)
        member = next(r for r in plan.output['tenant_member'] if r['role_code'] == 'OWNER')
        self.assertEqual(member['status'], 2)

    def test_multiple_contacts_and_payees_keep_people(self):
        """多联系人保留所有人员，仅一名 CONTACT，重复关系不得制造重复角色。"""
        data = fixture()
        data['rows']['insurance']['user_role'].append({'id': 10, 'user_id': 4, 'role_id': 4, 'is_delete': 0})
        plan = self.build(data)
        roles = plan.output['biz_merchant_staff_role']
        self.assertEqual(len(plan.output['biz_merchant_staff']), 2)
        self.assertEqual(sum(r['role_code'] == 'CONTACT' for r in roles), 1)
        self.assertEqual(sum(r['role_code'] == 'CLERK' for r in roles), 1)
        self.assertEqual(sum(r['role_code'] == 'PAYEE' for r in roles), 1)

    def test_payment_identity_is_not_written_as_phone(self):
        """旧收款证件号与手机号分别保存，支付记录必须绑定正确的新收款人员。"""
        plan = self.build()
        payment = plan.output['biz_workorder_payment'][0]
        self.assertEqual(payment['payee_id_num'], 'ID4')
        self.assertEqual(payment['payee_phone'], '13800000004')
        self.assertEqual(payment['payee_staff_id'], plan.maps['staff'][4])

    def test_duplicate_contact_relation_does_not_add_clerk_role(self):
        """同一人员重复的联系人关系只能建立一次 CONTACT，不额外降为 CLERK。"""
        data = fixture()
        data['rows']['insurance']['user_role'].append({'id': 10, 'user_id': 3, 'role_id': 3, 'is_delete': 0})
        plan = self.build(data)
        selected = [r for r in plan.output['biz_merchant_staff_role'] if r['staff_id'] == plan.maps['staff'][3]]
        self.assertEqual([r['role_code'] for r in selected], ['CONTACT'])

    def test_unrecognized_nonempty_table_rejects_plan(self):
        """源结构演进后新增数据不能被脚本静默忽略。"""
        data = fixture()
        data['rows']['insurance']['new_business'] = [{'id': 1}]
        with self.assertRaisesRegex(ValueError, '未定义迁移契约'):
            self.build(data)

    def test_sql_lock_and_commit_order(self):
        """生成 SQL 不在表锁之后启动新事务，且业务明细写在工单之后。"""
        plan = self.build()
        sql = render_sql(plan)
        self.assertLess(sql.index('SET autocommit=0'), sql.index('LOCK TABLES'))
        self.assertNotIn('START TRANSACTION', sql)
        self.assertLess(sql.index('INSERT INTO `insurance_saas`.`biz_workorder`'),
                        sql.index('INSERT INTO `insurance_saas`.`biz_workorder_file_archive`'))
        self.assertEqual(sql.count('COMMIT;'), 1)
        self.assertIn('AND NOT EXISTS', sql)


if __name__ == '__main__':
    unittest.main()
