package com.neon.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "withdrawal_request", indexes = {
        @Index(name = "idx_withdrawal_account", columnList = "account"),
        @Index(name = "idx_withdrawal_status", columnList = "status"),
        @Index(name = "idx_withdrawal_created_at", columnList = "createdAt")
})
public class WithdrawalRequest {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_REJECTED = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String account;

    @Column(length = 64)
    private String userName;

    @Column(nullable = false, length = 128)
    private String alipayAccount;

    @Column(nullable = false)
    private Integer nCoinAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashAmount;

    @Column(nullable = false)
    private Integer status = STATUS_PENDING;

    @Column(length = 64)
    private String adminAccount;

    @Column(length = 255)
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime processedAt;

    @Column(nullable = false)
    private LocalDateTime requestAt;

    @Column(nullable = false)
    private Integer frozenBeforeProcess = 0;

    @Column(nullable = false)
    private Integer balanceBeforeProcess = 0;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        requestAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
