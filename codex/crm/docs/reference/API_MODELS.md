# CRM API 数据模型与 JSON 示例

本文补充 [API 路径参考](API.md) 中未展开的数据结构。字段以当前 Java DTO/Entity 和股票 Controller SQL alias 为准；客户端仍应以实际环境的脱敏响应做最终确认。

## 1. 通用约定

### 1.1 类型与格式

| Java/数据库类型      | JSON           | 约定                                                     |
| --------------- | -------------- | ------------------------------------------------------ |
| `Long/Integer`  | number         | ID 不带引号；移动端使用 64 位类型接 Long                             |
| `BigDecimal`    | number 或 null  | 金额/比例可空，不把 null 当 0；客户端累计时避免二进制浮点误差                    |
| `Boolean`       | boolean 或 null | 不用 `0/1` 猜测，按真实 JSON 解析                                |
| `LocalDate`     | string         | 通常 `YYYY-MM-DD`                                        |
| 基金源日期 String    | string         | `navDate/cutoffDate/ratingDate` 可能为 `YYYYMMDD`，不要擅自混用  |
| `LocalDateTime` | string         | 当前 Jackson 基线为 `yyyy-MM-dd HH:mm:ss`                   |
| 可空字段            | null/缺失        | Web 用 optional，Swift 用 Optional，Android 先 `has/isNull` |

JSON 字段使用 camelCase。未知新增字段应被客户端忽略；服务端删除/改类型属于破坏性变更。

### 1.2 `ApiResponse<T>`

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

`T=Void` 时 `data` 可能为 null。客户端必须同时检查 HTTP 状态和 `code`。

### 1.3 `Page<T>`

```json
{
  "records": [],
  "total": 0,
  "size": 10,
  "current": 1,
  "pages": 0
}
```

不要根据 `records.length < size` 之外的猜测计算总页数，优先使用 `total/pages/current`。

## 2. 认证、用户、角色和菜单

### 2.1 登录

请求：

```json
{
  "username": "admin",
  "password": "<password>"
}
```

`username/password` 均有非空校验。成功 `data`：

```json
{
  "token": "<jwt>",
  "username": "admin",
  "permissions": ["ROLE_ADMIN", "crm:customer:list", "fund:list"]
}
```

`permissions` 是当前 Token 的权限快照；角色变化后需重新登录。

### 2.2 用户保存与响应

`UserSaveRequest`：

| 字段               | 类型            | 说明                                          |
| ---------------- | ------------- | ------------------------------------------- |
| `deptId`         | number/null   | 部门 ID                                       |
| `username`       | string        | 登录名；应保持唯一                                   |
| `password`       | string/null   | 创建时当前代码为空会使用固定默认值 `123456`，生产调用必须显式提供安全初始密码 |
| `realName`       | string/null   | 姓名                                          |
| `mobile`、`email` | string/null   | 联系方式                                        |
| `status`         | number/null   | 当前约定 1 启用；为空回退 1                            |
| `roleIds`        | number[]/null | 角色 ID；保存时重建关联                               |

`UserResponse` 不返回密码，字段为：`id/deptId/username/realName/mobile/email/status/lastLoginAt/createdAt/updatedAt/roleIds/roleNames`。

### 2.3 角色

`RoleSaveRequest`：

```json
{
  "roleName": "销售",
  "roleCode": "SALES",
  "dataScope": "SELF",
  "status": 1,
  "menuIds": [10, 11, 15]
}
```

响应 `RoleResponse` 还包含 `id/createdAt/updatedAt`。`dataScope` 当前被保存但未完整用于客户行级过滤。

### 2.4 菜单

`SysMenu` 字段：

| 字段                      | 类型          | 说明                           |
| ----------------------- | ----------- | ---------------------------- |
| `id`                    | number      | 更新时使用路径 ID                   |
| `parentId`              | number/null | 空时保存为 0                      |
| `menuName`              | string      | 名称                           |
| `menuType`              | string      | 当前数据使用 `CATALOG/MENU/BUTTON` |
| `path`、`component`      | string/null | 前端路由/组件标识                    |
| `permissionCode`        | string/null | 精确权限字符串                      |
| `icon`                  | string/null | Ant Design 图标名               |
| `sortOrder`             | number/null | 排序                           |
| `visible`               | number/null | 空时创建默认 1                     |
| `createdAt`、`updatedAt` | string/null | 只读审计字段                       |

## 3. CRM 模型

### 3.1 客户 `CrmCustomer`

| 字段                          | 类型          | 用途                          |
| --------------------------- | ----------- | --------------------------- |
| `id`                        | number      | 主键；新增不传，更新用路径 ID            |
| `customerName`              | string      | 客户名称                        |
| `customerType`              | string/null | 类型                          |
| `industry`、`source`         | string/null | 行业、来源                       |
| `level`                     | string/null | 当前种子值 `A/B/C`               |
| `status`                    | string/null | 当前种子值 `POTENTIAL/DEAL/LOST` |
| `ownerUserId`               | number/null | 负责人；当前未形成完整 data-scope 隔离   |
| `phone`、`email`             | string/null | 联系方式                        |
| `province`、`city`、`address` | string/null | 地址                          |
| `remark`                    | string/null | 备注                          |
| `createdBy`                 | number/null | 创建人 ID                      |
| `createdAt`、`updatedAt`     | string/null | 审计时间                        |

当前 Controller 直接接收 Entity，客户端只应发送业务可写字段。生产删除客户不会自动级联联系人/跟进等关联，见 [已知限制](KNOWN_LIMITATIONS.md)。

### 3.2 联系人 `CrmContact`

字段：`id/customerId/contactName/gender/title/mobile/email/wechat/isPrimary/remark/createdAt/updatedAt`。

- `customerId` 必须指向目标客户。
- `isPrimary` 当前为 number，通常用 `0/1`。
- 当前只有列表和新增，没有更新/删除 API。

### 3.3 跟进 `CrmFollowRecord`

字段：`id/customerId/contactId/followType/content/nextFollowAt/ownerUserId/createdAt`。

`followType` 当前种子值包括 `PHONE/WECHAT/VISIT`；当前只有列表和新增 API。

## 4. 基金模型

### 4.1 基金 `CfgFund`

基础字段：

| 字段                                           | 类型           | 说明                   |
| -------------------------------------------- | ------------ | -------------------- |
| `id`                                         | number/null  | 数据库 ID               |
| `fundCode`、`fundName`                        | string       | 基金代码、名称              |
| `inceptionDate`                              | string/null  | 成立日 `YYYY-MM-DD`     |
| `fundManager`、`fundType`、`managementCompany` | string/null  | 经理、类型、公司             |
| `netAssetScale`                              | string/null  | 源端规模文本，不应假设为纯 number |
| `scaleDate`                                  | string/null  | 规模日期                 |
| `canBuy`                                     | boolean/null | 是否可购买                |
| `createdAt`、`updatedAt`                      | string/null  | 审计时间                 |

基金列表会额外组装：`latestPerformance/latestRating/features/latestValuation/latestScore/favorite`。创建/更新不要发送这些只读聚合字段。

基金详情 `FundDetailResponse`：

```text
fund                 CfgFund
latestNav            FundNavHistory | null
latestPerformance    FundPerformanceHistory | null
latestValuation      FundDailyValuation | null
latestHoldings       FundStockHolding[]
features             FundFeatureData[]
ratings              FundRating[]
scoreDetail          FundScoreDetail | null
```

### 4.2 净值和业绩

`FundNavHistory`：`id/fundCode/navDate/unitNav/accumulatedNav/dailyGrowthRate/createdAt/updatedAt`。

`FundPerformanceHistory`：

```text
id, fundCode, navDate, fundNamePinyin, inceptionDate,
weeklyReturnRate, monthlyReturnRate, threeMonthReturnRate,
sixMonthReturnRate, oneYearReturnRate, twoYearReturnRate,
threeYearReturnRate, yearToDateReturnRate, sinceInceptionReturnRate,
customStartDate, customEndDate, customReturnRate, saleStatus,
originalFeeRate, discountedFeeRate, discountFactor,
cashManagementFeeRate, sourceRow, createdAt, updatedAt
```

收益率字段是数值比例的业务展示值；不要在客户端无依据地再乘/除 100，先对照实际响应和现有格式函数。

### 4.3 披露持仓、特征和评级

`FundStockHolding`：

```text
id, fundCode, reportPeriod, reportDate, cutoffDate, rankNo,
stockCode, stockName, latestPrice, changeRate, quoteTime,
relatedInfoUrl, netValueRatio, holdingShares10k,
holdingMarketValue10k, createdAt, updatedAt
```

`FundFeatureData`：`id/fundCode/periodLabel/cutoffDate/standardDeviation/sharpeRatio/createdAt/updatedAt`。

`FundRating`：`id/fundCode/ratingDate/zhaoshangRating/shanghaiRating3y/shanghaiRating5y/jianRating/morningStarRating/createdAt/updatedAt`。

### 4.4 估值 `FundDailyValuation`

字段：

```text
fundCode, valuationDate, holdingReportDate, holdingCutoffDate,
baseNavDate, baseUnitNav, estimatedUnitNav, estimatedChangeRate,
holdingWeight, quotedHoldingWeight, quoteCoverageRate,
holdingCount, quotedHoldingCount, quoteUpdatedAt
```

`quoteCoverageRate` 和持仓权重不足时，客户端应提示数据覆盖，不把估值当正式净值。

## 5. 评分模型

### 5.1 配置保存

```json
{
  "profileName": "稳健配置",
  "weights": {
    "return_1m": 1,
    "return_3m": 3,
    "return_6m": 5,
    "return_1y": 7,
    "return_2y": 5,
    "return_3y": 4,
    "volatility_1y": 5,
    "volatility_3y": 10,
    "sharpe_1y": 10,
    "sharpe_3y": 15,
    "drawdown_1y": 8,
    "drawdown_3y": 12,
    "rating_zhaoshang": 2,
    "rating_shanghai_3y": 2,
    "rating_shanghai_5y": 1,
    "rating_jian": 2,
    "rating_morningstar": 3,
    "scale": 5
  }
}
```

请求必须恰好包含当前 18 个因子 key，每项为 0～100 的整数且总和为 100。约束由 Java 与 `scoring.py` 共同校验，不要仅靠 UI 拼装未知 key。

`FundScoreProfile` 字段：

```text
id, profileName, versionNo, status, sourceType, targetMonths,
weights, validationStatus, active, createdBy, approvedBy,
approvedAt, createdAt, updatedAt
```

### 5.2 评分结果

`FundScoreSummary`：

```text
profileId, profileName, profileVersion, validationStatus,
asOfDate, totalScore, profitProbability, confidence,
dataCoverage, comparisonGroup, categoryRank, categoryCount,
methodologyVersion
```

`FundScoreDetail` 包含 `summary`、`components` 和 `disclaimer`。每个 component：`factorKey/label/rawValue/normalizedScore/weight/effectiveWeight/contribution`。

`profitProbability=null` 表示尚不能提供已验证概率，不应展示为 0%。

### 5.3 回测与任务

`FundScoreBacktest`：

```text
id, profileId, trainStartDate, trainEndDate, testStartDate,
testEndDate, sampleCount, foldCount, auc, brierScore,
baselineBrierScore, top20WinRate, baselineWinRate,
winRateLift, passed, limitationsJson, metricsJson, createdAt
```

`FundScoreJob`：`id/jobType/profileId/status/requestedBy/message/startedAt/finishedAt/createdAt/updatedAt`。

入队响应成功只表示 Job 已创建，最终结果以 Job 状态和回测记录为准。

## 6. 用户持仓与 OCR

### 6.1 上传约束

| 参数            | 值                                |
| ------------- | -------------------------------- |
| `sourceLabel` | `alipay` 或 `tencent`，默认 `alipay` |
| `importType`  | `holding` 或 `trade`，默认 `holding` |
| `images`      | 1～3 个 multipart 文件；JPG/JPEG/PNG  |
| Spring 限制     | 单文件 10 MB、单请求 20 MB              |
| OCR 超时        | Java 等待 Python 子进程最多 120 秒       |

同批重复图片会跳过；已确认的交易截图会按哈希避免重复应用。

### 6.2 预览

`PortfolioHoldingImportPreview`：

```text
importId, sourceLabel, importType, status, screenshotDate,
imageCount, imageHashes[], warnings[], rows[], tradeAdjustments[]
```

持仓预览行：

```text
rowNo, fundCode, fundName, holdingAmount, holdingProfit,
holdingReturnRate, holdingCost, yesterdayProfit, todayProfit,
holdingShares, costNav, screenshotDate, confidence,
rawTexts[], candidates[]
```

候选项为 `fundCode/fundName/score`。

交易调整：

```text
groupKey, fundCode, fundName, buyAmount, sellAmount, netAmount,
currentHoldingAmount, projectedHoldingAmount, transactionCount,
skippedCount, applicable, warnings[], candidates[]
```

导入历史分页中的 `PortfolioHoldingBatch` 摘要字段为：

```text
id, status, sourceLabel, importType, screenshotDate, imageCount,
itemCount, transactionCount, appliedCount, skippedCount,
confirmedAt, createdAt, updatedAt
```

### 6.3 确认请求

持仓快照可以提交用户校正后的行：

```json
{
  "screenshotDate": "2026-08-01",
  "items": [
    {
      "rowNo": 1,
      "fundCode": "519674",
      "fundName": "示例基金",
      "holdingAmount": 10000.00,
      "holdingProfit": 200.00,
      "holdingReturnRate": 2.04,
      "holdingCost": 9800.00,
      "holdingShares": 8000.00,
      "costNav": 1.225,
      "screenshotDate": "2026-08-01",
      "confidence": 0.95,
      "rawTexts": []
    }
  ]
}
```

交易明细可只提交人工映射：

```json
{
  "tradeMappings": [
    {"groupKey": "<preview-group-key>", "fundCode": "519674"}
  ]
}
```

确认响应：`affectedHoldingCount/appliedTransactionCount/skippedTransactionCount/warnings`。同一批次不可重复确认。

### 6.4 持仓列表与总览

`scope` 允许：

- `raw`：保留平台原始行；
- `all`：按基金聚合全部平台；
- `alipay/tencent`：只看指定平台。

排序字段允许 `fundName/estimatedDailyProfit/fundType/holdingProfit/holdingReturnRate/holdingAmount`；`sortOrder=asc` 为升序，其余值按降序处理；每页最多 200。

`UserFundHolding` 响应字段：

```text
id, ownerUsername, sourceLabel, fundCode, fundName, fundType,
holdingAmount, holdingProfit, holdingReturnRate, holdingCost,
yesterdayProfit, todayProfit, holdingShares, costNav,
valuationDate, holdingReportDate, holdingCutoffDate,
estimatedChangeRate, estimatedDailyProfit, estimatedHoldingAmount,
estimatedUnitNav, estimatedCumulativeChangeRate,
estimatedCumulativeProfit, valuationCoverageRate,
valuationUpdatedAt, screenshotDate, latestImportId,
latestImportAt, createdAt, updatedAt
```

总览 `PortfolioOverview` 包含 `total` 和 `accounts[]`。每个账户摘要：`sourceLabel/displayName/holdingCount/holdingAmount/holdingProfit/holdingReturnRate/todayProfit`。

## 7. 资讯与股票

### 7.1 新浪资讯

`SinaFinanceNews`：`id/newsId/categoryTag/categoryName/content/createTime/sourceUpdateTime/docUrl/tagsJson/imagesJson/createdAt/updatedAt`。

`tagsJson/imagesJson` 是 JSON 字符串字段，当前不是嵌套数组；客户端需要显示其中内容时应单独安全解析。

### 7.2 股票列表/详情

股票响应字段：

```text
id, stockCode, stockName, marketCode, exchangeName, listingDate,
tradeDate, quoteTime, latestPrice, changeRate, changeAmount,
volume, amount, amplitude, turnoverRate, peDynamic, volumeRatio,
fiveMinChangeRate, highPrice, lowPrice, openPrice, previousClose,
totalMarketCap, floatMarketCap, speedRate, pbRatio,
changeRate60d, changeRateYtd, mainNetInflow, peTtm,
updatedAt, comment
```

历史接口不返回股票名称/交易所基础字段，返回 `id/stockCode/tradeDate/quoteTime` 加上述行情字段。列表和历史每页最多 200；日期筛选为 `YYYY-MM-DD`。

## 8. 模型变更检查表

- [ ] Java DTO/Entity、Mapper alias 与本文字段一致。
- [ ] Web TypeScript、iOS Codable、Android JSON 的类型和 null 语义一致。
- [ ] 已保存脱敏的成功、空数据、错误和分页样例。
- [ ] 日期、金额、百分比、枚举和大 ID 在各客户端验证。
- [ ] 新字段保持向后兼容；删除/改类型有版本与旧客户端方案。
- [ ] 敏感字段未意外进入响应、日志或文档示例。
