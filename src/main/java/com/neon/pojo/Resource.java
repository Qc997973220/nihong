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
    private Integer status = 0;      // 审核状态：0-待审核，1-审核通过
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 修改时间

}