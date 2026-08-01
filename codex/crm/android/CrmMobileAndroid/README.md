# CrmMobileAndroid

原生 Android CRM 客户信息 App，功能对齐 iOS 版。

当前工程使用 Java Activity，不是 Kotlin/Compose。完整开发、签名、Google Play
和国内渠道教程见 [`docs/manuals/ANDROID.md`](../../docs/manuals/ANDROID.md)，
基础与进阶学习路线见 [`docs/learning/ANDROID.md`](../../docs/learning/ANDROID.md)。

## 功能

- 使用现有 CRM 账号登录。
- 配置网关 API 地址，例如 `http://192.168.1.10:8780/api`。
- 展示客户列表，支持关键词搜索、手动刷新、分页加载。
- 基金列表采用 Excel 式二维表格，每只基金一行，名称和代码固定，指标可横向滚动。
- 支持按表头排序，并可按基金类型、是否可购买筛选。
- 持仓页支持支付宝、腾讯理财通的持仓快照和交易明细截图导入。
- 持仓快照覆盖同平台持仓；交易明细只增减同平台已有基金，不新建或删除。
- 展示客户详情。
- 请求头会带 `X-Client-Source: android`，后端 `sys_api_log.source` 可区分 Android 调用。
- Token 存储在 SharedPreferences。

## 真机运行

1. 确认 Android 手机和后端网关在同一局域网。
2. 启动后端 `gateway/system/customer` 服务。
3. 在电脑上查看局域网 IP，例如 `192.168.1.10`。
4. 用 Android Studio 打开 `android/CrmMobileAndroid`。
5. 登录页服务器地址填写：

```text
http://192.168.1.10:8780/api
```

不要填写 `127.0.0.1`，真机上的 `127.0.0.1` 指向手机自己。

## 构建

当前工程使用 Android Gradle Plugin `8.5.2`。推荐用 Android Studio 打开并运行。

如果本机已安装 Gradle 和 Android SDK，也可以执行：

```bash
cd android/CrmMobileAndroid
gradle assembleDebug
```
