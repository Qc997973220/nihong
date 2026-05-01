package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import com.neon.service.CacheService;
import com.neon.service.AsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
public class FileUploadController {

    @Autowired
    private UsersDao usersDao;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private AsyncService asyncService;

    @PostMapping("/upload/avatar")
    public Map<String, Object> uploadAvatar(@RequestParam("file") MultipartFile file, @RequestParam("userName") String userName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                result.put("status", false);
                result.put("message", "文件为空");
                return result;
            }

            // 读取文件内容
            byte[] avatarBytes = file.getBytes();

            // 更新用户头像信息
            System.out.println("查询用户: " + userName);
            List<Users> users = usersDao.findByUserName(userName);
            System.out.println("查询结果: " + users.size());
            if (!users.isEmpty()) {
                Users user = users.get(0);
                user.setAvatar(avatarBytes);
                usersDao.save(user);

                // 生成Base64编码的头像URL
                String base64Avatar = Base64.getEncoder().encodeToString(avatarBytes);
                String avatarUrl = "data:" + file.getContentType() + ";base64," + base64Avatar;

                result.put("status", true);
                result.put("message", "头像上传成功");
                result.put("avatarUrl", avatarUrl);
                
                // 清除用户信息缓存
                String cacheKey = cacheService.getUserInfoKey(userName);
                cacheService.delete(cacheKey);
                
                // 异步处理文件上传后的操作
                asyncService.processFileUpload(userName, avatarUrl);
            } else {
                // 尝试使用findOneByUserName方法
                System.out.println("尝试使用findOneByUserName查询");
                java.util.Optional<Users> userOptional = usersDao.findOneByUserName(userName);
                if (userOptional.isPresent()) {
                    Users user = userOptional.get();
                    user.setAvatar(avatarBytes);
                    usersDao.save(user);

                    // 生成Base64编码的头像URL
                    String base64Avatar = Base64.getEncoder().encodeToString(avatarBytes);
                    String avatarUrl = "data:" + file.getContentType() + ";base64," + base64Avatar;

                    result.put("status", true);
                    result.put("message", "头像上传成功");
                    result.put("avatarUrl", avatarUrl);
                    
                    // 清除用户信息缓存
                    String cacheKey = cacheService.getUserInfoKey(userName);
                    cacheService.delete(cacheKey);
                    
                    // 异步处理文件上传后的操作
                    asyncService.processFileUpload(userName, avatarUrl);
                } else {
                    result.put("status", false);
                    result.put("message", "用户不存在");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", false);
            result.put("message", "头像上传失败: " + e.getMessage());
        }

        return result;
    }

    @GetMapping("/avatar/{userName}")
    public byte[] getAvatar(@PathVariable String userName) {
        try {
            List<Users> users = usersDao.findByUserName(userName);
            if (!users.isEmpty()) {
                Users user = users.get(0);
                return user.getAvatar();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
