# iOS 基础与进阶：结合 CrmMobile

目标：从 Swift/SwiftUI 基础进阶到能修改 iOS 客户端、定位接口问题并完成可发布构建。

## 1. 先跟一条链路

登录链路：

```text
CrmMobileApp -> RootView -> LoginView
-> SessionStore.login
-> ApiClient.login
-> Gateway /api/auth/login
-> LoginResult -> KeychainStore -> MainTabView
```

先阅读 `CrmMobileApp.swift` 的 App/Root/Tab、`SessionStore.swift`、`ApiClient.swift` 前 100 行和 `Models.swift` 的通用响应。

## 2. Swift 基础

### let、var 与类型推断

- `let` 不可重新赋值，默认优先。
- `var` 只用于确实变化的状态。
- 公共接口和复杂表达式写清类型，局部简单值可推断。

值语义能减少意外共享。Model 多用 `struct`，共享身份/状态如 Session/API Client 使用 `class`。

### Optional

`String?` 表示可能没有。必须掌握：

- `if let`/`guard let` 安全解包。
- `??` 提供显示默认值。
- Optional chaining `object?.property`。
- 避免 `!`，除非由系统/测试严格保证。

API 可空字段与业务 0/空字符串不同。不要为消除编译错误把所有字段都改 Optional。

### 集合

`Array` 有序，`Set` 去重，`Dictionary` 按 key 查找。SwiftUI `ForEach` 需要稳定 Identifiable ID；不要用数组下标作为长期身份。

### 函数和参数标签

```swift
func listCustomers(current: Int, size: Int, keyword: String?) async throws -> PageResult<Customer>
```

从签名可读出参数、异步、可能抛错和返回类型。维护时先理解签名，再看实现。

## 3. struct、class、协议与扩展

### struct vs class

- `struct` 值类型，复制后独立；适合 Codable Model 和 View。
- `class` 引用类型，有身份；适合 `SessionStore/ApiClient/KeychainStore`。
- `final class` 禁止继承，意图更明确且便于优化。

### 协议

协议描述能力，如 `Codable/Identifiable/Hashable`。模型遵循这些协议后可解码、用于列表和比较。

### extension

用于按功能拆分已有类型、实现协议或提供小型工具；不要用扩展隐藏跨层副作用。

## 4. 错误处理

`throws/try/await` 表示失败必须处理：

```swift
do {
    rows = try await api.listCustomers(...)
} catch {
    errorMessage = error.localizedDescription
}
```

`ApiError` 将无效 URL、HTTP、业务 code 和解码问题转为用户可理解错误。不要只打印错误而不给 UI 状态，也不要把 Token/完整响应写日志。

## 5. Codable

`Decodable` 按 JSON Key 和类型解码。常见错误：

| 错误              | 含义                            |
| --------------- | ----------------------------- |
| `keyNotFound`   | 必填字段缺失/字段名不一致                 |
| `typeMismatch`  | 后端 number/string/object 与模型不同 |
| `valueNotFound` | JSON null 进入非 Optional        |
| `dataCorrupted` | 日期/枚举/自定义解析失败                 |

项目字段多为后端 camelCase，通常无需 CodingKeys。若后端字段不能直接映射，显式 `CodingKeys`，不要在 UI 临时解析字典。

Decimal、日期、空值要用真实脱敏响应测试。

## 6. async/await 与并发

### 基础

- `async` 函数可暂停，不等于自动后台线程。
- `await` 等待异步结果，不阻塞当前任务线程。
- `Task {}` 从同步 UI 事件启动异步工作。
- UI 状态应在主 Actor 上更新。

### 取消与竞态

页面快速切换筛选可能产生多个请求：旧请求后返回会覆盖新结果。进阶处理：保存 Task 并取消旧任务，或比较请求条件/序号后再更新。

分页要避免同页重复请求；loading flag 只是基本保护，不解决所有竞态。

## 7. SwiftUI 基础

### View 与 body

View 是状态的声明式结果，不是一次性构建的传统页面。`body` 会重复计算，不在其中做网络、文件写入或重计算。

### 状态属性

| 属性                   | 用途                          | 项目例子             |
| -------------------- | --------------------------- | ---------------- |
| `@State`             | View 自有值                    | loading、筛选、列表    |
| `@Binding`           | 父子共享可写值                     | 预览行编辑            |
| `@StateObject`       | View 创建并持有 ObservableObject | 根 Session        |
| `@EnvironmentObject` | 环境注入共享对象                    | 各 Tab 使用 Session |
| `@Published`         | ObservableObject 触发 UI 更新   | 登录状态             |

错误选择会造成状态重建、丢失或不刷新。

### 生命周期

- `.task` 适合页面出现时异步加载并支持取消。
- `onAppear` 可能多次触发，不假设只调用一次。
- Sheet/Navigation 中确认状态归属和返回刷新。

## 8. URLSession

请求步骤：

1. 从规范 Base URL 构造 `URLComponents`。
2. 添加 query items，避免手拼转义。
3. 建 `URLRequest`，设置 method/header/body。
4. `URLSession.data(for:)`。
5. 检查 `HTTPURLResponse`。
6. 解 `ApiResponse<T>`，检查业务 code。

Base 已含 `/api`，方法 path 不再含 `/api`。multipart 要生成 boundary、每个 part 的 Content-Disposition/Type 和结束边界。

## 9. 登录态与 Keychain

Keychain 适合 Token，因为比 UserDefaults 更安全。必须理解：service/account key、更新已有项、读取 Data/String、删除和错误状态。

仍需注意：

- 不把密码长期保存。
- 退出删除 Token。
- Token 过期清理状态，不无限重试。
- 日志和崩溃上报脱敏。
- 生产评估 Keychain accessibility 级别。

## 10. 导航与页面组织

当前用 Root 条件、Tab 和 Navigation 组合。新增页面先决定：

- 是顶级 Tab、列表详情还是 Sheet 操作？
- 状态由谁拥有？
- 关闭后是否刷新父页面？
- 深层页面是否依赖 EnvironmentObject？

不要把全局 Session 复制成多个实例。主要业务集中在大文件，新增复杂功能时可在独立变更中拆文件，但不借小需求大范围重构。

## 11. 列表、分页和表格

- 首次加载、刷新和加载更多分开处理。
- 刷新清空页码，加载更多追加并去重。
- 使用后端 `total/current/size` 判断结尾。
- 排序/筛选变化取消旧请求并从第一页加载。
- 横向表格固定列与滚动区对齐行高/宽度。
- 大列表避免每行重计算复杂格式和图表。

## 12. 图表与数值

基金趋势：

1. 解析并排序日期。
2. 过滤选择区间。
3. 去除/处理无效净值。
4. 计算坐标范围，处理最大值等于最小值。
5. 少于两个点显示空态。

`Decimal` 转 `Double` 只用于绘图坐标，不用于资金记账。颜色涨跌规则集中，避免页面各自相反。

## 13. 图片与 multipart

OCR 图片可能很大：

- 选择数量和格式受控。
- 必要时压缩/缩放但保持文字清晰。
- 上传显示进度/防重复。
- 内存中及时释放大 Data。
- 预览失败不改变最终持仓。
- 图片是敏感用户数据，不存日志/分析。

## 14. ATS、TLS 与隐私

开发允许 HTTP 不代表生产可以。掌握：

- ATS 默认要求安全传输。
- `NSAllowsArbitraryLoads` 是宽泛绕过，生产应删除。
- 证书域名、链、有效期与重定向。
- 隐私政策、App Privacy 与实际收集数据一致。
- Keychain、日志、截图、剪贴板和第三方 SDK 的数据风险。

## 15. Xcode 构建与签名

### 无签名构建

```bash
xcodebuild \
  -project ios/CrmMobile/CrmMobile.xcodeproj \
  -scheme CrmMobile \
  -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/crm-ios-derived \
  CODE_SIGNING_ALLOWED=NO build
```

### 必会概念

- Bundle ID：App 唯一标识。
- Team：开发者组织。
- Certificate：证明签名者。
- Provisioning Profile：连接 App ID、证书、设备/分发方式。
- version/build：用户版本与每次上传递增号。
- Archive：发布构建归档。

签名失败先检查四者一致，不随机删除全部证书/配置。

## 16. 测试

项目当前无 XCTest target，应该理解：

- Unit Test：解析、日期、格式、趋势计算。
- UI Test：登录、导航、核心流程。
- Mock URLProtocol：不访问真实网络测试 ApiClient。
- Preview 不是测试，只是布局工具。

手工测试至少覆盖模拟器+真机、无网/慢网、Token 过期、空数据、深色模式/文字大小（若产品要求）。

## 17. 性能与内存

- 避免 body 中重计算和同步 I/O。
- 图片降采样，避免同时保留原图多份 Data。
- 列表使用 Lazy 容器。
- 避免闭包强引用形成循环；理解 `[weak self]` 在 class 闭包中的用途。
- Instruments 分析 Time Profiler、Allocations、Leaks 和网络，不靠猜。

## 18. 发布与兼容

- 服务端 API 先保持旧客户端兼容。
- TestFlight 先内部再外部。
- App Store build 一旦发布不能替换，只能发更高 build/version。
- 崩溃/API 错误监控支持分阶段发布决策。
- 隐私/审核资料来自真实功能，不复制旧版本结论。

## 19. 项目练习

### 练习 A：追踪客户列表

从 `CustomerListView` 追到 ApiClient/Models/API，画出 State 在刷新和分页时变化。

验收：能解释空列表、403、第二页和重复请求。

### 练习 B：新增可空展示字段

在独立分支给一个已有模型增加后端真实存在的可空字段并展示 `-`，不改业务逻辑。

验收：字段有值/为 null/旧响应缺失三种情况符合契约，无签名构建通过。

### 练习 C：诊断解码错误

用固定脱敏 JSON 制造 number/string 不一致，观察 DecodingError 并修正确切类型。

验收：不通过“全部改 String?”来逃避模型设计。

### 练习 D：生产网络评审

只读检查 `Info.plist` 和 ApiClient，列出从 HTTP 开发环境迁到 HTTPS 所需验证。

验收：包含 ATS、证书、Base URL、隐私和回滚，不实际修改生产。

## 20. 独立维护完成标准

- [ ] 能解释 Swift 值/引用、Optional、协议和错误。
- [ ] 能正确使用 SwiftUI 状态和 async/await。
- [ ] 能让 Codable 精确匹配 API 并诊断错误。
- [ ] 能维护 URLSession、Header、Token 和 multipart。
- [ ] 能处理分页竞态、生命周期、图片内存和空态。
- [ ] 能执行 Xcode 构建并理解签名链。
- [ ] 能评估 ATS、Keychain、隐私和旧客户端兼容。
- [ ] 能按 TestFlight/App Store 流程测试、监控和修复发布。
