package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import com.neon.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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
    @GetMapping("/first")
    @ResponseBody
    public Map<String, Object> first(@RequestParam String account, @RequestParam String password){
        Map<String, Object> result = new HashMap<>();
        int loginResult = loginService.login(account, password);
        result.put("status", loginResult);
        if (loginResult == 1) {
            Users user = usersDao.findByAccount(account);
            if (user != null) {
                // 只返回必要的用户信息
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("account", user.getAccount());
                userInfo.put("userName", user.getUserName());
                userInfo.put("role", user.getRole() != null && !user.getRole().isEmpty() ? user.getRole() : "0");
                userInfo.put("nickname", user.getNickname());
                userInfo.put("phone", user.getPhone());
                userInfo.put("email", user.getEmail());
                userInfo.put("gender", user.getGender());
                userInfo.put("token", user.getToken());
                // 不再返回用户头像，所有用户使用系统默认字母头像
                userInfo.put("avatar", null);
                userInfo.put("lastLoginTime", user.getLastLoginTime());
                result.put("user", userInfo);
            }
        }
        return result;
    }

    @PostMapping("/registered")
    @ResponseBody
    public int registered(Users users){
        return loginService.registered(users);
    }
}
