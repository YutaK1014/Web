# データベース設計書

2026-10-06更新。正本の`src/main/resources/schema.sql`とRepositoryの保存・取得処理に対応する。

個人利用はMySQL 8.0、公開デモはH2のMySQL互換モードを使用する。同一のschema.sqlとMyBatis Mapperを使う。ログイン・ユーザー情報管理は行わず、公開デモのデータは全閲覧者で共有する。

## 実行環境と初期化

| 環境 | 保存先 | 初期データ・寿命 |
| --- | --- | --- |
| 個人利用（プロファイル未指定） | MySQL / pf-db-data | 不足テーブルだけ作成。既存データ維持、初回は設定入力が必要 |
| 公開デモ（demo） | H2 / cashflow-demoメモリーDB | schema.sql後にdemo-data.sqlを実行。売却3件・購入1件・タグ・設定を作成、プロセス再起動で初期化 |
| 自動テスト | 各テスト用H2 | 個人利用MySQLとは独立 |

demo-data.sqlはdemoプロファイルだけが読む。日付は起動日とその1か月前を使い、ダッシュボードとレポートにサンプルを表示する。画像はDBに格納せず、Renderの一時ファイル領域へ保存する。H2コンソールは無効。マルチインスタンスでのデータ同期は行わない。

2026-10-05時点で公開用構成の準備・ローカル検証まで完了し、実デプロイと公開URL発行は未完了。上表の「公開デモ」はdemoプロファイルの構成を指す。

DBの初期化と画像ファイルの寿命は別である。アプリ起動時に画像を一括削除しないため、同じDockerコンテナを再起動するとDBだけが初期化され、未参照の画像が残る場合がある。画像ボリュームのない現行Composeではコンテナの削除・再作成で画像も破棄される。

## shipping_templates（送料・梱包テンプレート）

| カラム | 型 | NULL | 制約・初期値 | 説明 |
| --- | --- | --- | --- | --- |
| id | BIGINT | NO | PK, AUTO_INCREMENT | テンプレートID |
| name | VARCHAR(100) | NO | | 名前 |
| shipping_method | VARCHAR(100) | NO | | 配送方法 |
| packaging | VARCHAR(100) | NO | DEFAULT '' | 梱包内容 |
| shipping_cost | INT | NO | | 送料 |
| packaging_cost | INT | NO | | 梱包費 |

送料・梱包費および合計をアプリ側で0〜1,000,000,000円に制限する。取引との外部キーはなく、選択時の合計を既存のtransactions.shipping_costにコピーする。schema.sqlで不足テーブルを作成するため、既存取引テーブルの移行は不要。
名前の一意制約・作成日時・更新日時は設けない。一覧はID昇順。demoの初期データにテンプレートはなく、登録分は全閲覧者で共有し、DB再起動時に初期化する。

## users（既存DB互換用・アプリでは未使用）

既存データを維持するためテーブルは残す。ユーザー登録・認証・情報編集には使用しない。

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
| user_id | BIGINT | YES | FK | 互換用。アプリの新規登録ではNULL |
| item_name | VARCHAR(100) | NO |  | 商品名 |
| marketplace | VARCHAR(50) | NO |  | フリマサイト |
| custom_marketplace | VARCHAR(100) | YES |  | その他サイト名 |
| selling_price | INT | NO |  | 販売価格 |
| fee_rate | DECIMAL(5,2) | NO |  | 手数料率（10.00 = 10%） |
| selling_fee | INT | NO |  | 販売手数料 |
| shipping_cost | INT | NO |  | 送料 |
| purchase_price | INT | YES |  | 仕入価格 |
| profit | INT | NO |  | 利益 |
| sold_date | DATE | NO |  | 販売日 |
| image_path | VARCHAR(255) | YES |  | 画像保存先 |
| memo | TEXT | YES |  | メモ |
| created_at | DATETIME | NO |  | 登録日時 |
| updated_at | DATETIME | NO |  | 更新日時 |

## transaction_returns（返品）

| カラム | 型 | NULL | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| transaction_id | BIGINT | NO | PK, FK → transactions.id, ON DELETE CASCADE | 対象取引ID |
| return_cost | INT | NO | | 返品費用合計（0〜1,000,000,000円） |

transactionsの1件に対して0または1件。取引削除時はON DELETE CASCADEで削除する。返品日や返品取消状態の列は設けない。
レコードの存在が返品を表すため、費用0円も返品として識別できる。不足テーブルを起動時に作成し、既存transactionsのスキーマ・データ変更は不要。
返品登録・費用更新は取引行をロックして同一トランザクションでUPSERTする。元のtransactions.profitを含む販売情報は保持するが、表示・集計は返品費用のマイナスを使用する。

## marketplace_settings

| カラム | 型 | NULL | 制約 | 説明 |
|---|---|---|---|---|
| id | BIGINT | NO | PK, AUTO_INCREMENT | 設定ID |
| marketplace_name | VARCHAR(50) | NO | UNIQUE | サイト名 |
| fee_rate | DECIMAL(5,2) | NO |  | 手数料率 |
| created_at | DATETIME | NO |  | 登録日時 |
| updated_at | DATETIME | NO |  | 更新日時 |

## app_settings

| カラム | 型 | NULL | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| setting_key | VARCHAR(50) | NO | PK | 設定キー |
| setting_value | VARCHAR(255) | NO | | 設定値 |

- fee_rounding：DOWN（切り捨て）・HALF_UP（四捨五入）・UP（切り上げ）。
- monthly_goal：月間利益目標の整数文字列。未設定・空白は目標なし。

## データルール

### purchases（購入履歴・2026-09-30追加）

売却取引と独立したテーブル。起動時に存在しない場合のみ作成する。

| カラム | 型 | 制約・用途 |
| --- | --- | --- |
| id | BIGINT | PK、AUTO_INCREMENT |
| item_name | VARCHAR(100) | NOT NULL、商品名 |
| purchased_date | DATE | NOT NULL、購入日 |
| amount | INT | NOT NULL、購入金額 |
| store | VARCHAR(100) | NOT NULL、既定値は空文字、購入先 |
| memo | TEXT | NULL可、メモ |
| created_at | DATETIME | NOT NULL、CURRENT_TIMESTAMP |

(purchased_date, id)に一覧用インデックスを設定する。外部キーは設けない。

### transaction_tags・purchase_tags（タグ）

起動時に新しい2テーブルを作成し、既存の取引・購入テーブルの変更は不要。
各テーブルはid（BIGINT、自動採番主キー）、transaction_idまたはpurchase_id（BIGINT、NOT NULL）、tag（VARCHAR(30)、NOT NULL）を持つ。
各履歴IDはtransactions.idまたはpurchases.idへの外部キーで、ON DELETE CASCADEにより履歴削除時にタグも削除する。
タグの置換は履歴の保存と同じDBトランザクションで行う。入力の重複除去と検索の完全一致はJava側で行う。

TagFormはnull・空文字・空白のみを文字数検証前に空のタグ一覧へ変換する。空白のみの1,000文字超もタグなしとして扱い、その他の入力だけ分割前に1,000文字上限を検証する。画面はmaxlength=1000で入力を制限する。

### 共通ルール
- ダッシュボードの前月比較・カレンダーは既存取引から都度算出し、専用テーブルを設けない。返品は元のsold_dateで集計する。
- 値下げシミュレーションは既存の料率・端数処理を参照するだけで、入力・結果・その他経費・希望利益をDBに保存しない。
- 金額は原則0円以上
- purchase_priceがNULLの場合、計算時は0円扱い
- marketplaceが「その他」の場合、custom_marketplaceを必須とする
- users.idへの外部キーは互換用に残すが、取引に所有者は必須としない。
- 入力上限・必須項目は01_requirements.md「入力制限」に従いJava側で検証する。
- profitは負の値も許容し、Javaで計算して登録・更新する。表示・集計は金額列から再計算する。
- purchase_priceのDB初期値は0。created_at・updated_atはCURRENT_TIMESTAMPで初期化し、updated_atは更新時に更新する。
- transactionsには検索用の複合インデックス(sold_date, id)を設定する。
- schema.sqlは不足するテーブルを作成する。既存テーブルの変更やデータ移行は自動では行わない。
