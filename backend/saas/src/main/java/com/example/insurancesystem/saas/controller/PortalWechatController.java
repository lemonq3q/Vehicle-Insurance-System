package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.security.wechat.WechatFlowService;
import com.example.insurancesystem.saas.service.PortalAuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 门户微信登录和绑定入口，首次微信登录允许自动注册个人账号。
 * 与既有邮箱认证共享用户及 JWT 会话；绑定接口由安全过滤链要求登录。
 */
@RestController
public class PortalWechatController {
    private final WechatFlowService flow;
    private final PortalAuthService auth;

    /** 注入授权编排和现有门户会话服务，不自行生成新种类的 token。 */
    public PortalWechatController(WechatFlowService flow, PortalAuthService auth) { this.flow = flow; this.auth = auth; }

    /** 匿名申请登录二维码，返回公开参数与一次性 state。 */
    @PostMapping("/portal/auth/wechat/start")
    public ResponseResult<?> start(@RequestBody Map<String, Object> body) { return new ResponseResult<>(200, "操作成功", flow.start(body, false)); }

    /** 验证微信身份后自动注册或登录，复用既有用户、企业上下文及 JWT 格式。 */
    @PostMapping("/portal/auth/wechat/complete")
    public ResponseResult<?> complete(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "登录成功", auth.wechatLogin(flow.complete(body, false, true)));
    }

    /** 为当前登录会话申请绑定二维码，不允许匿名提供任意目标用户。 */
    @PostMapping("/portal/user/profile/wechat/start")
    public ResponseResult<?> bindStart(@RequestBody Map<String, Object> body) { return new ResponseResult<>(200, "操作成功", flow.start(body, true)); }

    /** 首次绑定后保留当前 JWT；重复绑定同一身份幂等，其他账号占用返回冲突。 */
    @PostMapping("/portal/user/profile/wechat/complete")
    public ResponseResult<?> bindComplete(@RequestBody Map<String, Object> body) {
        flow.complete(body, true, false);
        return new ResponseResult<>(200, "微信绑定成功", Map.of("wechatBound", true));
    }
}
