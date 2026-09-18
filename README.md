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
