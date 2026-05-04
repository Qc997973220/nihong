package com.neon.config;

import com.neon.pojo.Users;
import com.neon.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从Header获取Token
        String token = request.getHeader("Authorization");
        
        // 如果Header没有，尝试从参数获取
        if (token == null || token.trim().isEmpty()) {
            token = request.getParameter("token");
        }

        // 验证Token
        Map<String, Object> authResult = authService.validateToken(token);
        
        if (!(Boolean) authResult.get("valid")) {
            sendError(response, (String) authResult.get("message"), 401);
            return false;
        }

        // 将用户信息存入Request，供后续接口使用
        Users user = (Users) authResult.get("user");
        request.setAttribute("currentUser", user);
        return true;
    }

    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
