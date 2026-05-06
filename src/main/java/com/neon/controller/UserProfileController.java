package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import com.neon.service.AuthService;
import com.neon.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    @Autowired
    private AuthService authService;

    @GetMapping("/api/user")
    public Map<String, Object> getUserInfo(
            @RequestParam("userName") String userName,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return getUserInfoInternal(userName, token);
    }

    @GetMapping("/user")
    public Map<String, Object> getUserInfoByUserName(
            @RequestParam("userName") String userName,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return getUserInfoInternal(userName, token);
    }

    private Map<String, Object> getUserInfoInternal(String userName, String token) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 验证token，判断是否是查看自己的资料
            boolean isOwnProfile = false;
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Map<String, Object> tokenResult = authService.validateToken(token);
                if ((Boolean) tokenResult.get("valid")) {
                    Users currentUser = (Users) tokenResult.get("user");
                    if (currentUser != null && currentUser.getUserName().equals(userName)) {
                        isOwnProfile = true;
                    }
                }
            }

            // 尝试从缓存获取（根据是否是自己的资料使用不同的缓存键）
            String cacheKey = cacheService.getUserInfoKey(userName) + ":" + (isOwnProfile ? "own" : "other");
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
                userInfo.put("account", user.getAccount());
                userInfo.put("userName", user.getUserName());
                userInfo.put("nickname", user.getNickname());
                userInfo.put("gender", user.getGender());
                // 个人简介，默认显示"这个人很懒，什么也没写"
                userInfo.put("introduction", user.getIntroduction() != null && !user.getIntroduction().trim().isEmpty() 
                    ? user.getIntroduction() : "这个人很懒，什么也没写");
                
                // 只有查看自己的资料时才返回敏感信息
                if (isOwnProfile) {
                    userInfo.put("email", user.getEmail());
                    userInfo.put("phone", user.getPhone());
                    userInfo.put("lastLoginTime", user.getLastLoginTime());
                }
                
                userInfo.put("avatar", null);

                result.put("status", true);
                result.put("user", userInfo);
            } else {
                System.out.println("尝试使用findOneByUserName查询");
                java.util.Optional<Users> userOptional = usersDao.findOneByUserName(userName);
                if (userOptional.isPresent()) {
                    Users user = userOptional.get();
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("account", user.getAccount());
                    userInfo.put("userName", user.getUserName());
                    userInfo.put("nickname", user.getNickname());
                    userInfo.put("gender", user.getGender());
                    // 个人简介，默认显示"这个人很懒，什么也没写"
                    userInfo.put("introduction", user.getIntroduction() != null && !user.getIntroduction().trim().isEmpty() 
                        ? user.getIntroduction() : "这个人很懒，什么也没写");
                    
                    // 只有查看自己的资料时才返回敏感信息
                    if (isOwnProfile) {
                        userInfo.put("email", user.getEmail());
                        userInfo.put("phone", user.getPhone());
                        userInfo.put("lastLoginTime", user.getLastLoginTime());
                    }
                    
                    userInfo.put("avatar", null);

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

    @PostMapping("/user/introduction")
    public Map<String, Object> updateIntroduction(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (token == null || !token.startsWith("Bearer ")) {
                result.put("status", false);
                result.put("message", "请先登录");
                return result;
            }

            token = token.substring(7);
            Map<String, Object> tokenResult = authService.validateToken(token);
            if (!(Boolean) tokenResult.get("valid")) {
                result.put("status", false);
                result.put("message", "登录已失效，请重新登录");
                return result;
            }

            Users currentUser = (Users) tokenResult.get("user");
            if (currentUser == null) {
                result.put("status", false);
                result.put("message", "用户不存在");
                return result;
            }

            String introduction = request.get("introduction");
            if (introduction != null) {
                // 限制最多80字
                if (introduction.length() > 80) {
                    introduction = introduction.substring(0, 80);
                }
                // 去除首尾空格
                introduction = introduction.trim();
            }

            currentUser.setIntroduction(introduction);
            usersDao.save(currentUser);

            // 清除缓存
            String ownCacheKey = cacheService.getUserInfoKey(currentUser.getUserName()) + ":own";
            String otherCacheKey = cacheService.getUserInfoKey(currentUser.getUserName()) + ":other";
            cacheService.delete(ownCacheKey);
            cacheService.delete(otherCacheKey);

            result.put("status", true);
            result.put("message", "个人简介修改成功");
            result.put("introduction", introduction != null && !introduction.isEmpty() ? introduction : "这个人很懒，什么也没写");

        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", false);
            result.put("message", "修改失败: " + e.getMessage());
        }

        return result;
    }
}
