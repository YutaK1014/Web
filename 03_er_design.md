# ER設計

```mermaid
erDiagram
    USERS |o--o{ TRANSACTIONS : legacy_reference

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
```

## リレーション
- USERSとTRANSACTIONS.user_idは既存DB互換用。ユーザー情報管理には使用しない。
- 取引からユーザーへの参照は任意（0または1）。新規登録ではuser_idをNULLとする。
- marketplace_settings は手数料率参照用
- app_settingsは端数処理と月間利益目標を保存する。
- ログイン・所有者確認・ユーザー別データ分離は行わない。
