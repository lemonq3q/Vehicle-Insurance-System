package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.security.wechat.WechatMessageCodec;
import com.example.insurancesystem.saas.service.WechatRevocationService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * SaaS 独占微信消息推送入口；与用户授权前端回调分离，不需要用户 JWT 或用户 access_token。
 * 只有签名、解密及 AppID 验证通过的事件才进入会话撤销流程；不推送前端强退消息。
 */
@RestController
@RequestMapping("/portal/auth/wechat/events")
public class WechatEventController {
    private final WechatMessageCodec codec;
    private final WechatRevocationService revocations;

    /** 注入安全协议校验器和跨系统会话撤销服务，不直接操作用户表。 */
    public WechatEventController(WechatMessageCodec codec, WechatRevocationService revocations) { this.codec = codec; this.revocations = revocations; }

    /** 配置微信消息接收地址时完成服务器校验，返回 echostr 明文。 */
    @GetMapping(produces = "text/plain;charset=UTF-8")
    public String verify(@RequestParam Map<String, String> query) { return codec.verifyUrl(query); }

    /** 微信回调要求真正的失败 HTTP 状态，避免统一业务响应外壳返回 200 而丢失平台重试。 */
    @ExceptionHandler(com.example.insurancesystem.handler.exception.BusinessException.class)
    public org.springframework.http.ResponseEntity<String> reject(com.example.insurancesystem.handler.exception.BusinessException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getCode()).body("wechat event rejected");
    }

    /** 接收安全模式 XML/JSON，成功后回复 success；远端失败由既有异常处理返回非成功并保留待办。 */
    @PostMapping(produces = "text/plain;charset=UTF-8")
    public String receive(@RequestParam Map<String, String> query, @RequestBody String body) {
        revocations.accept(codec.decode(body, query));
        return "success";
    }
}
