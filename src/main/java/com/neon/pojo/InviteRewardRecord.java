package com.neon.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "invite_reward_record", indexes = {
        @Index(name = "idx_invite_reward_inviter", columnList = "inviterAccount"),
        @Index(name = "idx_invite_reward_invitee", columnList = "inviteeAccount"),
        @Index(name = "idx_invite_reward_member_type", columnList = "memberType")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_invite_reward_invitee_member", columnNames = {"inviteeAccount", "memberType"})
})
public class InviteRewardRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String inviterAccount;

    @Column(nullable = false, length = 64)
    private String inviteeAccount;

    @Column(nullable = false)
    private Integer memberType;

    @Column(nullable = false)
    private Integer rewardAmount;

    @Column(length = 32)
    private String memberStatus;

    @Column(nullable = false)
    private LocalDateTime rewardedAt;
}
