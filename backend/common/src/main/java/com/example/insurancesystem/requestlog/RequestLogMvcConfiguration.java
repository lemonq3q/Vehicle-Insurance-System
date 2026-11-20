package com.example.insurancesystem.requestlog;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import javax.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/** 在MVC入口读取服务端已认证身份，早于安全上下文清理；不修改认证或业务响应。 */
@Configuration
public class RequestLogMvcConfiguration implements WebMvcConfigurer {
    /** 只观察进入MVC异常解析边界的异常，返回null继续原处理器，不改变任何错误响应。 */
    @Override public void extendHandlerExceptionResolvers(java.util.List<HandlerExceptionResolver> resolvers) {
        resolvers.add(0,(request,response,handler,error) -> { RequestLogContext.recordException(error); return null; });
    }
    /** 请求进入控制器前记录用户和企业，匿名或其他主体保持空值。 */
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                Authentication auth=SecurityContextHolder.getContext().getAuthentication();
                if(auth!=null && auth.getPrincipal() instanceof LoginUser) { LoginUser user=(LoginUser)auth.getPrincipal();
                    RequestLogContext.put("enterpriseId",user.getEnterpriseId());
                    if(user.getUser()!=null) RequestLogContext.put("userId",user.getUser().getId());
                }
                return true;
            }
        });
    }
}
