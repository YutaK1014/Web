# Java / Spring Boot設計

2026-10-01更新。現在のパッケージ・クラス構成と公開デモの起動方式を示す。

## 構成

```text
src/main/java/com/example/cashflow/
├─ CashflowApplication.java
├─ controller/
│  ├─ PageController.java
│  ├─ TransactionController.java
│  ├─ HealthController.java
│  ├─ ApplicationExceptionHandler.java
│  └─ UploadExceptionHandler.java
├─ service/
│  ├─ TransactionService.java
│  ├─ MarketplaceService.java
│  ├─ ReportService.java
│  ├─ PurchaseService.java
│  └─ PhotoStorage.java
├─ repository/
│  ├─ TransactionRepository.java
│  ├─ PurchaseRepository.java
│  ├─ TagRepository.java
│  └─ MarketplaceSettingRepository.java
├─ entity/
│  ├─ Transaction.java
│  ├─ Purchase.java
│  └─ MarketplaceSetting.java
├─ dto/
│  ├─ TransactionForm.java
│  ├─ TransactionFilter.java
│  ├─ PurchaseForm.java
│  ├─ PurchaseFilter.java
│  ├─ DateRangeFilter.java
│  ├─ TagForm.java
│  └─ SettingsForm.java
└─ config/
   └─ PhotoConfiguration.java

src/main/resources/
├─ templates/
├─ static/
│  ├─ css/transactions.css
│  └─ js/
│     ├─ transaction-form.js
│     └─ theme.js
├─ schema.sql
├─ demo-data.sql
├─ application-demo.properties
└─ application.properties
```

## Controller

| クラス | 担当 |
| --- | --- |
| PageController | トップ・ダッシュボード・レポート・設定の表示、設定保存、購入登録・検索・購入タグ編集 |
| TransactionController | 取引一覧・登録・編集・削除確認・削除、CSV出力 |
| HealthController | GET /healthz。DB疎通成功時200 / UP、失敗時503 / DOWNのJSON応答 |
| ApplicationExceptionHandler | DB障害・URLパラメーターの型不正に対するエラー表示 |
| UploadExceptionHandler | アップロード容量超過時の案内と登録画面へのリダイレクト |

## Service・Entity

| クラス | 主なメソッド・責務 |
| --- | --- |
| TransactionService | search・get・save・delete。入力検証、保存・更新の共通処理、DBと画像の整合性管理 |
| MarketplaceService | rates・rate・rounding・calculateFee・settings・save・monthlyGoal。料率・端数処理・利益目標の管理 |
| ReportService | all・summarize・monthly・yearly・byMarketplace・chart。集計とグラフ表示用データの生成 |
| PhotoStorage | save・delete。画像内容検証、PNG変換、ファイル保存・削除 |
| PurchaseService | all・search・get・save・updateTags。購入入力検証、期間・タグ検索、タグの保存 |
| Purchase | 購入商品名・購入日・金額・購入先・メモ・タグ |
| Transaction | 取引情報。getProfitで利益を計算、getPlatformNameで表示サイト名、getPhotoUrlで有効な画像パスを取得 |
| MarketplaceSetting | サイト別料率の情報 |

手数料はMarketplaceService.calculateFee、利益はTransaction.getProfitで計算する。
TransactionService.saveは新規登録と編集を共通化し、IDの有無で処理を切り替える。

## Repository

購入履歴の追加構成：PageControllerがホーム表示とPOST /purchasesを担当し、
PurchaseServiceがPurchaseFormの検証とPurchaseへの変換・保存を行う。
PurchaseRepositoryのfindAll・findById・insertでpurchasesテーブルを操作する。
PurchaseHistoryTestsで登録・表示順・入力検証・HTMLエスケープ・収支集計の独立性を確認する。

MyBatisのMapperとSQLアノテーションでDBを操作する。

- TransactionRepository：search・findById・countImageReferences・insert・update・delete。
- MarketplaceSettingRepository：findAll・findByMarketplaceName・insert・update、getOption・insertOption・updateOption。
- ユーザー情報用のEntity・Repository・Service・Controllerは設けない。
- usersテーブルとuser_id列はDB互換用で、アプリのユーザー管理には使用しない。

## DTO・設定

- DateRangeFilter：期間プリセット・開始日・終了日の共通検証と日付範囲計算。リクエスト単位で基準日を固定する。
- PurchaseFilter：DateRangeFilterを継承し、購入履歴のタグ条件も検証する。TransactionFilterも同じ期間処理を利用する。

- TagForm：共通タグ入力の分割・前後空白除去・重複除去と上限検証。購入タグ編集の入力DTOも兼ねる。
- TagRepository：購入・売却のタグ取得・削除・追加。各Serviceから履歴保存と同じトランザクション内で呼ぶ。
- TransactionFilter.tag：売却検索・CSVのタグ条件。購入検索はPageControllerでタグ条件を受け付ける。
- TagHistoryTests：両履歴のタグ登録・編集・解除、完全一致と複合条件、CSV、入力制限、エスケープ、削除時の連動を確認する。

- TransactionForm：登録・編集の入力値と画像を受け取り、validateで検証する。
- TransactionFilter：検索条件を検証し、start・endで対象期間を算出する。
- SettingsForm：料率・端数処理・月間利益目標を検証する。
- PhotoConfiguration：/images/**を保存先ディレクトリおよびクラスパスの画像へ対応させる。
- schema.sql：起動時に不足するテーブルを作成する。

## 利用範囲とリクエスト

- 個人利用はserver.address=127.0.0.1。demoプロファイルとDockerは0.0.0.0で待ち受ける。
- server.portは環境変数PORT（未指定時8080）に従う。
- セッション追跡はCookieのみとし、初回POST後のURLにjsessionidを付けない。demoでは転送ヘッダーを解釈してHTTPSプロキシ配下のリダイレクトを維持する。
- ログイン・ユーザー情報・認証・所有者確認・ユーザー別データ分離は行わない。
- Spring Securityは使用しない。
- CSRFトークンの発行・フォームへの埋め込み・検証は行わない。CsrfConfigurationは設けない。
- 登録・更新・削除はPOST。入力検証、HTMLのエスケープ、CSVの文字列処理は行う。

## 画像

- 1取引1枚、任意。JPEG・PNG・WebP、入力ファイルは5MB以下かつ2,000万画素以下。
- ImageIOで内容を検証しPNGに変換する。WebPはTwelveMonkeys ImageIOで読み込む。
- 保存先の初期値はuploads/images/。app.photos.directoryで変更可能。
- UUIDのファイル名を用い、DBには/images/から始まるパスを保存する。
- リクエスト全体は6MB以下。自動縮小はしない。
- DBのトランザクション完了に合わせて置換・削除・ロールバック時の画像を処理する。

## テスト

JUnitとMockMvc、MySQL互換モードのH2を使用する。
ログイン・CSRFトークンなしでの画面表示と登録・編集・削除・設定保存、入力検証、計算、集計、検索、CSV、画像処理を確認する。
実利用中のMySQLには接続しない。

DemoApplicationTestsはdemoプロファイルをランダムポートで起動し、サンプルの投入、主要9画面、共有デモ案内、CSV、初期設定なしのCRUD、ヘルスチェックの正常・異常応答を確認する。実HTTPで初回POST後のHTTPSリダイレクト・URLへのセッションID非付与・Secure Cookieも検証する。

## 配布構成

- Dockerfile：Maven / JDK 21でverify後、実行用JRE 21へJARだけコピー。非root実行、JVMヒープ最大60%、Asia/Tokyo。
- .dockerignore：pom.xmlとsrc/だけをビルドコンテキストへ含める。
- render.yaml：DockerのFree Web Service、demo有効化、/healthzで監視。
- compose.demo.yaml：Dockerだけで起動するデモ。ホストの127.0.0.1:8080に限定して公開。
- application-demo.properties：H2メモリーDB（MySQL互換）、最大5接続、H2コンソール無効、demo-data.sql指定、画面用app.demo=true。
- demo-data.sql：共通schema.sqlの後に架空の取引・購入・タグ・設定を投入する。通常起動では読まない。
- デモ表示は共通fragments.htmlでapp.demoを参照する。公開環境に認証や個人データ隔離はない。
- bin/の旧コピー・生成物はMaven/Dockerの入力に含めない。
