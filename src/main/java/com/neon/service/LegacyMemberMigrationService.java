package com.neon.service;

import com.neon.dao.UsersDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class LegacyMemberMigrationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyMemberMigrationService.class);

    private final UsersDao usersDao;

    public LegacyMemberMigrationService(UsersDao usersDao) {
        this.usersDao = usersDao;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int affected = usersDao.convertLegacyMembersToRegular(LocalDateTime.now());
        if (affected > 0) {
            log.info("已将 {} 个已停用会员类型账号转换为普通用户", affected);
        }
    }
}
