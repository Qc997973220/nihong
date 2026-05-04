package com.neon.dao;

import com.neon.pojo.UserDownloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserDownloadRecordDao extends JpaRepository<UserDownloadRecord, Long> {

    @Query("SELECT COUNT(r) FROM UserDownloadRecord r WHERE r.account = :account AND r.downloadDate = :date")
    long countByAccountAndDate(@Param("account") String account, @Param("date") LocalDate date);

    @Query("SELECT r FROM UserDownloadRecord r WHERE r.account = :account AND r.downloadDate = :date ORDER BY r.createTime DESC")
    List<UserDownloadRecord> findByAccountAndDate(@Param("account") String account, @Param("date") LocalDate date);

    @Query("SELECT r FROM UserDownloadRecord r WHERE r.account = :account AND r.downloadDate = :date AND r.resourceId = :resourceId")
    List<UserDownloadRecord> findByAccountAndDateAndResourceId(@Param("account") String account, @Param("date") LocalDate date, @Param("resourceId") Long resourceId);

    long countByAccountAndDownloadDate(String account, LocalDate downloadDate);

    @Modifying
    @Query("DELETE FROM UserDownloadRecord r WHERE r.downloadDate < :cutoffDate")
    long deleteOldRecords(@Param("cutoffDate") LocalDate cutoffDate);
}