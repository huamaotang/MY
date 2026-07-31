# iOS 开发手册

本文覆盖 `ios/CrmMobile/` 原生 iPhone App 的运行、联调、代码结构和排错。

## 1. 技术栈

| 项 | 说明 |
| --- | --- |
| 语言 | Swift |
| UI | SwiftUI |
| 网络 | `URLSession` + async/await |
| Token 存储 | Keychain |
| 工程 | Xcode project |

## 2. 目录结构

```text
ios/CrmMobile/
  README.md
  CrmMobile.xcodeproj/
  CrmMobile/
    CrmMobileApp.swift       App 入口
    LoginView.swift          登录页
    CustomerListView.swift   客户列表
    CustomerDetailView.swift 客户详情
    ApiClient.swift          网络请求
    Models.swift             数据模型
    SessionStore.swift       登录态和全局状态
    KeychainStore.swift      Keychain 读写
    SharedViews.swift        通用视图
    Info.plist
    Assets.xcassets/
```

## 3. 运行前准备

必须启动后端：

```text
Nacos + Redis + system + customer + gateway
```

电脑和 iPhone 必须在同一个局域网。

查看 Mac 局域网 IP：

```bash
ipconfig getifaddr en0
```

假设输出是：

```text
192.168.1.10
```

iPhone App 里服务器地址填写：

```text
http://192.168.1.10:8780/api
```

不要填写：

```text
http://127.0.0.1:8780/api
```

因为 iPhone 上的 `127.0.0.1` 指向手机自己，不是你的 Mac。

## 4. 用 Xcode 运行

1. 打开 Xcode。
2. 打开 `ios/CrmMobile/CrmMobile.xcodeproj`。
3. 选择 scheme：`CrmMobile`。
4. 选择模拟器或真机。
5. 真机运行前，在 Signing & Capabilities 里选择自己的 Team。
6. 点击 Run。

如果只安装了 Command Line Tools，没有完整 Xcode，需要先安装 Xcode。

## 5. 命令行构建

确认 Xcode 路径：

```bash
xcode-select -p
```

如果不是完整 Xcode：

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

构建：

```bash
xcodebuild \
  -project ios/CrmMobile/CrmMobile.xcodeproj \
  -scheme CrmMobile \
  -destination 'generic/platform=iOS' \
  build
```

## 6. 网络层说明

`ApiClient.swift` 负责所有后端请求。

初始化参数：

```swift
init(baseURL: String, token: String? = nil, session: URLSession = .shared)
```

当前 API：

| 方法 | 后端路径 | 说明 |
| --- | --- | --- |
| `login(username:password:)` | `POST /auth/login` | 登录 |
| `listCustomers(current:size:keyword:)` | `GET /customers` | 客户分页 |
| `customerDetail(id:)` | `GET /customers/{id}` | 客户详情 |

公共请求头：

```text
Content-Type: application/json
Accept: application/json
X-Client-Source: ios
User-Agent: CrmMobile/iOS
Authorization: Bearer <token>
```

注意：`baseURL` 应包含 `/api`，方法里的 `path` 不包含 `/api`。

## 7. 登录态

登录成功后：

1. 后端返回 token。
2. `SessionStore` 保存 token。
3. `KeychainStore` 持久化 token。
4. 后续请求自动带 `Authorization`。

如果后端返回 `403`，优先重新登录。

## 8. 新增接口

以新增联系人列表为例：

1. 后端确认已有 `/api/contacts`。
2. 在 `Models.swift` 增加：

```swift
struct Contact: Codable, Identifiable {
    let id: Int?
    let customerId: Int
    let contactName: String
    let mobile: String?
}
```

3. 在 `ApiClient.swift` 增加：

```swift
func listContacts(customerId: Int?) async throws -> [Contact] {
    var queryItems: [URLQueryItem] = []
    if let customerId {
        queryItems.append(URLQueryItem(name: "customerId", value: String(customerId)))
    }
    return try await request(path: "/contacts", queryItems: queryItems)
}
```

4. 新建 SwiftUI 页面，例如 `ContactListView.swift`。
5. 在入口页面增加导航。
6. 真机验证。

## 9. 新增页面建议

SwiftUI 页面建议按这个结构写：

```text
@State loading
@State errorMessage
@State data
body
load() async
```

网络请求放在 `Task { await load() }` 或 `.task {}` 中。

错误提示不要只打印 Console，要给用户可见提示。

## 10. ATS 和 HTTP

本地后端默认是 HTTP，不是 HTTPS。如果真机无法访问 HTTP，检查 `Info.plist` 是否允许本地 HTTP 调试。生产环境应改为 HTTPS。

## 11. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 真机连不上 | 地址填了 `127.0.0.1` | 改成 Mac 局域网 IP |
| 模拟器连不上 | Gateway 未启动或端口不通 | `curl http://127.0.0.1:8780/actuator/health` |
| 登录后客户列表 403 | token 无效或未保存 | 退出重登，检查 Keychain |
| JSON 解码失败 | Swift 模型字段和后端响应不一致 | 对照 `Models.swift` 和后端 DTO |
| Xcode 签名失败 | Team 未配置 | Signing & Capabilities 选择 Team |
| HTTP 被拦截 | ATS 限制 | 本地放行 HTTP，生产改 HTTPS |

## 12. 提交前检查

1. 真机或模拟器能登录。
2. 客户列表能分页加载。
3. 关键词搜索能返回结果或空状态。
4. 客户详情能打开。
5. 退出登录后再次进入需要重新登录。
6. `X-Client-Source: ios` 在后端 `sys_api_log` 中可见。

## 13. 持仓截图导入

“持仓”页的导入区域支持支付宝、腾讯理财通，以及持仓快照、交易明细两种类型。
快照确认会覆盖同平台完整持仓；交易确认只调整同平台已有基金的持有金额。

交易预览按基金汇总，并显示买入、卖出、净额、当前金额、预计金额、应用数和跳过原因。
基金映射选项来自后端返回的同平台已有持仓，无法匹配的交易可以留空跳过。

命令行验证：

```bash
xcodebuild \
  -project ios/CrmMobile/CrmMobile.xcodeproj \
  -scheme CrmMobile \
  -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/crm-ios-derived \
  CODE_SIGNING_ALLOWED=NO \
  build
```
