package com.neon.dao;

import com.neon.pojo.DownloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DownloadRecordDao extends JpaRepository<DownloadRecord, Long> {
    
    // 检查用户是否已经下载过某个资源
    boolean existsByAccountAndResourceId(String account, Long resourceId);
    
    // 统计用户今日下载次数
    @Query("SELECT COUNT(dr) FROM DownloadRecord dr WHERE dr.account = :account AND dr.downloadedAt >= :startOfDay")
    Long countTodayDownloads(@Param("account") String account, @Param("startOfDay") LocalDateTime startOfDay);
    
    // 获取用户下载记录
    List<DownloadRecord> findByAccountOrderByDownloadedAtDesc(String account);
}