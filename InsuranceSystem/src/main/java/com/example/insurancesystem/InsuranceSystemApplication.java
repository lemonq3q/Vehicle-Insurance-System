package com.example.insurancesystem;

import com.example.insurancesystem.utils.OSSClientSingleton;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InsuranceSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(InsuranceSystemApplication.class, args);
		Runtime.getRuntime().addShutdownHook(new Thread(OSSClientSingleton::shutdown));
	}

}
