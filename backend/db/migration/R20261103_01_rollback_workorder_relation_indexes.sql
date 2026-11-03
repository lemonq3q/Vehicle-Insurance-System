/*
 * 仅撤销V20261103_01新增的具名索引，不修改任何行或原有索引。
 * 使用前核对information_schema.STATISTICS并确认迁移记录。
 * MySQL DDL隐式提交；仅在确需恢复旧执行计划时执行。
 */
USE insurance_saas;
ALTER TABLE `biz_workorder_quote` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_underwriting` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_logistics` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_vehicle_license` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_vehicle_certificate` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_vehicle_invoice` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_file` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_insurance` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_quote_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_underwriting_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_logistics_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_vehicle_license_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_vehicle_certificate_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_vehicle_invoice_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_file_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_insurance_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE `biz_workorder_commission_archive` DROP INDEX `idx_20261103_workorder_link`, ALGORITHM=INPLACE, LOCK=NONE;

