/*
 * 工单两表改造，适用 MySQL 8.0 / InnoDB。
 * 最终业务结构：workorder 合并 underwriting/payment；quote 合并 commission。
 * 三种起期均在 workorder；三种保费/车船税和政策费用在 quote。
 * 不新增合计保费或保单应付金额；required_pay_amount 保留原支付录入语义。
 *
 * 执行：先完整备份并验证可恢复，停止所有应用/导入/归档/统计任务的数据库写入，
 * 排空在途事务后，通过支持 DELIMITER 的 MySQL 客户端运行。
 * 不要使用 --force 忽略错误。须与适配新表结构的后端一同上线，旧后端不可直接继续运行。
 * 此脚本不启动/停止业务服务，执行前需自行暂停写入。
 *
 * 在线与归档同时迁移；先预检，再复制到影子表，逐字段验证，最后一次原子 RENAME。
 * 十张原表全部保留为 *_bak261107，原主键、子表审计时间和历史数据仍可追溯。
 * 子表的 created_at/updated_at/id 不覆盖主表审计信息，完整保留在备份表。
 * 主表和 quote 保留原字段/索引；新公司、终期、非车险起期/号码没有可靠来源，置 NULL。
 * 失败时业务原表不变，可能留下 *_m261107 影子表；核对后处理，不自动删除。
 * 成功后不可重复执行；检测到备份或影子表即中止，防止覆盖已有迁移结果。
 * DDL 隐式提交，不能依靠事务 ROLLBACK 回退整个迁移。
 */
DELIMITER $$

/*
 * 仅执行本文件内部拼装的固定白名单表名 SQL，不接收外部表名。
 * 每次立即释放预编译语句；发生错误交由上层中止，不吞掉迁移错误。
 */
CREATE PROCEDURE mig261107_exec(IN p_sql LONGTEXT)
BEGIN
    SET @mig261107_sql = p_sql;
    PREPARE mig261107_stmt FROM @mig261107_sql;
    EXECUTE mig261107_stmt;
    DEALLOCATE PREPARE mig261107_stmt;
END$$

/*
 * 在线和归档分别预检一对数据集，不修改业务数据。
 * 只有一份可明确归属到相同企业、工单和删除状态的子记录才允许折叠。
 * 重复、孤儿、未知上下游方向均报错，不用 MAX(id) 或金额求和猜测原始业务。
 */
CREATE PROCEDURE mig261107_check(IN p_suffix VARCHAR(8))
BEGIN
    DECLARE v_kind VARCHAR(30);
    DECLARE v_i INT DEFAULT 0;
    IF p_suffix NOT IN ('', '_archive') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid migration suffix';
    END IF;
    SET @mw = CONCAT('biz_workorder', p_suffix);
    SET @mq = CONCAT('biz_workorder_quote', p_suffix);
    SET @mc = CONCAT('biz_workorder_commission', p_suffix);
    WHILE v_i < 4 DO
        SET v_kind = ELT(v_i+1,'quote','underwriting','payment','commission');
        SET @mt = CONCAT('biz_workorder_',v_kind,p_suffix);
        CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM ',@mt,
            ' WHERE enterprise_id IS NULL OR workorder_id IS NULL OR deleted NOT IN (0,1)'));
        IF @mig_bad > 0 THEN
            SELECT @mt AS invalid_relation_table, @mig_bad AS invalid_rows;
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='NULL tenant/workorder or invalid deleted flag; inspect source';
        END IF;
        CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM (SELECT enterprise_id,workorder_id,deleted',
            IF(v_kind='commission',',side',''),' FROM ',@mt,
            ' GROUP BY enterprise_id,workorder_id,deleted',IF(v_kind='commission',',side',''),
            ' HAVING COUNT(*)>1) d'));
        IF @mig_bad > 0 THEN
            SELECT @mt AS duplicate_relation_table, @mig_bad AS duplicate_groups;
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ambiguous duplicate child records; no data was selected arbitrarily';
        END IF;
        CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM ',@mt,' c LEFT JOIN ',@mw,
            ' w ON w.id=c.workorder_id AND w.enterprise_id=c.enterprise_id AND w.deleted=c.deleted WHERE w.id IS NULL'));
        IF @mig_bad > 0 THEN
            SELECT @mt AS unmatched_parent_table, @mig_bad AS unmatched_rows;
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing same-set parent or tenant/deleted mismatch; inspect source';
        END IF;
        SET v_i = v_i + 1;
    END WHILE;
    CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM ',@mc,
        ' WHERE side IS NULL OR BINARY side NOT IN (BINARY ''UPSTREAM'',BINARY ''DOWNSTREAM'')'));
    IF @mig_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown commission side; inspect source';
    END IF;
END$$

/*
 * 建立主表/金额表的影子副本，逐列保留原数据和索引。
 * 保持原自增高水位，避免源表删除过最大 ID 后在新表复用历史主键。
 * 禁止生成列由主过程预先检查，故这里可安全地复制完整原始列。
 */
CREATE PROCEDURE mig261107_clone(IN p_table VARCHAR(64))
BEGIN
    DECLARE v_auto BIGINT UNSIGNED;
    SELECT AUTO_INCREMENT INTO v_auto FROM information_schema.TABLES
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table;
    CALL mig261107_exec(CONCAT('CREATE TABLE ',p_table,'_m261107 LIKE ',p_table));
    CALL mig261107_exec(CONCAT('INSERT INTO ',p_table,'_m261107 SELECT * FROM ',p_table));
    IF v_auto IS NOT NULL THEN
        CALL mig261107_exec(CONCAT('ALTER TABLE ',p_table,'_m261107 AUTO_INCREMENT=',v_auto));
    END IF;
END$$

/*
 * 分别转换在线/归档数据。主表吸收核保承保与收款支付字段，
 * quote 吸收上下游费用，已有 quote 行保留 ID、备注和全部金额原值。
 * 对仅有政策费用而没有 quote 行的工单补建金额行，不丢弃单侧政策或零金额。
 */
CREATE PROCEDURE mig261107_build(IN p_suffix VARCHAR(8))
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_side VARCHAR(20);
    DECLARE v_prefix VARCHAR(20);
    SET @mw=CONCAT('biz_workorder',p_suffix);
    SET @mq=CONCAT('biz_workorder_quote',p_suffix);
    SET @mu=CONCAT('biz_workorder_underwriting',p_suffix);
    SET @mp=CONCAT('biz_workorder_payment',p_suffix);
    SET @mc=CONCAT('biz_workorder_commission',p_suffix);
    SET @nw=CONCAT(@mw,'_m261107');
    SET @nq=CONCAT(@mq,'_m261107');
    CALL mig261107_clone(@mw);
    CALL mig261107_clone(@mq);
    CALL mig261107_exec(CONCAT('ALTER TABLE ',@nw,' ADD COLUMN underwriting_remark VARCHAR(500) NULL DEFAULT NULL COMMENT ''核保反馈备注'', ADD COLUMN underwriting_failed_remark VARCHAR(500) NULL DEFAULT NULL COMMENT ''核保失败原因'', ADD COLUMN underwriting_time DATETIME NULL DEFAULT NULL COMMENT ''核保时间'', ADD COLUMN commercial_policy_no VARCHAR(100) NULL DEFAULT NULL COMMENT ''商业险保单号'', ADD COLUMN compulsory_policy_no VARCHAR(100) NULL DEFAULT NULL COMMENT ''交强险保单号'', ADD COLUMN accept_insurance_remark VARCHAR(500) NULL DEFAULT NULL COMMENT ''承保反馈备注'', ADD COLUMN accept_insurance_failed_remark VARCHAR(500) NULL DEFAULT NULL COMMENT ''承保失败原因'', ADD COLUMN finish_time DATETIME NULL DEFAULT NULL COMMENT ''承保完成时间'', ADD COLUMN required_pay_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''原支付环节录入金额，非保单应付派生值'', ADD COLUMN payee_name VARCHAR(100) NULL DEFAULT NULL COMMENT ''收款人姓名'', ADD COLUMN payee_phone VARCHAR(20) NULL DEFAULT NULL COMMENT ''收款人联系方式'', ADD COLUMN payee_id_num VARCHAR(100) NULL DEFAULT NULL COMMENT ''收款人证件号'', ADD COLUMN merchant_bank VARCHAR(100) NULL DEFAULT NULL COMMENT ''收款开户行'', ADD COLUMN merchant_bank_card_num VARCHAR(100) NULL DEFAULT NULL COMMENT ''收款银行卡号'', ADD COLUMN pay_remark VARCHAR(500) NULL DEFAULT NULL COMMENT ''支付反馈备注'', ADD COLUMN pay_failed_remark VARCHAR(500) NULL DEFAULT NULL COMMENT ''支付失败原因'', ADD COLUMN non_motor_insurance_start_time BIGINT NULL DEFAULT NULL COMMENT ''非车险起期，Unix秒'', ADD COLUMN commercial_insurer_name VARCHAR(200) NULL DEFAULT NULL COMMENT ''商业险保险公司名称'', ADD COLUMN compulsory_insurer_name VARCHAR(200) NULL DEFAULT NULL COMMENT ''交强险保险公司名称'', ADD COLUMN non_motor_insurer_name VARCHAR(200) NULL DEFAULT NULL COMMENT ''非车险保险公司名称'', ADD COLUMN non_motor_policy_no VARCHAR(100) NULL DEFAULT NULL COMMENT ''非车险保单号'', ADD COLUMN commercial_insurance_end_time BIGINT NULL DEFAULT NULL COMMENT ''商业险终期，Unix秒'', ADD COLUMN compulsory_insurance_end_time BIGINT NULL DEFAULT NULL COMMENT ''交强险终期，Unix秒'', ADD COLUMN non_motor_insurance_end_time BIGINT NULL DEFAULT NULL COMMENT ''非车险终期，Unix秒'''));
    CALL mig261107_exec(CONCAT('ALTER TABLE ',@nq,' ADD COLUMN upstream_compute_type TINYINT NULL DEFAULT NULL COMMENT ''上游费用计算方式，沿用原值'', ADD COLUMN upstream_commercial_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''上游商业险政策比例'', ADD COLUMN upstream_compulsory_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''上游交强险政策比例'', ADD COLUMN upstream_vehicle_and_vessel_tax_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''上游车船税政策比例'', ADD COLUMN upstream_non_motor_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''上游非车险政策比例'', ADD COLUMN upstream_commercial_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''上游商业险政策金额'', ADD COLUMN upstream_compulsory_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''上游交强险政策金额'', ADD COLUMN upstream_vehicle_and_vessel_tax_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''上游车船税政策金额'', ADD COLUMN upstream_non_motor_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''上游非车险政策金额'', ADD COLUMN downstream_compute_type TINYINT NULL DEFAULT NULL COMMENT ''下游费用计算方式，沿用原值'', ADD COLUMN downstream_commercial_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''下游商业险政策比例'', ADD COLUMN downstream_compulsory_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''下游交强险政策比例'', ADD COLUMN downstream_vehicle_and_vessel_tax_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''下游车船税政策比例'', ADD COLUMN downstream_non_motor_percentage DECIMAL(8,2) NULL DEFAULT NULL COMMENT ''下游非车险政策比例'', ADD COLUMN downstream_commercial_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''下游商业险政策金额'', ADD COLUMN downstream_compulsory_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''下游交强险政策金额'', ADD COLUMN downstream_vehicle_and_vessel_tax_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''下游车船税政策金额'', ADD COLUMN downstream_non_motor_amount DECIMAL(12,2) NULL DEFAULT NULL COMMENT ''下游非车险政策金额'''));

    CALL mig261107_exec(CONCAT('UPDATE ',@nw,' w JOIN ',@mu,
        ' u ON u.enterprise_id=w.enterprise_id AND u.workorder_id=w.id AND u.deleted=w.deleted SET ',
        'w.underwriting_remark=u.underwriting_remark,w.underwriting_failed_remark=u.underwriting_failed_remark,w.underwriting_time=u.underwriting_time,w.commercial_policy_no=u.commercial_policy_no,w.compulsory_policy_no=u.compulsory_policy_no,w.accept_insurance_remark=u.accept_insurance_remark,w.accept_insurance_failed_remark=u.accept_insurance_failed_remark,w.finish_time=u.finish_time'));
    CALL mig261107_exec(CONCAT('UPDATE ',@nw,' w JOIN ',@mp,
        ' p ON p.enterprise_id=w.enterprise_id AND p.workorder_id=w.id AND p.deleted=w.deleted SET ',
        'w.required_pay_amount=p.required_pay_amount,w.payee_name=p.payee_name,w.payee_phone=p.payee_phone,w.payee_id_num=p.payee_id_num,w.merchant_bank=p.merchant_bank,w.merchant_bank_card_num=p.merchant_bank_card_num,w.pay_remark=p.pay_remark,w.pay_failed_remark=p.pay_failed_remark'));

    CALL mig261107_exec(CONCAT('INSERT INTO ',@nq,
        ' (enterprise_id,workorder_id,deleted,created_at,updated_at) ',
        'SELECT c.enterprise_id,c.workorder_id,c.deleted,MIN(c.created_at),MAX(c.updated_at) FROM ',@mc,
        ' c LEFT JOIN ',@mq,' q ON q.enterprise_id=c.enterprise_id AND q.workorder_id=c.workorder_id AND q.deleted=c.deleted ',
        'WHERE q.id IS NULL GROUP BY c.enterprise_id,c.workorder_id,c.deleted'));

    WHILE v_i < 2 DO
        SET v_side=ELT(v_i+1,'UPSTREAM','DOWNSTREAM');
        SET v_prefix=ELT(v_i+1,'upstream','downstream');
        CALL mig261107_exec(CONCAT('UPDATE ',@nq,' q JOIN ',@mc,
            ' c ON c.enterprise_id=q.enterprise_id AND c.workorder_id=q.workorder_id AND c.deleted=q.deleted ',
            'AND BINARY c.side=BINARY ''',v_side,''' SET ',
            'q.',v_prefix,'_compute_type=c.compute_type',',',
            'q.',v_prefix,'_commercial_percentage=c.commercial_percentage',',',
            'q.',v_prefix,'_compulsory_percentage=c.compulsory_percentage',',',
            'q.',v_prefix,'_vehicle_and_vessel_tax_percentage=c.vehicle_tax_percentage',',',
            'q.',v_prefix,'_non_motor_percentage=c.non_motor_percentage',',',
            'q.',v_prefix,'_commercial_amount=c.commercial_amount',',',
            'q.',v_prefix,'_compulsory_amount=c.compulsory_amount',',',
            'q.',v_prefix,'_vehicle_and_vessel_tax_amount=c.vehicle_tax_amount',',',
            'q.',v_prefix,'_non_motor_amount=c.non_motor_amount'));
        SET v_i=v_i+1;
    END WHILE;

    /* 承保完成时间已合并到主表，保留现有日报查询需要的租户/状态/时间索引。 */
    CALL mig261107_exec(CONCAT('ALTER TABLE ',@nw,
        ' ADD INDEX idx_m261107_daily_finished (enterprise_id,deleted,finish_time,id)'));
END$$

/*
 * 对照源表校验每一个原字段，采用二进制 NULL 安全比较，保留空值、0、大小写和原始文本。
 * 业务主键/审计字段不重新生成；只允许 quote 额外出现有政策但无旧 quote 的补建行。
 */
CREATE PROCEDURE mig261107_verify_original(IN p_table VARCHAR(64), IN p_allow_extra BOOLEAN)
BEGIN
    DECLARE v_compare LONGTEXT;
    SELECT GROUP_CONCAT(CONCAT('NOT(CAST(n.`',COLUMN_NAME,'` AS BINARY) <=> CAST(o.`',COLUMN_NAME,
        '` AS BINARY))') ORDER BY ORDINAL_POSITION SEPARATOR ' OR ') INTO v_compare
      FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table;
    CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM ',p_table,' o LEFT JOIN ',p_table,
        '_m261107 n ON n.id=o.id WHERE n.id IS NULL OR ',v_compare));
    IF @mig_bad > 0 THEN
        SELECT p_table AS original_value_mismatch_table, @mig_bad AS mismatch_rows;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original field verification failed; original tables remain unchanged';
    END IF;
    IF NOT p_allow_extra THEN
        CALL mig261107_exec(CONCAT('SELECT ABS((SELECT COUNT(*) FROM ',p_table,
            ')-(SELECT COUNT(*) FROM ',p_table,'_m261107)) INTO @mig_bad'));
        IF @mig_bad > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Main table row count mismatch';
        END IF;
    END IF;
END$$

/*
 * 验证所有被折叠的子表字段均迁入对应目标；不重新计算任何政策费用或支付金额。
 * 与原行一一核对，而不是仅核对合计，避免金额相抵掩盖迁错工单。
 */
CREATE PROCEDURE mig261107_verify_merged(IN p_suffix VARCHAR(8))
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_prefix VARCHAR(20);
    DECLARE v_side VARCHAR(20);
    SET @nw=CONCAT('biz_workorder',p_suffix,'_m261107');
    SET @nq=CONCAT('biz_workorder_quote',p_suffix,'_m261107');
    SET @mu=CONCAT('biz_workorder_underwriting',p_suffix);
    SET @mp=CONCAT('biz_workorder_payment',p_suffix);
    SET @mc=CONCAT('biz_workorder_commission',p_suffix);
    CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM ',@mu,' c LEFT JOIN ',@nw,
        ' w ON w.enterprise_id=c.enterprise_id AND w.id=c.workorder_id AND w.deleted=c.deleted WHERE w.id IS NULL OR ',
        'NOT(CAST(w.underwriting_remark AS BINARY)<=>CAST(c.underwriting_remark AS BINARY)) OR NOT(CAST(w.underwriting_failed_remark AS BINARY)<=>CAST(c.underwriting_failed_remark AS BINARY)) OR NOT(CAST(w.underwriting_time AS BINARY)<=>CAST(c.underwriting_time AS BINARY)) OR NOT(CAST(w.commercial_policy_no AS BINARY)<=>CAST(c.commercial_policy_no AS BINARY)) OR NOT(CAST(w.compulsory_policy_no AS BINARY)<=>CAST(c.compulsory_policy_no AS BINARY)) OR NOT(CAST(w.accept_insurance_remark AS BINARY)<=>CAST(c.accept_insurance_remark AS BINARY)) OR NOT(CAST(w.accept_insurance_failed_remark AS BINARY)<=>CAST(c.accept_insurance_failed_remark AS BINARY)) OR NOT(CAST(w.finish_time AS BINARY)<=>CAST(c.finish_time AS BINARY))'));
    IF @mig_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Underwriting field verification failed';
    END IF;
    CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM ',@mp,' c LEFT JOIN ',@nw,
        ' w ON w.enterprise_id=c.enterprise_id AND w.id=c.workorder_id AND w.deleted=c.deleted WHERE w.id IS NULL OR ',
        'NOT(CAST(w.required_pay_amount AS BINARY)<=>CAST(c.required_pay_amount AS BINARY)) OR NOT(CAST(w.payee_name AS BINARY)<=>CAST(c.payee_name AS BINARY)) OR NOT(CAST(w.payee_phone AS BINARY)<=>CAST(c.payee_phone AS BINARY)) OR NOT(CAST(w.payee_id_num AS BINARY)<=>CAST(c.payee_id_num AS BINARY)) OR NOT(CAST(w.merchant_bank AS BINARY)<=>CAST(c.merchant_bank AS BINARY)) OR NOT(CAST(w.merchant_bank_card_num AS BINARY)<=>CAST(c.merchant_bank_card_num AS BINARY)) OR NOT(CAST(w.pay_remark AS BINARY)<=>CAST(c.pay_remark AS BINARY)) OR NOT(CAST(w.pay_failed_remark AS BINARY)<=>CAST(c.pay_failed_remark AS BINARY))'));
    IF @mig_bad > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Payment field verification failed';
    END IF;
    WHILE v_i < 2 DO
        SET v_prefix=ELT(v_i+1,'upstream','downstream');
        SET v_side=ELT(v_i+1,'UPSTREAM','DOWNSTREAM');
        CALL mig261107_exec(CONCAT('SELECT COUNT(*) INTO @mig_bad FROM ',@mc,' c LEFT JOIN ',@nq,
            ' q ON q.enterprise_id=c.enterprise_id AND q.workorder_id=c.workorder_id AND q.deleted=c.deleted ',
            'WHERE BINARY c.side=BINARY ''',v_side,''' AND (q.id IS NULL OR ',
            'NOT(CAST(q.',v_prefix,'_compute_type AS BINARY)<=>CAST(c.compute_type AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_commercial_percentage AS BINARY)<=>CAST(c.commercial_percentage AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_compulsory_percentage AS BINARY)<=>CAST(c.compulsory_percentage AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_vehicle_and_vessel_tax_percentage AS BINARY)<=>CAST(c.vehicle_tax_percentage AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_non_motor_percentage AS BINARY)<=>CAST(c.non_motor_percentage AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_commercial_amount AS BINARY)<=>CAST(c.commercial_amount AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_compulsory_amount AS BINARY)<=>CAST(c.compulsory_amount AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_vehicle_and_vessel_tax_amount AS BINARY)<=>CAST(c.vehicle_tax_amount AS BINARY))',' OR ',
            'NOT(CAST(q.',v_prefix,'_non_motor_amount AS BINARY)<=>CAST(c.non_motor_amount AS BINARY))',')'));
        IF @mig_bad > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Commission field verification failed';
        END IF;
        SET v_i=v_i+1;
    END WHILE;
END$$

/*
 * 总控：结构前置检查必须全部通过才创建影子表。
 * 仅支持已确认的在线表审计时间触发器；未知触发器、外键或生成列仍中止。
 * 全部在线/归档转换及校验通过后，单条 RENAME TABLE 原子切换；旧数据不删除。
 */
CREATE PROCEDURE mig261107_main()
BEGIN
    DECLARE v_gc BIGINT UNSIGNED DEFAULT @@SESSION.group_concat_max_len;
    DECLARE v_mode TEXT DEFAULT @@SESSION.sql_mode;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET SESSION group_concat_max_len=v_gc;
        SET SESSION sql_mode=v_mode;
        RESIGNAL;
    END;
    SET @mig261107_ready=0;
    IF DATABASE() IS NULL OR (DATABASE() NOT IN ('insurance_saas','insurance_saas_dev') AND DATABASE() NOT LIKE 'insurance_saas_mergecheck_%') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected target database; select insurance_saas or insurance_saas_dev explicitly';
    END IF;
    IF VERSION() NOT REGEXP '^8[.]' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='This migration requires MySQL 8.x';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder_bak261107', 'biz_workorder_m261107', 'biz_workorder_archive_bak261107', 'biz_workorder_archive_m261107', 'biz_workorder_quote_bak261107', 'biz_workorder_quote_m261107', 'biz_workorder_quote_archive_bak261107', 'biz_workorder_quote_archive_m261107', 'biz_workorder_underwriting_bak261107', 'biz_workorder_underwriting_m261107', 'biz_workorder_underwriting_archive_bak261107', 'biz_workorder_underwriting_archive_m261107', 'biz_workorder_payment_bak261107', 'biz_workorder_payment_m261107', 'biz_workorder_payment_archive_bak261107', 'biz_workorder_payment_archive_m261107', 'biz_workorder_commission_bak261107', 'biz_workorder_commission_m261107', 'biz_workorder_commission_archive_bak261107', 'biz_workorder_commission_archive_m261107')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration backup/staging table exists; inspect earlier run before retry';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder', 'biz_workorder_archive', 'biz_workorder_quote', 'biz_workorder_quote_archive', 'biz_workorder_underwriting', 'biz_workorder_underwriting_archive', 'biz_workorder_payment', 'biz_workorder_payment_archive', 'biz_workorder_commission', 'biz_workorder_commission_archive') AND ENGINE='InnoDB') <> 10 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected ten source InnoDB tables including archives';
    END IF;
    /*
     * 只接受用户提供的审计时间逻辑，规范化空白/反引号后逐项核对。
     * 不接受归档触发器或额外业务语句，避免迁移后遗失未知副作用。
     * 原触发器随备份表保留，新表触发器在数据复制校验之后安装。
     */
    IF EXISTS(SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()
        AND EVENT_OBJECT_TABLE IN ('biz_workorder', 'biz_workorder_archive', 'biz_workorder_quote', 'biz_workorder_quote_archive', 'biz_workorder_underwriting', 'biz_workorder_underwriting_archive', 'biz_workorder_payment', 'biz_workorder_payment_archive', 'biz_workorder_commission', 'biz_workorder_commission_archive')
        AND NOT (EVENT_OBJECT_TABLE IN ('biz_workorder','biz_workorder_quote','biz_workorder_underwriting','biz_workorder_payment','biz_workorder_commission')
            AND ACTION_TIMING='BEFORE' AND (
                (EVENT_MANIPULATION='INSERT' AND REGEXP_REPLACE(LOWER(ACTION_STATEMENT),'[[:space:]`;]','')='setnew.created_at=coalesce(new.created_at,current_timestamp),new.updated_at=coalesce(new.updated_at,current_timestamp)')
                OR (EVENT_MANIPULATION='UPDATE' AND REGEXP_REPLACE(LOWER(ACTION_STATEMENT),'[[:space:]`;]','')='setnew.updated_at=current_timestamp')
            ))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown source trigger; requires tailored migration';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()
        AND TRIGGER_NAME IN ('trg_m261107_workorder_bi','trg_m261107_workorder_bu','trg_m261107_quote_bi','trg_m261107_quote_bu')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration trigger name exists; inspect earlier run';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.KEY_COLUMN_USAGE WHERE
        (TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('biz_workorder', 'biz_workorder_archive', 'biz_workorder_quote', 'biz_workorder_quote_archive', 'biz_workorder_underwriting', 'biz_workorder_underwriting_archive', 'biz_workorder_payment', 'biz_workorder_payment_archive', 'biz_workorder_commission', 'biz_workorder_commission_archive') AND REFERENCED_TABLE_NAME IS NOT NULL)
        OR (REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IN ('biz_workorder', 'biz_workorder_archive', 'biz_workorder_quote', 'biz_workorder_quote_archive', 'biz_workorder_underwriting', 'biz_workorder_underwriting_archive', 'biz_workorder_payment', 'biz_workorder_payment_archive', 'biz_workorder_commission', 'biz_workorder_commission_archive'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source has foreign key; requires tailored migration';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder', 'biz_workorder_archive', 'biz_workorder_quote', 'biz_workorder_quote_archive', 'biz_workorder_underwriting', 'biz_workorder_underwriting_archive', 'biz_workorder_payment', 'biz_workorder_payment_archive', 'biz_workorder_commission', 'biz_workorder_commission_archive') AND COALESCE(GENERATION_EXPRESSION,'')<>'') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source has generated columns; requires explicit copy list';
    END IF;
    /* 子表新增过未知业务列时先中止，避免合并只覆盖已知字段。 */
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder_underwriting','biz_workorder_underwriting_archive')) <> 28
       OR EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder_underwriting','biz_workorder_underwriting_archive')
        AND COLUMN_NAME NOT IN ('id','enterprise_id','workorder_id','underwriting_remark','underwriting_failed_remark','underwriting_time','commercial_policy_no','compulsory_policy_no','accept_insurance_remark','accept_insurance_failed_remark','finish_time','created_at','updated_at','deleted')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected underwriting columns; update field mapping before migration';
    END IF;
    /* 子表新增过未知业务列时先中止，避免合并只覆盖已知字段。 */
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder_payment','biz_workorder_payment_archive')) <> 28
       OR EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder_payment','biz_workorder_payment_archive')
        AND COLUMN_NAME NOT IN ('id','enterprise_id','workorder_id','required_pay_amount','payee_name','payee_phone','payee_id_num','merchant_bank','merchant_bank_card_num','pay_remark','pay_failed_remark','created_at','updated_at','deleted')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected payment columns; update field mapping before migration';
    END IF;
    /* 子表新增过未知业务列时先中止，避免合并只覆盖已知字段。 */
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder_commission','biz_workorder_commission_archive')) <> 32
       OR EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN ('biz_workorder_commission','biz_workorder_commission_archive')
        AND COLUMN_NAME NOT IN ('id','enterprise_id','workorder_id','side','compute_type','commercial_percentage','compulsory_percentage','vehicle_tax_percentage','non_motor_percentage','commercial_amount','compulsory_amount','vehicle_tax_amount','non_motor_amount','created_at','updated_at','deleted')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected commission columns; update field mapping before migration';
    END IF;
    SET SESSION group_concat_max_len=1048576;
    SET SESSION sql_mode=CONCAT_WS(',',NULLIF(v_mode,''),'STRICT_ALL_TABLES');
    CALL mig261107_check('');
    CALL mig261107_check('_archive');
    CALL mig261107_build('');
    CALL mig261107_build('_archive');
    CALL mig261107_verify_original('biz_workorder',FALSE);
    CALL mig261107_verify_original('biz_workorder_archive',FALSE);
    CALL mig261107_verify_original('biz_workorder_quote',TRUE);
    CALL mig261107_verify_original('biz_workorder_quote_archive',TRUE);
    CALL mig261107_verify_merged('');
    CALL mig261107_verify_merged('_archive');

    SET SESSION group_concat_max_len=v_gc;
    SET SESSION sql_mode=v_mode;
    SET @mig261107_ready=1;
END$$

/*
 * 只有本次复制校验和四个新触发器安装全部成功后才切换表。
 * 独立过程使客户端遇错继续执行时也无法跳过准备失败或缺失触发器的检查。
 * 原表触发器由 MySQL 随改名保留，原业务表及其审计逻辑可一起回退。
 */
CREATE PROCEDURE mig261107_finish()
BEGIN
    IF COALESCE(@mig261107_ready,0)<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration preparation failed; no table switch allowed';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()
        AND ((EVENT_OBJECT_TABLE='biz_workorder_m261107' AND TRIGGER_NAME IN ('trg_m261107_workorder_bi','trg_m261107_workorder_bu'))
          OR (EVENT_OBJECT_TABLE='biz_workorder_quote_m261107' AND TRIGGER_NAME IN ('trg_m261107_quote_bi','trg_m261107_quote_bu'))))<>4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration audit triggers incomplete; no table switch allowed';
    END IF;
    /*
     * 唯一切换点。此前仅写影子表，任何错误都不覆盖原业务表。
     * 备份保持原始结构/数据，供核对和维护窗口内的人工回退使用。
     */
    RENAME TABLE
        biz_workorder TO biz_workorder_bak261107,
        biz_workorder_archive TO biz_workorder_archive_bak261107,
        biz_workorder_quote TO biz_workorder_quote_bak261107,
        biz_workorder_quote_archive TO biz_workorder_quote_archive_bak261107,
        biz_workorder_underwriting TO biz_workorder_underwriting_bak261107,
        biz_workorder_underwriting_archive TO biz_workorder_underwriting_archive_bak261107,
        biz_workorder_payment TO biz_workorder_payment_bak261107,
        biz_workorder_payment_archive TO biz_workorder_payment_archive_bak261107,
        biz_workorder_commission TO biz_workorder_commission_bak261107,
        biz_workorder_commission_archive TO biz_workorder_commission_archive_bak261107,
        biz_workorder_m261107 TO biz_workorder,
        biz_workorder_archive_m261107 TO biz_workorder_archive,
        biz_workorder_quote_m261107 TO biz_workorder_quote,
        biz_workorder_quote_archive_m261107 TO biz_workorder_quote_archive;
    SET @mig261107_ready=0;
    SELECT 'Migration completed. Keep *_bak261107; deploy adapted backend before resuming writes.' AS result;
END$$
DELIMITER ;

CALL mig261107_main();

/*
 * MySQL 不支持通过 PREPARE 创建触发器，因此使用独立 DDL 在影子表上安装。
 * 此时历史数据已复制并逐字段校验；不会因 UPDATE 触发器覆盖历史更新时间。
 * 新增写入补齐空审计时间，更新始终刷新 updated_at，与现网定义一致。
 * 使用本次执行账号作为 DEFINER，不依赖另一台服务器的 root@localhost 授权。
 */
CREATE TRIGGER trg_m261107_workorder_bi BEFORE INSERT ON biz_workorder_m261107
FOR EACH ROW SET NEW.created_at=COALESCE(NEW.created_at,CURRENT_TIMESTAMP), NEW.updated_at=COALESCE(NEW.updated_at,CURRENT_TIMESTAMP);
CREATE TRIGGER trg_m261107_workorder_bu BEFORE UPDATE ON biz_workorder_m261107
FOR EACH ROW SET NEW.updated_at=CURRENT_TIMESTAMP;
CREATE TRIGGER trg_m261107_quote_bi BEFORE INSERT ON biz_workorder_quote_m261107
FOR EACH ROW SET NEW.created_at=COALESCE(NEW.created_at,CURRENT_TIMESTAMP), NEW.updated_at=COALESCE(NEW.updated_at,CURRENT_TIMESTAMP);
CREATE TRIGGER trg_m261107_quote_bu BEFORE UPDATE ON biz_workorder_quote_m261107
FOR EACH ROW SET NEW.updated_at=CURRENT_TIMESTAMP;

CALL mig261107_finish();

DROP PROCEDURE mig261107_finish;
DROP PROCEDURE mig261107_main;
DROP PROCEDURE mig261107_verify_merged;
DROP PROCEDURE mig261107_verify_original;
DROP PROCEDURE mig261107_build;
DROP PROCEDURE mig261107_clone;
DROP PROCEDURE mig261107_check;
DROP PROCEDURE mig261107_exec;

/*
 * 回退说明：仅在尚未恢复业务写入的维护窗口内，可将新主表/quote 改名留存，
 * 再将十张 *_bak261107 按原名一次性 RENAME 回来；不得直接 DROP 新表。
 * 已恢复写入后不可直接换回备份，否则会丢失新业务，必须先备份并专项对账。
 * 本文件不自动执行回退，也不删除任何备份。
 */

