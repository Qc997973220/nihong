package com.neon.dao;

import com.neon.pojo.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestDao extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findByAccountOrderByCreatedAtDesc(String account);
    Page<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);
    Page<WithdrawalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<WithdrawalRequest> findByIdAndStatus(Long id, Integer status);
    long countByStatus(Integer status);
    long countByStatusAndAccount(Integer status, String account);
    boolean existsByAccountAndStatus(String account, Integer status);
}
