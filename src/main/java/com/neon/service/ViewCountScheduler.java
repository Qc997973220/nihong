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
     * 执行自动增加访问量
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
                Resource resource = resourceDao.findById(resourceId).orElse(null);
                if (resource != null) {
                    int currentCount = resource.getViewCount() != null ? resource.getViewCount() : 0;
                    int newCount = currentCount + increment;
                    
                    // 检查是否超过阈值
                    Integer threshold = resource.getStopThreshold();
                    if (threshold != null && newCount >= threshold) {
                        newCount = threshold;
                    }
                    
                    resource.setViewCount(newCount);
                    resource.setUpdatedAt(LocalDateTime.now());
                    resourceDao.save(resource);
                    
                    // 清除缓存
                    String cacheKey = cacheService.getResourceDetailKey(resourceId);
                    cacheService.delete(cacheKey);
                    
                    System.out.println("资源 " + resourceId + " 自动增加访问量: " + increment + ", 当前访问量: " + newCount);
                }
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