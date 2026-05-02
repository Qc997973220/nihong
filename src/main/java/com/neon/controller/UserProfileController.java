package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import com.neon.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
public class UserProfileController {

    @Autowired
    private UsersDao usersDao;
    @Autowired
    private CacheService cacheService;

    @GetMapping("/api/user")
    public Map<String, Object> getUserInfo(@RequestParam("userName") String userName) {
        return getUserInfoInternal(userName);
    }

    @GetMapping("/user")
    public Map<String, Object> getUserInfoByUserName(@RequestParam("userName") String userName) {
        return getUserInfoInternal(userName);
    }

    private Map<String, Object> getUserInfoInternal(String userName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 尝试从缓存获取
            String cacheKey = cacheService.getUserInfoKey(userName);
            Map<String, Object> cachedResult = (Map<String, Object>) cacheService.get(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }

            System.out.println("查询用户: " + userName);
            List<Users> users = usersDao.findByUserName(userName);
            System.out.println("查询结果: " + users.size());
            if (!users.isEmpty()) {
                Users user = users.get(0);
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("userName", user.getUserName());
                userInfo.put("nickname", user.getNickname());
                userInfo.put("gender", user.getGender());
                userInfo.put("email", user.getEmail());
                userInfo.put("phone", user.getPhone());
                userInfo.put("avatar", null);
                userInfo.put("lastLoginTime", user.getLastLoginTime());

                result.put("status", true);
                result.put("user", userInfo);
            } else {
                System.out.println("尝试使用findOneByUserName查询");
                java.util.Optional<Users> userOptional = usersDao.findOneByUserName(userName);
                if (userOptional.isPresent()) {
                    Users user = userOptional.get();
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("userName", user.getUserName());
                    userInfo.put("nickname", user.getNickname());
                    userInfo.put("gender", user.getGender());
                    userInfo.put("email", user.getEmail());
                    userInfo.put("phone", user.getPhone());
                    userInfo.put("avatar", null);
                    userInfo.put("lastLoginTime", user.getLastLoginTime());

                    result.put("status", true);
                    result.put("user", userInfo);
                } else {
                    result.put("status", false);
                    result.put("message", "用户不存在");
                }
            }

            cacheService.set(cacheKey, result, 1800);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", false);
            result.put("message", "获取用户信息失败: " + e.getMessage());
        }

        return result;
    }
}
