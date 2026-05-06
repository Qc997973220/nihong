package com.neon.dao;

import com.neon.pojo.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentDao  extends JpaRepository<Comment, Long> {
    List<Comment> findByResourceIdOrderByCreatedAtDesc(Long resourceId);

    Page<Comment> findByResourceIdAndParentIdOrderByLikesDescCreatedAtDesc(Long resourceId, Long parentId, Pageable pageable);

    List<Comment> findByResourceIdAndParentIdOrderByCreatedAtDesc(Long resourceId, Long parentId);

    @Query("SELECT c FROM Comment c WHERE c.resourceId = :resourceId AND c.parentId = 0 ORDER BY c.likes DESC, c.createdAt DESC")
    Page<Comment> findTopLevelCommentsByLikesAndCreatedAt(@Param("resourceId") Long resourceId, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.resourceId = :resourceId AND c.parentId > 0 ORDER BY c.likes DESC, c.createdAt DESC")
    List<Comment> findRepliesByResourceId(@Param("resourceId") Long resourceId);
}
