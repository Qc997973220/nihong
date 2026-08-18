package com.neon.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     * 原子性递增
     * @param key 缓存键
     * @param delta 增量值
     * @return 递增后的值
     */
    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            // Redis不可用时，返回null，回退到数据库
            System.out.println("Redis increment failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 原子性递增（默认+1）
     * @param key 缓存键
     * @return 递增后的值
     */
    public Long increment(String key) {
        return increment(key, 1);
    }

    /**
     * 获取整数值
     * @param key 缓存键
     * @return 整数值，如果不存在返回0
     */
    public int getInt(String key) {
        Object value = get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        return 0;
    }

    /**
     * 获取所有匹配pattern的key（使用 SCAN 游标遍历，避免 KEYS 命令阻塞 Redis）
     * @param pattern 匹配模式
     * @return key列表
     */
    public Set<String> keys(String pattern) {
        try {
            return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<String> result = new HashSet<>();
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return result;
            });
        } catch (Exception e) {
            System.out.println("Redis scan failed: " + e.getMessage());
            return null;
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
     * 生成资源访问量缓存键
     * @param id 资源ID
     * @return 缓存键
     */
    public String getResourceViewCountKey(Long id) {
        return "resource:viewcount:" + id;
    }

    /**
     * 生成访问量增量缓存键（用于批量持久化）
     * @param id 资源ID
     * @return 缓存键
     */
    public String getResourceViewCountDeltaKey(Long id) {
        return "resource:viewcount:delta:" + id;
    }

    /**
     * 生成用户资源访问记录缓存键
     * @param userIdentifier 用户标识（用户名或IP）
     * @param resourceId 资源ID
     * @return 缓存键
     */
    public String getUserResourceViewKey(String userIdentifier, Long resourceId) {
        return "user:view:" + userIdentifier + ":resource:" + resourceId;
    }

    /**
     * 检查用户是否已访问过某资源
     * @param userIdentifier 用户标识（用户名或IP）
     * @param resourceId 资源ID
     * @return 是否已访问
     */
    public boolean hasUserViewedResource(String userIdentifier, Long resourceId) {
        String key = getUserResourceViewKey(userIdentifier, resourceId);
        return exists(key);
    }

    /**
     * 标记用户已访问某资源
     * @param userIdentifier 用户标识（用户名或IP）
     * @param resourceId 资源ID
     */
    public void markUserViewedResource(String userIdentifier, Long resourceId) {
        String key = getUserResourceViewKey(userIdentifier, resourceId);
        // 设置过期时间为1小时（3600秒）
        set(key, "1", 3600);
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