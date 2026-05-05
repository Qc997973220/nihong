package com.neon.service;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LoginService {
    @Autowired
    UsersDao usersDao;

    @Autowired
    CardKeyService cardKeyService;

    @Autowired
    AuthService authService;

    public Map<String, Object> login(String account, String password){
        Map<String, Object> result = new java.util.HashMap<>();
        
        // 检查登录失败次数
        Map<String, Object> attemptCheck = authService.checkLoginAttempt(account);
        if (!(Boolean) attemptCheck.get("allowed")) {
            result.put("status", -2);
            result.put("message", attemptCheck.get("message"));
            return result;
        }
        
        Users user = usersDao.findByAccount(account);
        if (user == null) {
            // 账号不存在，也记录失败次数
            Map<String, Object> failedResult = authService.recordFailedAttempt(account);
            result.put("status", -1);
            result.put("message", "账号或密码错误");
            result.put("failedCount", failedResult.get("failedCount"));
            result.put("remainingAttempts", failedResult.get("remainingAttempts"));
            if (failedResult.get("locked") != null && (Boolean) failedResult.get("locked")) {
                result.put("status", -2);
                result.put("message", "登录失败次数过多，账号已锁定。请在 " + failedResult.get("lockUntil") + " 后重试");
            }
            return result;
        }
        
        if (user.getPassword().equals(password)){
            // 检查是否有旧token（其他设备正在登录）
            boolean wasLoggedInElsewhere = user.getToken() != null && !user.getToken().isEmpty();
            String oldToken = user.getToken();
            
            // 登录成功，生成新token（这会自动使旧token失效）
            String token = authService.generateToken(user);
            user.setLastLoginTime(java.time.LocalDateTime.now());
            authService.clearFailedAttempt(account);
            
            result.put("status", 1);
            result.put("token", token);
            result.put("user", user);
            result.put("wasLoggedInElsewhere", wasLoggedInElsewhere);
            return result;
        } else {
            // 登录失败，记录
            Map<String, Object> failedResult = authService.recordFailedAttempt(account);
            if (failedResult.get("locked") != null && (Boolean) failedResult.get("locked")) {
                result.put("status", -2);
                result.put("message", "登录失败次数过多，账号已锁定。请在 " + failedResult.get("lockUntil") + " 后重试");
            } else {
                result.put("status", 0);
                result.put("message", "账号或密码错误");
                result.put("failedCount", failedResult.get("failedCount"));
                result.put("remainingAttempts", failedResult.get("remainingAttempts"));
            }
            return result;
        }
    }

    public int registered(Users users) {
        String account = users.getAccount();
        
        // 检查账号格式：只能包含字母和数字，6-9位
        if (account == null || !account.matches("^[A-Za-z0-9]{6,9}$")) {
            return 0;
        }
        
        // 检查豹子号（连续4个相同字符）
        if (hasConsecutiveSameChars(account, 4)) {
            return 0;
        }
        
        // 检查顺序号（连续4个递增或递减字符）
        if (hasSequentialChars(account, 4)) {
            return 0;
        }
        
        // 检查密码格式：只能包含字母和数字，6-16位
        String password = users.getPassword();
        if (password == null || !password.matches("^[A-Za-z0-9]{6,16}$")) {
            return 0;
        }
        
        if (usersDao.existsByAccount(users.getAccount())) {
            return 0;
        }
        if (usersDao.existsByUserName(users.getUserName())) {
            return 2;
        }

        users.setId(UUID.randomUUID().toString());
        if (users.getRole() == null || users.getRole().isEmpty()) {
            users.setRole("0");
        }
        users.setCreateTime(java.time.LocalDateTime.now());
        users.setMemberType(0);

        String inviteCode = generateInviteCode();
        while (usersDao.existsByInviteCode(inviteCode)) {
            inviteCode = generateInviteCode();
        }
        users.setInviteCode(inviteCode);
        String invitedBy = users.getInvitedBy();
        if (invitedBy != null && !invitedBy.trim().isEmpty()) {
            Users inviter = usersDao.findByAccount(invitedBy.trim());
            if (inviter == null) {
                inviter = usersDao.findByInviteCode(invitedBy.trim());
            }
            if (inviter != null) {
                users.setInvitedBy(inviter.getAccount());
            }
        }
        usersDao.save(users);
        return 1;
    }

    private boolean hasConsecutiveSameChars(String account, int count) {
        int consecutive = 1;
        for (int i = 1; i < account.length(); i++) {
            if (account.charAt(i) == account.charAt(i - 1)) {
                consecutive++;
                if (consecutive >= count) {
                    return true;
                }
            } else {
                consecutive = 1;
            }
        }
        return false;
    }

    private boolean hasSequentialChars(String account, int count) {
        int ascending = 1;
        int descending = 1;
        
        for (int i = 1; i < account.length(); i++) {
            char current = account.charAt(i);
            char prev = account.charAt(i - 1);
            
            // 检查递增（数字或字母）
            if ((Character.isDigit(current) && Character.isDigit(prev) && current == prev + 1) ||
                (Character.isLetter(current) && Character.isLetter(prev) && 
                 Character.toLowerCase(current) == Character.toLowerCase(prev) + 1)) {
                ascending++;
            } else {
                ascending = 1;
            }
            
            // 检查递减（数字或字母）
            if ((Character.isDigit(current) && Character.isDigit(prev) && current == prev - 1) ||
                (Character.isLetter(current) && Character.isLetter(prev) && 
                 Character.toLowerCase(current) == Character.toLowerCase(prev) - 1)) {
                descending++;
            } else {
                descending = 1;
            }
            
            if (ascending >= count || descending >= count) {
                return true;
            }
        }
        return false;
    }

    public java.util.Map<String, Object> activateMember(String account, String activationCode) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", false);

        if (account == null || account.trim().isEmpty()) {
            result.put("message", "账号不能为空");
            return result;
        }
        if (activationCode == null || activationCode.trim().isEmpty()) {
            result.put("message", "激活码不能为空");
            return result;
        }

        Users user = usersDao.findByAccount(account);
        if (user == null) {
            result.put("message", "用户不存在");
            return result;
        }

        java.util.Map<String, Object> cardResult = cardKeyService.useCardKey(activationCode.trim(), account);
        if (!(boolean) cardResult.get("success")) {
            result.put("message", "激活码无效或已使用");
            return result;
        }

        Integer memberType = (int) cardResult.get("memberType");
        int expireDays = (int) cardResult.get("expireDays");
        user.setMemberType(memberType);
        if (expireDays == Integer.MAX_VALUE) {
            user.setMemberExpiredAt(null);
            user.setMemberStatus("permanent");
        } else {
            user.setMemberExpiredAt(java.time.LocalDateTime.now().plusDays(expireDays));
            user.setMemberStatus("active");
        }
        user.setOperatingTime(java.time.LocalDateTime.now());
        usersDao.save(user);

        result.put("success", true);
        result.put("memberType", memberType);
        if (memberType == 4) {
            result.put("memberStatus", "permanent");
            result.put("memberExpireText", "永久有效");
        } else {
            result.put("memberStatus", "active");
            if (user.getMemberExpiredAt() != null) {
                result.put("memberExpireText", user.getMemberExpiredAt().toLocalDate() + " 到期");
            }
        }
        result.put("message", "激活成功");
        return result;
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }
}