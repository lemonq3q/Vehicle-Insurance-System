package com.example.insurancesystem.saas;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.insurancesystem")
@MapperScan(basePackages = {"com.example.insurancesystem.saas.mapper", "com.example.insurancesystem.utils.email"}, annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableScheduling
public class SaasApplication {

  public static void main(String[] args) {
    SpringApplication.run(SaasApplication.class, args);
  }
}
