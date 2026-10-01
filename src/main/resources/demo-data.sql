-- Only loaded by the demo profile into a fresh in-memory database.
-- Rates are examples for trying the app, not marketplace pricing guidance.
INSERT INTO marketplace_settings (marketplace_name, fee_rate) VALUES
('メルカリ', 10.00), ('ラクマ', 10.00), ('Yahoo!フリマ', 5.00);
INSERT INTO app_settings (setting_key, setting_value) VALUES
('fee_rounding', 'DOWN'), ('monthly_goal', '10000');

INSERT INTO transactions
(item_name, marketplace, custom_marketplace, selling_price, fee_rate, selling_fee, shipping_cost, purchase_price, profit, sold_date, memo)
VALUES
('デモ：読み終えた本', 'メルカリ', NULL, 1800, 10.00, 180, 210, 500, 910, CURRENT_DATE, '編集やタグ検索をお試しください。'),
('デモ：スニーカー', 'Yahoo!フリマ', NULL, 6000, 5.00, 300, 750, 2500, 2450, CURRENT_DATE, '架空の売却データです。'),
('デモ：マグカップ', 'ラクマ', NULL, 800, 10.00, 80, 750, 500, -530, DATEADD('MONTH', -1, CURRENT_DATE), '赤字の表示例です。');
INSERT INTO transaction_tags (transaction_id, tag) VALUES
(1, '本'), (1, 'デモ'), (2, '衣類'), (2, 'デモ'), (3, 'デモ');

INSERT INTO purchases (item_name, purchased_date, amount, store, memo) VALUES
('デモ：ノート', CURRENT_DATE, 300, '文房具店', '購入履歴は売却の収支集計とは独立しています。');
INSERT INTO purchase_tags (purchase_id, tag) VALUES (1, '文房具'), (1, 'デモ');
