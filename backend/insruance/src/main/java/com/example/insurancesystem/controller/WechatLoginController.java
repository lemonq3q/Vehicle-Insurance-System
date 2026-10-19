package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.security.wechat.WechatFlowService;
import com.example.insurancesystem.service.LoginService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 车险微信认证只接纳已注册共享账号，后续企业、成员、套餐和菜单检查沿用邮箱登录规则。
 * 允许当前用户增加微信绑定，不支持自动注册、身份转移或账号合并。
 */
@RestController
public class WechatLoginController {
    private final WechatFlowService flow;
    private final LoginService auth;

    /** 注入共享微信流程与本系统 JWT 签发服务，权限不由微信授权本身授予。 */
    public WechatLoginController(WechatFlowService flow, LoginService auth) { this.flow = flow; this.auth = auth; }

    /** 申请已有账号的扫码入口，尚未配置时提示改用邮箱。 */
    @PostMapping("/auth/wechat/start")
    public ResponseResult<?> start(@RequestBody Map<String, Object> body) { return new ResponseResult<>(200, "操作成功", flow.start(body, false)); }

    /** 固定禁止注册；身份存在后仍须通过车险业务访问校验才签发 JWT。 */
    @PostMapping("/auth/wechat/complete")
    public ResponseResult<?> complete(@RequestBody Map<String, Object> body) { return auth.wechatLogin(flow.complete(body, false, false)); }

    /** 当前用户发起首次微信绑定，state 关联当前用户及会话。 */
    @PostMapping("/auth/profile/wechat/start")
    public ResponseResult<?> bindStart(@RequestBody Map<String, Object> body) { return new ResponseResult<>(200, "操作成功", flow.start(body, true)); }

    /** 绑定仅增加登录方式，不修改账号 ID、企业、姓名或现有 JWT。 */
    @PostMapping("/auth/profile/wechat/complete")
    public ResponseResult<?> bindComplete(@RequestBody Map<String, Object> body) {
        flow.complete(body, true, false);
        return new ResponseResult<>(200, "微信绑定成功", Map.of("wechatBound", true));
    }
}
