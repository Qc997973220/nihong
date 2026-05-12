package com.neon.pojo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_key")
public class CardKey {

    public static final int TYPE_MONTHLY = 1;    // 旧版会员（已停用）
    public static final int TYPE_QUARTERLY = 2;  // 旧版会员（已停用）
    public static final int TYPE_YEARLY = 3;     // 年费会员 360天
    public static final int TYPE_PERMANENT = 4;  // 永久会员
    public static final int TYPE_AGENT = 5;      // 霓虹代理（仅管理员授予，不生成卡密）

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String cardKey;

    @Column(nullable = false)
    private Integer status = 0;

    private Integer memberType = TYPE_YEARLY;

    private LocalDateTime usedAt;

    private String usedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public CardKey() {
        this.createdAt = LocalDateTime.now();
    }

    public CardKey(String cardKey) {
        this.cardKey = cardKey;
        this.status = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCardKey() {
        return cardKey;
    }

    public void setCardKey(String cardKey) {
        this.cardKey = cardKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public String getUsedBy() {
        return usedBy;
    }

    public void setUsedBy(String usedBy) {
        this.usedBy = usedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getMemberType() {
        return memberType;
    }

    public void setMemberType(Integer memberType) {
        this.memberType = memberType;
    }

    public String getMemberTypeName() {
        if (memberType == null) return "未知";
        switch (memberType) {
            case TYPE_MONTHLY:
            case TYPE_QUARTERLY: return "旧版会员(已停用)";
            case TYPE_YEARLY: return "年费会员(360天)";
            case TYPE_PERMANENT: return "永久会员";
            case TYPE_AGENT: return "霓虹代理";
            default: return "未知";
        }
    }
}
