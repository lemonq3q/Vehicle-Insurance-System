package com.example.insurancesystem;

import com.example.insurancesystem.utils.OSSClientSingleton;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * 车险业务系统的 Spring Boot 启动入口，同时启用每日维护等定时任务。
 */
public class InsuranceSystemApplication {

	/**
	 * 启动应用并注册 JVM 关闭钩子，确保退出时释放共享 OSS 客户端连接资源。
	 */
	public static void main(String[] args) {
		SpringApplication.run(InsuranceSystemApplication.class, args);
		Runtime.getRuntime().addShutdownHook(new Thread(OSSClientSingleton::shutdown));
	}

}
