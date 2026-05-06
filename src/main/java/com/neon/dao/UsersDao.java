package com.neon.dao;

import com.neon.pojo.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsersDao extends JpaRepository<Users, String> {
    Optional<Users> findOneByUserName(String userName);
    List<Users> findByUserName(String userName);
    Users findByAccount(String account);
    boolean existsByAccount(String account);
    boolean existsByUserName(String userName);
    Users findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
    Users findByEmail(String email);
    boolean existsByEmail(String email);
    Users findByToken(String token);
    
    List<Users> findByRegisteredIpAndRegisteredDate(String registeredIp, LocalDateTime registeredDate);

    boolean existsByRegisteredIpAndRegisteredDate(String registeredIp, LocalDateTime registeredDate);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) > 0 FROM Users u WHERE u.registeredIp = :ip AND u.registeredDate >= :start AND u.registeredDate < :end")
    boolean existsByRegisteredIpToday(@org.springframework.data.repository.query.Param("ip") String ip, @org.springframework.data.repository.query.Param("start") LocalDateTime start, @org.springframework.data.repository.query.Param("end") LocalDateTime end);
}
