package com.neon.service;

import com.neon.dao.SiteStatsDao;
import com.neon.pojo.SiteStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SiteStatsService {

    private static final String VISITOR_COUNT_KEY = "site:visitor:count";
    private static final Long INITIAL_COUNT = 92895L;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SiteStatsDao siteStatsDao;

    public Long getVisitorCount() {
        Object count = redisTemplate.opsForValue().get(VISITOR_COUNT_KEY);
        if (count == null) {
            SiteStats stats = siteStatsDao.findById(1L).orElse(null);
            if (stats != null) {
                Long visitorCount = stats.getVisitorCount();
                redisTemplate.opsForValue().set(VISITOR_COUNT_KEY, visitorCount);
                return visitorCount;
            }
            redisTemplate.opsForValue().set(VISITOR_COUNT_KEY, INITIAL_COUNT);
            return INITIAL_COUNT;
        }
        return Long.parseLong(count.toString());
    }

    public Long incrementInRedis() {
        Long newCount = redisTemplate.opsForValue().increment(VISITOR_COUNT_KEY);
        if (newCount == null) {
            newCount = INITIAL_COUNT + 1;
            redisTemplate.opsForValue().set(VISITOR_COUNT_KEY, newCount);
        }
        return newCount;
    }

    @Transactional
    @Scheduled(fixedRate = 60000)
    public void syncVisitorCountToDb() {
        try {
            Object count = redisTemplate.opsForValue().get(VISITOR_COUNT_KEY);
            if (count != null) {
                Long visitorCount = Long.parseLong(count.toString());
                SiteStats stats = siteStatsDao.findById(1L).orElse(null);
                if (stats == null) {
                    stats = new SiteStats();
                    stats.setId(1L);
                }
                stats.setVisitorCount(visitorCount);
                stats.setLastVisitTime(LocalDateTime.now());
                siteStatsDao.save(stats);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void syncNow() {
        syncVisitorCountToDb();
    }

    public Integer getRunningDays() {
        // 获取网站运行天数，从2024-01-01开始计算
        LocalDateTime startDate = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        LocalDateTime now = LocalDateTime.now();
        
        // 计算天数差
        return java.time.Duration.between(startDate, now).toDaysPart() + 1;
    }
}