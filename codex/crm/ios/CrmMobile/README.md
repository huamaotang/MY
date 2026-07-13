# CrmMobile

原生 iPhone CRM 客户信息 App，使用 SwiftUI 实现。

## 功能

- 使用现有 CRM 账号登录。
- 配置网关 API 地址，例如 `http://192.168.1.10:8780/api`。
- 展示客户列表，支持关键词搜索、下拉刷新、分页加载。
- 展示客户详情。
- Token 存储在 Keychain。

## 真机运行

1. 确认 iPhone 和后端网关在同一局域网。
2. 启动后端 `gateway/system/customer` 服务。
3. 在 Mac 上查看局域网 IP，例如 `192.168.1.10`。
4. 用 Xcode 打开 `CrmMobile.xcodeproj`。
5. 选择自己的 Team 后运行到 iPhone。
6. 登录页服务器地址填写：

```text
http://192.168.1.10:8780/api
```

不要填写 `127.0.0.1`，真机上的 `127.0.0.1` 指向 iPhone 自己。

## 验证限制

当前机器只安装了 Command Line Tools，`xcodebuild` 无法构建 iOS App。需要安装完整 Xcode，或执行：

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

然后在仓库根目录运行：

```bash
xcodebuild -project ios/CrmMobile/CrmMobile.xcodeproj -scheme CrmMobile -destination 'generic/platform=iOS' build
```
