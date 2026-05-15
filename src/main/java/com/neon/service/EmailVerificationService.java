package com.neon.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailVerificationService {

    private static final int CODE_TTL_MINUTES = 5;
    private static final int RESEND_INTERVAL_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, VerificationCodeRecord> codeStore = new ConcurrentHashMap<>();

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.from-name:Neon}")
    private String fromName;

    public SendCodeResult sendBindEmailCode(String account, String email) {
        return sendCode(
                "bind:" + normalizeKeyPart(account),
                email,
                "绑定邮箱验证码",
                "您正在绑定账号邮箱，本次验证码用于确认邮箱归属。"
        );
    }

    public boolean verifyBindEmailCode(String account, String email, String code) {
        return verifyCode("bind:" + normalizeKeyPart(account), email, code);
    }

    public SendCodeResult sendPasswordResetCode(String email) {
        return sendCode(
                "password-reset",
                email,
                "重置密码验证码",
                "您正在重置账号密码，本次验证码用于确认邮箱归属。"
        );
    }

    public boolean verifyPasswordResetCode(String email, String code) {
        return verifyCode("password-reset", email, code);
    }

    private SendCodeResult sendCode(String purpose, String email, String subject, String description) {
        cleanupExpiredCodes();

        String normalizedEmail = normalizeEmail(email);
        String key = buildKey(purpose, normalizedEmail);
        LocalDateTime now = LocalDateTime.now();
        VerificationCodeRecord existing = codeStore.get(key);
        if (existing != null && existing.getLastSentAt().plusSeconds(RESEND_INTERVAL_SECONDS).isAfter(now)) {
            long waitSeconds = Duration.between(now, existing.getLastSentAt().plusSeconds(RESEND_INTERVAL_SECONDS)).getSeconds();
            return SendCodeResult.fail("验证码发送过于频繁，请 " + Math.max(1, waitSeconds) + " 秒后再试");
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        try {
            sendMail(normalizedEmail, subject, description, code);
        } catch (Exception e) {
            e.printStackTrace();
            return SendCodeResult.fail("验证码发送失败，请检查邮箱服务配置后重试");
        }

        codeStore.put(key, new VerificationCodeRecord(code, now.plusMinutes(CODE_TTL_MINUTES), now));
        return SendCodeResult.success("验证码已发送，请在5分钟内完成验证");
    }

    private boolean verifyCode(String purpose, String email, String code) {
        cleanupExpiredCodes();

        String normalizedEmail = normalizeEmail(email);
        String key = buildKey(purpose, normalizedEmail);
        VerificationCodeRecord record = codeStore.get(key);
        if (record == null) {
            return false;
        }
        if (record.getExpiredAt().isBefore(LocalDateTime.now())) {
            codeStore.remove(key);
            return false;
        }
        if (!record.getCode().equals(code != null ? code.trim() : "")) {
            if (record.recordFailedAttempt() >= MAX_VERIFY_ATTEMPTS) {
                codeStore.remove(key);
            }
            return false;
        }
        codeStore.remove(key);
        return true;
    }

    private void sendMail(String to, String subject, String description, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(from)) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(
                description + "\n\n" +
                "验证码：" + code + "\n" +
                "有效期：5分钟\n\n" +
                "如果不是您本人操作，请忽略本邮件。\n" +
                fromName
        );
        mailSender.send(message);
    }

    private void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        codeStore.entrySet().removeIf(entry -> entry.getValue().getExpiredAt().isBefore(now));
    }

    private String buildKey(String purpose, String email) {
        return purpose + ":" + normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static class VerificationCodeRecord {
        private final String code;
        private final LocalDateTime expiredAt;
        private final LocalDateTime lastSentAt;
        private int failedAttempts;

        private VerificationCodeRecord(String code, LocalDateTime expiredAt, LocalDateTime lastSentAt) {
            this.code = code;
            this.expiredAt = expiredAt;
            this.lastSentAt = lastSentAt;
        }

        private String getCode() {
            return code;
        }

        private LocalDateTime getExpiredAt() {
            return expiredAt;
        }

        private LocalDateTime getLastSentAt() {
            return lastSentAt;
        }

        private synchronized int recordFailedAttempt() {
            failedAttempts += 1;
            return failedAttempts;
        }
    }

    public static class SendCodeResult {
        private final boolean success;
        private final String message;

        private SendCodeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        private static SendCodeResult success(String message) {
            return new SendCodeResult(true, message);
        }

        private static SendCodeResult fail(String message) {
            return new SendCodeResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
