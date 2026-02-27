package com.neon.dao;

import com.neon.pojo.UserAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserActionDao extends JpaRepository<UserAction, Long> {
    
    // 根据用户ID和评论ID查找用户是否点赞过
    Optional<UserAction> findByUserIdAndCommentId(String userId, Long commentId);
    
    // 统计某个评论的点赞数
    @Query("SELECT COUNT(ua) FROM UserAction ua WHERE ua.commentId = :commentId")
    Long countLikesByCommentId(@Param("commentId") Long commentId);
}