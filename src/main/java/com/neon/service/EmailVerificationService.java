package com.neon.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailVerificationService {

    private static final int CODE_TTL_MINUTES = 5;
    private static final int RESEND_INTERVAL_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int RESET_TOKEN_TTL_MINUTES = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, VerificationCodeRecord> codeStore = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> sendCooldownStore = new ConcurrentHashMap<>();
    private final Map<String, PasswordResetTokenRecord> passwordResetTokenStore = new ConcurrentHashMap<>();

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.from-name:Neon}")
    private String fromName;

    @Value("${app.mail.logo-url:}")
    private String logoUrl;

    public SendCodeResult sendBindEmailCode(String account, String email) {
        String normalizedAccount = normalizeKeyPart(account);
        String normalizedEmail = normalizeEmail(email);
        return sendCode(
                "bind:" + normalizedAccount,
                normalizedEmail,
                "绑定邮箱验证码",
                "您正在绑定账号邮箱，本次验证码用于确认邮箱归属。",
                new String[]{
                        "cooldown:bind:account:" + normalizedAccount,
                        "cooldown:bind:email:" + normalizedEmail
                }
        );
    }

    public boolean verifyBindEmailCode(String account, String email, String code) {
        return verifyCode("bind:" + normalizeKeyPart(account), email, code);
    }

    public SendCodeResult sendPasswordResetCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        return sendCode(
                "password-reset",
                normalizedEmail,
                "重置密码验证码",
                "您正在重置账号密码，本次验证码用于确认邮箱归属。",
                new String[]{
                        "cooldown:password-reset:email:" + normalizedEmail
                }
        );
    }

    public boolean verifyPasswordResetCode(String email, String code) {
        return verifyCode("password-reset", email, code);
    }

    public PasswordResetVerifyResult verifyPasswordResetCodeAndCreateToken(String email, String code) {
        if (!verifyCode("password-reset", email, code)) {
            return PasswordResetVerifyResult.fail("验证码错误或已过期，请重新获取");
        }

        String token = UUID.randomUUID().toString();
        passwordResetTokenStore.put(
                token,
                new PasswordResetTokenRecord(normalizeEmail(email), LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES))
        );
        return PasswordResetVerifyResult.success(token, RESET_TOKEN_TTL_MINUTES * 60);
    }

    public boolean consumePasswordResetToken(String email, String token) {
        cleanupExpiredResetTokens();
        if (!StringUtils.hasText(token)) {
            return false;
        }

        PasswordResetTokenRecord record = passwordResetTokenStore.remove(token.trim());
        if (record == null || record.getExpiredAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return record.getEmail().equals(normalizeEmail(email));
    }

    private SendCodeResult sendCode(String purpose, String email, String subject, String description, String[] extraCooldownKeys) {
        cleanupExpiredCodes();
        cleanupExpiredCooldowns();
        cleanupExpiredResetTokens();

        String normalizedEmail = normalizeEmail(email);
        String key = buildKey(purpose, normalizedEmail);
        LocalDateTime now = LocalDateTime.now();

        Set<String> cooldownKeys = new LinkedHashSet<>();
        cooldownKeys.add(key);
        if (extraCooldownKeys != null) {
            cooldownKeys.addAll(Arrays.asList(extraCooldownKeys));
        }

        for (String cooldownKey : cooldownKeys) {
            LocalDateTime lastSentAt = sendCooldownStore.get(cooldownKey);
            if (lastSentAt != null && lastSentAt.plusSeconds(RESEND_INTERVAL_SECONDS).isAfter(now)) {
                long waitSeconds = Duration.between(now, lastSentAt.plusSeconds(RESEND_INTERVAL_SECONDS)).getSeconds();
                long retryAfterSeconds = Math.max(1, waitSeconds);
                return SendCodeResult.fail("验证码发送过于频繁，请 " + retryAfterSeconds + " 秒后再试", retryAfterSeconds);
            }
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        try {
            sendMail(normalizedEmail, subject, description, code);
        } catch (MailAuthenticationException e) {
            e.printStackTrace();
            return SendCodeResult.fail("邮箱登录认证失败，请确认发件邮箱和QQ邮箱授权码匹配，并检查服务器已配置QQ_MAIL_AUTH_CODE");
        } catch (Exception e) {
            e.printStackTrace();
            return SendCodeResult.fail("验证码发送失败，请稍后重试");
        }

        codeStore.put(key, new VerificationCodeRecord(code, now.plusMinutes(CODE_TTL_MINUTES), now));
        for (String cooldownKey : cooldownKeys) {
            sendCooldownStore.put(cooldownKey, now);
        }
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

    private void sendMail(String to, String subject, String description, String code) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        if (StringUtils.hasText(from)) {
            if (StringUtils.hasText(fromName)) {
                helper.setFrom(from, fromName);
            } else {
                helper.setFrom(from);
            }
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(buildPlainText(description, code), buildHtmlText(description, code));
        mailSender.send(message);
    }

    private String buildPlainText(String description, String code) {
        return "霓虹之都.中国  NEON\n\n" +
                description + "\n\n" +
                "验证码：" + code + "\n" +
                "有效期：5分钟\n\n" +
                "如果不是您本人操作，请忽略本邮件。\n" +
                fromName;
    }

    private String buildHtmlText(String description, String code) {
        String logoHtml = StringUtils.hasText(logoUrl)
                ? "<img src=\"" + escapeHtml(logoUrl) + "\" alt=\"NEON\" style=\"width:42px;height:42px;border-radius:12px;display:block;object-fit:cover;\">"
                : "<div style=\"width:42px;height:42px;border-radius:12px;background:linear-gradient(135deg,#78f7ff,#6affc3);color:#07101f;font-weight:900;font-size:13px;line-height:42px;text-align:center;letter-spacing:0;\">NEON</div>";

        return "<!doctype html>" +
                "<html><body style=\"margin:0;padding:0;background:#eef4f8;font-family:Arial,'Microsoft YaHei',sans-serif;color:#152232;\">" +
                "<div style=\"max-width:560px;margin:0 auto;padding:28px 16px;\">" +
                "<div style=\"overflow:hidden;border:1px solid #d6e4ec;border-radius:16px;background:#ffffff;box-shadow:0 18px 48px rgba(18,37,61,0.12);\">" +
                "<div style=\"padding:24px 26px;background:linear-gradient(135deg,#06111f,#0a2036 58%,#09251c);color:#effcff;\">" +
                "<div style=\"display:flex;align-items:center;gap:12px;\">" +
                logoHtml +
                "<div>" +
                "<div style=\"font-size:20px;font-weight:900;letter-spacing:0;\">霓虹之都.中国&nbsp;&nbsp;NEON</div>" +
                "<div style=\"margin-top:4px;font-size:12px;color:#aeeeff;letter-spacing:0;\">Official Verification Code</div>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "<div style=\"padding:28px 26px 26px;\">" +
                "<div style=\"font-size:15px;line-height:1.8;color:#405168;\">" + escapeHtml(description) + "</div>" +
                "<div style=\"margin:22px 0;padding:18px;border-radius:14px;background:#f4fbfc;border:1px solid #cce8ee;text-align:center;\">" +
                "<div style=\"font-size:12px;color:#6b8095;font-weight:700;\">验证码</div>" +
                "<div style=\"margin-top:8px;font-size:34px;line-height:1;font-weight:900;letter-spacing:8px;color:#07101f;font-family:Arial,sans-serif;\">" + escapeHtml(code) + "</div>" +
                "</div>" +
                "<div style=\"font-size:13px;line-height:1.7;color:#66788d;\">" +
                "验证码有效期为 <b>5分钟</b>。如果不是您本人操作，请忽略本邮件。" +
                "</div>" +
                "</div>" +
                "<div style=\"padding:14px 26px;border-top:1px solid #e5eef3;background:#f8fbfd;color:#8190a2;font-size:12px;\">" +
                escapeHtml(fromName) +
                "</div>" +
                "</div>" +
                "</div>" +
                "</body></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        codeStore.entrySet().removeIf(entry -> entry.getValue().getExpiredAt().isBefore(now));
    }

    private void cleanupExpiredCooldowns() {
        LocalDateTime now = LocalDateTime.now();
        sendCooldownStore.entrySet().removeIf(entry ->
                entry.getValue().plusSeconds(RESEND_INTERVAL_SECONDS).isBefore(now));
    }

    private void cleanupExpiredResetTokens() {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenStore.entrySet().removeIf(entry -> entry.getValue().getExpiredAt().isBefore(now));
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

    private static class PasswordResetTokenRecord {
        private final String email;
        private final LocalDateTime expiredAt;

        private PasswordResetTokenRecord(String email, LocalDateTime expiredAt) {
            this.email = email;
            this.expiredAt = expiredAt;
        }

        private String getEmail() {
            return email;
        }

        private LocalDateTime getExpiredAt() {
            return expiredAt;
        }
    }

    public static class SendCodeResult {
        private final boolean success;
        private final String message;
        private final long retryAfterSeconds;

        private SendCodeResult(boolean success, String message, long retryAfterSeconds) {
            this.success = success;
            this.message = message;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        private static SendCodeResult success(String message) {
            return new SendCodeResult(true, message, 0);
        }

        private static SendCodeResult fail(String message) {
            return fail(message, 0);
        }

        private static SendCodeResult fail(String message, long retryAfterSeconds) {
            return new SendCodeResult(false, message, retryAfterSeconds);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static class PasswordResetVerifyResult {
        private final boolean success;
        private final String message;
        private final String resetToken;
        private final long expiresInSeconds;

        private PasswordResetVerifyResult(boolean success, String message, String resetToken, long expiresInSeconds) {
            this.success = success;
            this.message = message;
            this.resetToken = resetToken;
            this.expiresInSeconds = expiresInSeconds;
        }

        private static PasswordResetVerifyResult success(String resetToken, long expiresInSeconds) {
            return new PasswordResetVerifyResult(true, "验证码验证成功，请设置新密码", resetToken, expiresInSeconds);
        }

        private static PasswordResetVerifyResult fail(String message) {
            return new PasswordResetVerifyResult(false, message, null, 0);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getResetToken() {
            return resetToken;
        }

        public long getExpiresInSeconds() {
            return expiresInSeconds;
        }
    }
}
