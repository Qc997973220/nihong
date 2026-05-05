-- 创建登录尝试记录表
CREATE TABLE IF NOT EXISTS login_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account VARCHAR(50) NOT NULL,
    failed_count INT DEFAULT 0,
    locked_until DATETIME NULL,
    attempt_time DATETIME NOT NULL,
    ip_address VARCHAR(50),
    INDEX idx_attempt_account (account),
    INDEX idx_attempt_time (attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 为users表添加缺失的字段（如果不存在）
ALTER TABLE users ADD COLUMN IF NOT EXISTS token VARCHAR(36) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_expired_at DATETIME NULL;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_users_token ON users(token);
CREATE INDEX IF NOT EXISTS idx_users_token_version ON users(token_version);