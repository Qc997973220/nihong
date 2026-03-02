package com.neon.controller;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
public class FileUploadController {

    @Autowired
    private UsersDao usersDao;

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

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String fileName = UUID.randomUUID().toString() + suffix;

            // 确定文件存储路径
            // 使用Spring Boot的外部存储路径，确保在任何环境下都能正确存储文件
            String uploadDir = System.getProperty("user.dir") + "/avatars/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 保存文件
            File dest = new File(uploadDir + fileName);
            file.transferTo(dest);

            // 更新用户头像信息
            System.out.println("查询用户: " + userName);
            List<Users> users = usersDao.findByUserName(userName);
            System.out.println("查询结果: " + users.size());
            if (!users.isEmpty()) {
                Users user = users.get(0);
                user.setAvatar("/avatars/" + fileName);
                usersDao.save(user);

                result.put("status", true);
                result.put("message", "头像上传成功");
                result.put("avatarUrl", "/avatars/" + fileName);
            } else {
                // 尝试使用findOneByUserName方法
                System.out.println("尝试使用findOneByUserName查询");
                java.util.Optional<Users> userOptional = usersDao.findOneByUserName(userName);
                if (userOptional.isPresent()) {
                    Users user = userOptional.get();
                    user.setAvatar("/avatars/" + fileName);
                    usersDao.save(user);

                    result.put("status", true);
                    result.put("message", "头像上传成功");
                    result.put("avatarUrl", "/avatars/" + fileName);
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
}
