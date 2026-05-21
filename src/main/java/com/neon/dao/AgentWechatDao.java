package com.neon.dao;

import com.neon.pojo.AgentWechat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentWechatDao extends JpaRepository<AgentWechat, Long> {

    Optional<AgentWechat> findByNormalizedWechatId(String normalizedWechatId);

    boolean existsByNormalizedWechatIdAndStatus(String normalizedWechatId, Integer status);

    List<AgentWechat> findByWechatIdContainingIgnoreCaseOrRemarkContainingIgnoreCaseOrderByCreatedAtDesc(
            String wechatId,
            String remark
    );

    List<AgentWechat> findByWechatIdContainingIgnoreCaseOrRemarkContainingIgnoreCaseOrderByUpdatedAtDesc(
            String wechatId,
            String remark
    );

    List<AgentWechat> findAllByOrderByUpdatedAtDesc();
}
