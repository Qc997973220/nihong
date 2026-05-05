package com.neon.service;

import com.neon.dao.LoginAttemptDao;
import com.neon.dao.UsersDao;
import com.neon.pojo.LoginAttempt;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final int TOKEN_VALID_HOURS = 24;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 30;

    public Map<String, Object> checkLoginAttempt(String account) {
        Map<String, Object> result = new HashMap<>();
        result.put("allowed", true);

        Optional<LoginAttempt> optional = loginAttemptDao.findByAccount(account);
        if (optional.isPresent()) {
            LoginAttempt attempt = optional.get();

            if (attempt.getLockedUntil() != null &&
                attempt.getLockedUntil().isAfter(LocalDateTime.now())) {
                result.put("allowed", false);
                result.put("message", "登录失败次数过多，账号已锁定。请在 " +
                    attempt.getLockedUntil().toString().replace("T", " ") + " 后重试");
                return result;
            }

            result.put("failedCount", attempt.getFailedCount());
            result.put("remainingAttempts", MAX_FAILED_ATTEMPTS - attempt.getFailedCount());
        } else {
            result.put("failedCount", 0);
            result.put("remainingAttempts", MAX_FAILED_ATTEMPTS);
        }

        return result;
    }

    @Transactional
    public Map<String, Object> recordFailedAttempt(String account) {
        LoginAttempt attempt = loginAttemptDao.findByAccount(account).orElse(null);
        Map<String, Object> result = new HashMap<>();

        if (attempt == null) {
            attempt = new LoginAttempt();
            attempt.setAccount(account);
            attempt.setFailedCount(1);
            attempt.setAttemptTime(LocalDateTime.now());
        } else {
            if (attempt.getLockedUntil() != null &&
                attempt.getLockedUntil().isBefore(LocalDateTime.now())) {
                attempt.setFailedCount(1);
                attempt.setLockedUntil(null);
            } else {
                attempt.setFailedCount(attempt.getFailedCount() + 1);
            }
            attempt.setAttemptTime(LocalDateTime.now());
        }

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

    @Transactional
    public void clearFailedAttempt(String account) {
        loginAttemptDao.deleteByAccount(account);
    }

    @Transactional
    public String generateToken(Users user) {
        String token = UUID.randomUUID().toString();
        user.setToken(token);
        user.setTokenExpiredAt(LocalDateTime.now().plusHours(TOKEN_VALID_HOURS));
        if (user.getTokenVersion() == null) {
            user.setTokenVersion(1);
        } else {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        usersDao.save(user);
        return token;
    }

    public Map<String, Object> validateToken(String token) {
        Map<String, Object> result = new HashMap<>();
        
        // 去掉 "Bearer " 前缀（如果存在）
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        if (token == null || token.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "请先登录");
            return result;
        }

        Users user = usersDao.findByToken(token);
        if (user == null) {
            result.put("valid", false);
            result.put("message", "您的账号已在其他设备登录，请重新登录");
            result.put("kicked", true);
            return result;
        }

        if (user.getTokenExpiredAt() == null ||
            user.getTokenExpiredAt().isBefore(LocalDateTime.now())) {
            result.put("valid", false);
            result.put("message", "登录已过期，请重新登录");
            user.setToken(null);
            user.setTokenExpiredAt(null);
            usersDao.save(user);
            return result;
        }

        result.put("valid", true);
        result.put("user", user);
        return result;
    }

    @Transactional
    public void invalidateCurrentToken(Users user) {
        user.setToken(null);
        user.setTokenExpiredAt(null);
        user.setTokenVersion(user.getTokenVersion() != null ? user.getTokenVersion() + 1 : 1);
        usersDao.save(user);
    }

    @Transactional
    public void logout(String token) {
        Users user = usersDao.findByToken(token);
        if (user != null) {
            user.setToken(null);
            user.setTokenExpiredAt(null);
            usersDao.save(user);
        }
    }
}