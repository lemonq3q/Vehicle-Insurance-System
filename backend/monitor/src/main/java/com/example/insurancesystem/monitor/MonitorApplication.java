package com.example.insurancesystem.monitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 客服监控系统后端启动入口。当前阶段只开放 SaaS 维护任务需要的内部提醒合并能力，
 * 后续客服登录、提醒列表和处理接口均在该独立包体中扩展，避免与企业门户权限模型混用。
 */
@SpringBootApplication(scanBasePackages = "com.example.insurancesystem")
@MapperScan("com.example.insurancesystem.monitor.mapper")
public class MonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitorApplication.class, args);
    }
}
