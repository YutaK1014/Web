# もうけメモ 開発ドキュメント

このフォルダには、Webアプリ「もうけメモ」の要件定義・設計情報をまとめています。

## サービス概要
「もうけメモ」は、メルカリ・ラクマ・Yahoo!フリマなどのフリマサイトで売却した商品の収支を管理するWebアプリケーションです。

主な目的は以下です。

- フリマ販売の利益を自動計算する
- 複数フリマサイトの収支を一元管理する
- 月別・年間・サイト別に収支を集計する
- 老若男女が使いやすいシンプルなUIを提供する

## 技術構成
- Java
- Spring Boot
- Thymeleaf
- HTML / CSS / JavaScript
- MySQL
- Docker
- Git / GitHub

## ドキュメント一覧
- `01_requirements.md` : 要件定義
- `02_database_design.md` : DB・テーブル設計
- `03_er_design.md` : ER設計
- `04_screen_design.md` : 画面構成・画面遷移
- `05_function_design.md` : 機能設計
- `06_java_architecture.md` : Java / Spring Boot構成
- `CODEX_INSTRUCTIONS.md` : CodeX向け開発指示

## CodeXへの指示
まず `CODEX_INSTRUCTIONS.md` と本READMEを読み、その後必要な設計書を参照してください。

## 現在の実装と起動方法

ユーザー指定によりログインなし・同じPCからの利用（127.0.0.1）です。
取引の登録・一覧・編集・削除、画像、手数料・利益計算、検索・絞り込み、ダッシュボード、
月別・年間・サイト別の集計とグラフ、CSV出力、月間利益目標、ダークモードを実装しています。

1. `docker compose up -d db` でMySQLを起動します。
2. Windowsでは `.\mvnw.cmd spring-boot:run` でアプリを起動します（Java 21）。
3. `http://localhost:8080/` を開きます。
4. 初回は「設定」で3サイトの手数料率と端数処理を設定します。

テーブルは起動時に作成されます。既存取引は上書きしません。
詳細と旧試作データの扱いは [操作・実装仕様](docs/transaction-list.md) を参照してください。
自動テストは `.\mvnw.cmd test` です。テストはH2を使い、実際のMySQLデータを変更しません。
