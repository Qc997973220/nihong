package com.neon.dao;

import com.neon.pojo.InviteRewardRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteRewardRecordDao extends JpaRepository<InviteRewardRecord, Long> {
    Optional<InviteRewardRecord> findByInviteeAccountAndMemberType(String inviteeAccount, Integer memberType);
    List<InviteRewardRecord> findByInviterAccountOrderByRewardedAtDesc(String inviterAccount);
    List<InviteRewardRecord> findByInviteeAccountOrderByRewardedAtDesc(String inviteeAccount);
    long countByInviterAccount(String inviterAccount);
    long countByInviteeAccount(String inviteeAccount);
}
