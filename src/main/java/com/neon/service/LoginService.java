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
        java.util.Map<String, Object> cardResult = cardKeyService.useCardKey(cardKey.trim(), users.getAccount());
        if (!(boolean) cardResult.get("success")) {
            return -1;
        }

        users.setId(UUID.randomUUID().toString());
        if (users.getRole() == null || users.getRole().isEmpty()) {
            users.setRole("0");
        }
        users.setCreateTime(java.time.LocalDateTime.now());

        Integer memberType = (Integer) cardResult.get("memberType");
        int expireDays = (int) cardResult.get("expireDays");
        users.setMemberType(memberType);
        if (expireDays == Integer.MAX_VALUE) {
            users.setMemberExpiredAt(null);
        } else {
            users.setMemberExpiredAt(java.time.LocalDateTime.now().plusDays(expireDays));
        }

        String inviteCode = generateInviteCode();
        while (usersDao.existsByInviteCode(inviteCode)) {
            inviteCode = generateInviteCode();
        }
        users.setInviteCode(inviteCode);
        String invitedBy = users.getInvitedBy();
        if (invitedBy != null && !invitedBy.trim().isEmpty()) {
            Users inviter = usersDao.findByAccount(invitedBy.trim());
            if (inviter == null) {
                inviter = usersDao.findByInviteCode(invitedBy.trim());
            }
            if (inviter != null) {
                users.setInvitedBy(inviter.getAccount());
            }
        }
        usersDao.save(users);
        return 1;
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }
}

