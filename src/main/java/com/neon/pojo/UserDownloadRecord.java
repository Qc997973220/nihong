package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "user_download_record",
    indexes = {
        @Index(name = "idx_account", columnList = "account"),
        @Index(name = "idx_download_date", columnList = "downloadDate")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_resource_date", columnNames = {"account", "resource_id", "download_date"})
    }
)
public class UserDownloadRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String account;

    @Column(nullable = false)
    private Long resourceId;

    private String resourceTitle;

    private LocalDate downloadDate;

    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (downloadDate == null) {
            downloadDate = LocalDate.now();
        }
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}