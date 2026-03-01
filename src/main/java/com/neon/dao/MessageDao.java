package com.neon.dao;

import com.neon.pojo.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageDao extends JpaRepository<Message, Long> {
    
    // 根据用户ID查找未读消息
    List<Message> findByUserIdAndIsReadFalse(String userId);
    
    // 统计用户未读消息数量
    @Query("SELECT COUNT(m) FROM Message m WHERE m.userId = :userId AND m.isRead = false")
    Long countUnreadByUserId(@Param("userId") String userId);
    
    // 标记消息为已读
    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.id = :id")
    void markAsRead(@Param("id") Long id);
}
