package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(indexes = {
    @Index(name = "idx_resource_created_at", columnList = "createdAt"),
    @Index(name = "idx_resource_category", columnList = "category")
})
public class Resource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;           // 标题
    private String summary;          // 前言/简介（首页卡片用）
    @Column(columnDefinition = "TEXT")
    private String content;          // 详细内容（详情页用）
    private String category;         // 分类（如“游戏”、“免费”）
    private String author;           // 创建人
    private String image;            // 封面图片URL
    private String downloadLink;       // 下载链接
    private String downloadPassword;  // 下载密码
    private Integer status = 0;      // 审核状态：0-待审核，1-审核通过
    private Integer commentEnabled = 0;  // 评论开关：0-关闭，1-开启
    private Integer viewCount = 0;       // 访问量
    private Integer stopThreshold;       // 自动增加停止阈值（50-300随机）
    private Integer top = 0;             // 是否置顶：0-否，1-是
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 修改时间

}