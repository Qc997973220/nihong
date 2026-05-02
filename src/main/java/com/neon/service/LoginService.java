package com.neon.service;

import com.neon.dao.UsersDao;
import com.neon.pojo.Users;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
@Service
public class LoginService {
    @Autowired
    UsersDao usersDao;
    public int login(String account, String password){
        Users user = usersDao.findByAccount(account);
        if (user == null) return -1;
        if (user.getPassword().equals(password)){
            user.setToken(UUID.randomUUID().toString());
            user.setLastLoginTime(java.time.LocalDateTime.now());
            usersDao.save(user);
            return 1;
        }else {
            return 0;
        }
    }


    public int registered(Users users) {
        // 检查账号是否已存在
        if (usersDao.existsByAccount(users.getAccount())) {
            return 0;
        }
        // 检查用户名是否已存在
        if (usersDao.existsByUserName(users.getUserName())) {
            return 2;
        }
        if (users.getActivationCode().equals("666668")){
            users.setId(UUID.randomUUID().toString());
            // 设置默认角色为0（普通用户）
            if (users.getRole() == null || users.getRole().isEmpty()) {
                users.setRole("0");
            }
            usersDao.save(users);
            return 1;
        }else {
            return -1;
        }
    }
}

