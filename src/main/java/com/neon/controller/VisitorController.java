package com.neon.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/visitor")
public class VisitorController {

    private static final String VISITOR_COUNT_KEY = "site:visitor:count";
    private static final long INITIAL_COUNT = 92895L;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/count")
    public Map<String, Object> getVisitorCount() {
        Map<String, Object> result = new HashMap<>();
        try {
            Object count = redisTemplate.opsForValue().get(VISITOR_COUNT_KEY);
            long visitorCount;
            if (count == null) {
                visitorCount = INITIAL_COUNT;
                redisTemplate.opsForValue().set(VISITOR_COUNT_KEY, visitorCount);
            } else {
                visitorCount = Long.parseLong(count.toString());
            }
            result.put("success", true);
            result.put("count", visitorCount);
        } catch (Exception e) {
            result.put("success", false);
            result.put("count", INITIAL_COUNT);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/count/increment")
    public Map<String, Object> incrementVisitorCount() {
        Map<String, Object> result = new HashMap<>();
        try {
            Long newCount = redisTemplate.opsForValue().increment(VISITOR_COUNT_KEY);
            result.put("success", true);
            result.put("count", newCount);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
