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
            
            // 返回当前失败次数
            result.put("failedCount", attempt.getFailedCount());
            result.put("remainingAttempts", MAX_FAILED_ATTEMPTS - attempt.getFailedCount());
        } else {
            // 无失败记录
            result.put("failedCount", 0);
            result.put("remainingAttempts", MAX_FAILED_ATTEMPTS);
        }

        return result;
    }

    /**
     * 记录登录失败
     * @param account 账号
     * @return 包含剩余尝试次数的信息
     */
    public Map<String, Object> recordFailedAttempt(String account) {
        LoginAttempt attempt = loginAttemptDao.findByAccount(account).orElse(null);
        Map<String, Object> result = new HashMap<>();
        
        if (attempt == null) {
            // 创建新记录
            attempt = new LoginAttempt();
            attempt.setAccount(account);
            attempt.setFailedCount(1);
            attempt.setAttemptTime(LocalDateTime.now());
        } else {
            // 更新现有记录
            // 如果锁定已过期，重置失败计数
            if (attempt.getLockedUntil() != null && 
                attempt.getLockedUntil().isBefore(LocalDateTime.now())) {
                attempt.setFailedCount(1);
                attempt.setLockedUntil(null);
            } else {
                attempt.setFailedCount(attempt.getFailedCount() + 1);
            }
            attempt.setAttemptTime(LocalDateTime.now());
        }
        
        // 达到失败上限，锁定账号
        boolean locked = false;
        if (attempt.getFailedCount() >= MAX_FAILED_ATTEMPTS) {
            attempt.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            locked = true;
        }
        
        loginAttemptDao.save(attempt);
        
        result.put("failedCount", attempt.getFailedCount());
        result.put("remainingAttempts", Math.max(0, MAX_FAILED_ATTEMPTS - attempt.getFailedCount()));
        result.put("locked", locked);
        if (locked) {
            result.put("lockUntil", attempt.getLockedUntil().toString().replace("T", " "));
        }
        
        return result;
    }

    /**
     * 登录成功，清除失败记录
     */
    public void clearFailedAttempt(String account) {
        loginAttemptDao.deleteByAccount(account);
    }

    /**
     * 生成Token并设置过期时间（单点登录：每次登录都会使旧token失效）
     */
    public String generateToken(Users user) {
        String token = UUID.randomUUID().toString();
        user.setToken(token);
        user.setTokenExpiredAt(LocalDateTime.now().plusHours(TOKEN_VALID_HOURS));
        // 单点登录：递增token版本号，使之前的所有token失效
        if (user.getTokenVersion() == null) {
            user.setTokenVersion(1);
        } else {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
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
            // token不存在，可能是被新登录挤掉了
            result.put("valid", false);
            result.put("message", "您的账号已在其他设备登录，请重新登录");
            result.put("kicked", true);
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
     * 使当前用户的token失效（用于单点登录挤人）
     */
    public void invalidateCurrentToken(Users user) {
        user.setToken(null);
        user.setTokenExpiredAt(null);
        user.setTokenVersion(user.getTokenVersion() != null ? user.getTokenVersion() + 1 : 1);
        usersDao.save(user);
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
