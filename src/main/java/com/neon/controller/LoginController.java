package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import com.neon.service.AuthService;
import com.neon.service.CacheService;
import com.neon.service.LoginService;
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

    @GetMapping("/first")
    @ResponseBody
    public Map<String, Object> first(@RequestParam String account, @RequestParam String password){
        Map<String, Object> result = new HashMap<>();
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

    @GetMapping("/validateToken")
    @ResponseBody
    public Map<String, Object> validateToken(@RequestParam String token) {
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
        userInfo.put("memberType", user.getMemberType() != null ? user.getMemberType() : 0);
        userInfo.put("inviteCode", user.getInviteCode());

        if (user.getMemberType() != null && user.getMemberType() == 4) {
            userInfo.put("memberStatus", "permanent");
            userInfo.put("memberExpireText", "永久有效");
        } else if (user.getMemberType() != null && user.getMemberType() == 0) {
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

    @PostMapping("/registered")
    @ResponseBody
    public Map<String, Object> registered(Users users, HttpServletRequest request){
        Map<String, Object> result = new HashMap<>();
        
        String clientIp = getClientIp(request);
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        
        if (usersDao.existsByRegisteredIpAndRegisteredDate(clientIp, todayStart)) {
            result.put("status", 0);
            result.put("message", "该IP今日已注册过一个账号，请明日再试");
            return result;
        }
        
        int regResult = loginService.registered(users);
        if (regResult == 1) {
            Users user = usersDao.findByAccount(users.getAccount());
            if (user != null) {
                user.setRegisteredIp(clientIp);
                user.setRegisteredDate(LocalDateTime.now());
                usersDao.save(user);
                
                String memberTypeName = "未知";
                if (user.getMemberType() != null) {
                    switch (user.getMemberType()) {
                        case 1: memberTypeName = "月度会员(30天)"; break;
                        case 2: memberTypeName = "季度会员(90天)"; break;
                        case 3: memberTypeName = "年度会员(360天)"; break;
                        case 4: memberTypeName = "永久会员"; break;
                        default: memberTypeName = "非会员"; break;
                    }
                }
                result.put("memberType", user.getMemberType());
                result.put("memberTypeName", memberTypeName);
            }
        }
        result.put("status", regResult);
        return result;
    }

    @PostMapping("/updateUserInfo")
    @ResponseBody
    public Map<String, Object> updateUserInfo(@RequestParam String account,
                                              @RequestParam(required = false) String userName,
                                              @RequestParam(required = false) String gender,
                                              @RequestParam(required = false) String phone,
                                              @RequestParam(required = false) String email) {
        Map<String, Object> result = new HashMap<>();
        Users user = usersDao.findByAccount(account);
        if (user == null) {
            result.put("status", 0);
            result.put("message", "用户不存在");
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
            if (usersDao.existsByEmail(email) && !email.equals(user.getEmail())) {
                result.put("status", 0);
                result.put("message", "该邮箱已被其他账号绑定，请更换邮箱");
                return result;
            }
            user.setEmail(email);
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

    @PostMapping("/activate")
    @ResponseBody
    public Map<String, Object> activate(@RequestParam String account,
                                       @RequestParam String activationCode) {
        Map<String, Object> result = loginService.activateMember(account, activationCode);
        if ((boolean) result.get("success")) {
            result.put("status", 1);
        } else {
            result.put("status", 0);
        }
        return result;
    }
}
