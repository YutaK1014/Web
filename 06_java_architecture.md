# Java / Spring Boot設計

## 逆走ハイウェイ（2026-10-26）

ルート定義の`difficulty`は地方区間の境界から算出し、`Run`は現在のチェックポイントを基に難易度・対向車速度・生成間隔・並走確率を返す。Runのdistanceは累計距離、completedLapsは完走周数、lapDistanceは周内距離を返す。東京帰還で走行状態を止めずチェックポイントを周回先頭へ戻し、対向車・車線位置・出現タイミングを引き継ぐ。2周目以降のdifficultyは14を返す。ブラウザーテストでは全IC境界の直前・到達時、同地方内、再入場、停止・再挑戦、最高難易度までの交通回避、3周連続走行、周回境界の超過距離と対向車の保持、周回表示、再挑戦時の周数・難易度リセットを検証する。

ホームのみで`highway-engine.js`・`highway.js`・`highway.css`を読み込む。`HighwayGame.Run`はDOMから独立して、ルート・車線移動・対向車生成・衝突・距離・中継通過・クリア判定を担当する。描画フレーム間を最大1/120秒のサブステップで処理して衝突のすり抜けを防ぐ。UI側はdialog、Canvas、キーボード／タッチ、ゲーム間の起動排他、距離表示、停止・再開を扱う。終了時に描画予約を止めてホームのフォーカス・スクロール位置を復元する。バックエンド・DBの追加はない。

`src/test/js/highway-tests.html`で衝突、再挑戦、全区間の交通回避と東京帰還、クリア直前の衝突優先、PC／スマホ起動、狭幅、停止・復帰、入力保持、既存ピンボールとの共存を検証する。実端末のタッチ入力は別途確認対象とする。

## 隠しピンボール（2026-10-26）

`pinball-rewards.js`のPinballRewardsが得点・コンボ・ゲージ・フィーバー時間を管理し、`pinball.js`より先に読み込む。バンパーとフリッパーの衝突時に得点を確定し、物理更新と同じ刻みで残り時間を更新する。描画用の粒子は100個・得点表示は10個・軌跡は12点に制限する。効果音はユーザーのON操作でWeb Audioを作成し、OFF・終了時にAudioContextを閉じる。ブラウザーテストで倍率上限、タイムアウト、フィーバー再突入、落球時保持、初期化を検証する。

`home.html`だけが`pinball.js`・`pinball.css`を読み込む。HTML dialogとCanvas 2Dを使用し、固定時間刻みの重力・壁／バンパー／回転フリッパー衝突をブラウザーで計算する。非表示時は進行を止め、フォーカス離脱時は操作状態を解除する。終了時はrequestAnimationFrameをキャンセルしてホームのフォーカス・スクロールを復元する。Java・DBの追加はない。`src/test/js/pinball-tests.html`で起動判定、得点、再発射、終了、再入場を検証する。

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

## 保存保護（2026-10-14追加）

- 2026-10-15：ShippingTemplateForm / ShippingTemplateにversionを追加する。Repositoryはshipping_template_versionsをLEFT JOINし、行がなければ0で返す。ShippingTemplateService.save/deleteは@Transactionalで親行ロック・版照合を行い、競合をSaveConflictExceptionとする。TemplateConflictTestsで同時編集、編集と削除の競合、版欠落・不正、入力保持、再確認・ロールバックを検証する。
- UploadExceptionHandlerは解析不能な本文やRefererを参照せず、requestURIからcontextPathを除いた送信先を照合する。既知の編集URLは同じ編集、新規POSTは登録画面へリダイレクトする。それ以外の容量超過は413とする。HttpEdgeCaseTestsでファイル上限・全体上限・編集画面の復元案内・contextPath・外部Refererの無視を検証する。
- TransactionFilterはnullのmonth / marketplace / keywordを入力エラーとして記録して正規化する。SettingsFormはnullのroundingを入力エラーとする。ApplicationExceptionHandlerはInvalidPropertyExceptionを400の共通エラー画面へ変換する。NullPointerException等を一括捕捉して隠す処理は追加しない。

- 2026-10-15：SettingsForm.versionを設定画面に埋め込む。MarketplaceService.settings/saveは@TransactionalでMarketplaceSettingRepository.lockVersionを使用し、設定全体の版を照合する。保存成功時のみincrementVersionする。PageControllerはSaveConflictExceptionを409とsettingsConflict属性に変換する。SettingsConflictTestsで初回・通常の同時保存、入力保持、欠落・不正版、入力訂正、ロールバック、schema再実行時の版保持を確認する。

- 2026-10-15：ReturnForm.versionをGETで設定しhiddenでPOSTへ渡す。TransactionService.saveReturnは親行ロック後に版を照合し、不一致・欠落をSaveConflictExceptionとする。Controllerは409とreturnConflict属性を返し、入力金額・送信版を保持する。AdversarialSaveTestsで同時保存・古い返品画面・通常編集後の返品・不正版・入力訂正を検証する。

- TransactionService.delete(id, version)は親行をFOR UPDATEでロックして版を比較する。不一致・欠落はSaveConflictExceptionとし、削除SQLや画像削除を実行しない。Controllerは409とdeleteConflict属性付きのtransaction-deleteを返し、最新の確認画面へのリンクを表示する。AdversarialSaveTestsで版欠落・不正値・タグ変更・返品・再確認を、TransactionRegistrationTestsで画像更新中の古い削除要求の拒否と画像保持を検証する。

- SubmissionService.claimは既存トランザクション必須（MANDATORY）でSubmissionRepositoryから登録IDをINSERTし、重複をSaveConflictExceptionに変換する。売却・購入のServiceから呼び、履歴・タグ・画像とまとめて成功またはロールバックする。
- TransactionForm / PurchaseFormのsubmissionId、TransactionForm / TagFormのversionをフォームに埋め込む。IDの発行はGETだけで行い、POST時に欠落IDを自動発行しない。SaveConflictExceptionはControllerでHTTP 409と入力を保持した画面に変換する。
- TransactionService.get / PurchaseService.getは親行をロックするトランザクション内で本体・版番号・タグを取得し、画面用スナップショットを揃える。保存時にも親行ロック後に版を照合する。版番号は専用テーブルに保存し、行がない場合は0。売却編集・返品保存・購入タグ編集で増分する。
- InputText.stripSpaceはCharacter.isWhitespace / isSpaceCharで商品名の前後空白を除去する。
- SaveProtectionTestsで同時再送、同時編集、意図的な同内容の別登録、ID欠落、検証・画像失敗後の再試行、Unicode空白を検証する。PurchaseHistoryTestsでDBエラー後の同一ID再試行を確認する。form-draft-tests.htmlで再送ID・版の復元と旧下書きの扱いを確認する。

## Controller

| クラス | 担当 |
| --- | --- |
| PageController | トップ・ダッシュボード・レポート・月次印刷レポート・設定の表示、設定保存、購入登録・検索・購入タグ編集。購入登録成功時の下書き版通知 |
| TransactionController | 取引一覧・登録・編集・返品登録／費用更新・削除確認・削除、CSV出力。旧登録URLからのリダイレクト、フォームへの送料テンプレート供給 |
| ShippingTemplateController | 送料・梱包テンプレートの一覧・登録・編集・削除確認・削除 |
| DiscountController | 値下げシミュレーションの入力・計算結果・入力エラー表示 |
| HealthController | GET /healthz。DB疎通成功時200 / UP、失敗時503 / DOWNのJSON応答 |
| ApplicationExceptionHandler | DB障害・URLパラメーターの型不正に対するエラー表示 |
| UploadExceptionHandler | multipart解析失敗をHTTP 400と日本語案内に変換。容量超過は専用ハンドラーで従来の案内・リダイレクト／413を維持 |

## Service・Entity

| クラス | 主なメソッド・責務 |
| --- | --- |
| TransactionService | search・get・save・saveReturn・delete。入力検証、通常編集・返品登録・削除の排他制御、DBと画像の整合性管理 |
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
PurchaseRepositoryのsearch・findAll・findById・insertでpurchasesテーブルを操作する。
購入登録のDBエラーはPageController.savePurchaseが捕捉し、HTTP 503で入力値とエラーをホームへ再表示する。履歴の読み込みも失敗した場合はpurchaseHistoryUnavailableで専用の案内を表示し、フォームと検証エラーを保持する。PurchaseHistoryTestsで保存失敗時のロールバック・再送信・下書き削除通知の抑止・履歴取得失敗時の表示を確認する。
PurchaseHistoryTestsで登録・表示順・入力検証・HTMLエスケープ・収支集計の独立性を確認する。

MyBatisのMapperとSQLアノテーションでDBを操作する。

- TransactionRepository：search・searchWithTag・findById・countImageReferences・insert・update・delete・lockById・saveReturn。transaction_returnsをLEFT JOINして取得し、返品費用をUPSERTする。
- ShippingTemplateRepository：findAll・findById・insert・update・delete。一覧はID昇順、更新・削除は影響行数で存在を確認する。
- PurchaseRepository：search・findAll・findById・insert。searchは購入期間とタグ候補をSQLで絞り込み、購入日降順・同日はID降順で取得する。findAllは条件なしのsearchを呼ぶ。
- TagRepository：単一履歴のタグ取得に加え、transactionTagsByIds・purchaseTagsByIdsで検索結果の全タグを一括取得する。履歴ごとのタグ列昇順を維持し、clearTransaction・clearPurchase・addTransaction・addPurchaseで置換する。
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
- 一覧検索はPurchaseRepository.search・TransactionRepository.searchWithTagで期間とタグ候補を絞り込み、TagRepositoryのID一覧による一括取得とHistoryTag.groupで各履歴に全タグを割り当てる。DBの候補が0件ならSQL 1回、それ以外は2回。タグの厳密な一致はDB照合順序に依存しないようServiceでも確認する。
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
- `CsrfConfiguration`は32バイトの暗号学的乱数によるトークンをHTTPセッションに保持する。`RequestDataValueProcessor`で更新系Thymeleafフォームに`_csrf`を自動挿入する。GETフォームには挿入せず、トークンを含む画面は`Cache-Control: no-store`とする。
- 全MVC経路に登録したInterceptorでGET・HEAD・OPTIONS・TRACE以外を検証する。multipart解析後、Controllerのバインド・保存処理前に照合し、欠落・不一致・セッション切れ・複数値は403の日本語エラー画面で拒否する。容量超過は従来のアップロード例外処理が先に処理し、保存しない。multipartの一時領域消費・大量送信そのものを防ぐ機能ではない。
- トークンは同じセッションの複数タブで共有し、正常送信後も使用できる。再送ID・版番号とは別の目的であり、それらの検証も継続する。CSRFトークンをlocalStorageやURLへ出力しない。ログイン・アクセス制御は追加しない。
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

- 売却登録／編集のsale-save-barは1024px以下の画面で表示する。transaction-form.jsが通常・固定バーの利益と送信ボタン状態を同期し、Visual Viewportでキーボード表示時の位置、ResizeObserverでバーの高さに応じた余白を調整する。
- 2026-10-22：入力欄が固定保存バーに隠れる問題を修正。resize処理は最初のrequestAnimationFrameでバー位置・余白を更新し、次のrequestAnimationFrameでフォーカス中の入力欄の下端とバー上端を比較する。重なりがあれば12pxの余裕を含めてwindow.scrollByで補正する。focusin・画面サイズ変更・pageshow・Visual Viewportのresizeで実行し、Visual Viewportのscrollではバー位置を即時更新したうえで同じ補正を予約する。
- src/test/js/transaction-form-tests.htmlで表示幅、利益計算との連動、模擬キーボードの位置補正、フォーカス後および表示領域の移動後の入力欄の可視性、入力検証、送信・戻る操作を検証する。修正後は同テストと下書きテストがALL PASSED。Chromeの実画面でも、入力フォーカスを有効にした模擬キーボード条件で重なりの解消を確認した。実端末のソフトウェアキーボードは未確認であり、別途確認する。
- transactions.htmlの取引一覧では検索フォームと経費・件数の集計をdetailsで開閉する。検索条件の保持とエラー時の展開はThymeleafで描画し、売上・利益を常時表示する。共通summaryフラグメントを使用する他画面の集計表示は従来どおり。TransactionRegistrationTestsで折りたたみ・条件保持・集計値・CSV検索・エラー時の展開を確認する。
- 月次レポートを除く通常画面ではfragments.htmlの共通viewport・ナビゲーション・集計部品を利用する。登録リンクは`/sales/register`、値下げ試算リンクは`/discount-simulator`。
- transaction-form.jsは手数料・利益のプレビュー、写真プレビュー、送料テンプレート選択時の費用コピーを担当する。保存時の金額はJavaで再検証・再計算する。
- form-draft.jsは入力・変更・送信時の保存、手動復元・削除、成功時の版一致削除、storageイベントによる保存状態の案内を担当する。transaction-form.jsはdraftrestoredイベントでテンプレート選択表示を解除し、料率・利益のプレビューを更新する。
- monthly-reportは共通head・navを使わず、専用CSSと印刷スクリプトを読み込む。印刷CSSはA4縦・余白12mm、操作欄非表示、改ページの抑制と損失の斜線表示を指定する。
- transactions.cssは1024px以下でメニュー3列・検索欄2列、600px以下でメニュー2列・入力欄1列へ切り替える。取引一覧は1024px以下の画面でカード、それ以外と印刷では表を表示する。カードの内訳はHTMLのdetailsで開閉し、操作リンクはtransactions.htmlのtransactionActionsフラグメントを表と共有する。狭い画面のグラフは数値の下へ棒を配置し、その他の表・カレンダーはコンテナ内で横スクロールする。
- theme.jsはブラウザーにテーマを保存し、ダークモードにも共通のレスポンシブレイアウトを使用する。値下げ試算はJavaScriptに依存せずPOSTで計算する。

## テスト

- `MonthlyReportTests`：うるう日・月境界・返品・赤字の集計、日別合計と月合計の一致、空月・対応年月の上下限・不正入力、画面描画と導線の対象月を検証する。ブラウザーの実際のPDF保存・印刷結果はこのテストの対象外。
- `TransactionRegistrationTests`・`PurchaseHistoryTests`：下書きキー付きフォーム、成功時のキー・版通知、入力エラー時に削除通知しないことを確認する。
- `src/test/js/form-draft-tests.html`：テスト専用ブラウザープロファイルで開いて`ALL PASSED`を確認する。自動保存・再表示後の復元・送信失敗時の保持・成功時の版照合・フォーム別分離・削除・保存不可・破損を検証する。該当キーを初期化するため実利用のプロファイルは使わない。Mavenとは別に実行する。
- `DashboardReportTests`：前月値が正の増減率・四捨五入・増減方向と、前月値が0／負の場合の算出不可を検証する。`DemoApplicationTests`で画面の率・理由の表示も確認する。

ShippingTemplateTestsで管理CRUD、入力値保持、0円・上限・合計上限、HTMLエスケープ、過去の取引金額に影響しないことを確認する。DiscountSimulationTestsで試算の境界値・丸め・最低価格の最小性・入力不正・設定不足・非保存を確認する。TransactionReturnTestsで返品登録・更新・元データ保持・集計・CSV・削除連動を確認する。

DashboardReportTestsで前月比較の年またぎ・平均の丸め・0件・うるう日・4〜6週のカレンダー・日別合算・大きな利益を確認する。DemoApplicationTestsでは月指定のダッシュボード描画・日別検索リンク・年月の境界と不正入力も検証する。

JUnitとMockMvc、MySQL互換モードのH2を使用する。
ログインなしでの画面表示と、正規画面から取得したCSRFトークン・セッションを使った登録・編集・削除・設定保存、入力検証、計算、集計、検索、CSV、画像処理を確認する。CSRF専用テストではトークン欠落・不一致・別セッション・期限切れ・複数値の拒否と、通常送信・複数タブ・GET閲覧の動作を検証する。
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


スマホでの隠しゲーム起動：ホームのメイン見出し上の「もうけメモ」を0.7秒長押しするとコマンド入力パネルを表示する。矢印・B・Aをタップして「↑ ↑ ↓ ↓ ← → ← → B A」を入力すると起動する。短いタップ・スクロール・タッチキャンセルでは表示しない。パネルは閉じるボタンで終了でき、再表示時は入力をリセットする。キーボード操作ではロゴボタンのEnter／Spaceでもパネルを開ける。
