package com.neon.dao;

import com.neon.pojo.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsersDao extends JpaRepository<Users, String> {
    Optional<Users> findOneByUserName(String userName);
    List<Users> findByUserName(String userName);
    Users findByAccount(String account);
    boolean existsByAccount(String account);
    boolean existsByUserName(String userName);

}
