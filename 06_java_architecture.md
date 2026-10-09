# Java / Spring Boot設計

2026-10-09更新。返品・送料／梱包テンプレート・値下げ試算・前月比較／カレンダー・画面サイズ対応に加え、増減率・下書き・月次PDF保存を含む構成を示す。

2026-10-06追加：`ShippingTemplateController`は管理画面とCRUDのルーティング、`ShippingTemplateForm`は文字数・金額・合計の検証、`ShippingTemplateService`は保存・取得・削除と存在チェック、`ShippingTemplateRepository`はMyBatisによる`shipping_templates`操作を担当する。`ShippingTemplate`が保存項目と合計金額を表す。`TransactionController`がフォーム用の一覧を取得し、`transaction-form.js`が選択時の費用コピーを行う。

現在のパッケージ・クラス構成と公開用demoの起動方式を示す。リポジトリに記録された公開状況はローカル検証までで、Renderへの実デプロイ・公開URL発行は未完了。

## 構成

値下げシミュレーション：`DiscountController`がGET/POST `/discount-simulator`を担当し、`DiscountForm`が金額・サイト・料率を検証する。`DiscountService`が共通手数料計算と最低価格探索を実行し、`discount-simulator.html`に結果を渡す。`DiscountSimulationTests`で端数処理ごとの最小性・赤字・0/100%・大きな費用・入力検証・設定不足・HTML表示・取引への非保存を確認する。DBスキーマの変更はない。

```text
src/main/java/com/example/cashflow/
├─ CashflowApplication.java
├─ controller/
│  ├─ PageController.java
│  ├─ TransactionController.java
│  ├─ ShippingTemplateController.java
│  ├─ DiscountController.java
│  ├─ HealthController.java
│  ├─ ApplicationExceptionHandler.java
│  └─ UploadExceptionHandler.java
├─ service/
│  ├─ TransactionService.java
│  ├─ MarketplaceService.java
│  ├─ ReportService.java
│  ├─ PurchaseService.java
│  ├─ ShippingTemplateService.java
│  ├─ DiscountService.java
│  └─ PhotoStorage.java
├─ repository/
│  ├─ TransactionRepository.java
│  ├─ PurchaseRepository.java
│  ├─ TagRepository.java
│  ├─ ShippingTemplateRepository.java
│  └─ MarketplaceSettingRepository.java
├─ entity/
│  ├─ Transaction.java
│  ├─ Purchase.java
│  ├─ ShippingTemplate.java
│  └─ MarketplaceSetting.java
├─ dto/
│  ├─ TransactionForm.java
│  ├─ ReturnForm.java
│  ├─ ShippingTemplateForm.java
│  ├─ DiscountForm.java
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
│  ├─ css/
│  │  ├─ transactions.css
│  │  └─ monthly-report.css
│  └─ js/
│     ├─ transaction-form.js
│     ├─ form-draft.js
│     ├─ monthly-report.js
│     └─ theme.js
├─ schema.sql
├─ demo-data.sql
├─ application-demo.properties
└─ application.properties
```

## Controller

| クラス | 担当 |
| --- | --- |
| PageController | トップ・ダッシュボード・レポート・月次印刷レポート・設定の表示、設定保存、購入登録・検索・購入タグ編集。購入登録成功時の下書き版通知 |
| TransactionController | 取引一覧・登録・編集・返品登録／費用更新・削除確認・削除、CSV出力。旧登録URLからのリダイレクト、フォームへの送料テンプレート供給 |
| ShippingTemplateController | 送料・梱包テンプレートの一覧・登録・編集・削除確認・削除 |
| DiscountController | 値下げシミュレーションの入力・計算結果・入力エラー表示 |
| HealthController | GET /healthz。DB疎通成功時200 / UP、失敗時503 / DOWNのJSON応答 |
| ApplicationExceptionHandler | DB障害・URLパラメーターの型不正に対するエラー表示 |
| UploadExceptionHandler | アップロード容量超過時の案内と登録画面へのリダイレクト |

## Service・Entity

| クラス | 主なメソッド・責務 |
| --- | --- |
| TransactionService | search・get・save・saveReturn・delete。入力検証、通常編集と返品登録の排他制御、DBと画像の整合性管理 |
| MarketplaceService | rates・rate・rounding・calculateFee・settings・save・monthlyGoal。料率・端数処理・利益目標の管理 |
| ReportService | all・summarize・daily・monthly・yearly・byMarketplace・chart・monthSummary・compare・calendar。返品を考慮した集計、前月比較・カレンダー・日別／月別グラフの生成 |
| ShippingTemplateService | all・get・save・delete。テンプレート取得・保存・削除、未登録IDの404処理 |
| DiscountService | calculate。候補価格ごとの手数料再計算と最低価格の二分探索。Resultに表示結果を保持し、DBには保存しない |
| PhotoStorage | save・delete。画像内容検証、PNG変換、ファイル保存・削除 |
| PurchaseService | all・search・get・save・updateTags。購入入力検証、期間・タグ検索、タグの保存 |
| Purchase | 購入商品名・購入日・金額・購入先・メモ・タグ |
| Transaction | 取引情報。getProfitで利益を計算、getPlatformNameで表示サイト名、getPhotoUrlで有効な画像パスを取得 |
| MarketplaceSetting | サイト別料率の情報 |
| ShippingTemplate | テンプレート名・配送方法・梱包内容・送料・梱包費、getTotalCostによる合計 |

手数料はMarketplaceService.calculateFee、利益はTransaction.getProfitで計算する。
TransactionService.saveは新規登録と編集を共通化し、IDの有無で処理を切り替える。

## 下書き・月次レポート・前月比の構成

- `PageController.monthlyReport`がGET `/reports/monthly`を受け付け、月を検証して`month`・`issuedOn`・`summary`・`chart`・`empty`をモデルへ設定する。`ReportService.daily`が月内全日のGroupを生成し、既存のchartへ渡す。`Summary.expenses()`が経費合計を返す。
- `monthly-report.html`・`monthly-report.css`で専用のA4印刷用HTML/SVGを描画し、`monthly-report.js`から`window.print()`を呼ぶ。PDFライブラリー・PDF配信API・保存用Repositoryは追加しない。
- 下書きは`form-draft.js`と`fragments.html`の`draft`フラグメントで扱う。homeとtransaction-formの`data-draft-key`により対象フォームを区別する。
- `TransactionController.create/update`と`PageController.savePurchase`は`draftRevision`を受け取る（省略時は空文字）。保存成功時だけ`savedDraftKey`と`savedDraftRevision`をflash属性に渡す。共通navに出力したマーカーとlocalStorageの版を照合し、ブラウザーで該当版を削除する。専用DTO・DB・Repositoryは追加しない。
- `ReportService.Comparison`は`difference()`に加えて`trend()`と`percentageChange()`を持つ。前月値が正ならBigDecimalで小数第1位へHALF_UPし、0以下ならnullを返す。`dashboard.html`が増減方向・率・算出不可の理由を表示する。

## Repository

購入履歴の追加構成：PageControllerがホーム表示とPOST /purchasesを担当し、
PurchaseServiceがPurchaseFormの検証とPurchaseへの変換・保存を行う。
PurchaseRepositoryのfindAll・findById・insertでpurchasesテーブルを操作する。
PurchaseHistoryTestsで登録・表示順・入力検証・HTMLエスケープ・収支集計の独立性を確認する。

MyBatisのMapperとSQLアノテーションでDBを操作する。

- TransactionRepository：search・findById・countImageReferences・insert・update・delete・lockById・saveReturn。transaction_returnsをLEFT JOINして取得し、返品費用をUPSERTする。
- ShippingTemplateRepository：findAll・findById・insert・update・delete。一覧はID昇順、更新・削除は影響行数で存在を確認する。
- PurchaseRepository：findAll・findById・insert。購入日降順・同日はID降順で全件を取得し、PurchaseServiceがタグと期間をJava側で絞り込む。
- TagRepository：transactionTags・purchaseTagsでタグ列昇順に取得し、clearTransaction・clearPurchase・addTransaction・addPurchaseで置換する。売却はSQLで商品名・サイト・販売期間を絞った後、TransactionServiceがタグの完全一致をJava側で適用する。
- MarketplaceSettingRepository：findAll・findByMarketplaceName・insert・update、getOption・insertOption・updateOption。
- ユーザー情報用のEntity・Repository・Service・Controllerは設けない。
- usersテーブルとuser_id列はDB互換用で、アプリのユーザー管理には使用しない。

## DTO・設定

- ShippingTemplateForm：文字列の前後空白除去・文字数・各費用・合計上限を検証。from・toEntityでフォームとEntityを変換する。
- DiscountForm：現在価格・送料・仕入価格・その他経費・希望利益、対応サイト、「その他」の料率を検証する。
- ReturnForm：返品費用の必須・整数・範囲検証。TransactionControllerのGET/POST `/transactions/{id}/return`とTransactionService.saveReturnで扱う。
- TransactionRepository：transaction_returnsをLEFT JOINして返品費用を取得し、lockById・saveReturnで既存取引をロックして費用をUPSERTする。
- Transaction：returnCostがNULL以外なら返品。getRecordedSales/Fees/Shipping/Purchasesは返品時0、getProfitは返品費用のマイナスを返す。元の金額のgetterは保持する。
- ReportService.Summaryは返品費用・返品件数も保持する。CalendarDayは販売件数と返品件数を区別する。
- TransactionReturnTests：費用だけの保存・元データ保持・0円・更新・重複送信・入力不正・404・検索/CSV/集計/カレンダー・削除連動を確認する。

- DateRangeFilter：期間プリセット・開始日・終了日の共通検証と日付範囲計算。リクエスト単位で基準日を固定する。
- PurchaseFilter：DateRangeFilterを継承し、購入履歴のタグ条件も検証する。TransactionFilterも同じ期間処理を利用する。

- TagForm：共通タグ入力の分割・前後空白除去・重複除去と上限検証。購入タグ編集の入力DTOも兼ねる。
- TagForm.parseはnull・String.isBlankを先に判定する。該当する入力は長さによらず空一覧とし、それ以外だけ分割前の1,000文字上限を検証する。画面のタグ入力欄にはmaxlength=1000を指定する。
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
- multipartのリクエスト全体は6MB以下（spring.servlet.multipart.max-request-size）。全POST共通の制限ではない。自動縮小はしない。
- DBのトランザクション完了に合わせて置換・削除・ロールバック時の画像を処理する。
- アプリ起動時の画像一括削除・未参照画像の定期削除は実装しない。同じDockerコンテナの再起動ではH2のデータだけが初期化され、画像ファイルは残る。

## 共通画面・JavaScript・CSS

- 月次レポートを除く通常画面ではfragments.htmlの共通viewport・ナビゲーション・集計部品を利用する。登録リンクは`/sales/register`、値下げ試算リンクは`/discount-simulator`。
- transaction-form.jsは手数料・利益のプレビュー、写真プレビュー、送料テンプレート選択時の費用コピーを担当する。保存時の金額はJavaで再検証・再計算する。
- form-draft.jsは入力・変更・送信時の保存、手動復元・削除、成功時の版一致削除、storageイベントによる保存状態の案内を担当する。transaction-form.jsはdraftrestoredイベントでテンプレート選択表示を解除し、料率・利益のプレビューを更新する。
- monthly-reportは共通head・navを使わず、専用CSSと印刷スクリプトを読み込む。印刷CSSはA4縦・余白12mm、操作欄非表示、改ページの抑制と損失の斜線表示を指定する。
- transactions.cssは1024px以下でメニュー3列・検索欄2列、600px以下でメニュー2列・入力欄1列へ切り替える。狭い画面のグラフは数値の下へ棒を配置し、表・カレンダーはコンテナ内で横スクロールする。
- theme.jsはブラウザーにテーマを保存し、ダークモードにも共通のレスポンシブレイアウトを使用する。値下げ試算はJavaScriptに依存せずPOSTで計算する。

## テスト

- `MonthlyReportTests`：うるう日・月境界・返品・赤字の集計、日別合計と月合計の一致、空月・対応年月の上下限・不正入力、画面描画と導線の対象月を検証する。ブラウザーの実際のPDF保存・印刷結果はこのテストの対象外。
- `TransactionRegistrationTests`・`PurchaseHistoryTests`：下書きキー付きフォーム、成功時のキー・版通知、入力エラー時に削除通知しないことを確認する。
- `src/test/js/form-draft-tests.html`：テスト専用ブラウザープロファイルで開いて`ALL PASSED`を確認する。自動保存・再表示後の復元・送信失敗時の保持・成功時の版照合・フォーム別分離・削除・保存不可・破損を検証する。該当キーを初期化するため実利用のプロファイルは使わない。Mavenとは別に実行する。
- `DashboardReportTests`：前月値が正の増減率・四捨五入・増減方向と、前月値が0／負の場合の算出不可を検証する。`DemoApplicationTests`で画面の率・理由の表示も確認する。

ShippingTemplateTestsで管理CRUD、入力値保持、0円・上限・合計上限、HTMLエスケープ、過去の取引金額に影響しないことを確認する。DiscountSimulationTestsで試算の境界値・丸め・最低価格の最小性・入力不正・設定不足・非保存を確認する。TransactionReturnTestsで返品登録・更新・元データ保持・集計・CSV・削除連動を確認する。

DashboardReportTestsで前月比較の年またぎ・平均の丸め・0件・うるう日・4〜6週のカレンダー・日別合算・大きな利益を確認する。DemoApplicationTestsでは月指定のダッシュボード描画・日別検索リンク・年月の境界と不正入力も検証する。

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
- home.htmlの初回設定案内にはプロファイル・設定済み状態による表示条件がなく、demoでも常に表示する。
- bin/の旧コピー・生成物はMaven/Dockerの入力に含めない。
