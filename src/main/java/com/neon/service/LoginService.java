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

        users.setId(UUID.randomUUID().toString());
        if (users.getRole() == null || users.getRole().isEmpty()) {
            users.setRole("0");
        }
        users.setCreateTime(java.time.LocalDateTime.now());
        users.setMemberType(0);

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

    public java.util.Map<String, Object> activateMember(String account, String activationCode) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", false);

        if (account == null || account.trim().isEmpty()) {
            result.put("message", "账号不能为空");
            return result;
        }
        if (activationCode == null || activationCode.trim().isEmpty()) {
            result.put("message", "激活码不能为空");
            return result;
        }

        Users user = usersDao.findByAccount(account);
        if (user == null) {
            result.put("message", "用户不存在");
            return result;
        }

        java.util.Map<String, Object> cardResult = cardKeyService.useCardKey(activationCode.trim(), account);
        if (!(boolean) cardResult.get("success")) {
            result.put("message", "激活码无效或已使用");
            return result;
        }

        Integer memberType = (int) cardResult.get("memberType");
        int expireDays = (int) cardResult.get("expireDays");
        user.setMemberType(memberType);
        if (expireDays == Integer.MAX_VALUE) {
            user.setMemberExpiredAt(null);
            user.setMemberStatus("permanent");
        } else {
            user.setMemberExpiredAt(java.time.LocalDateTime.now().plusDays(expireDays));
            user.setMemberStatus("active");
        }
        user.setOperatingTime(java.time.LocalDateTime.now());
        usersDao.save(user);

        result.put("success", true);
        result.put("memberType", memberType);
        if (memberType == 4) {
            result.put("memberStatus", "permanent");
            result.put("memberExpireText", "永久有效");
        } else {
            result.put("memberStatus", "active");
            if (user.getMemberExpiredAt() != null) {
                result.put("memberExpireText", user.getMemberExpiredAt().toLocalDate() + " 到期");
            }
        }
        result.put("message", "激活成功");
        return result;
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

