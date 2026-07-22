-- 企业拥有者应拥有车险业务管理员的全部权限。
-- 使用 NOT EXISTS 保证脚本可重复执行，不会产生重复的有效权限关联。
USE insurance_saas;

START TRANSACTION;

INSERT INTO auth_role_permission (
  role_id,
  permission_id,
  created_at,
  updated_by,
  deleted
)
SELECT
  owner_role.id,
  admin_permission.permission_id,
  NOW(),
  NULL,
  0
FROM auth_role owner_role
JOIN auth_role admin_role
  ON admin_role.code = 'ADMIN'
 AND admin_role.role_scope = 'TENANT'
 AND admin_role.status = 1
 AND admin_role.deleted = 0
JOIN auth_role_permission admin_permission
  ON admin_permission.role_id = admin_role.id
 AND admin_permission.deleted = 0
JOIN auth_permission permission
  ON permission.id = admin_permission.permission_id
 AND permission.system_code = 'INSURANCE'
 AND permission.status = 1
 AND permission.deleted = 0
WHERE owner_role.code = 'OWNER'
  AND owner_role.role_scope = 'TENANT'
  AND owner_role.status = 1
  AND owner_role.deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM auth_role_permission existing_permission
    WHERE existing_permission.role_id = owner_role.id
      AND existing_permission.permission_id = admin_permission.permission_id
      AND existing_permission.deleted = 0
  );

COMMIT;
