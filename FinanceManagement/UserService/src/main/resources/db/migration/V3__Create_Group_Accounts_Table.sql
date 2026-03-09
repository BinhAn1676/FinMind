CREATE TABLE IF NOT EXISTS group_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    label VARCHAR(150),
    bank_brand_name VARCHAR(150),
    account_number VARCHAR(120),
    currency VARCHAR(50),
    bank_code VARCHAR(50),
    accumulated VARCHAR(120),
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_accounts_group FOREIGN KEY (group_id) REFERENCES user_groups (id) ON DELETE CASCADE,
    CONSTRAINT uk_group_accounts UNIQUE (group_id, account_id)
);

CREATE INDEX idx_group_accounts_group ON group_accounts (group_id);












