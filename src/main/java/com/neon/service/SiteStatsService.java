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
    private static final String RUNNING_DAYS_KEY = "site:running:days";
    private static final Long INITIAL_COUNT = 92895L;
    private static final Integer INITIAL_RUNNING_DAYS = 842;

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

    public Integer getRunningDays() {
        Object days = redisTemplate.opsForValue().get(RUNNING_DAYS_KEY);
        if (days == null) {
            SiteStats stats = siteStatsDao.findById(1L).orElse(null);
            if (stats != null && stats.getRunningDays() != null) {
                redisTemplate.opsForValue().set(RUNNING_DAYS_KEY, stats.getRunningDays());
                return stats.getRunningDays();
            }
            redisTemplate.opsForValue().set(RUNNING_DAYS_KEY, INITIAL_RUNNING_DAYS);
            return INITIAL_RUNNING_DAYS;
        }
        return Integer.parseInt(days.toString());
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
    @Scheduled(cron = "0 0 0 * * ?")
    public void incrementRunningDays() {
        try {
            Integer currentDays = getRunningDays();
            Integer newDays = currentDays + 1;
            redisTemplate.opsForValue().set(RUNNING_DAYS_KEY, newDays);

            SiteStats stats = siteStatsDao.findById(1L).orElse(null);
            if (stats == null) {
                stats = new SiteStats();
                stats.setId(1L);
            }
            stats.setRunningDays(newDays);
            stats.setLastVisitTime(LocalDateTime.now());
            siteStatsDao.save(stats);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void syncNow() {
        syncVisitorCountToDb();
    }
}