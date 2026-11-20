-- 请求日志归监控系统所有；不设置 deleted，不参加业务逻辑删除归档。
CREATE TABLE IF NOT EXISTS monitor_request_log (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 log_id varchar(36) NOT NULL,
 source_system varchar(64) NOT NULL,
 started_at datetime(3) NOT NULL,
 duration_ms bigint NOT NULL,
 method varchar(16), uri varchar(768),
 http_status int, business_code varchar(32), content_type varchar(128),
 has_exception tinyint NOT NULL DEFAULT 0,
 enterprise_id bigint, user_id bigint,
 UNIQUE KEY uk_request_log_id(log_id),
 KEY idx_request_time(started_at,id),
 KEY idx_request_service(source_system,started_at,id),
 KEY idx_request_enterprise(enterprise_id,started_at,id),
 KEY idx_request_user(user_id,started_at,id),
 KEY idx_request_exception(has_exception,started_at,id),
 KEY idx_request_http(http_status,started_at,id),
 KEY idx_request_business(business_code,started_at,id),
 KEY idx_request_uri(uri(191),started_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS monitor_request_log_detail (
 log_id varchar(36) NOT NULL PRIMARY KEY,
 payload longtext NOT NULL COMMENT '请求、响应、业务文本与异常原始日志JSON'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
