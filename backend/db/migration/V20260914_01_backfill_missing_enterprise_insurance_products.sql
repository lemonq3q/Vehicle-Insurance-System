-- 为早于“企业创建时自动初始化险种”功能的存量企业补齐标准险种目录。
-- 执行前应核对当前连接的是 insurance_saas；以企业 3 当前有效的 23 条险种为复制模板。
-- 此脚本不向 biz_workorder_insurance 写入数据：该表只保存真实工单已选择的险种明细。
-- 幂等边界：已有任意有效险种的企业整体跳过，避免覆盖企业可能定制过的险种配置。
-- 保持文件只有一条原子 INSERT，以适配当前 MySQL MCP 的单语句执行器。

INSERT INTO biz_insurance_product (
    enterprise_id, name, type, options_json, default_option_json,
    deductible_options_json, default_deductible_option_json, remark,
    created_at, updated_at, updated_by, deleted
)
SELECT
    enterprise.id,
    source_product.name,
    source_product.type,
    source_product.options_json,
    source_product.default_option_json,
    source_product.deductible_options_json,
    source_product.default_deductible_option_json,
    source_product.remark,
    NOW(), NOW(), enterprise.owner_user_id, 0
FROM tenant_enterprise enterprise
JOIN biz_insurance_product source_product
    ON source_product.enterprise_id = 3 AND source_product.deleted = 0
WHERE enterprise.deleted = 0
  AND enterprise.id <> 3
  AND NOT EXISTS (
      SELECT 1
      FROM biz_insurance_product existing
      WHERE existing.enterprise_id = enterprise.id AND existing.deleted = 0
  );
