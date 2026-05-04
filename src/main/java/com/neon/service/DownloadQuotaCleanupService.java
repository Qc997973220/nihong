package com.neon.service;

import com.neon.dao.UserDownloadRecordDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class DownloadQuotaCleanupService {

    private static final int RETENTION_DAYS = 7;

    @Autowired
    private UserDownloadRecordDao downloadRecordDao;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupOldRecords() {
        LocalDate cutoffDate = LocalDate.now().minusDays(RETENTION_DAYS);
        long deletedCount = downloadRecordDao.deleteOldRecords(cutoffDate);
        System.out.println("[DownloadQuotaCleanup] 已清理 " + deletedCount + " 条过期的下载记录（" + RETENTION_DAYS + "天前）");
    }
}