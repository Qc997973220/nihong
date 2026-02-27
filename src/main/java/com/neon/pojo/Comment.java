package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long resourceId;         // 所属资源ID
    private String author;            // 评论人
    @Column(columnDefinition = "TEXT")
    private String content;           // 评论内容
    private LocalDateTime createdAt;  // 评论时间

    // getters and setters
}