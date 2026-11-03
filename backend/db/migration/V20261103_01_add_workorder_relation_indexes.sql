/*
 * 工单列表与明细关联性能优化：只新增缺失的非唯一关联索引。
 * 保持PageHelper、Mapper SQL、字段、租户隔离及原索引不变。
 * 在线/归档表统一支持enterprise_id + workorder_id + deleted查找。
 * 已有等效前缀索引时跳过，避免重复创建；不新增业务唯一约束。
 * 适用MySQL 8.0/InnoDB；DDL隐式提交，执行前完整备份。
 * ALGORITHM=INPLACE, LOCK=NONE不支持时直接报错，不降级为锁表重建。
 */
DROP PROCEDURE IF EXISTS mig_20261103_add_workorder_link;
DELIMITER $$
/*
 * 为固定调用清单内的阶段和车辆表补足工单关联索引。
 * 实时检查目标库、InnoDB及索引前缀，已覆盖则不变。
 * 同名异构索引或元数据不符时中止，保留已完成操作供审计。
 */
CREATE PROCEDURE mig_20261103_add_workorder_link(IN p_table VARCHAR(64))
BEGIN
  DECLARE v_covered INT DEFAULT 0;
  DECLARE v_named INT DEFAULT 0;
  IF DATABASE()<>'insurance_saas' AND DATABASE() NOT LIKE 'insurance_saas_indexcheck_%' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected database for workorder index migration';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND ENGINE='InnoDB')<>1
     OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND COLUMN_NAME IN ('enterprise_id','workorder_id','deleted'))<>3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected InnoDB workorder relation table/columns not found';
  END IF;
  SELECT COUNT(*) INTO v_covered FROM (
    SELECT INDEX_NAME FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES'
    GROUP BY INDEX_NAME
    HAVING SUBSTRING_INDEX(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),',',3)='enterprise_id,workorder_id,deleted'
  ) existing_prefix;
  SELECT COUNT(*) INTO v_named FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND INDEX_NAME='idx_20261103_workorder_link';
  IF v_covered=0 THEN
    IF v_named>0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration index name exists with unexpected definition';
    END IF;
    SET @workorder_link_ddl=CONCAT('ALTER TABLE `',REPLACE(p_table,'`','``'),'` ADD INDEX `idx_20261103_workorder_link` (`enterprise_id`,`workorder_id`,`deleted`), ALGORITHM=INPLACE, LOCK=NONE');
    PREPARE workorder_link_stmt FROM @workorder_link_ddl;
    EXECUTE workorder_link_stmt;
    DEALLOCATE PREPARE workorder_link_stmt;
    SELECT p_table AS table_name,'added' AS migration_result;
  ELSE
    SELECT p_table AS table_name,'already_covered' AS migration_result;
  END IF;
END$$
DELIMITER ;
CALL mig_20261103_add_workorder_link('biz_workorder_quote');
CALL mig_20261103_add_workorder_link('biz_workorder_underwriting');
CALL mig_20261103_add_workorder_link('biz_workorder_logistics');
CALL mig_20261103_add_workorder_link('biz_vehicle_license');
CALL mig_20261103_add_workorder_link('biz_vehicle_certificate');
CALL mig_20261103_add_workorder_link('biz_vehicle_invoice');
CALL mig_20261103_add_workorder_link('biz_workorder_file');
CALL mig_20261103_add_workorder_link('biz_workorder_insurance');
CALL mig_20261103_add_workorder_link('biz_workorder_quote_archive');
CALL mig_20261103_add_workorder_link('biz_workorder_underwriting_archive');
CALL mig_20261103_add_workorder_link('biz_workorder_logistics_archive');
CALL mig_20261103_add_workorder_link('biz_vehicle_license_archive');
CALL mig_20261103_add_workorder_link('biz_vehicle_certificate_archive');
CALL mig_20261103_add_workorder_link('biz_vehicle_invoice_archive');
CALL mig_20261103_add_workorder_link('biz_workorder_file_archive');
CALL mig_20261103_add_workorder_link('biz_workorder_insurance_archive');
CALL mig_20261103_add_workorder_link('biz_workorder_commission_archive');
DROP PROCEDURE mig_20261103_add_workorder_link;

