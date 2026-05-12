package com.neon.controller;

import com.neon.pojo.Users;
import com.neon.service.AuthService;
import com.neon.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/wallet")
public class WalletController {

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @GetMapping("/summary")
    public Map<String, Object> getSummary(@RequestHeader(value = "Authorization", required = false) String token) {
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            return authError("请先登录");
        }
        Map<String, Object> result = walletService.buildWalletSummary(user);
        result.put("success", true);
        return result;
    }

    @GetMapping("/invites")
    public Map<String, Object> getInvites(@RequestHeader(value = "Authorization", required = false) String token) {
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            return authError("请先登录");
        }
        if (!walletService.hasPromotionPermission(user)) {
            return authError("权限不足,永久VIP专享");
        }
        Map<String, Object> result = walletService.buildInviteSummary(user);
        result.put("success", true);
        return result;
    }

    @GetMapping("/withdrawals/me")
    public Map<String, Object> getMyWithdrawals(@RequestHeader(value = "Authorization", required = false) String token) {
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            return authError("请先登录");
        }
        Map<String, Object> result = walletService.buildWithdrawalSummary(user);
        result.put("success", true);
        return result;
    }

    @PostMapping("/withdrawals")
    public Map<String, Object> createWithdrawal(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, String> request) {
        Users user = authService.getAuthenticatedUser(token);
        if (user == null) {
            return authError("请先登录");
        }
        String alipayAccount = request != null ? request.get("alipayAccount") : null;
        return walletService.createWithdrawalRequest(user, alipayAccount);
    }

    @GetMapping("/admin/withdrawals")
    public Map<String, Object> getAdminWithdrawals(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "0") int status) {
        Users admin = authService.getAdminUser(token);
        if (admin == null) {
            return adminError();
        }
        Map<String, Object> result = walletService.buildAdminWithdrawalPage(page, pageSize, status);
        result.put("success", true);
        return result;
    }

    @PostMapping("/admin/withdrawals/{id}/approve")
    public Map<String, Object> approveWithdrawal(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        Users admin = authService.getAdminUser(token);
        if (admin == null) {
            return adminError();
        }
        return walletService.approveWithdrawal(admin, id);
    }

    @PostMapping("/admin/withdrawals/{id}/reject")
    public Map<String, Object> rejectWithdrawal(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        Users admin = authService.getAdminUser(token);
        if (admin == null) {
            return adminError();
        }
        String remark = request != null ? request.get("remark") : null;
        return walletService.rejectWithdrawal(admin, id, remark);
    }

    private Map<String, Object> authError(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("status", 0);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> adminError() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("status", 0);
        result.put("message", "管理员权限不足");
        return result;
    }
}
