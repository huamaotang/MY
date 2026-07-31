# CrmMobile

原生 iPhone CRM 客户信息 App，使用 SwiftUI 实现。

## 功能

- 使用现有 CRM 账号登录。
- 配置网关 API 地址，例如 `http://192.168.1.10:8780/api`。
- 展示客户列表，支持关键词搜索、下拉刷新、分页加载。
- 基金列表采用 Excel 式二维表格，每只基金一行，名称和代码固定，指标可横向滚动。
- 支持按表头排序，并可按基金类型、是否可购买筛选。
- 持仓页支持支付宝、腾讯理财通的持仓快照和交易明细截图导入。
- 持仓快照覆盖同平台持仓；交易明细只增减同平台已有基金，不新建或删除。
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

## 命令行验证

如果 `xcode-select` 没有指向完整 Xcode，先执行：

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

然后在仓库根目录运行：

```bash
xcodebuild \
  -project ios/CrmMobile/CrmMobile.xcodeproj \
  -scheme CrmMobile \
  -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/crm-ios-derived \
  CODE_SIGNING_ALLOWED=NO \
  build
```
