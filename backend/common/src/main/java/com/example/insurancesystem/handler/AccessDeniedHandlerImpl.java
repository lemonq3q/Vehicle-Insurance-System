package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.utils.WebUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
/**
 * 处理已经完成身份认证但权限不足的请求，将 Spring Security 异常转换为项目统一 JSON 响应而不是默认 HTML 错误页。
 */
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    /**
     * 返回 403 业务响应，并使用项目主 ObjectMapper 保持字段和时间序列化规则一致；WebUtils 负责设置 JSON 编码并写出响应。
     */
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException, ServletException {
        e.printStackTrace();
        ResponseResult result = new ResponseResult(HttpStatus.FORBIDDEN.value(), "你没有权限访问此资源");
        String json = objectMapper.writeValueAsString(result);
        WebUtils.renderString(response, json);
    }
}
