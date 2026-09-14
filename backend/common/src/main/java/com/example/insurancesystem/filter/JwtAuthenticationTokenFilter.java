package com.example.insurancesystem.filter;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import com.example.insurancesystem.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;

@Component
/**
 * 每个请求执行一次的 JWT 认证过滤器，同时兼容历史 token 请求头和标准 Bearer 头。
 * 令牌验签后还需与 Redis 单登录会话核对 jti，成功才向 SecurityContext 写入完整用户权限。
 */
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "new-token";

    @Autowired
    private SingleLoginSessionManager sessionManager;

    @Autowired
    private AuthenticationEntryPoint authenticationEntryPoint;

    /**
     * 游客线索新增是官网匿名入口，其安全边界由来源白名单和提交频率保护组件负责。
     * 明确跳过 JWT 解析可避免浏览器残留的过期令牌把本应公开的提交错误转换为 401；
     * 这里只豁免固定路径的 POST，请求该资源的其他方法仍由 Spring Security 路由规则处理。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/portal/visitor-leads".equals(request.getServletPath());
    }

    @Override
    /**
     * 提取并验证 JWT，检查 Redis 中当前会话，刷新活跃会话有效期，并在 JWT 临近过期时通过 new-token 响应头续签。
     * 令牌非法或会话已失效时直接交给统一认证入口返回 401，不再进入业务过滤链；匿名请求则不建立认证上下文并继续放行。
     */
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("token");
        if (token == null) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Claims claims;
        String userid;
        String jti;
        try {
            claims = JwtUtil.parseJWT(token);
            userid = claims.getSubject();
            jti = claims.getId();
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("非法token，请重新登陆", e)
            );
            return;
        }

        Long userId = Long.valueOf(userid);
        LoginUser loginUser = sessionManager.get(userId, jti);
        if (loginUser == null) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("用户未登陆")
            );
            return;
        }
        /*
         * Redis 会话采用滑动过期：每次合法请求都会续期，而 JWT 只在达到刷新阈值时重新签发，
         * 避免每个响应都生成新令牌，同时保持活跃会话连续可用。
         */
        sessionManager.refresh(userId, loginUser);

        Date expiration = claims.getExpiration();
        long remainMillis = expiration.getTime() - System.currentTimeMillis();
        if (remainMillis <= JwtUtil.JWT_REFRESH_THRESHOLD) {
            String newToken = JwtUtil.createJWT(userid, JwtUtil.LOGIN_JWT_TTL, jti);
            response.setHeader(TOKEN_HEADER, newToken);
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
