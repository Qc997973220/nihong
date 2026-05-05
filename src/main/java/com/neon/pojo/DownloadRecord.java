package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "download_record")
public class DownloadRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String account;           // 用户账号
    Long resourceId;          // 资源ID
    String resourceTitle;     // 资源标题
    LocalDateTime downloadedAt; // 下载时间

    @PrePersist
    protected void onCreate() {
        downloadedAt = LocalDateTime.now();
    }
}