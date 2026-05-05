package com.example.insurancesystem.filter;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.utils.JwtUtil;
import com.example.insurancesystem.utils.RedisCache;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Autowired
    private RedisCache redisCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("token");
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String userid;
        String jti;
        try {
            Claims claims = JwtUtil.parseJWT(token);
            userid = claims.getSubject();
            jti = claims.getId();
        } catch (Exception e) {
//            e.printStackTrace();
            throw new RuntimeException("token illegal");
        }

        String redisKey = "login:" + userid + ":" + jti;
        LoginUser loginUser = redisCache.getCacheObject(redisKey);
        if (loginUser == null) {
            throw new RuntimeException("user not login");
        }
        // 刷新过期时间
        redisCache.setCacheObject("login:"+userid+":"+jti, loginUser, 24 , TimeUnit.HOURS);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
