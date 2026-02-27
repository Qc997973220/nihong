package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    
    // 新增字段
    private Integer likes = 0;       // 点赞数
    private Integer dislikes = 0;    // 踩数
    private Long parentId = 0L;       // 父评论ID，0表示顶级评论
    
    // 回复列表（非数据库字段）
    @Transient
    private List<Comment> replies = new ArrayList<>();

    // getters and setters
}