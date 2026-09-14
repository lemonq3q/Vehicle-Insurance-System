package com.example.insurancesystem.coordinator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 独立维护协调后端 C 的启动入口。组件扫描限定在 coordinator 包，避免加载 common 中面向业务后端的
 * JWT、请求过滤器和参与端状态机；common 在本模块中只提供维护协议模型。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class})
@EnableScheduling
public class MaintenanceCoordinatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MaintenanceCoordinatorApplication.class, args);
    }
}