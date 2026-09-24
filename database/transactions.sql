-- ローカル試作の取引一覧用。既存のmarkerテーブルは変更しません。
USE cashflow;

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
