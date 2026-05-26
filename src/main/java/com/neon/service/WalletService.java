package com.neon.service;

import com.neon.dao.InviteRewardRecordDao;
import com.neon.dao.UsersDao;
import com.neon.dao.WithdrawalRequestDao;
import com.neon.pojo.CardKey;
import com.neon.pojo.InviteRewardRecord;
import com.neon.pojo.Users;
import com.neon.pojo.WithdrawalRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WalletService {

    private static final BigDecimal N_COIN_TO_CNY = new BigDecimal("0.9");
    private static final int MIN_WITHDRAW_NCOIN = 100;
    private static final int MAX_ADMIN_INVITE_DATA_COUNT = 200;
    private static final String RANDOM_ACCOUNT_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final String RANDOM_INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String RANDOM_USER_NAME_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private UsersDao usersDao;

    @Autowired
    private InviteRewardRecordDao inviteRewardRecordDao;

    @Autowired
    private WithdrawalRequestDao withdrawalRequestDao;

    @Autowired
    private AsyncService asyncService;

    public int getAvailableNCoin(Users user) {
        return user != null && user.getNCoinBalance() != null ? user.getNCoinBalance() : 0;
    }

    public int getFrozenNCoin(Users user) {
        return user != null && user.getNCoinFrozen() != null ? user.getNCoinFrozen() : 0;
    }

    public BigDecimal toCashAmount(int nCoinAmount) {
        return BigDecimal.valueOf(nCoinAmount)
                .multiply(N_COIN_TO_CNY)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Map<String, Object> buildWalletSummary(Users user) {
        Map<String, Object> result = new HashMap<>();
        int available = getAvailableNCoin(user);
        int frozen = getFrozenNCoin(user);
        long inviteCount = user != null ? usersDao.findByInvitedByOrderByCreateTimeDesc(user.getAccount()).size() : 0;
        long inviteRewardTotal = user != null ? inviteRewardRecordDao.findByInviterAccountOrderByRewardedAtDesc(user.getAccount())
                .stream()
                .mapToLong(record -> record.getRewardAmount() != null ? record.getRewardAmount() : 0)
                .sum() : 0;
        long pendingWithdrawals = user != null ? withdrawalRequestDao.countByStatusAndAccount(WithdrawalRequest.STATUS_PENDING, user.getAccount()) : 0;

        result.put("nCoinBalance", available);
        result.put("nCoinFrozen", frozen);
        result.put("nCoinTotal", available + frozen);
        result.put("cashEquivalent", toCashAmount(available).toPlainString());
        result.put("inviteCount", inviteCount);
        result.put("inviteRewardTotal", inviteRewardTotal);
        result.put("promotionPermission", hasPromotionPermission(user));
        result.put("isAgent", isAgent(user));
        result.put("inviteRewardRates", buildRewardPolicy(user));
        result.put("pendingWithdrawalCount", pendingWithdrawals);
        return result;
    }

    public Map<String, Object> buildInviteSummary(Users inviter) {
        Map<String, Object> result = new HashMap<>();
        if (inviter == null) {
            result.put("invitees", new ArrayList<>());
            result.put("inviteCount", 0);
            result.put("inviteRewardTotal", 0);
            result.put("promotionPermission", false);
            result.put("isAgent", false);
            result.put("inviteRewardRates", new HashMap<>());
            return result;
        }

        List<Users> invitees = usersDao.findByInvitedByOrderByCreateTimeDesc(inviter.getAccount());
        List<Map<String, Object>> inviteeList = new ArrayList<>();
        for (Users invitee : invitees) {
            inviteeList.add(buildInviteeItem(invitee, inviter.getAccount()));
        }

        long rewardTotal = inviteRewardRecordDao.findByInviterAccountOrderByRewardedAtDesc(inviter.getAccount())
                .stream()
                .mapToLong(record -> record.getRewardAmount() != null ? record.getRewardAmount() : 0)
                .sum();

        result.put("invitees", inviteeList);
        result.put("inviteCount", inviteeList.size());
        result.put("inviteRewardTotal", rewardTotal);
        result.put("promotionPermission", hasPromotionPermission(inviter));
        result.put("isAgent", isAgent(inviter));
        result.put("inviteRewardRates", buildRewardPolicy(inviter));
        return result;
    }

    @Transactional
    public int reconcileInviteRewardsForInviter(Users inviter) {
        if (inviter == null || inviter.getAccount() == null || !hasPromotionPermission(inviter)) {
            return 0;
        }

        int granted = 0;
        List<Users> invitees = usersDao.findByInvitedByOrderByCreateTimeDesc(inviter.getAccount());
        for (Users invitee : invitees) {
            if (shouldGrantInviteReward(invitee) && grantInviteRewardIfNeeded(invitee)) {
                granted++;
            }
        }
        return granted;
    }

    public Map<String, Object> buildWithdrawalSummary(Users user) {
        Map<String, Object> result = new HashMap<>();
        if (user == null) {
            result.put("withdrawals", new ArrayList<>());
            result.put("pendingCount", 0);
            return result;
        }

        List<WithdrawalRequest> withdrawals = withdrawalRequestDao.findByAccountOrderByCreatedAtDesc(user.getAccount());
        List<Map<String, Object>> items = new ArrayList<>();
        for (WithdrawalRequest request : withdrawals) {
            items.add(buildWithdrawalItem(request));
        }
        result.put("withdrawals", items);
        result.put("pendingCount", withdrawalRequestDao.countByStatusAndAccount(WithdrawalRequest.STATUS_PENDING, user.getAccount()));
        return result;
    }

    public Map<String, Object> buildAdminWithdrawalPage(int page, int pageSize, Integer status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(pageSize, 50));
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        Page<WithdrawalRequest> withdrawalPage;
        if (status == null || status < 0) {
            withdrawalPage = withdrawalRequestDao.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            withdrawalPage = withdrawalRequestDao.findByStatusOrderByCreatedAtDesc(status, pageable);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (WithdrawalRequest request : withdrawalPage.getContent()) {
            items.add(buildWithdrawalItem(request));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("withdrawals", items);
        result.put("currentPage", safePage);
        result.put("pageSize", safeSize);
        result.put("totalElements", withdrawalPage.getTotalElements());
        result.put("totalPages", withdrawalPage.getTotalPages());
        result.put("hasNext", withdrawalPage.hasNext());
        result.put("hasPrevious", withdrawalPage.hasPrevious());
        result.put("pendingCount", withdrawalRequestDao.countByStatus(WithdrawalRequest.STATUS_PENDING));
        result.put("paidCount", withdrawalRequestDao.countByStatus(WithdrawalRequest.STATUS_PAID));
        result.put("rejectedCount", withdrawalRequestDao.countByStatus(WithdrawalRequest.STATUS_REJECTED));
        return result;
    }

    @Transactional
    public Map<String, Object> createAdminInviteData(String inviterAccount,
                                                     int permanentCount,
                                                     int yearlyCount,
                                                     int regularCount,
                                                     int permanentRewardAmount,
                                                     int yearlyRewardAmount) {
        Map<String, Object> result = new LinkedHashMap<>();
        String normalizedAccount = inviterAccount == null ? "" : inviterAccount.trim();
        if (normalizedAccount.isEmpty()) {
            result.put("success", false);
            result.put("message", "邀请人账号不能为空");
            return result;
        }

        Users inviter = usersDao.findByAccount(normalizedAccount);
        if (inviter == null) {
            result.put("success", false);
            result.put("message", "邀请人账号不存在");
            return result;
        }

        if (permanentCount < 0 || yearlyCount < 0 || regularCount < 0) {
            result.put("success", false);
            result.put("message", "生成数量不能为负数");
            return result;
        }
        if (permanentRewardAmount < 0 || yearlyRewardAmount < 0) {
            result.put("success", false);
            result.put("message", "奖励金额不能为负数");
            return result;
        }
        if (permanentCount > 0 && permanentRewardAmount <= 0) {
            result.put("success", false);
            result.put("message", "永久VIP奖励金额必须大于0");
            return result;
        }
        if (yearlyCount > 0 && yearlyRewardAmount <= 0) {
            result.put("success", false);
            result.put("message", "年费VIP奖励金额必须大于0");
            return result;
        }

        int totalCount = permanentCount + yearlyCount + regularCount;
        if (totalCount <= 0) {
            result.put("success", false);
            result.put("message", "至少需要生成1个邀请用户");
            return result;
        }
        if (totalCount > MAX_ADMIN_INVITE_DATA_COUNT) {
            result.put("success", false);
            result.put("message", "单次最多生成" + MAX_ADMIN_INVITE_DATA_COUNT + "个邀请用户");
            return result;
        }

        List<AdminInviteSpec> specs = new ArrayList<>();
        for (int i = 0; i < permanentCount; i++) {
            specs.add(new AdminInviteSpec(CardKey.TYPE_PERMANENT, "permanent", permanentRewardAmount));
        }
        for (int i = 0; i < yearlyCount; i++) {
            specs.add(new AdminInviteSpec(CardKey.TYPE_YEARLY, "active", yearlyRewardAmount));
        }
        for (int i = 0; i < regularCount; i++) {
            specs.add(new AdminInviteSpec(0, "none", 0));
        }
        Collections.shuffle(specs, RANDOM);

        List<Map<String, Object>> generatedUsers = new ArrayList<>();
        int rewardTotal = 0;

        for (int i = 0; i < specs.size(); i++) {
            AdminInviteSpec spec = specs.get(i);
            LocalDateTime registeredAt = buildSyntheticRegisteredAt(i, specs.size());
            Users invitee = createSyntheticInvitee(inviter, spec.memberType, spec.memberStatus, registeredAt);
            if (spec.memberType == CardKey.TYPE_YEARLY) {
                invitee.setMemberExpiredAt(registeredAt.plusDays(360));
            }
            usersDao.save(invitee);
            if (spec.rewardAmount > 0) {
                saveSyntheticReward(inviter, invitee, spec.memberType, spec.rewardAmount, spec.memberStatus, registeredAt);
                rewardTotal += spec.rewardAmount;
            }
            generatedUsers.add(buildGeneratedInviteDataItem(invitee, spec.rewardAmount));
        }

        inviter.setNCoinBalance(getAvailableNCoin(inviter) + rewardTotal);
        inviter.setOperatingTime(LocalDateTime.now());
        usersDao.save(inviter);

        result.put("success", true);
        result.put("message", "邀请数据已生成");
        result.put("inviterAccount", inviter.getAccount());
        result.put("createdCount", totalCount);
        result.put("permanentCount", permanentCount);
        result.put("yearlyCount", yearlyCount);
        result.put("regularCount", regularCount);
        result.put("addedRewardTotal", rewardTotal);
        result.put("nCoinBalance", getAvailableNCoin(inviter));
        result.put("generatedUsers", generatedUsers);
        result.put("wallet", buildWalletSummary(inviter));
        return result;
    }

    @Transactional
    public Map<String, Object> createWithdrawalRequest(Users user, String alipayAccount) {
        Map<String, Object> result = new HashMap<>();
        if (user == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        String normalizedAlipay = alipayAccount == null ? "" : alipayAccount.trim();
        if (normalizedAlipay.isEmpty()) {
            result.put("success", false);
            result.put("message", "支付宝账号不能为空");
            return result;
        }
        if (normalizedAlipay.length() > 128) {
            result.put("success", false);
            result.put("message", "支付宝账号长度超出限制");
            return result;
        }

        if (withdrawalRequestDao.existsByAccountAndStatus(user.getAccount(), WithdrawalRequest.STATUS_PENDING)) {
            result.put("success", false);
            result.put("message", "您已有待处理的提现申请，请等待管理员处理后再提交");
            return result;
        }

        int available = getAvailableNCoin(user);
        if (available < MIN_WITHDRAW_NCOIN) {
            result.put("success", false);
            result.put("message", "N币余额不足100，暂不能提现");
            return result;
        }

        int frozen = getFrozenNCoin(user);
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccount(user.getAccount());
        request.setUserName(user.getUserName());
        request.setAlipayAccount(normalizedAlipay);
        request.setNCoinAmount(available);
        request.setCashAmount(toCashAmount(available));
        request.setStatus(WithdrawalRequest.STATUS_PENDING);
        request.setFrozenBeforeProcess(frozen);
        request.setBalanceBeforeProcess(available);
        withdrawalRequestDao.save(request);

        user.setNCoinBalance(0);
        user.setNCoinFrozen(frozen + available);
        user.setOperatingTime(LocalDateTime.now());
        usersDao.save(user);

        result.put("success", true);
        result.put("message", "提现申请成功，预计1~3个工作日到账");
        result.put("withdrawal", buildWithdrawalItem(request));
        result.put("wallet", buildWalletSummary(user));

        return result;
    }

    @Transactional
    public Map<String, Object> approveWithdrawal(Users admin, Long withdrawalId) {
        Map<String, Object> result = new HashMap<>();
        WithdrawalRequest request = getPendingWithdrawalOrFail(withdrawalId, result);
        if (request == null) {
            return result;
        }

        Users owner = usersDao.findByAccount(request.getAccount());
        if (owner == null) {
            result.put("success", false);
            result.put("message", "提现用户不存在");
            return result;
        }

        int frozen = getFrozenNCoin(owner);
        int amount = request.getNCoinAmount() != null ? request.getNCoinAmount() : 0;
        owner.setNCoinFrozen(Math.max(0, frozen - amount));
        owner.setOperatingTime(LocalDateTime.now());
        usersDao.save(owner);

        request.setStatus(WithdrawalRequest.STATUS_PAID);
        request.setAdminAccount(admin != null ? admin.getAccount() : null);
        request.setProcessedAt(LocalDateTime.now());
        request.setRemark("已打款");
        withdrawalRequestDao.save(request);

        result.put("success", true);
        result.put("message", "提现已标记为打款");
        result.put("withdrawal", buildWithdrawalItem(request));
        return result;
    }

    @Transactional
    public Map<String, Object> rejectWithdrawal(Users admin, Long withdrawalId, String remark) {
        Map<String, Object> result = new HashMap<>();
        WithdrawalRequest request = getPendingWithdrawalOrFail(withdrawalId, result);
        if (request == null) {
            return result;
        }

        Users owner = usersDao.findByAccount(request.getAccount());
        if (owner == null) {
            result.put("success", false);
            result.put("message", "提现用户不存在");
            return result;
        }

        int amount = request.getNCoinAmount() != null ? request.getNCoinAmount() : 0;
        owner.setNCoinBalance(getAvailableNCoin(owner) + amount);
        owner.setNCoinFrozen(Math.max(0, getFrozenNCoin(owner) - amount));
        owner.setOperatingTime(LocalDateTime.now());
        usersDao.save(owner);

        request.setStatus(WithdrawalRequest.STATUS_REJECTED);
        request.setAdminAccount(admin != null ? admin.getAccount() : null);
        request.setProcessedAt(LocalDateTime.now());
        request.setRemark(remark != null && !remark.trim().isEmpty() ? remark.trim() : "已拒绝");
        withdrawalRequestDao.save(request);

        result.put("success", true);
        result.put("message", "提现申请已拒绝");
        result.put("withdrawal", buildWithdrawalItem(request));
        return result;
    }

    @Transactional
    public boolean grantInviteRewardIfNeeded(Users invitee) {
        if (invitee == null) {
            return false;
        }

        String inviterAccount = invitee.getInvitedBy();
        Integer memberType = normalizeMemberType(invitee.getMemberType());
        if (invitee.getAccount() == null || inviterAccount == null || inviterAccount.trim().isEmpty()) {
            return false;
        }
        if (!shouldGrantInviteReward(invitee)) {
            return false;
        }

        Users inviter = usersDao.findByAccount(inviterAccount.trim());
        if (inviter == null || inviter.getAccount() == null || inviter.getAccount().equals(invitee.getAccount())) {
            return false;
        }
        if (!hasPromotionPermission(inviter)) {
            return false;
        }

        Integer rewardAmount = getRewardAmount(inviter, memberType);
        if (rewardAmount == null || rewardAmount <= 0) {
            return false;
        }

        if (inviteRewardRecordDao.findByInviteeAccountAndMemberType(invitee.getAccount(), memberType).isPresent()) {
            return false;
        }

        InviteRewardRecord record = new InviteRewardRecord();
        record.setInviterAccount(inviter.getAccount());
        record.setInviteeAccount(invitee.getAccount());
        record.setMemberType(memberType);
        record.setRewardAmount(rewardAmount);
        record.setMemberStatus(invitee.getMemberStatus());
        record.setRewardedAt(LocalDateTime.now());
        inviteRewardRecordDao.save(record);

        inviter.setNCoinBalance(getAvailableNCoin(inviter) + rewardAmount);
        inviter.setOperatingTime(LocalDateTime.now());
        usersDao.save(inviter);

        asyncService.sendMessageNotification(
                inviter.getUserName() != null && !inviter.getUserName().trim().isEmpty() ? inviter.getUserName() : inviter.getAccount(),
                "您的邀请用户开通了" + getMemberTypeName(memberType) + "，已奖励 " + rewardAmount + " N币",
                "invite_reward",
                record.getId());
        return true;
    }

    public List<Map<String, Object>> buildInviteeList(Users inviter) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (inviter == null || inviter.getAccount() == null) {
            return result;
        }

        List<Users> invitees = usersDao.findByInvitedByOrderByCreateTimeDesc(inviter.getAccount());
        for (Users invitee : invitees) {
            result.add(buildInviteeItem(invitee, inviter.getAccount()));
        }
        return result;
    }

    public List<Map<String, Object>> buildWithdrawalList(Users user) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (user == null || user.getAccount() == null) {
            return result;
        }
        for (WithdrawalRequest request : withdrawalRequestDao.findByAccountOrderByCreatedAtDesc(user.getAccount())) {
            result.add(buildWithdrawalItem(request));
        }
        return result;
    }

    private Users createSyntheticInvitee(Users inviter, Integer memberType, String memberStatus, LocalDateTime registeredAt) {
        Users invitee = new Users();
        invitee.setId(UUID.randomUUID().toString());
        invitee.setAccount(generateUniqueAccount());
        invitee.setUserName(generateUniqueDisplayUserName());
        invitee.setPassword(generateRandomString(RANDOM_ACCOUNT_CHARS, 12));
        invitee.setRole("0");
        invitee.setInviteCode(generateUniqueInviteCode());
        invitee.setInvitedBy(inviter.getAccount());
        invitee.setMemberType(memberType);
        invitee.setMemberStatus(memberStatus);
        invitee.setMemberExpiredAt(null);
        invitee.setNCoinBalance(0);
        invitee.setNCoinFrozen(0);
        invitee.setCreateTime(registeredAt);
        invitee.setRegisteredDate(registeredAt);
        invitee.setOperatingTime(registeredAt);
        return invitee;
    }

    private void saveSyntheticReward(Users inviter,
                                     Users invitee,
                                     Integer memberType,
                                     Integer rewardAmount,
                                     String memberStatus,
                                     LocalDateTime rewardedAt) {
        InviteRewardRecord record = new InviteRewardRecord();
        record.setInviterAccount(inviter.getAccount());
        record.setInviteeAccount(invitee.getAccount());
        record.setMemberType(memberType);
        record.setRewardAmount(rewardAmount);
        record.setMemberStatus(memberStatus);
        record.setRewardedAt(rewardedAt);
        inviteRewardRecordDao.save(record);
    }

    private Map<String, Object> buildGeneratedInviteDataItem(Users invitee, int rewardAmount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("account", invitee.getAccount());
        item.put("userName", invitee.getUserName());
        item.put("memberType", normalizeMemberType(invitee.getMemberType()));
        item.put("memberTypeName", getMemberTypeName(invitee.getMemberType()));
        item.put("memberStatus", invitee.getMemberStatus());
        item.put("registeredDate", invitee.getRegisteredDate());
        item.put("rewardAmount", rewardAmount);
        return item;
    }

    private LocalDateTime buildSyntheticRegisteredAt(int sequence, int totalCount) {
        int daysBack = Math.max(1, totalCount - sequence);
        return LocalDateTime.now()
                .minusDays(daysBack)
                .withHour(8 + RANDOM.nextInt(15))
                .withMinute(RANDOM.nextInt(60))
                .withSecond(RANDOM.nextInt(60))
                .withNano(0);
    }

    private String generateUniqueAccount() {
        String account = generateRandomString(RANDOM_ACCOUNT_CHARS, 9);
        while (usersDao.existsByAccount(account)) {
            account = generateRandomString(RANDOM_ACCOUNT_CHARS, 9);
        }
        return account;
    }

    private String generateUniqueDisplayUserName() {
        String userName = "用户_" + generateRandomString(RANDOM_USER_NAME_CHARS, 6);
        while (usersDao.existsByUserName(userName)) {
            userName = "用户_" + generateRandomString(RANDOM_USER_NAME_CHARS, 6);
        }
        return userName;
    }

    private String generateUniqueInviteCode() {
        String inviteCode = generateRandomString(RANDOM_INVITE_CODE_CHARS, 8);
        while (usersDao.existsByInviteCode(inviteCode)) {
            inviteCode = generateRandomString(RANDOM_INVITE_CODE_CHARS, 8);
        }
        return inviteCode;
    }

    private String generateRandomString(String chars, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return value.toString();
    }

    private static class AdminInviteSpec {
        private final Integer memberType;
        private final String memberStatus;
        private final int rewardAmount;

        private AdminInviteSpec(Integer memberType, String memberStatus, int rewardAmount) {
            this.memberType = memberType;
            this.memberStatus = memberStatus;
            this.rewardAmount = rewardAmount;
        }
    }

    private Map<String, Object> buildInviteeItem(Users invitee, String inviterAccount) {
        Map<String, Object> item = new HashMap<>();
        if (invitee == null) {
            return item;
        }

        Integer memberType = normalizeMemberType(invitee.getMemberType());
        List<InviteRewardRecord> rewardRecords = inviteRewardRecordDao.findByInviteeAccountOrderByRewardedAtDesc(invitee.getAccount())
                .stream()
                .filter(record -> inviterAccount == null || inviterAccount.equals(record.getInviterAccount()))
                .collect(Collectors.toList());
        List<Map<String, Object>> rewardItems = new ArrayList<>();
        long rewardAmountTotal = 0;
        for (InviteRewardRecord record : rewardRecords) {
            rewardItems.add(buildInviteRewardItem(record));
            rewardAmountTotal += record.getRewardAmount() != null ? record.getRewardAmount() : 0;
        }

        item.put("account", invitee.getAccount());
        item.put("userName", invitee.getUserName());
        item.put("inviteCode", invitee.getInviteCode());
        item.put("invitedBy", invitee.getInvitedBy());
        item.put("memberType", memberType != null ? memberType : 0);
        item.put("memberTypeName", getMemberTypeName(memberType));
        item.put("memberStatus", resolveMemberStatus(invitee));
        item.put("memberExpireText", resolveMemberExpireText(invitee));
        item.put("registeredDate", invitee.getRegisteredDate());
        item.put("createTime", invitee.getCreateTime());
        item.put("lastLoginTime", invitee.getLastLoginTime());
        item.put("rewardedMemberTypes", rewardRecords.stream()
                .map(InviteRewardRecord::getMemberType)
                .collect(Collectors.toList()));
        item.put("rewardRecords", rewardItems);
        item.put("rewardAmountTotal", rewardAmountTotal);
        return item;
    }

    private Map<String, Object> buildInviteRewardItem(InviteRewardRecord record) {
        Map<String, Object> item = new HashMap<>();
        if (record == null) {
            return item;
        }
        item.put("memberType", record.getMemberType());
        item.put("memberTypeName", getMemberTypeName(record.getMemberType()));
        item.put("rewardAmount", record.getRewardAmount());
        item.put("memberStatus", record.getMemberStatus());
        item.put("rewardedAt", record.getRewardedAt());
        return item;
    }

    private Map<String, Object> buildWithdrawalItem(WithdrawalRequest request) {
        Map<String, Object> item = new HashMap<>();
        if (request == null) {
            return item;
        }
        item.put("id", request.getId());
        item.put("account", request.getAccount());
        item.put("userName", request.getUserName());
        item.put("alipayAccount", request.getAlipayAccount());
        item.put("nCoinAmount", request.getNCoinAmount());
        item.put("cashAmount", request.getCashAmount() != null ? request.getCashAmount().toPlainString() : "0.00");
        item.put("status", request.getStatus());
        item.put("statusText", resolveWithdrawalStatusText(request.getStatus()));
        item.put("adminAccount", request.getAdminAccount());
        item.put("remark", request.getRemark());
        item.put("createdAt", request.getCreatedAt());
        item.put("requestAt", request.getRequestAt());
        item.put("processedAt", request.getProcessedAt());
        item.put("frozenBeforeProcess", request.getFrozenBeforeProcess());
        item.put("balanceBeforeProcess", request.getBalanceBeforeProcess());
        return item;
    }

    private WithdrawalRequest getPendingWithdrawalOrFail(Long withdrawalId, Map<String, Object> result) {
        if (withdrawalId == null) {
            result.put("success", false);
            result.put("message", "提现ID不能为空");
            return null;
        }

        WithdrawalRequest request = withdrawalRequestDao.findById(withdrawalId).orElse(null);
        if (request == null) {
            result.put("success", false);
            result.put("message", "提现申请不存在");
            return null;
        }
        if (request.getStatus() == null || request.getStatus() != WithdrawalRequest.STATUS_PENDING) {
            result.put("success", false);
            result.put("message", "该提现申请已处理");
            return null;
        }
        return request;
    }

    public boolean hasPromotionPermission(Users user) {
        if (user == null || user.getMemberType() == null) {
            return false;
        }
        return user.getMemberType() == CardKey.TYPE_PERMANENT || user.getMemberType() == CardKey.TYPE_AGENT;
    }

    public boolean isAgent(Users user) {
        return user != null && user.getMemberType() != null && user.getMemberType() == CardKey.TYPE_AGENT;
    }

    public Map<String, Integer> buildRewardPolicy(Users inviter) {
        Map<String, Integer> rates = new LinkedHashMap<>();
        if (isAgent(inviter)) {
            rates.put("yearly", 69);
            rates.put("permanent", 99);
        } else if (hasPromotionPermission(inviter)) {
            rates.put("yearly", 25);
            rates.put("permanent", 40);
        }
        return rates;
    }

    private Integer getRewardAmount(Users inviter, Integer memberType) {
        if (memberType == null) {
            return null;
        }
        Map<String, Integer> rates = buildRewardPolicy(inviter);
        switch (memberType) {
            case CardKey.TYPE_YEARLY:
                return rates.get("yearly");
            case CardKey.TYPE_PERMANENT:
                return rates.get("permanent");
            default:
                return null;
        }
    }

    private boolean shouldGrantInviteReward(Users invitee) {
        if (invitee == null) {
            return false;
        }
        Integer memberType = normalizeMemberType(invitee.getMemberType());
        if (memberType == CardKey.TYPE_PERMANENT) {
            return true;
        }
        return memberType == CardKey.TYPE_YEARLY && "active".equals(resolveMemberStatus(invitee));
    }

    private String getMemberTypeName(Integer type) {
        if (type == null) {
            return "未知会员";
        }
        switch (type) {
            case 3:
                return "年费VIP";
            case 4:
                return "永久VIP";
            case 5:
                return "霓虹代理";
            default:
                return "普通用户";
        }
    }

    private Integer normalizeMemberType(Integer type) {
        if (type == null || type == CardKey.TYPE_MONTHLY || type == CardKey.TYPE_QUARTERLY) {
            return 0;
        }
        return type;
    }

    private String resolveMemberStatus(Users user) {
        if (user == null) {
            return "none";
        }
        if (user.getMemberType() != null && (user.getMemberType() == CardKey.TYPE_PERMANENT || user.getMemberType() == CardKey.TYPE_AGENT)) {
            return "permanent";
        }
        if (user.getMemberType() == null || user.getMemberType() != CardKey.TYPE_YEARLY) {
            return "none";
        }
        if (user.getMemberExpiredAt() != null && user.getMemberExpiredAt().isBefore(LocalDateTime.now())) {
            return "expired";
        }
        return "active";
    }

    private String resolveMemberExpireText(Users user) {
        if (user == null) {
            return "暂无";
        }
        if (user.getMemberType() != null && user.getMemberType() == CardKey.TYPE_AGENT) {
            return "霓虹代理 · 永久有效";
        }
        if (user.getMemberType() != null && user.getMemberType() == CardKey.TYPE_PERMANENT) {
            return "永久有效";
        }
        if (user.getMemberType() == null || user.getMemberType() != CardKey.TYPE_YEARLY) {
            return "非会员";
        }
        if (user.getMemberExpiredAt() == null) {
            return "暂无到期时间";
        }
        return user.getMemberExpiredAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String resolveWithdrawalStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case WithdrawalRequest.STATUS_PENDING:
                return "待处理";
            case WithdrawalRequest.STATUS_PAID:
                return "已打款";
            case WithdrawalRequest.STATUS_REJECTED:
                return "已拒绝";
            default:
                return "未知";
        }
    }
}
