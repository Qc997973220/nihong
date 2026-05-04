package com.neon.pojo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "site_stats")
public class SiteStats {
    @Id
    private Long id = 1L;

    private Long visitorCount = 92895L;

    private LocalDateTime lastVisitTime;
}