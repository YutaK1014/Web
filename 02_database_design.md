# データベース設計書

## users

| カラム | 型 | NULL | 制約 | 説明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK, AUTO_INCREMENT | ユーザーID |
| username | VARCHAR(50) | NO |  | ユーザー名 |
| email | VARCHAR(255) | NO | UNIQUE | メールアドレス |
| password | VARCHAR(255) | NO |  | ハッシュ済みパスワード |
| created_at | DATETIME | NO |  | 登録日時 |
| updated_at | DATETIME | NO |  | 更新日時 |

## transactions

| カラム | 型 | NULL | 制約 | 説明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK, AUTO_INCREMENT | 取引ID |
| user_id | BIGINT | NO | FK | ユーザーID |
| item_name | VARCHAR(100) | NO |  | 商品名 |
| marketplace | VARCHAR(50) | NO |  | フリマサイト |
| custom_marketplace | VARCHAR(100) | YES |  | その他サイト名 |
| selling_price | INT | NO |  | 販売価格 |
| fee_rate | DECIMAL(5,2) | NO |  | 手数料率 |
| selling_fee | INT | NO |  | 販売手数料 |
| shipping_cost | INT | NO |  | 送料 |
| purchase_price | INT | YES |  | 仕入価格 |
| profit | INT | NO |  | 利益 |
| sold_date | DATE | NO |  | 販売日 |
| image_path | VARCHAR(255) | YES |  | 画像保存先 |
| memo | TEXT | YES |  | メモ |
| created_at | DATETIME | NO |  | 登録日時 |
| updated_at | DATETIME | NO |  | 更新日時 |

## marketplace_settings

| カラム | 型 | NULL | 制約 | 説明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK, AUTO_INCREMENT | 設定ID |
| marketplace_name | VARCHAR(50) | NO | UNIQUE | サイト名 |
| fee_rate | DECIMAL(5,2) | NO |  | 手数料率 |
| created_at | DATETIME | NO |  | 登録日時 |
| updated_at | DATETIME | NO |  | 更新日時 |

## データルール
- 金額は原則0円以上
- purchase_priceがNULLの場合、計算時は0円扱い
- marketplaceが「その他」の場合、custom_marketplaceを必須とする
- users.id と transactions.user_id は1対多
