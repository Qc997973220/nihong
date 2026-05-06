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

    // 存储每个资源的定时任务Future，用于取消任务
    private Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

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
        if (runningTasks.containsKey(resourceId)) {
            return;
        }

        // 生成随机停止阈值（50-300）
        int threshold = random.nextInt(251) + 50; // 50~300
        resourceService.setStopThreshold(resourceId, threshold);

        // 启动定时任务，每10分钟执行一次
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> executeAutoIncrement(resourceId),
                java.time.Instant.now().atZone(ZoneId.systemDefault()).toInstant(),
                java.time.Duration.ofMinutes(10)
        );

        runningTasks.put(resourceId, future);
        System.out.println("已启动资源 " + resourceId + " 的访问量自动增加任务，停止阈值: " + threshold);
    }

    /**
     * 执行自动增加访问量（使用 Redis 缓存）
     * @param resourceId 资源ID
     */
    private void executeAutoIncrement(Long resourceId) {
        try {
            // 检查是否需要继续执行
            if (!resourceService.shouldContinueAutoIncrement(resourceId)) {
                stopAutoIncrement(resourceId);
                return;
            }

            // 随机增加0~30的访问量
            int increment = random.nextInt(31); // 0~30
            
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
        ScheduledFuture<?> future = runningTasks.remove(resourceId);
        if (future != null) {
            future.cancel(false);
            System.out.println("已停止资源 " + resourceId + " 的访问量自动增加任务");
        }
    }

    /**
     * 检查资源是否有运行中的自动增加任务
     * @param resourceId 资源ID
     * @return 是否有运行中的任务
     */
    public boolean isTaskRunning(Long resourceId) {
        return runningTasks.containsKey(resourceId);
    }

    /**
     * 系统启动时恢复定时任务
     */
    private void resumeScheduledTasks() {
        // 查找所有审核通过的资源
        java.util.List<Resource> approvedResources = resourceDao.findByStatusOrderByCreatedAtDesc(1);
        
        for (Resource resource : approvedResources) {
            // 检查是否有停止阈值且未达到阈值
            if (resource.getStopThreshold() != null && resource.getStopThreshold() > 0) {
                int currentCount = resource.getViewCount() != null ? resource.getViewCount() : 0;
                if (currentCount < resource.getStopThreshold()) {
                    // 恢复定时任务
                    startAutoIncrement(resource.getId());
                }
            }
        }
    }

    /**
     * 获取任务调度器（用于外部访问）
     */
    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }
}