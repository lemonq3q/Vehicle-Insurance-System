-- 车险系统单次增量：合并原20261029_01和20261029_02，不能再执行原两份文件。
-- 适用于尚未执行本次改造的旧库。请停止旧版本写入，同一连接完整执行本文件，再启动新版本。
-- 包含结构调整、历史快照回填、唯一约束及旧人员表删除；MySQL DDL不能整体事务回滚。
-- 2026-10-30用户确认：服务器企业和分类均为空的42145条车商及39832条经营区域直接删除，不保留备份。
-- 本文件已合并该清理步骤，服务器无需另运行清理文件。数量不符或有业务引用时停止，不扩大删除范围。
-- 旧单一续保状态保留，新双险状态初始未处理；历史状态迁移方案须审阅确认后执行。
-- 原结构迁移已在本地验证；服务器清理范围依据用户提供的查询截图核对，执行前请停止业务写入。
-- V20261029_00只读预检文件可用于提前检查；本文件已内置关键阻断检查，无需另执行写入脚本。
-- 如失败，不可直接重跑：须核对已执行DDL及触发器，按备份恢复或修复后继续。

-- 明确选择本项目目标库，避免客户端只连接MySQL实例而未选择数据库时全部DDL失败。
USE insurance_saas;

-- 仅本会话回填时保护原更新时间；普通业务连接变量为空，仍沿用项目原更新时间规则。
SET @insurance_optimization_preserve_time=0;
DROP TRIGGER IF EXISTS trg_biz_merchant_bu;
CREATE TRIGGER trg_biz_merchant_bu BEFORE UPDATE ON biz_merchant
FOR EACH ROW SET NEW.updated_at = IF(COALESCE(@insurance_optimization_preserve_time,0)=1,OLD.updated_at,CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS trg_biz_workorder_bu;
CREATE TRIGGER trg_biz_workorder_bu BEFORE UPDATE ON biz_workorder
FOR EACH ROW SET NEW.updated_at = IF(COALESCE(@insurance_optimization_preserve_time,0)=1,OLD.updated_at,CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS trg_biz_workorder_payment_bu;
CREATE TRIGGER trg_biz_workorder_payment_bu BEFORE UPDATE ON biz_workorder_payment
FOR EACH ROW SET NEW.updated_at = IF(COALESCE(@insurance_optimization_preserve_time,0)=1,OLD.updated_at,CURRENT_TIMESTAMP);

DELIMITER $$
CREATE PROCEDURE mig_20261030_insurance_optimization()
BEGIN
  DECLARE orphan_merchant_count bigint DEFAULT 0;
  DECLARE orphan_area_count bigint DEFAULT 0;
  /* 任何回填或DDL错误立即退出，绝不继续执行后续人员表删除；已提交DDL仍需人工核对。 */
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    SET @insurance_optimization_preserve_time=0;
    RESIGNAL;
  END;
  /* 先阻断已执行或部分执行的结构，防止重复迁移及继续删除旧表。 */
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='请先选择需要更新的服务器数据库';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND ((table_name='biz_merchant' AND column_name='direction')
        OR (table_name='biz_workorder' AND column_name='submitter_name'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='本次迁移已执行或部分执行，请先核对结构，不可重复执行';
  END IF;
  /*
   * 先清理用户确认无价值的服务器历史车商，严格限定企业与分类同时为空的已审阅记录。
   * 临时ID表在同一事务中锁定删除范围；数量或关联变化则回滚本次清理，禁止猜测企业归属。
   * 没有此类记录时直接继续常规迁移；历史资料不创建备份，这是用户明确选择。
   */
  SELECT COUNT(*) INTO orphan_merchant_count FROM biz_merchant
    WHERE enterprise_id IS NULL AND category_id IS NULL;
  IF orphan_merchant_count>0 THEN
    IF DATABASE()<>'insurance_saas' OR orphan_merchant_count<>42145 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='异常车商数量或目标库与确认范围不符，未删除，请先核对';
    END IF;
    START TRANSACTION;
    CREATE TEMPORARY TABLE mig_20261030_orphan_ids (id bigint NOT NULL PRIMARY KEY);
    INSERT INTO mig_20261030_orphan_ids(id)
      SELECT id FROM biz_merchant WHERE enterprise_id IS NULL AND category_id IS NULL FOR UPDATE;
    IF (SELECT COUNT(*) FROM mig_20261030_orphan_ids)<>42145 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商数量变化，本次清理回滚';
    END IF;
    SELECT COUNT(*) INTO orphan_area_count FROM biz_merchant_area a
      JOIN mig_20261030_orphan_ids t ON t.id=a.merchant_id;
    IF orphan_area_count<>39832 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='区域数量与确认范围不符，本次清理回滚';
    END IF;

    /* 分项查询避免MySQL同一查询重复打开同一临时表；任一当前或归档业务引用都阻断。 */
    IF EXISTS(SELECT 1 FROM biz_workorder w JOIN mig_20261030_orphan_ids t ON t.id=w.create_merchant_id OR t.id=w.handle_merchant_id OR t.id=w.insurance_merchant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商存在业务或归档关联，本次清理回滚';
    END IF;
    IF EXISTS(SELECT 1 FROM biz_workorder_archive w JOIN mig_20261030_orphan_ids t ON t.id=w.create_merchant_id OR t.id=w.handle_merchant_id OR t.id=w.insurance_merchant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商存在业务或归档关联，本次清理回滚';
    END IF;
    IF EXISTS(SELECT 1 FROM biz_merchant_staff s JOIN mig_20261030_orphan_ids t ON t.id=s.merchant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商存在业务或归档关联，本次清理回滚';
    END IF;
    IF EXISTS(SELECT 1 FROM biz_merchant_staff_archive s JOIN mig_20261030_orphan_ids t ON t.id=s.merchant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商存在业务或归档关联，本次清理回滚';
    END IF;
    IF EXISTS(SELECT 1 FROM biz_merchant_staff_role r JOIN mig_20261030_orphan_ids t ON t.id=r.merchant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商存在业务或归档关联，本次清理回滚';
    END IF;
    IF EXISTS(SELECT 1 FROM biz_merchant_staff_role_archive r JOIN mig_20261030_orphan_ids t ON t.id=r.merchant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商存在业务或归档关联，本次清理回滚';
    END IF;
    IF EXISTS(SELECT 1 FROM biz_merchant_area_archive a JOIN mig_20261030_orphan_ids t ON t.id=a.merchant_id) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='目标车商存在业务或归档关联，本次清理回滚';
    END IF;
    DELETE a FROM biz_merchant_area a JOIN mig_20261030_orphan_ids t ON t.id=a.merchant_id;
    IF ROW_COUNT()<>orphan_area_count THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='区域删除数量异常，本次清理回滚';
    END IF;
    DELETE m FROM biz_merchant m JOIN mig_20261030_orphan_ids t ON t.id=m.id;
    IF ROW_COUNT()<>orphan_merchant_count THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='车商删除数量异常，本次清理回滚';
    END IF;
    DROP TEMPORARY TABLE mig_20261030_orphan_ids;
    COMMIT;
  END IF;

  IF EXISTS(SELECT 1 FROM biz_merchant m LEFT JOIN biz_merchant_category c ON c.id=m.category_id
      WHERE m.enterprise_id IS NULL OR c.direction IS NULL OR c.direction NOT IN ('UPSTREAM','DOWNSTREAM')
        OR NULLIF(TRIM(m.name),'') IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='机构存在缺失的企业、方向或名称，请先核对预检清单';
  END IF;
  IF EXISTS(SELECT 1 FROM biz_merchant m JOIN biz_merchant_category c ON c.id=m.category_id
      WHERE m.deleted=0 GROUP BY m.enterprise_id,c.direction,TRIM(m.name) HAVING COUNT(*)>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在同企业同方向重名机构，请先处理后再迁移';
  END IF;
  IF EXISTS(SELECT 1 FROM biz_merchant_staff_role r
      JOIN biz_merchant_staff s ON s.id=r.staff_id AND s.enterprise_id=r.enterprise_id AND s.deleted=0
      WHERE r.role_code='CONTACT' AND r.deleted=0 GROUP BY r.enterprise_id,r.merchant_id HAVING COUNT(DISTINCT r.staff_id)>1)
     OR EXISTS(SELECT 1 FROM biz_merchant_staff_role_archive r
      JOIN biz_merchant_staff_archive s ON s.id=r.staff_id AND s.enterprise_id=r.enterprise_id
      WHERE r.role_code='CONTACT' AND r.deleted=0 GROUP BY r.enterprise_id,r.merchant_id HAVING COUNT(DISTINCT r.staff_id)>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在多个默认联系人，请先核对，不会随机选择联系人';
  END IF;
  SET @insurance_optimization_preserve_time=1;

  ALTER TABLE biz_merchant
    ADD COLUMN direction varchar(32) DEFAULT NULL COMMENT 'UPSTREAM或DOWNSTREAM，与分类一致',
    MODIFY COLUMN contact varchar(100) DEFAULT NULL COMMENT '上下游联系人',
    MODIFY COLUMN phone varchar(20) DEFAULT NULL COMMENT '上下游联系手机';
  ALTER TABLE biz_merchant_archive
    ADD COLUMN direction varchar(32) DEFAULT NULL COMMENT '上下游方向',
    MODIFY COLUMN contact varchar(100) DEFAULT NULL COMMENT '上下游联系人',
    MODIFY COLUMN phone varchar(20) DEFAULT NULL COMMENT '上下游联系手机';

  UPDATE biz_merchant m JOIN biz_merchant_category c ON c.id=m.category_id SET m.direction=c.direction,m.name=TRIM(m.name),m.updated_at=m.updated_at;
  UPDATE biz_merchant_archive m JOIN biz_merchant_category c ON c.id=m.category_id SET m.direction=c.direction,m.name=TRIM(m.name),m.updated_at=m.updated_at;

  -- 联系人只从明确的CONTACT角色回填；主体已有值保持原值，冲突项先在预检阶段处理。
  UPDATE biz_merchant m
  JOIN biz_merchant_staff_role r ON r.merchant_id=m.id AND r.enterprise_id=m.enterprise_id AND r.role_code='CONTACT' AND r.deleted=0
  JOIN biz_merchant_staff s ON s.id=r.staff_id AND s.enterprise_id=m.enterprise_id AND s.deleted=0
  SET m.contact=COALESCE(NULLIF(TRIM(m.contact),''),s.name),m.phone=COALESCE(NULLIF(TRIM(m.phone),''),s.phone),m.updated_at=m.updated_at
  WHERE m.direction='DOWNSTREAM';
  UPDATE biz_merchant_archive m
  JOIN biz_merchant_staff_role_archive r ON r.merchant_id=m.id AND r.enterprise_id=m.enterprise_id AND r.role_code='CONTACT' AND r.deleted=0
  JOIN biz_merchant_staff_archive s ON s.id=r.staff_id AND s.enterprise_id=m.enterprise_id
  SET m.contact=COALESCE(NULLIF(TRIM(m.contact),''),s.name),m.phone=COALESCE(NULLIF(TRIM(m.phone),''),s.phone),m.updated_at=m.updated_at
  WHERE m.direction='DOWNSTREAM';

  -- 仅有效主体参与唯一约束，允许软删除后复用名称及多次删除，不限制其他企业或另一方向的名称。
  -- 若方向缺失或重名，必须修复预检异常后再执行，不能忽略唯一索引创建失败。
  ALTER TABLE biz_merchant MODIFY COLUMN direction varchar(32) NOT NULL,
    ADD COLUMN active_name varchar(100) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN TRIM(name) ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_biz_merchant_active_name (enterprise_id,direction,active_name);
  -- 归档保持列结构兼容但不施加名称唯一性，历史多个版本允许并存。
  ALTER TABLE biz_merchant_archive
    ADD COLUMN active_name varchar(100) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN TRIM(name) ELSE NULL END) STORED;

  ALTER TABLE biz_workorder
    ADD COLUMN submitter_name varchar(100) DEFAULT NULL COMMENT '提交人姓名快照，与录入账号独立',
    ADD COLUMN commercial_renewal_status tinyint NOT NULL DEFAULT 0 COMMENT '商业续保0未处理1已续保2已流失',
    ADD COLUMN commercial_renewal_cycle int NOT NULL DEFAULT 0 COMMENT '商业最近处理年度周期',
    ADD COLUMN compulsory_renewal_status tinyint NOT NULL DEFAULT 0 COMMENT '交强续保0未处理1已续保2已流失',
    ADD COLUMN compulsory_renewal_cycle int NOT NULL DEFAULT 0 COMMENT '交强最近处理年度周期';
  ALTER TABLE biz_workorder_archive
    ADD COLUMN submitter_name varchar(100) DEFAULT NULL COMMENT '提交人姓名快照，与录入账号独立',
    ADD COLUMN commercial_renewal_status tinyint NOT NULL DEFAULT 0,
    ADD COLUMN commercial_renewal_cycle int NOT NULL DEFAULT 0,
    ADD COLUMN compulsory_renewal_status tinyint NOT NULL DEFAULT 0,
    ADD COLUMN compulsory_renewal_cycle int NOT NULL DEFAULT 0;

  -- 优先真实来源人员姓名，来源缺失时保留改造前页面实际展示的系统录入人姓名；不使用当前店铺联系人替代历史。
  UPDATE biz_workorder w
  LEFT JOIN biz_merchant_staff s ON s.id=w.source_staff_id AND s.enterprise_id=w.enterprise_id
  LEFT JOIN biz_merchant_staff_archive sa ON sa.id=w.source_staff_id AND sa.enterprise_id=w.enterprise_id
  LEFT JOIN tenant_user u ON u.id=w.created_by
  SET w.submitter_name=COALESCE(NULLIF(TRIM(s.name),''),NULLIF(TRIM(sa.name),''),u.real_name),w.updated_at=w.updated_at WHERE w.submitter_name IS NULL;
  UPDATE biz_workorder_archive w
  LEFT JOIN biz_merchant_staff s ON s.id=w.source_staff_id AND s.enterprise_id=w.enterprise_id
  LEFT JOIN biz_merchant_staff_archive sa ON sa.id=w.source_staff_id AND sa.enterprise_id=w.enterprise_id
  LEFT JOIN tenant_user u ON u.id=w.created_by
  SET w.submitter_name=COALESCE(NULLIF(TRIM(s.name),''),NULLIF(TRIM(sa.name),''),u.real_name),w.updated_at=w.updated_at WHERE w.submitter_name IS NULL;

  -- 原有姓名、手机、证件及银行资料不覆盖，只补齐仍依赖人员ID但尚未形成快照的字段。
  UPDATE biz_workorder_payment p
  LEFT JOIN biz_merchant_staff s ON s.id=p.payee_staff_id AND s.enterprise_id=p.enterprise_id
  LEFT JOIN biz_merchant_staff_archive sa ON sa.id=p.payee_staff_id AND sa.enterprise_id=p.enterprise_id
  SET p.payee_name=COALESCE(p.payee_name,s.name,sa.name),p.payee_phone=COALESCE(p.payee_phone,s.phone,sa.phone),
      p.payee_id_num=COALESCE(p.payee_id_num,s.id_num,sa.id_num),p.updated_at=p.updated_at;
  UPDATE biz_workorder_payment_archive p
  LEFT JOIN biz_merchant_staff s ON s.id=p.payee_staff_id AND s.enterprise_id=p.enterprise_id
  LEFT JOIN biz_merchant_staff_archive sa ON sa.id=p.payee_staff_id AND sa.enterprise_id=p.enterprise_id
  SET p.payee_name=COALESCE(p.payee_name,s.name,sa.name),p.payee_phone=COALESCE(p.payee_phone,s.phone,sa.phone),
      p.payee_id_num=COALESCE(p.payee_id_num,s.id_num,sa.id_num),p.updated_at=p.updated_at;


  /* 快照核验通过后，在同一次调用内清理旧引用和人员表。 */
  IF EXISTS(SELECT 1 FROM biz_workorder WHERE source_staff_id IS NOT NULL AND NULLIF(TRIM(submitter_name),'') IS NULL)
     OR EXISTS(SELECT 1 FROM biz_workorder_archive WHERE source_staff_id IS NOT NULL AND NULLIF(TRIM(submitter_name),'') IS NULL)
     OR EXISTS(SELECT 1 FROM biz_workorder_payment WHERE payee_staff_id IS NOT NULL AND NULLIF(TRIM(payee_name),'') IS NULL)
     OR EXISTS(SELECT 1 FROM biz_workorder_payment_archive WHERE payee_staff_id IS NOT NULL AND NULLIF(TRIM(payee_name),'') IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='历史姓名快照尚未补齐，禁止删除人员引用';
  END IF;
  ALTER TABLE biz_workorder DROP INDEX idx_workorder_source_staff, DROP COLUMN source_staff_id;
  ALTER TABLE biz_workorder_archive DROP INDEX idx_workorder_archive_source_staff, DROP COLUMN source_staff_id;
  ALTER TABLE biz_workorder_payment DROP INDEX idx_workorder_payment_payee, DROP COLUMN payee_staff_id;
  -- 归档索引名与项目当前建表契约一致，执行前仍须核对真实库。
  ALTER TABLE biz_workorder_payment_archive DROP INDEX idx_workorder_payment_archive_payee, DROP COLUMN payee_staff_id;
  DROP TABLE biz_merchant_staff_role_archive,biz_merchant_staff_archive,biz_merchant_staff_role,biz_merchant_staff;

  SET @insurance_optimization_preserve_time=0;
  SELECT 'insurance_optimization_completed' AS migration_result,
         orphan_merchant_count AS deleted_orphan_merchants,orphan_area_count AS deleted_orphan_areas;
END$$
DELIMITER ;
CALL mig_20261030_insurance_optimization();
DROP PROCEDURE mig_20261030_insurance_optimization;

-- 无论CALL成功或失败，恢复基线触发器后才能恢复业务写入。
SET @insurance_optimization_preserve_time=0;
DROP TRIGGER IF EXISTS trg_biz_merchant_bu;
CREATE TRIGGER trg_biz_merchant_bu BEFORE UPDATE ON biz_merchant
FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
DROP TRIGGER IF EXISTS trg_biz_workorder_bu;
CREATE TRIGGER trg_biz_workorder_bu BEFORE UPDATE ON biz_workorder
FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
DROP TRIGGER IF EXISTS trg_biz_workorder_payment_bu;
CREATE TRIGGER trg_biz_workorder_payment_bu BEFORE UPDATE ON biz_workorder_payment
FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
