package com.neon.service;

import com.neon.dao.UserDownloadRecordDao;
import com.neon.dao.UsersDao;
import com.neon.pojo.Resource;
import com.neon.pojo.UserDownloadRecord;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DownloadQuotaService {

    private static final int MONTHLY_MEMBER_DAILY_LIMIT = 2;
    private static final int QUARTERLY_MEMBER_DAILY_LIMIT = 3;
    private static final int YEARLY_MEMBER_DAILY_LIMIT = Integer.MAX_VALUE;
    private static final int PERMANENT_MEMBER_DAILY_LIMIT = Integer.MAX_VALUE;

    @Autowired
    private UserDownloadRecordDao downloadRecordDao;

    @Autowired
    private UsersDao usersDao;

    public int getDailyLimit(Integer memberType) {
        if (memberType == null) return 0;
        switch (memberType) {
            case 1:
                return MONTHLY_MEMBER_DAILY_LIMIT;
            case 2:
                return QUARTERLY_MEMBER_DAILY_LIMIT;
            case 3:
                return YEARLY_MEMBER_DAILY_LIMIT;
            case 4:
                return PERMANENT_MEMBER_DAILY_LIMIT;
            default:
                return 0;
        }
    }

    public String getMemberTypeName(Integer memberType) {
        if (memberType == null) return "非会员";
        switch (memberType) {
            case 1:
                return "月度会员";
            case 2:
                return "季度会员";
            case 3:
                return "年度会员";
            case 4:
                return "永久会员";
            default:
                return "非会员";
        }
    }

    public long getTodayDownloadCount(String account) {
        return downloadRecordDao.countByAccountAndDate(account, LocalDate.now());
    }

    public boolean hasDownloadedToday(String account, Long resourceId) {
        List<UserDownloadRecord> records = downloadRecordDao.findByAccountAndDateAndResourceId(
            account, LocalDate.now(), resourceId);
        return !records.isEmpty();
    }

    @Transactional
    public Map<String, Object> recordDownload(String account, Long resourceId, String resourceTitle) {
        Map<String, Object> result = new HashMap<>();

        Users user = usersDao.findByAccount(account);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        Integer memberType = user.getMemberType();
        String memberStatus = user.getMemberStatus();

        if (memberStatus == null || memberStatus.equals("none") || memberStatus.equals("expired")) {
            result.put("success", false);
            result.put("message", "您不是VIP会员，无法查看下载链接");
            return result;
        }

        if (memberType == null || memberType == 0) {
            result.put("success", false);
            result.put("message", "您不是VIP会员，无法查看下载链接");
            return result;
        }

        if (hasDownloadedToday(account, resourceId)) {
            result.put("success", true);
            result.put("message", "已获取过今日下载链接");
            result.put("alreadyDownloaded", true);
            return result;
        }

        int dailyLimit = getDailyLimit(memberType);
        long todayCount = getTodayDownloadCount(account);

        if (todayCount >= dailyLimit) {
            result.put("success", false);
            result.put("message", "您今日下载额度已用尽，请明天再来！");
            result.put("quotaExceeded", true);
            result.put("memberType", memberType);
            result.put("dailyLimit", dailyLimit);
            result.put("todayCount", todayCount);
            result.put("remaining", 0);
            return result;
        }

        UserDownloadRecord record = new UserDownloadRecord();
        record.setAccount(account);
        record.setResourceId(resourceId);
        record.setResourceTitle(resourceTitle);
        record.setDownloadDate(LocalDate.now());
        record.setCreateTime(LocalDateTime.now());
        downloadRecordDao.save(record);

        long newCount = getTodayDownloadCount(account);
        int remaining = dailyLimit == Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) (dailyLimit - newCount);

        result.put("success", true);
        result.put("message", "获取成功");
        result.put("alreadyDownloaded", false);
        result.put("memberType", memberType);
        result.put("memberTypeName", getMemberTypeName(memberType));
        result.put("dailyLimit", dailyLimit == Integer.MAX_VALUE ? "无限" : dailyLimit);
        result.put("todayCount", newCount);
        result.put("remaining", remaining);

        return result;
    }

    public Map<String, Object> getDownloadQuotaInfo(String account) {
        Map<String, Object> result = new HashMap<>();

        Users user = usersDao.findByAccount(account);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        Integer memberType = user.getMemberType();
        String memberStatus = user.getMemberStatus();

        if (memberStatus == null || memberStatus.equals("none") || memberStatus.equals("expired") ||
            memberType == null || memberType == 0) {
            result.put("success", true);
            result.put("hasQuota", false);
            result.put("memberType", 0);
            result.put("memberTypeName", "非会员");
            result.put("dailyLimit", 0);
            result.put("todayCount", 0);
            result.put("remaining", 0);
            return result;
        }

        int dailyLimit = getDailyLimit(memberType);
        long todayCount = getTodayDownloadCount(account);
        int remaining = dailyLimit == Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) (dailyLimit - todayCount);

        result.put("success", true);
        result.put("hasQuota", true);
        result.put("memberType", memberType);
        result.put("memberTypeName", getMemberTypeName(memberType));
        result.put("dailyLimit", dailyLimit == Integer.MAX_VALUE ? "无限" : dailyLimit);
        result.put("todayCount", todayCount);
        result.put("remaining", remaining);
        result.put("memberStatus", memberStatus);

        return result;
    }
}