# ER設計

2026-10-05更新。MySQLの個人利用とH2のdemo構成で共通の論理構造を使用する。公開用構成は準備済みだが、実デプロイ・公開URL発行は未完了。demoでは同じデータを全閲覧者が操作し、ユーザーごとの分離は行わない。初期データ・保存期間は02_database_design.mdを参照。

画像の実体はER図の管理対象外のファイル領域にあり、transactions.image_pathだけをDBへ保存する。demoのDB初期化は画像の一括削除と連動しない。

```mermaid
erDiagram
    USERS |o--o{ TRANSACTIONS : legacy_reference
    TRANSACTIONS ||--o{ TRANSACTION_TAGS : tagged
    PURCHASES ||--o{ PURCHASE_TAGS : tagged

    TRANSACTION_TAGS {
        BIGINT id PK
        BIGINT transaction_id FK
        VARCHAR tag
    }

    PURCHASE_TAGS {
        BIGINT id PK
        BIGINT purchase_id FK
        VARCHAR tag
    }

    USERS {
        BIGINT id PK
        VARCHAR username
        VARCHAR email
        VARCHAR password
        DATETIME created_at
        DATETIME updated_at
    }

    TRANSACTIONS {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR item_name
        VARCHAR marketplace
        VARCHAR custom_marketplace
        INT selling_price
        DECIMAL fee_rate
        INT selling_fee
        INT shipping_cost
        INT purchase_price
        INT profit
        DATE sold_date
        VARCHAR image_path
        TEXT memo
        DATETIME created_at
        DATETIME updated_at
    }

    MARKETPLACE_SETTINGS {
        BIGINT id PK
        VARCHAR marketplace_name
        DECIMAL fee_rate
        DATETIME created_at
        DATETIME updated_at
    }

    APP_SETTINGS {
        VARCHAR setting_key PK
        VARCHAR setting_value
    }

    PURCHASES {
        BIGINT id PK
        VARCHAR item_name
        DATE purchased_date
        INT amount
        VARCHAR store
        TEXT memo
        DATETIME created_at
    }
```

## リレーション
- USERSとTRANSACTIONS.user_idは既存DB互換用。ユーザー情報管理には使用しない。
- 取引からユーザーへの参照は任意（0または1）。新規登録ではuser_idをNULLとする。
- marketplace_settings は手数料率参照用
- app_settingsは端数処理と月間利益目標を保存する。
- purchasesはホーム画面の購入履歴を保存する独立したテーブル。transactionsとの関連・自動反映は行わない。
- ログイン・所有者確認・ユーザー別データ分離は行わない。
