package com.neon.service;

import com.neon.dao.ResourceDao;
import com.neon.pojo.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class ViewCountScheduler {

    @Autowired
    private ResourceDao resourceDao;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CacheService cacheService;

    private ThreadPoolTaskScheduler taskScheduler;

    // 存储每个资源的定时任务Future，用于取消任务（10分钟任务）
    private Map<Long, ScheduledFuture<?>> runningTasks10min = new ConcurrentHashMap<>();
    
    // 存储每个资源的定时任务Future，用于取消任务（1分钟任务）
    private Map<Long, ScheduledFuture<?>> runningTasks1min = new ConcurrentHashMap<>();

    private Random random = new Random();

    @PostConstruct
    public void init() {
        // 初始化定时任务调度器
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(10);
        taskScheduler.setThreadNamePrefix("view-count-scheduler-");
        taskScheduler.initialize();
        
        // 检查是否有已审核通过且未达到阈值的资源，恢复定时任务
        resumeScheduledTasks();
        
        // 启动定期持久化任务（每5分钟执行一次）
        startFlushTask();
    }

    /**
     * 启动定期将缓存访问量持久化到数据库的任务
     */
    private void startFlushTask() {
        taskScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        resourceService.flushViewCountsToDatabase();
                        System.out.println("View count flush task executed successfully");
                    } catch (Exception e) {
                        System.out.println("View count flush task failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                },
                java.time.Instant.now().atZone(ZoneId.systemDefault()).toInstant(),
                java.time.Duration.ofMinutes(5)
        );
        System.out.println("View count flush task started (every 5 minutes)");
    }

    /**
     * 为资源启动自动增加访问量的定时任务
     * @param resourceId 资源ID
     */
    public void startAutoIncrement(Long resourceId) {
        // 检查是否已经有运行中的任务
        if (runningTasks10min.containsKey(resourceId) || runningTasks1min.containsKey(resourceId)) {
            System.out.println("资源 " + resourceId + " 的定时任务已在运行中，跳过");
            return;
        }

        // 检查是否已有停止阈值，如果没有则生成新的
        Integer existingThreshold = resourceService.getStopThreshold(resourceId);
        int threshold;
        if (existingThreshold != null && existingThreshold > 0) {
            threshold = existingThreshold;
            System.out.println("资源 " + resourceId + " 使用已有的停止阈值: " + threshold);
        } else {
            // 生成随机停止阈值（50-300）
            threshold = random.nextInt(251) + 50; // 50~300
            resourceService.setStopThreshold(resourceId, threshold);
            System.out.println("资源 " + resourceId + " 生成新的停止阈值: " + threshold);
        }

        // 启动定时任务，每10分钟执行一次（随机增加0~30）
        ScheduledFuture<?> future10min = taskScheduler.scheduleAtFixedRate(
                () -> executeAutoIncrement(resourceId, 30), // 0~30
                java.time.Instant.now().atZone(ZoneId.systemDefault()).toInstant(),
                java.time.Duration.ofMinutes(10)
        );

        // 启动定时任务，每1分钟执行一次（随机增加0~3）
        ScheduledFuture<?> future1min = taskScheduler.scheduleAtFixedRate(
                () -> executeAutoIncrement(resourceId, 3), // 0~3
                java.time.Instant.now().atZone(ZoneId.systemDefault()).toInstant(),
                java.time.Duration.ofMinutes(1)
        );

        runningTasks10min.put(resourceId, future10min);
        runningTasks1min.put(resourceId, future1min);
        System.out.println("已启动资源 " + resourceId + " 的访问量自动增加任务，停止阈值: " + threshold);
    }

    /**
     * 执行自动增加访问量（使用 Redis 缓存）
     * @param resourceId 资源ID
     * @param maxIncrement 最大增量（0~maxIncrement）
     */
    private void executeAutoIncrement(Long resourceId, int maxIncrement) {
        try {
            // 检查是否需要继续执行
            if (!resourceService.shouldContinueAutoIncrement(resourceId)) {
                stopAutoIncrement(resourceId);
                return;
            }

            // 随机增加访问量（0~maxIncrement）
            int increment = random.nextInt(maxIncrement + 1);
            
            if (increment > 0) {
                // 使用 Redis 原子递增，记录增量
                String deltaKey = cacheService.getResourceViewCountDeltaKey(resourceId);
                cacheService.increment(deltaKey, increment);
                
                // 清除资源详情缓存
                cacheService.delete(cacheService.getResourceDetailKey(resourceId));
                
                // 获取当前总访问量（包含缓存增量）
                int currentCount = resourceService.getViewCount(resourceId);
                System.out.println("资源 " + resourceId + " 自动增加访问量: " + increment + ", 当前访问量: " + currentCount);
            }

            // 再次检查是否达到阈值
            if (!resourceService.shouldContinueAutoIncrement(resourceId)) {
                stopAutoIncrement(resourceId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止资源的自动增加任务
     * @param resourceId 资源ID
     */
    public void stopAutoIncrement(Long resourceId) {
        // 停止10分钟任务
        ScheduledFuture<?> future10min = runningTasks10min.remove(resourceId);
        if (future10min != null) {
            future10min.cancel(false);
        }
        
        // 停止1分钟任务
        ScheduledFuture<?> future1min = runningTasks1min.remove(resourceId);
        if (future1min != null) {
            future1min.cancel(false);
        }
        
        System.out.println("已停止资源 " + resourceId + " 的访问量自动增加任务");
    }

    /**
     * 检查资源是否有运行中的自动增加任务
     * @param resourceId 资源ID
     * @return 是否有运行中的任务
     */
    public boolean isTaskRunning(Long resourceId) {
        return runningTasks10min.containsKey(resourceId) || runningTasks1min.containsKey(resourceId);
    }

    /**
     * 系统启动时恢复定时任务
     */
    private void resumeScheduledTasks() {
        // 查找所有审核通过的资源
        java.util.List<Resource> approvedResources = resourceDao.findByStatusOrderByCreatedAtDesc(1);
        System.out.println("系统启动：发现 " + approvedResources.size() + " 个已审核通过的资源，开始检查定时任务恢复...");
        
        int resumedCount = 0;
        for (Resource resource : approvedResources) {
            // 检查是否有停止阈值且未达到阈值
            if (resource.getStopThreshold() != null && resource.getStopThreshold() > 0) {
                // 使用包含缓存增量的总访问量来判断
                int currentCount = resourceService.getViewCount(resource.getId());
                if (currentCount < resource.getStopThreshold()) {
                    // 恢复定时任务
                    startAutoIncrement(resource.getId());
                    resumedCount++;
                } else {
                    System.out.println("资源 " + resource.getId() + " 已达到阈值 " + resource.getStopThreshold() + "，跳过恢复定时任务");
                }
            } else {
                // 没有设置阈值的新资源，需要启动定时任务
                System.out.println("资源 " + resource.getId() + " 未设置停止阈值，启动新的定时任务");
                startAutoIncrement(resource.getId());
                resumedCount++;
            }
        }
        System.out.println("系统启动：成功恢复/启动 " + resumedCount + " 个资源的定时任务");
    }

    /**
     * 获取任务调度器（用于外部访问）
     */
    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }
}