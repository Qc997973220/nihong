package com.neon.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存
     * @param key 缓存键
     * @param value 缓存值
     * @param expire 过期时间（秒）
     */
    public void set(String key, Object value, long expire) {
        try {
            redisTemplate.opsForValue().set(key, value, expire, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis不可用时，静默失败，不影响主流程
            System.out.println("Redis set failed: " + e.getMessage());
        }
    }

    /**
     * 获取缓存
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Redis不可用时，直接返回null，回退到数据库
            System.out.println("Redis get failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 删除缓存
     * @param key 缓存键
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            // Redis不可用时，静默失败，不影响主流程
            System.out.println("Redis delete failed: " + e.getMessage());
        }
    }

    /**
     * 检查缓存是否存在
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean exists(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            // Redis不可用时，直接返回false，回退到数据库
            System.out.println("Redis exists failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 生成资源列表缓存键
     * @return 缓存键
     */
    public String getResourceListKey() {
        return "resource:list";
    }

    /**
     * 生成资源详情缓存键
     * @param id 资源ID
     * @return 缓存键
     */
    public String getResourceDetailKey(Long id) {
        return "resource:detail:" + id;
    }

    /**
     * 生成用户信息缓存键
     * @param userName 用户名
     * @return 缓存键
     */
    public String getUserInfoKey(String userName) {
        return "user:info:" + userName;
    }

    /**
     * 生成未读消息数量缓存键
     * @param userName 用户名
     * @return 缓存键
     */
    public String getUnreadMessageCountKey(String userName) {
        return "message:unread:count:" + userName;
    }
}