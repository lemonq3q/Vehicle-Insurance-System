package com.example.insurancesystem.filter;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.utils.JwtUtil;
import com.example.insurancesystem.utils.RedisCache;
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
import java.util.concurrent.TimeUnit;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "new-token";

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("token");
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

        String redisKey = "login:" + userid + ":" + jti;
        LoginUser loginUser = redisCache.getCacheObject(redisKey);
        if (loginUser == null) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("用户未登陆")
            );
            return;
        }
        // 刷新过期时间
        redisCache.setCacheObject("login:"+userid+":"+jti, loginUser, 24 , TimeUnit.HOURS);

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
