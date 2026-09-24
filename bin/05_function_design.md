# 機能設計書

## ユーザー登録
入力：
- username
- email
- password

処理：
1. 入力検証
2. メールアドレス重複確認
3. パスワードハッシュ化
4. usersへ保存

## ログイン
1. email/password入力
2. 認証
3. 成功時ダッシュボード
4. 失敗時エラー表示

## 取引登録
1. 入力値検証
2. サイト選択
3. marketplace_settingsから手数料率取得
4. 販売手数料計算
5. 利益計算
6. 画像があれば保存
7. transactionsへ保存
8. 一覧またはダッシュボードへ遷移

## 手数料計算
sellingFee = sellingPrice × feeRate

## 利益計算
profit = sellingPrice - sellingFee - shippingCost - purchasePrice

purchasePrice未入力の場合は0円扱い。

## 取引編集
- 登録済み取引を取得
- ログインユーザー所有か確認
- 内容更新
- 手数料・利益を再計算
- 保存

## 取引削除
- 所有者確認
- 削除確認
- DBから削除
- 商品画像があれば画像ファイルも削除

## 取引一覧
ログインユーザーの取引のみ取得する。

## 集計
- 総売上
- 総利益
- 総送料
- 総販売手数料
- 総仕入価格
- 総取引件数

## 検索・絞り込み
- 商品名
- フリマサイト
- 販売月
- 販売期間
