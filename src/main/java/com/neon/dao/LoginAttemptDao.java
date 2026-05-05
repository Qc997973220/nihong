package com.neon.dao;

import com.neon.pojo.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginAttemptDao extends JpaRepository<LoginAttempt, Long> {
    Optional<LoginAttempt> findByAccount(String account);
    void deleteByAccount(String account);
}