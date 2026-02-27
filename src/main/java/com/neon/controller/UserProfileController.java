package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
public class UserProfileController {

    @Autowired
    private UsersDao usersDao;

    @GetMapping("/api/user")
    public Map<String, Object> getUserInfo(@RequestParam("userName") String userName) {
        Map<String, Object> result = new HashMap<>();

        try {
            System.out.println("查询用户: " + userName);
            List<Users> users = usersDao.findByUserName(userName);
            System.out.println("查询结果: " + users.size());
            if (!users.isEmpty()) {
                Users user = users.get(0);
                // 只返回必要的用户信息
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userName", user.getUserName());
                userInfo.put("nickname", user.getNickname());
                userInfo.put("gender", user.getGender());
                userInfo.put("email", user.getEmail());
                userInfo.put("phone", user.getPhone());
                userInfo.put("avatar", user.getAvatar());
                userInfo.put("lastLoginTime", user.getLastLoginTime());

                result.put("status", true);
                result.put("user", userInfo);
            } else {
                // 尝试使用findOneByUserName方法
                System.out.println("尝试使用findOneByUserName查询");
                java.util.Optional<Users> userOptional = usersDao.findOneByUserName(userName);
                if (userOptional.isPresent()) {
                    Users user = userOptional.get();
                    // 只返回必要的用户信息
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("userName", user.getUserName());
                    userInfo.put("nickname", user.getNickname());
                    userInfo.put("gender", user.getGender());
                    userInfo.put("email", user.getEmail());
                    userInfo.put("phone", user.getPhone());
                    userInfo.put("avatar", user.getAvatar());
                    userInfo.put("lastLoginTime", user.getLastLoginTime());

                    result.put("status", true);
                    result.put("user", userInfo);
                } else {
                    result.put("status", false);
                    result.put("message", "用户不存在");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", false);
            result.put("message", "获取用户信息失败: " + e.getMessage());
        }

        return result;
    }
}
