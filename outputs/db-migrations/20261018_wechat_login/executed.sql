-- 在 insurance_saas 执行一次；执行前审阅并备份。只增加可空微信字段及唯一索引，不修改或删除任何账号资料。
-- SaaS 与车险必须连接同一共享 tenant_user。先执行本脚本，再发布应用；不在运行时自动迁移。
USE `insurance_saas`;

ALTER TABLE `tenant_user`
  ADD COLUMN `wechat_app_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '绑定的网站应用AppID',
  ADD COLUMN `wechat_open_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '应用内微信身份，撤回授权仍保留',
  ADD COLUMN `wechat_union_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '开放平台内用户标识，预留且不自动合并账号',
  ADD COLUMN `wechat_nickname` VARCHAR(100) NULL COMMENT '首次授权昵称，不覆盖后续编辑姓名',
  ADD COLUMN `wechat_bound_at` DATETIME NULL COMMENT '首次绑定或微信注册时间',
  ADD UNIQUE KEY `uk_tenant_user_wechat` (`wechat_app_id`, `wechat_open_id`);

-- 不含头像字段；未绑定账号的两个身份字段均为 NULL。索引保留已软删除记录的占用，禁止静默重新注册同一身份。
SHOW INDEX FROM `tenant_user` WHERE Key_name='uk_tenant_user_wechat';
