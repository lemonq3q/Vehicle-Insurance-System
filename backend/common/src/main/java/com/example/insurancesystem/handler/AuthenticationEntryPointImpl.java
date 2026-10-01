package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.utils.WebUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
/**
 * Spring Security 未认证入口，统一处理缺少、非法、过期或已被单登录机制失效的令牌。
 */
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    @Autowired
    @Qualifier("apiObjectMapper")
    private ObjectMapper objectMapper;

    @Override
    /**
     * 将认证异常转换为 401 统一响应，避免客户端依赖容器默认错误格式，并提示重新建立登录会话。
     * 本入口绕过 MVC 转换器，显式使用 API Mapper 保持接口序列化协议。
     * 缺少或过期令牌属于可预期的客户端状态，不向标准错误流打印完整异常堆栈，防止公网匿名请求
     * 或登录过期后的前端请求持续污染服务日志。
     */
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException, ServletException {
        ResponseResult result = new ResponseResult(HttpStatus.UNAUTHORIZED.value(), "用户认证失败，请重新登陆");
        String json = objectMapper.writeValueAsString(result);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        WebUtils.renderString(response, json);
    }
}
