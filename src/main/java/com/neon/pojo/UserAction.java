package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "user_action", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"userId", "commentId"})
})
public class UserAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String userId;           // 用户ID（用户名）
    private Long commentId;          // 评论ID
    private LocalDateTime createdAt; // 操作时间
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}