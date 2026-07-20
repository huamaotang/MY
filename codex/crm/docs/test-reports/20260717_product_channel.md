# 产品频道自动化测试报告

日期：2026-07-17

## 测试结论

- 后端 `fund` 服务编译和测试通过。
- 后端核心服务打包通过，包含 `gateway`、`system`、`customer`、`fund`。
- Web 前端生产构建通过。
- iOS 工程模拟器 Debug 构建通过。
- 本机 Nacos 已发布 `gateway-dev.yaml` 和 `fund-dev.yaml`。
- Android 未完成编译验证：项目未包含 `gradlew`，本机也没有系统 `gradle` 命令。

## 执行记录

| 模块 | 命令 | 结果 | 备注 |
| --- | --- | --- | --- |
| 后端 fund | `mvn -pl fund -am test` | 通过 | 无测试用例，完成编译和 Surefire 阶段 |
| 后端服务 | `mvn -pl gateway,fund,system,customer -am package -DskipTests` | 通过 | 生成各服务 Jar |
| Web | `npm run build` | 通过 | Vite 输出 chunk size 警告，不影响构建 |
| iOS | `xcodebuild -project CrmMobile.xcodeproj -scheme CrmMobile -sdk iphonesimulator -configuration Debug -derivedDataPath /private/tmp/crm-ios-derived build` | 通过 | 需在沙箱外访问 Xcode/CoreSimulator |
| Nacos | 发布 `gateway-dev.yaml`、`fund-dev.yaml` | 通过 | Nacos API 返回 `true` |
| Android | `./gradlew assembleDebug` | 未执行 | 项目缺少 Gradle Wrapper |
| Android | `gradle assembleDebug` | 未执行 | 本机 `gradle` 命令不存在 |

## 覆盖范围

- `fund` 服务 Maven 模块编译。
- `core` 鉴权和公共依赖被 `fund` 服务引用的编译兼容性。
- 网关、系统、客户、基金服务整体 Maven 打包。
- Web 基金管理页 TypeScript 类型检查和生产构建。
- iOS 三 Tab、产品列表、产品详情相关 Swift 编译。

## 未覆盖风险

- 未连接真实 MySQL 执行接口级集成测试。
- 未自动执行 SQL 迁移；现有环境需要执行 `sql/20260717_add_fund_menu.sql`，并确保 `fund_spider/sql/init.sql` 已初始化 `fund` 库。
- Android 因本机缺少 Gradle 工具链未完成编译验证。
