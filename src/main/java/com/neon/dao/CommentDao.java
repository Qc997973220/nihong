package com.neon.dao;

import com.neon.pojo.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentDao  extends JpaRepository<Comment, Long> {
    List<Comment> findByResourceIdOrderByCreatedAtDesc(Long resourceId);
}
