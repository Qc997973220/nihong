package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.CardKey;
import com.neon.pojo.Users;
import com.neon.service.AuthService;
import com.neon.service.CacheService;
import com.neon.service.EmailVerificationService;
import com.neon.service.LoginService;
import com.neon.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/login")
public class LoginController {
    @Autowired
    LoginService loginService;
    @Autowired
    UsersDao usersDao;
    @Autowired
    AuthService authService;
    @Autowired
    CacheService cacheService;
    @Autowired
    WalletService walletService;
    @Autowired
    EmailVerificationService emailVerificationService;

    @PostMapping("/first")
    @ResponseBody
    public Map<String, Object> first(@RequestBody Map<String, String> request){
        Map<String, Object> result = new HashMap<>();
        String account = request.get("account");
        String password = request.get("password");
        
        if (account == null || account.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            result.put("status", 0);
            result.put("message", "账号和密码不能为空");
            return result;
        }
        
        int loginResult = loginService.login(account, password);
        result.put("status", loginResult);
        if (loginResult == 1) {
            Users user = usersDao.findByAccount(account);
            if (user != null) {
                Map<String, Object> userInfo = buildUserInfo(user);
                result.put("user", userInfo);
            }
        }
        return result;
    }

    @PostMapping("/validateToken")
    @ResponseBody
    public Map<String, Object> validateToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        Map<String, Object> result = authService.validateToken(token);
        
        if ((Boolean) result.get("valid") && result.get("user") != null) {
            Users user = (Users) result.get("user");
            Map<String, Object> userInfo = buildUserInfo(user);
            result.put("user", userInfo);
        }
        
        return result;
    }

    private Map<String, Object> buildUserInfo(Users user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("account", user.getAccount());
        userInfo.put("userName", user.getUserName());
        userInfo.put("role", user.getRole() != null && !user.getRole().isEmpty() ? user.getRole() : "0");
        userInfo.put("nickname", user.getNickname());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        userInfo.put("gender", user.getGender());
        userInfo.put("token", user.getToken());
        userInfo.put("avatar", null);
        userInfo.put("lastLoginTime", user.getLastLoginTime());
        Integer memberType = normalizeMemberType(user.getMemberType());
        userInfo.put("memberType", memberType);
        userInfo.put("memberTypeName", getMemberTypeName(memberType));
        userInfo.put("isAgent", memberType == CardKey.TYPE_AGENT);
        userInfo.put("promotionPermission", walletService.hasPromotionPermission(user));
        userInfo.put("inviteRewardRates", walletService.buildRewardPolicy(user));
        userInfo.put("inviteCode", ensureInviteCode(user));
        userInfo.put("invitedBy", user.getInvitedBy());
        userInfo.putAll(walletService.buildWalletSummary(user));

        if (memberType == CardKey.TYPE_AGENT) {
            userInfo.put("memberStatus", "permanent");
            userInfo.put("memberExpireText", "霓虹代理 · 永久有效");
        } else if (memberType == CardKey.TYPE_PERMANENT) {
            userInfo.put("memberStatus", "permanent");
            userInfo.put("memberExpireText", "永久有效");
        } else if (memberType == 0) {
            userInfo.put("memberStatus", "none");
            userInfo.put("memberExpireText", "非会员");
        } else if (user.getMemberExpiredAt() != null) {
            boolean isExpired = user.getMemberExpiredAt().isBefore(LocalDateTime.now());
            userInfo.put("memberStatus", isExpired ? "expired" : "active");
            userInfo.put("memberExpireAt", user.getMemberExpiredAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            userInfo.put("memberExpireText", isExpired ? "已到期" : "到期时间: " + user.getMemberExpiredAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            userInfo.put("memberStatus", "none");
            userInfo.put("memberExpireText", "非会员");
        }
        
        return userInfo;
    }

    private String ensureInviteCode(Users user) {
        String inviteCode = user.getInviteCode();
        if (inviteCode != null && !inviteCode.trim().isEmpty()) {
            return inviteCode;
        }

        inviteCode = generateInviteCode();
        while (usersDao.existsByInviteCode(inviteCode)) {
            inviteCode = generateInviteCode();
        }
        user.setInviteCode(inviteCode);
        usersDao.save(user);
        return inviteCode;
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }

    @PostMapping("/registered")
    @ResponseBody
    public Map<String, Object> registered(Users users, HttpServletRequest request){
        Map<String, Object> result = new HashMap<>();
        
        String clientIp = getClientIp(request);
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        if (usersDao.existsByRegisteredIpToday(clientIp, todayStart, tomorrowStart)) {
            result.put("status", 0);
            result.put("message", "[霓虹云防护引擎]检测到您已注册过一个账号,请明日再试!");
            return result;
        }
        
        int regResult = loginService.registered(users);
        if (regResult == 1) {
            Users user = usersDao.findByAccount(users.getAccount());
            if (user != null) {
                user.setRegisteredIp(clientIp);
                user.setRegisteredDate(LocalDateTime.now());
                usersDao.save(user);
                
                String memberTypeName = getMemberTypeName(user.getMemberType());
                result.put("memberType", user.getMemberType());
                result.put("memberTypeName", memberTypeName);
            }
        }
        result.put("status", regResult);
        return result;
    }

    @PostMapping("/updateUserInfo")
    @ResponseBody
    public Map<String, Object> updateUserInfo(@RequestHeader(value = "Authorization", required = false) String token,
                                              @RequestParam(required = false) String account,
                                              @RequestParam(required = false) String userName,
                                              @RequestParam(required = false) String gender,
                                              @RequestParam(required = false) String phone,
                                              @RequestParam(required = false) String email) {
        Map<String, Object> result = new HashMap<>();
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            result.put("status", 0);
            result.put("message", "请先登录");
            return result;
        }
        if (account != null && !account.trim().isEmpty() && !account.equals(user.getAccount())) {
            result.put("status", 0);
            result.put("message", "无权修改其他用户资料");
            return result;
        }
        
        // 保存旧用户名，用于后续清除缓存
        String oldUserName = user.getUserName();
        
        if (userName != null && !userName.isEmpty()) {
            if (usersDao.existsByUserName(userName) && !userName.equals(user.getUserName())) {
                result.put("status", 0);
                result.put("message", "用户名已被使用");
                return result;
            }
            user.setUserName(userName);
        }
        if (gender != null) {
            user.setGender(gender);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (email != null) {
            String normalizedEmail = normalizeEmail(email);
            String currentEmail = normalizeEmail(user.getEmail());
            if (!normalizedEmail.isEmpty() && !normalizedEmail.equals(currentEmail)) {
                result.put("status", 0);
                result.put("message", "绑定邮箱需要先完成邮箱验证码验证");
                return result;
            }
        }
        user.setOperatingTime(LocalDateTime.now());
        usersDao.save(user);
        
        // 清除用户信息缓存
        if (userName != null && !userName.isEmpty() && !userName.equals(oldUserName)) {
            // 如果用户名被修改，清除旧用户名的缓存
            cacheService.delete(cacheService.getUserInfoKey(oldUserName) + ":own");
            cacheService.delete(cacheService.getUserInfoKey(oldUserName) + ":other");
        }
        // 清除新用户名的缓存
        cacheService.delete(cacheService.getUserInfoKey(user.getUserName()) + ":own");
        cacheService.delete(cacheService.getUserInfoKey(user.getUserName()) + ":other");
        
        result.put("status", 1);
        result.put("message", "更新成功");
        return result;
    }

    @PostMapping("/sendBindEmailCode")
    @ResponseBody
    public Map<String, Object> sendBindEmailCode(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam String email) {
        Map<String, Object> result = new HashMap<>();
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            result.put("status", 0);
            result.put("message", "请先登录");
            return result;
        }

        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail)) {
            result.put("status", 0);
            result.put("message", "请输入有效的邮箱地址");
            return result;
        }

        Users emailOwner = usersDao.findByEmailIgnoreCase(normalizedEmail);
        if (emailOwner != null && !emailOwner.getAccount().equals(user.getAccount())) {
            result.put("status", 0);
            result.put("message", "该邮箱已被其他账号绑定，请更换邮箱");
            return result;
        }

        EmailVerificationService.SendCodeResult sendResult =
                emailVerificationService.sendBindEmailCode(user.getAccount(), normalizedEmail);
        result.put("status", sendResult.isSuccess() ? 1 : 0);
        result.put("message", sendResult.getMessage());
        return result;
    }

    @PostMapping("/bindEmail")
    @ResponseBody
    public Map<String, Object> bindEmail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam String email,
            @RequestParam String code) {
        Map<String, Object> result = new HashMap<>();
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            result.put("status", 0);
            result.put("message", "请先登录");
            return result;
        }

        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail)) {
            result.put("status", 0);
            result.put("message", "请输入有效的邮箱地址");
            return result;
        }
        if (!isValidCode(code)) {
            result.put("status", 0);
            result.put("message", "请输入6位数字验证码");
            return result;
        }

        Users emailOwner = usersDao.findByEmailIgnoreCase(normalizedEmail);
        if (emailOwner != null && !emailOwner.getAccount().equals(user.getAccount())) {
            result.put("status", 0);
            result.put("message", "该邮箱已被其他账号绑定，请更换邮箱");
            return result;
        }

        if (!emailVerificationService.verifyBindEmailCode(user.getAccount(), normalizedEmail, code)) {
            result.put("status", 0);
            result.put("message", "验证码错误或已过期，请重新获取");
            return result;
        }

        user.setEmail(normalizedEmail);
        user.setOperatingTime(LocalDateTime.now());
        usersDao.save(user);
        clearUserInfoCache(user);

        result.put("status", 1);
        result.put("message", "邮箱绑定成功");
        result.put("email", normalizedEmail);
        return result;
    }

    @PostMapping("/changePassword")
    @ResponseBody
    public Map<String, Object> changePassword(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> tokenResult = authService.validateToken(token);
        if (!((Boolean) tokenResult.get("valid"))) {
            result.put("status", 0);
            result.put("message", tokenResult.get("message"));
            return result;
        }

        Users user = (Users) tokenResult.get("user");
        if (user == null) {
            result.put("status", 0);
            result.put("message", "用户不存在");
            return result;
        }

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (oldPassword == null || oldPassword.trim().isEmpty()
                || newPassword == null || newPassword.trim().isEmpty()) {
            result.put("status", 0);
            result.put("message", "旧密码和新密码不能为空");
            return result;
        }

        if (!oldPassword.equals(user.getPassword())) {
            result.put("status", 0);
            result.put("message", "旧密码不正确");
            return result;
        }

        if (!newPassword.matches("^[A-Za-z0-9]{6,16}$")) {
            result.put("status", 0);
            result.put("message", "新密码只能包含字母和数字，长度为6-16位");
            return result;
        }

        if (oldPassword.equals(newPassword)) {
            result.put("status", 0);
            result.put("message", "新密码不能和旧密码相同");
            return result;
        }

        user.setPassword(newPassword);
        user.setOperatingTime(LocalDateTime.now());
        authService.invalidateCurrentToken(user);

        result.put("status", 1);
        result.put("message", "密码修改成功");
        return result;
    }

    @PostMapping("/activate")
    @ResponseBody
    public Map<String, Object> activate(@RequestHeader(value = "Authorization", required = false) String token,
                                       @RequestParam(required = false) String account,
                                       @RequestParam String activationCode) {
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("status", 0);
            result.put("message", "请先登录");
            return result;
        }
        if (account != null && !account.trim().isEmpty() && !account.equals(user.getAccount())) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("status", 0);
            result.put("message", "无权为其他账号激活会员");
            return result;
        }

        Map<String, Object> result = loginService.activateMember(user.getAccount(), activationCode);
        if ((boolean) result.get("success")) {
            result.put("status", 1);
        } else {
            result.put("status", 0);
        }
        return result;
    }

    @PostMapping("/recover/sendCode")
    @ResponseBody
    public Map<String, Object> sendRecoverCode(@RequestParam String email,
                                               @RequestParam(required = false) String account) {
        Map<String, Object> result = new HashMap<>();
        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail)) {
            result.put("status", 0);
            result.put("message", "请输入有效的邮箱地址");
            return result;
        }

        Users user = usersDao.findByEmailIgnoreCase(normalizedEmail);
        if (user == null) {
            result.put("status", 0);
            result.put("message", "该邮箱未绑定账号");
            return result;
        }
        if (account != null && !account.trim().isEmpty() && !account.trim().equalsIgnoreCase(user.getAccount())) {
            result.put("status", 0);
            result.put("message", "账号与绑定邮箱不匹配");
            return result;
        }

        EmailVerificationService.SendCodeResult sendResult =
                emailVerificationService.sendPasswordResetCode(normalizedEmail);
        result.put("status", sendResult.isSuccess() ? 1 : 0);
        result.put("message", sendResult.getMessage());
        return result;
    }

    @PostMapping("/recover/resetPassword")
    @ResponseBody
    public Map<String, Object> resetPasswordByEmailCode(@RequestParam String email,
                                                        @RequestParam String code,
                                                        @RequestParam String newPassword) {
        Map<String, Object> result = new HashMap<>();
        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail)) {
            result.put("status", 0);
            result.put("message", "请输入有效的邮箱地址");
            return result;
        }
        if (!isValidCode(code)) {
            result.put("status", 0);
            result.put("message", "请输入6位数字验证码");
            return result;
        }
        if (newPassword == null || !newPassword.matches("^[A-Za-z0-9]{6,16}$")) {
            result.put("status", 0);
            result.put("message", "新密码只能包含字母和数字，长度为6-16位");
            return result;
        }

        Users user = usersDao.findByEmailIgnoreCase(normalizedEmail);
        if (user == null) {
            result.put("status", 0);
            result.put("message", "该邮箱未绑定账号");
            return result;
        }
        if (!emailVerificationService.verifyPasswordResetCode(normalizedEmail, code)) {
            result.put("status", 0);
            result.put("message", "验证码错误或已过期，请重新获取");
            return result;
        }
        if (newPassword.equals(user.getPassword())) {
            result.put("status", 0);
            result.put("message", "新密码不能和原密码相同");
            return result;
        }

        user.setPassword(newPassword);
        user.setOperatingTime(LocalDateTime.now());
        authService.invalidateCurrentToken(user);

        result.put("status", 1);
        result.put("message", "密码重置成功，请使用新密码登录");
        return result;
    }

    @PostMapping("/recover")
    @ResponseBody
    public Map<String, Object> recover(@RequestParam String account,
                                       @RequestParam String email) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", 0);
        result.put("message", "请先获取邮箱验证码，然后通过验证码重置密码");
        return result;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private boolean isValidCode(String code) {
        return code != null && code.trim().matches("^\\d{6}$");
    }

    private void clearUserInfoCache(Users user) {
        if (user == null || user.getUserName() == null) {
            return;
        }
        cacheService.delete(cacheService.getUserInfoKey(user.getUserName()) + ":own");
        cacheService.delete(cacheService.getUserInfoKey(user.getUserName()) + ":other");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    private String getMemberTypeName(Integer memberType) {
        memberType = normalizeMemberType(memberType);
        if (memberType == null || memberType == 0) {
            return "非会员";
        }
        switch (memberType) {
            case CardKey.TYPE_YEARLY:
                return "年费会员(360天)";
            case CardKey.TYPE_PERMANENT:
                return "永久会员";
            case CardKey.TYPE_AGENT:
                return "霓虹代理";
            default:
                return "未知会员";
        }
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
