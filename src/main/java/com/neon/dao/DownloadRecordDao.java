package com.neon.dao;

import com.neon.pojo.DownloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

public interface DownloadRecordDao extends JpaRepository<DownloadRecord, Long> {

    boolean existsByAccountAndResourceId(String account, Long resourceId);

    @Query("SELECT COUNT(dr) FROM DownloadRecord dr WHERE dr.account = :account AND dr.downloadedAt >= :startOfDay")
    Long countTodayDownloads(@Param("account") String account, @Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT COUNT(DISTINCT dr.resourceId) FROM DownloadRecord dr " +
            "WHERE dr.account = :account AND dr.downloadedAt >= :startOfDay " +
            "AND dr.resourceId IN (SELECT r.id FROM Resource r WHERE r.category = :category)")
    Long countTodayFreeUnlocks(@Param("account") String account,
                               @Param("startOfDay") LocalDateTime startOfDay,
                               @Param("category") String category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(dr) FROM DownloadRecord dr WHERE dr.account = :account AND dr.downloadedAt >= :startOfDay")
    Long countTodayDownloadsWithLock(@Param("account") String account, @Param("startOfDay") LocalDateTime startOfDay);

    List<DownloadRecord> findByAccountOrderByDownloadedAtDesc(String account);
}
