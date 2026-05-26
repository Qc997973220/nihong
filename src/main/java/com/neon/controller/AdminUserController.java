package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.CardKey;
import com.neon.pojo.Users;
import com.neon.service.AuthService;
import com.neon.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UsersDao usersDao;

    @Autowired
    private WalletService walletService;

    @GetMapping("/search")
    public Map<String, Object> searchUsers(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }

        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> users = usersDao.findAll().stream()
                .filter(user -> matchesKeyword(user, normalized))
                .limit(30)
                .map(this::buildUserItem)
                .collect(Collectors.toList());

        result.put("success", true);
        result.put("users", users);
        return result;
    }

    @PostMapping("/member")
    public Map<String, Object> updateMemberType(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }

        String account = request != null && request.get("account") != null
                ? request.get("account").toString().trim()
                : "";
        Integer memberType = parseMemberType(request != null ? request.get("memberType") : null);
        if (account.isEmpty()) {
            result.put("success", false);
            result.put("message", "账号不能为空");
            return result;
        }
        if (!isAssignableMemberType(memberType)) {
            result.put("success", false);
            result.put("message", "会员类型无效，仅支持年费会员、永久会员和霓虹代理");
            return result;
        }

        Users target = usersDao.findByAccount(account);
        if (target == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        applyMemberType(target, memberType);
        usersDao.save(target);
        walletService.grantInviteRewardIfNeeded(target);

        result.put("success", true);
        result.put("message", "会员权限已更新");
        result.put("user", buildUserItem(target));
        return result;
    }

    @PostMapping("/invite-data")
    public Map<String, Object> createInviteData(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        if (!requireAdmin(token, result)) {
            return result;
        }

        String account = request != null && request.get("account") != null
                ? request.get("account").toString().trim()
                : "";
        int permanentCount = parseInt(request != null ? request.get("permanentCount") : null, 0);
        int yearlyCount = parseInt(request != null ? request.get("yearlyCount") : null, 0);
        int regularCount = parseInt(request != null ? request.get("regularCount") : null, 0);
        int permanentRewardAmount = parseInt(request != null ? request.get("permanentRewardAmount") : null, 100);
        int yearlyRewardAmount = parseInt(request != null ? request.get("yearlyRewardAmount") : null, 69);

        return walletService.createAdminInviteData(
                account,
                permanentCount,
                yearlyCount,
                regularCount,
                permanentRewardAmount,
                yearlyRewardAmount);
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean requireAdmin(String token, Map<String, Object> result) {
        Users admin = authService.getAdminUser(token);
        if (admin == null) {
            result.put("success", false);
            result.put("message", "管理员权限不足");
            return false;
        }
        return true;
    }

    private boolean matchesKeyword(Users user, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        return contains(user.getAccount(), keyword)
                || contains(user.getUserName(), keyword)
                || contains(user.getNickname(), keyword)
                || contains(user.getPhone(), keyword)
                || contains(user.getEmail(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private Integer parseMemberType(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isAssignableMemberType(Integer memberType) {
        return memberType != null
                && (memberType == 0
                || memberType == CardKey.TYPE_YEARLY
                || memberType == CardKey.TYPE_PERMANENT
                || memberType == CardKey.TYPE_AGENT);
    }

    private void applyMemberType(Users user, Integer memberType) {
        user.setMemberType(memberType);
        if (memberType == null || memberType == 0) {
            user.setMemberStatus("none");
            user.setMemberExpiredAt(null);
        } else if (memberType == CardKey.TYPE_YEARLY) {
            user.setMemberStatus("active");
            user.setMemberExpiredAt(LocalDateTime.now().plusDays(360));
        } else {
            user.setMemberStatus("permanent");
            user.setMemberExpiredAt(null);
        }
        user.setOperatingTime(LocalDateTime.now());
    }

    private Map<String, Object> buildUserItem(Users user) {
        Map<String, Object> item = new LinkedHashMap<>();
        Integer memberType = normalizeMemberType(user.getMemberType());
        item.put("account", user.getAccount());
        item.put("userName", user.getUserName());
        item.put("nickname", user.getNickname());
        item.put("phone", user.getPhone());
        item.put("email", user.getEmail());
        item.put("role", user.getRole());
        item.put("memberType", memberType);
        item.put("memberTypeName", getMemberTypeName(memberType));
        item.put("memberStatus", resolveMemberStatus(user));
        item.put("memberExpireText", resolveMemberExpireText(user));
        item.put("isAgent", walletService.isAgent(user));
        item.put("promotionPermission", walletService.hasPromotionPermission(user));
        item.put("createdAt", user.getCreateTime());
        item.put("lastLoginTime", user.getLastLoginTime());
        return item;
    }

    private String getMemberTypeName(Integer memberType) {
        memberType = normalizeMemberType(memberType);
        if (memberType == null || memberType == 0) {
            return "普通用户";
        }
        switch (memberType) {
            case CardKey.TYPE_YEARLY:
                return "年费会员";
            case CardKey.TYPE_PERMANENT:
                return "永久会员";
            case CardKey.TYPE_AGENT:
                return "霓虹代理";
            default:
                return "未知会员";
        }
    }

    private String resolveMemberStatus(Users user) {
        Integer memberType = normalizeMemberType(user.getMemberType());
        if (memberType == null || memberType == 0) {
            return "none";
        }
        if (memberType == CardKey.TYPE_PERMANENT || memberType == CardKey.TYPE_AGENT) {
            return "permanent";
        }
        if (user.getMemberExpiredAt() != null && user.getMemberExpiredAt().isBefore(LocalDateTime.now())) {
            return "expired";
        }
        return "active";
    }

    private String resolveMemberExpireText(Users user) {
        Integer memberType = normalizeMemberType(user.getMemberType());
        if (memberType == null || memberType == 0) {
            return "非会员";
        }
        if (memberType == CardKey.TYPE_AGENT) {
            return "霓虹代理 · 永久有效";
        }
        if (memberType == CardKey.TYPE_PERMANENT) {
            return "永久有效";
        }
        if (user.getMemberExpiredAt() == null) {
            return "暂无到期时间";
        }
        return user.getMemberExpiredAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private Integer normalizeMemberType(Integer memberType) {
        if (memberType == null
                || memberType == CardKey.TYPE_MONTHLY
                || memberType == CardKey.TYPE_QUARTERLY) {
            return 0;
        }
        return memberType;
    }
}
