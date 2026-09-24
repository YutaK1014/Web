# 取引一覧のローカル試作

アプリの起動後に `http://localhost:8080/transactions` または `http://localhost:8080/` を開くと一覧が表示されます。

表示項目は写真、商品名、価格、送料、利益、取引プラットフォームです。販売日が新しい順に並びます。同じ販売日の場合は取引番号が大きい順です。

## データの準備

`database/transactions.sql` は `cashflow` 内に `users` と `transactions` を作成するSQL（データベースへの命令）です。既存の `marker` は変更しません。別の環境に移した際は、MySQLの操作画面でこのSQLを実行してください。

商品データは `transactions` に保存します。登録・編集画面は今回の対象外です。データが0件なら「取引はまだありません」と表示します。サンプルの取引は自動登録しません。

| 画面 | データベースの項目 |
| --- | --- |
| 写真 | image_path |
| 商品名 | item_name |
| 価格 | selling_price |
| 送料 | shipping_cost |
| 利益 | Javaで selling_price − selling_fee − shipping_cost − purchase_price を計算 |
| 取引プラットフォーム | marketplace。「その他」の場合はcustom_marketplace |

金額は円です。仕入価格が未設定の場合は0円として計算します。保存済みのprofitではなく、各金額から再計算した利益を表示します。

写真は `src/main/resources/static/images/` に置き、image_path に `/images/item-1.jpg` のように指定します。ファイル名は半角英数字・ハイフン・アンダースコア、拡張子はjpg・jpeg・png・webpに対応します。画像アップロードは未実装です。

## 試作の範囲

ユーザーの指定により、ログインなしで全取引を表示します。同じPCからのみアクセスするため `server.address=127.0.0.1` を設定しています。

設計書のユーザー別表示はまだ実装していません。公開する前にユーザー登録・ログインを実装し、既存取引のuser_id（所有者の番号）を設定して必須にし、取得条件をログインユーザーに限定する必要があります。
