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

    @Autowired
    CardKeyService cardKeyService;

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
        if (usersDao.existsByAccount(users.getAccount())) {
            return 0;
        }
        if (usersDao.existsByUserName(users.getUserName())) {
            return 2;
        }
        String cardKey = users.getActivationCode();
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return -1;
        }
        if (!cardKeyService.validateCardKey(cardKey.trim())) {
            return -1;
        }
        users.setId(UUID.randomUUID().toString());
        if (users.getRole() == null || users.getRole().isEmpty()) {
            users.setRole("0");
        }
        usersDao.save(users);
        cardKeyService.useCardKey(cardKey.trim(), users.getAccount());
        return 1;
    }
}

