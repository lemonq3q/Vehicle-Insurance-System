package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.system.MaintenanceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
/**
 * 用于验证认证链和 hello:select 权限配置的轻量探活控制器，不承载实际车险业务。
 */
public class HelloController {

    @Autowired
    private MaintenanceManager maintenanceManager;

    @GetMapping
    @PreAuthorize("hasAuthority('hello:select')")
    /**
     * 在权限校验通过后返回固定文本，用于开发环境快速检查 JWT 与方法级鉴权是否生效。
     */
    public String hello(){
        return "hello";
    }

//    @GetMapping("/test")
//    public ResponseResult test(){
//        maintenanceManager.startMaintenance();
//        new Thread(() -> {
//            try {
//                // 线程休眠30秒（单位：毫秒）
//                Thread.sleep(30 * 1000);
//                // 30秒后自动执行结束方法
//                maintenanceManager.stopMaintenance();
//                System.out.println("30秒已到，自动执行维护结束方法");
//            } catch (InterruptedException e) {
//                // 线程中断异常处理
//                Thread.currentThread().interrupt();
//                System.out.println("延迟执行线程被中断");
//            }
//        }).start(); // 启动线程
//
//        // 立即返回结果，不等待30秒
//        return new ResponseResult(200, "test succeed");
//    }

}
