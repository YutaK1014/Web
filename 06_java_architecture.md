# Java / Spring Boot 設計

## 想定構成

```text
src/main/java/
└─ com.example.moukememo/
   ├─ controller/
   ├─ service/
   ├─ repository/
   ├─ entity/
   ├─ dto/
   ├─ config/
   └─ MoukememoApplication.java

src/main/resources/
├─ templates/
├─ static/
│  ├─ css/
│  ├─ js/
│  └─ images/
└─ application.properties
```

## Entity
- User
- Transaction
- MarketplaceSetting

## Repository
### UserRepository
- findByEmail(String email)
- existsByEmail(String email)

### TransactionRepository
- findByUserId(...)
- findByUserIdAndSoldDateBetween(...)
- findByUserIdAndMarketplace(...)
- findByUserIdAndItemNameContaining(...)

### MarketplaceSettingRepository
- findByMarketplaceName(...)

## Service
### UserService
- registerUser
- getCurrentUser

### TransactionService
- createTransaction
- updateTransaction
- deleteTransaction
- calculateFee
- calculateProfit
- getTransactionsForUser

### ReportService
- getMonthlySummary
- getYearlySummary
- getMarketplaceSummary

## Controller
- HomeController
- AuthController
- DashboardController
- TransactionController
- ReportController
- SettingsController

## セキュリティ
Spring Securityの利用を想定。

要件：
- 未ログインユーザーは管理画面にアクセス不可
- 他ユーザーの取引IDを指定しても閲覧・編集・削除不可
- パスワードはBCrypt等でハッシュ化
- CSRF対策
- 入力値検証

## 画像
- 1取引1枚
- JPEG / PNG / WebP
- 最大5MB
- DBにはファイルパスのみ保存
