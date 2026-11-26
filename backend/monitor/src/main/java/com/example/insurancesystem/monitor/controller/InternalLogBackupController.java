package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.monitor.service.RequestLogWriteGate;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** 仅维护协调器可暂停日志消费者，放行维护路径但仍逐次校验共享密钥，不暂停普通业务线程。 */
@RestController
@RequestMapping("/internal/maintenance/log-consumer")
public class InternalLogBackupController {
    private final RequestLogWriteGate gate;private final String secret;
    /** 使用现有维护密钥，不提供用户JWT调用入口。 */
    public InternalLogBackupController(RequestLogWriteGate gate,@Value("${maintenance.participant.internal-secret}")String secret){this.gate=gate;this.secret=secret;}
    /** 等待在途入库完成再确认暂停，最长两小时自动恢复。 */
    @PostMapping("/pause") public ResponseResult<?> pause(@RequestHeader("X-Maintenance-Secret")String credential,@RequestParam String runId,@RequestParam int seconds)throws InterruptedException{
        authenticate(credential);try{gate.pause(runId,seconds);}catch(IllegalArgumentException e){throw new HttpBusinessException(400,e.getMessage());}catch(IllegalStateException e){throw new HttpBusinessException(409,e.getMessage());}return new ResponseResult<>(200,"已暂停");
    }
    /** 恢复本次备份暂停，重复恢复保持幂等。 */
    @PostMapping("/resume") public ResponseResult<?> resume(@RequestHeader("X-Maintenance-Secret")String credential,@RequestParam String runId){authenticate(credential);gate.resume(runId);return new ResponseResult<>(200,"已恢复");}
    /** 缺少或无效密钥拒绝访问。 */
    private void authenticate(String value){if(secret==null||secret.isBlank()||!secret.equals(value))throw new HttpBusinessException(403,"内部调用凭证无效");}
}
