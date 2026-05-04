package com.neon.pojo;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "login_attempt", indexes = {
    @Index(name = "idx_attempt_account", columnList = "account"),
    @Index(name = "idx_attempt_time", columnList = "attemptTime")
})
public class LoginAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    
    @Column(nullable = false)
    String account;
    
    @Column(nullable = false)
    Integer failedCount = 0;
    
    @Column
    LocalDateTime lockedUntil;
    
    @Column(nullable = false)
    LocalDateTime attemptTime = LocalDateTime.now();
    
    @Column
    String ipAddress;
}
