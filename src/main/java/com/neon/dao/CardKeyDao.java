package com.neon.dao;

import com.neon.pojo.CardKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardKeyDao extends JpaRepository<CardKey, Long> {

    Optional<CardKey> findByCardKey(String cardKey);

    boolean existsByCardKey(String cardKey);
    
    List<CardKey> findByMemberType(Integer memberType);
}
