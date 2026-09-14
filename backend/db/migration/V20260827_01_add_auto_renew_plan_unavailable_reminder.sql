-- 注册“自动续费套餐已下架”类型。提醒事实表继续通过稳定字符串关联字典，不需要修改两张提醒表结构。
-- 先部署本迁移再启用新的 SaaS 每日提醒规则，避免监控端因未知类型拒绝合并请求。
INSERT INTO sys_reminder_type(category_id,type_code,type_name,description,status,sort_no)
SELECT c.id,'AUTO_RENEW_PLAN_UNAVAILABLE','自动续费套餐已下架',
       '企业已开启自动续费，但本周期实际续费目标套餐已下架或删除',1,15
FROM sys_reminder_category c
WHERE c.category_code='SUBSCRIPTION'
ON DUPLICATE KEY UPDATE category_id=VALUES(category_id),type_name=VALUES(type_name),
                        description=VALUES(description),status=VALUES(status),sort_no=VALUES(sort_no);
