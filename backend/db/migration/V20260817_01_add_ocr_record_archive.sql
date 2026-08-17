-- 企业套餐结束超过数据保留期后，OCR 记录与其他车险业务数据一样迁移到归档表。
-- 本脚本只补充归档存储结构，不迁移或删除任何现有数据；实际迁移由维护任务按 enterprise_id 执行。
CREATE TABLE IF NOT EXISTS `biz_ocr_record_archive` LIKE `biz_ocr_record`;

ALTER TABLE `biz_ocr_record_archive`
  ADD KEY `idx_biz_ocr_record_archive_enterprise_time` (`enterprise_id`, `created_at`);
