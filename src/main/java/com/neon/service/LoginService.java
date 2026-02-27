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
    public int login(String userName,String password){
        List<Users> users = usersDao.findByUserName(userName);
        if (users.isEmpty()) return -1;
        if (users.get(0).getPassword().equals(password)){
            Users usersUpdate = users.get(0);
            usersUpdate.setToken(UUID.randomUUID().toString());
            usersUpdate.setLastLoginTime(java.time.LocalDateTime.now());
            usersDao.save(usersUpdate);
            return 1;
        }else {
            return 0;
        }
    }


    public int registered(Users users) {
        List<Users> name = usersDao.findByUserName(users.getUserName());
        if (!name.isEmpty()) {
            return 0;
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

