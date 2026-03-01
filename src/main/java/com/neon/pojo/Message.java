package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;            // 接收消息的用户ID
    @Column(columnDefinition = "TEXT")
    private String content;           // 消息内容
    private String type;              // 消息类型，例如 "comment_reply" 表示评论回复
    private Long relatedId;           // 相关ID，例如评论ID
    private Boolean isRead = false;     // 是否已读
    private LocalDateTime createdAt;  // 创建时间
}
