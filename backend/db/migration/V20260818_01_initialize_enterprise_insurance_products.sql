-- 为多租户改造后创建、但尚未初始化险种目录的企业补齐标准险种。
-- 标准险种直接读取旧 insurance 库；已有任意有效险种的企业不会被修改，避免覆盖企业自定义配置。
INSERT INTO biz_insurance_product (
    enterprise_id,
    name,
    type,
    options_json,
    default_option_json,
    deductible_options_json,
    default_deductible_option_json,
    remark,
    created_at,
    updated_at,
    updated_by,
    deleted
)
SELECT
    enterprise.id,
    source_product.name,
    source_product.type,
    CAST(source_product.options_json AS JSON),
    source_product.default_option_json,
    CAST(source_product.deductible_options_json AS JSON),
    source_product.default_deductible_option_json,
    source_product.remark,
    FROM_UNIXTIME(source_product.create_time),
    FROM_UNIXTIME(source_product.update_time),
    enterprise.owner_user_id,
    source_product.is_delete
FROM tenant_enterprise enterprise
JOIN insurance.insurance source_product
    ON source_product.is_delete = 0
WHERE enterprise.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM biz_insurance_product current_product
      WHERE current_product.enterprise_id = enterprise.id
        AND current_product.deleted = 0
  );
