package com.neon.service;

import com.neon.dao.LoginAttemptDao;
import com.neon.dao.UsersDao;
import com.neon.pojo.LoginAttempt;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UsersDao usersDao;

    @Autowired
    private LoginAttemptDao loginAttemptDao;

    // Token有效期：24小时
    private static final int TOKEN_VALID_HOURS = 24;
    
    // 最大登录失败次数
    private static final int MAX_FAILED_ATTEMPTS = 5;
    
    // 锁定时长：30分钟
    private static final int LOCK_MINUTES = 30;

    /**
     * 验证登录失败次数
     */
    public Map<String, Object> checkLoginAttempt(String account) {
        Map<String, Object> result = new HashMap<>();
        result.put("allowed", true);

        Optional<LoginAttempt> optional = loginAttemptDao.findByAccount(account);
        if (optional.isPresent()) {
            LoginAttempt attempt = optional.get();
            
            // 检查是否被锁定
            if (attempt.getLockedUntil() != null && 
                attempt.getLockedUntil().isAfter(LocalDateTime.now())) {
                result.put("allowed", false);
                result.put("message", "登录失败次数过多，账号已锁定。请在 " + 
                    attempt.getLockedUntil().toString().replace("T", " ") + " 后重试");
                return result;
            }
            
            // 锁定已过期，清除旧记录
            loginAttemptDao.delete(attempt);
        }

        return result;
    }

    /**
     * 记录登录失败
     */
    public void recordFailedAttempt(String account) {
        LoginAttempt attempt = loginAttemptDao.findByAccount(account).orElse(new LoginAttempt());
        
        if (attempt.getAccount() == null) {
            attempt.setAccount(account);
            attempt.setFailedCount(1);
        } else {
            attempt.setFailedCount(attempt.getFailedCount() + 1);
        }
        
        attempt.setAttemptTime(LocalDateTime.now());
        
        // 达到失败上限，锁定账号
        if (attempt.getFailedCount() >= MAX_FAILED_ATTEMPTS) {
            attempt.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        }
        
        loginAttemptDao.save(attempt);
    }

    /**
     * 登录成功，清除失败记录
     */
    public void clearFailedAttempt(String account) {
        loginAttemptDao.deleteByAccount(account);
    }

    /**
     * 生成Token并设置过期时间
     */
    public String generateToken(Users user) {
        String token = UUID.randomUUID().toString();
        user.setToken(token);
        user.setTokenExpiredAt(LocalDateTime.now().plusHours(TOKEN_VALID_HOURS));
        usersDao.save(user);
        return token;
    }

    /**
     * 验证Token
     */
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> result = new HashMap<>();
        
        if (token == null || token.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "请先登录");
            return result;
        }

        Users user = usersDao.findByToken(token);
        if (user == null) {
            result.put("valid", false);
            result.put("message", "无效的登录状态，请重新登录");
            return result;
        }

        // 检查token是否过期
        if (user.getTokenExpiredAt() == null || 
            user.getTokenExpiredAt().isBefore(LocalDateTime.now())) {
            result.put("valid", false);
            result.put("message", "登录已过期，请重新登录");
            // 清除过期token
            user.setToken(null);
            user.setTokenExpiredAt(null);
            usersDao.save(user);
            return result;
        }

        result.put("valid", true);
        result.put("user", user);
        return result;
    }

    /**
     * 登出，清除token
     */
    public void logout(String token) {
        Users user = usersDao.findByToken(token);
        if (user != null) {
            user.setToken(null);
            user.setTokenExpiredAt(null);
            usersDao.save(user);
        }
    }
}
