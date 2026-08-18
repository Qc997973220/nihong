package com.neon.dao;

import com.neon.pojo.Users;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    List<Users> findByInvitedByOrderByCreateTimeDesc(String invitedBy);
    Users findByEmail(String email);
    Users findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    Users findByToken(String token);
    
    List<Users> findByRegisteredIpAndRegisteredDate(String registeredIp, LocalDateTime registeredDate);

    boolean existsByRegisteredIpAndRegisteredDate(String registeredIp, LocalDateTime registeredDate);

    @Query("SELECT COUNT(u) > 0 FROM Users u WHERE u.registeredIp = :ip AND u.registeredDate >= :start AND u.registeredDate < :end")
    boolean existsByRegisteredIpToday(@Param("ip") String ip, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Users u SET u.memberType = 0, u.memberStatus = 'none', u.memberExpiredAt = null, u.operatingTime = :now WHERE u.memberType IN (1, 2)")
    int convertLegacyMembersToRegular(@Param("now") LocalDateTime now);

    // 关键词搜索（数据库层分页），替代 findAll() 全表加载
    @Query("SELECT u FROM Users u WHERE LOWER(u.account) LIKE :p OR LOWER(u.userName) LIKE :p OR LOWER(u.nickname) LIKE :p OR LOWER(u.phone) LIKE :p OR LOWER(u.email) LIKE :p ORDER BY u.createTime DESC")
    List<Users> searchTopN(@Param("p") String pattern, Pageable pageable);
}
