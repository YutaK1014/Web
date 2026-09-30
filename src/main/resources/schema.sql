-- ログインなしのローカル利用。既存のmarker・取引データは変更しません。


CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- ログインなしの試作では未指定。ログイン導入時に所有者を設定してNOT NULLに変更。
    user_id BIGINT NULL,
    item_name VARCHAR(100) NOT NULL,
    marketplace VARCHAR(50) NOT NULL,
    custom_marketplace VARCHAR(100) NULL,
    selling_price INT NOT NULL,
    fee_rate DECIMAL(5,2) NOT NULL,
    selling_fee INT NOT NULL,
    shipping_cost INT NOT NULL,
    purchase_price INT NULL DEFAULT 0,
    profit INT NOT NULL,
    sold_date DATE NOT NULL,
    image_path VARCHAR(255) NULL,
    memo TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_transactions_sold_date (sold_date, id)
);

CREATE TABLE IF NOT EXISTS marketplace_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    marketplace_name VARCHAR(50) NOT NULL UNIQUE,
    fee_rate DECIMAL(5,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(50) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS purchases (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_name VARCHAR(100) NOT NULL,
    purchased_date DATE NOT NULL,
    amount INT NOT NULL,
    store VARCHAR(100) NOT NULL DEFAULT '',
    memo TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_purchases_date (purchased_date, id)
);

CREATE TABLE IF NOT EXISTS transaction_tags (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    tag VARCHAR(30) NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS purchase_tags (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    tag VARCHAR(30) NOT NULL,
    FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE
);
