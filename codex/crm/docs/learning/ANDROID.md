# Android 基础与进阶：结合 CrmMobileAndroid

目标：从 Android/Java 基础进阶到能维护当前 Activity + HttpURLConnection 工程并完成安全发布。Java 语言基础先读 [Java 教程](JAVA.md) 的语法、OOP、集合、异常、并发和 Maven/Gradle通用思想。

## 1. 跟一条链路

```text
LoginActivity 点击登录
-> 后台线程
-> ApiClient.login
-> Gateway /api/auth/login
-> LoginResult
-> SessionStore/SharedPreferences
-> runOnUiThread
-> MainTabActivity
```

先阅读 Manifest、`LoginActivity.java`、`ApiClient.java` 前 120 行、`SessionStore.java` 和一个简单 Model。

## 2. Android 项目结构

| 概念                 | 当前工程                      |
| ------------------ | ------------------------- |
| application module | `app/`                    |
| package/namespace  | `com.example.crm.android` |
| source             | `app/src/main/java/...`   |
| resources          | `app/src/main/res/`       |
| manifest           | 权限、Application、Activity   |
| Gradle             | 根插件版本 + app Android 配置    |

`local.properties` 通常包含本机 SDK 路径，不应作为团队通用配置或提交秘密。

## 3. Activity 生命周期

必须理解：

```text
onCreate -> onStart -> onResume
onPause -> onStop -> onDestroy
```

系统可因旋转、内存或导航重建 Activity。不能假设构造一次永远存在。

项目页面多在 `onCreate` 建 UI/绑定事件。网络完成时 Activity 可能已销毁：更新前检查状态，避免持有旧 Activity/View 的长任务。

### 状态

- 短期页面状态：成员字段/保存实例状态。
- 会话：`SessionStore`/SharedPreferences。
- Intent 输入：明确 key 和默认/缺失处理。
- 不把大 Bitmap/列表放 Intent extra。

## 4. Context、Intent 与 Manifest

`Context` 提供资源、启动组件和存储。Activity 本身是 Context，但长期对象持有 Activity Context 会泄漏；全局存储可使用 Application Context。

```java
Intent intent = new Intent(this, CustomerDetailActivity.class);
intent.putExtra("customerId", customer.getId());
startActivity(intent);
```

目标 Activity 必须在 Manifest 注册。`exported=false` 防止外部 App 直接启动内部页面；Launcher Activity 需要 `exported=true`。

## 5. View 与 UI

当前 UI 主要由 Java 构造，不是 XML/Compose。必须掌握：

- LayoutParams、dp/sp、资源与主题。
- TextView/EditText/Button/Recycler/Scroll 容器等基本控件。
- 点击监听和输入校验。
- loading、空态、错误和内容状态。
- 不硬编码用户可见文本/颜色（逐步迁到 resources）。

复杂表格要保持固定列和横向指标行高一致。大列表若当前直接构建所有 View，性能问题出现时再规划 RecyclerView 重构，不能在小修中半迁移。

## 6. 主线程与后台线程

Android UI 只能主线程更新，网络不能主线程执行。

```java
new Thread(() -> {
    try {
        PageResult<Customer> page = api.listCustomers(1, 20, null);
        runOnUiThread(() -> render(page));
    } catch (ApiException error) {
        runOnUiThread(() -> showError(error.getMessage()));
    }
}).start();
```

进阶风险：

- Activity 销毁后回调。
- 多请求竞态，旧结果覆盖新筛选。
- 重复点击启动多个线程。
- 无界创建 Thread。
- 异常未捕获导致进程崩溃。

当前模式可维护，但功能增长时应在独立架构变更中评估 Executor/ViewModel/协程等，不混在普通需求。

## 7. HttpURLConnection

请求步骤：

1. 构造正确 URL 和 query 编码。
2. 打开连接，设置 connect/read timeout。
3. 设置 method/header。
4. 必要时写 JSON body。
5. 读取 status；错误流与成功流不同。
6. 关闭流并 `disconnect()`。
7. 解析 `ApiResponse` 和 data。

Header：JSON、`X-Client-Source: android`、User-Agent、Bearer Token。Base URL 含 `/api`，方法 path 不含。

不要信任错误 body 一定是 JSON；网络断开、代理和 Nginx 可能返回 HTML/空响应。

## 8. URL 与联调

| 场景           | 地址                         |
| ------------ | -------------------------- |
| Emulator 到宿主 | `http://10.0.2.2:8780/api` |
| 真机到电脑        | `http://<LAN-IP>:8780/api` |
| 生产           | `https://<domain>/api`     |

query 使用 `URLEncoder`，路径段如 fundCode/stockCode 若来源不受控也需正确编码。不要简单字符串替换处理 URL。

## 9. JSON

当前 `org.json` 手工解析，必须区分：

```java
if (json.has("note") && !json.isNull("note")) {
    note = json.getString("note");
}
```

- `get...` 缺字段抛异常，适合必填。
- `opt...` 可能吞掉类型问题，适合有明确默认的可选字段。
- JSON null 与字符串 `"null"` 不同。
- Long/Decimal/日期不应无脑 `double`/String。

模型解析失败应说明模型/字段和脱敏上下文，不只抛“JSON error”。

## 10. SharedPreferences 与会话

SharedPreferences 适合简单偏好，不是强安全 Secret Store。当前保存 Base URL、Token、用户名。

必须做到：

- 私有 mode。
- 登录成功后原子更新必要字段。
- 退出清除 Token 和返回栈。
- 日志不输出全部 preferences。
- 生产评估 Android Keystore 加密 Token。

不要保存用户密码。

## 11. 分页、筛选和排序

- `current` 从 1 开始，`size` 与 API 一致。
- 刷新重置页码/数据；更多页追加并去重。
- 返回数量少不总等于最后一页，优先使用 `total/pages`。
- 筛选变化使旧请求失效。
- 排序字段只使用后端白名单，order 统一映射。
- Activity 返回时按业务需要刷新，不重复堆叠页面。

## 12. 图片、multipart 与内存

OCR 处理：

1. 通过受支持选择器获取 Uri。
2. 检查 MIME、数量、大小。
3. 按需读取/缩放，避免原图完整解码多次。
4. multipart 正确写 boundary/文件名/Content-Type。
5. 上传与解析在后台。
6. 预览确认前不改最终持仓。
7. 释放流/Bitmap/byte[] 引用。

大图片最常见问题是 OOM、上传超时和 Activity 被回收。测试低内存设备和多图。

## 13. Gradle

理解：

- Gradle Wrapper 锁版本（当前缺失）。
- Android Gradle Plugin 驱动 Android 构建。
- `compileSdk` 决定编译 API；`targetSdk` 声明行为目标；`minSdk` 决定最低设备。
- `debug/release` 是 build type。
- `versionCode` 必须递增；`versionName` 给用户看。

```bash
cd android/CrmMobileAndroid
gradle tasks
gradle assembleDebug
gradle bundleRelease
```

`bundleRelease` 只有签名/生产配置完成才算可上传产物；命令成功不等于隐私、安全和商店要求已完成。

## 14. 签名

Android 每个安装包必须签名。概念：

- keystore：密钥容器。
- alias：某个 key 的名字。
- signing certificate：用户设备/商店识别更新身份。
- upload key 与 Play app signing key 可能不同。

密钥丢失/改变会阻止升级。JKS/密码不入库，使用秘密管理器和加密备份。记录证书指纹：

```bash
keytool -list -v -keystore '<secure-keystore>' -alias '<alias>'
```

不要在共享聊天/日志粘贴密码。

## 15. 网络安全

当前 `usesCleartextTraffic=true` 是开发风险。生产：

- HTTPS + 可信证书。
- release 禁止全局 cleartext。
- 不信任任意证书/HostnameVerifier。
- Token 用 Keystore 方案评审。
- 禁止 debug 日志、测试后门和生产测试账号。
- 最小权限；Manifest 每项权限都能解释用户价值。

用户持仓/OCR 属敏感数据，隐私政策、传输、存储、删除和第三方共享必须一致。

## 16. 生命周期与内存进阶

常见泄漏：静态持有 Activity、未结束线程/回调、匿名内部类、Bitmap、Dialog。使用 Android Studio Profiler/Leak 工具证明。

配置变化会重建 Activity。当前项目以竖屏为主，但仍不能假设进程永不被系统杀死。关键操作状态可恢复，Token 从 SessionStore 重建。

## 17. 测试

测试层：

- JVM unit：JSON 模型、格式、排序/计算。
- Instrumentation：Activity/Intent/SharedPreferences。
- MockWebServer 类工具需要新增依赖后才使用；当前没有。
- 手工设备矩阵：API 23 附近、主流版本、target 新版本。

错误路径：无网、慢网、HTTP 非 JSON、业务 code 失败、Token 过期、429/503、大图片、返回/旋转。

## 18. 日志与排查

```bash
adb devices
adb logcat | rg 'AndroidRuntime|com.example.crm.android'
```

先找 FATAL EXCEPTION 和 cause 链。日志只记录状态、路径模板、耗时和脱敏 ID，不记录 JWT、密码、图片、完整用户数据。

网络问题同时用 curl 验证 Gateway，区分 App、设备网络和后端。

## 19. 发布

### Google Play

理解 AAB、Play App Signing、internal/closed/open/production track、staged rollout、Pre-launch report、ANR/crash。每次 versionCode 递增。

### 国内渠道

通常使用签名 APK，提交开发者资质、应用资料、隐私/权限说明、测试账号和可能的行业/版权材料。包名与签名保持一致，多渠道使用同一可复现构建或明确 flavor。

商店规则会变化，按 [Android 发布手册](../manuals/ANDROID.md) 中的官方链接发布当日重查。

## 20. 架构演进知识

理解但不要假装当前已使用：

- Kotlin：空安全、协程、Android 主流语言。
- AndroidX/Jetpack：Lifecycle、ViewModel、Navigation、Room。
- RecyclerView：大列表复用。
- Retrofit/OkHttp：声明式 API、拦截器和测试。
- MVVM：分离 UI/状态/数据。
- WorkManager：有约束的持久后台任务。

引入需独立 ADR/迁移计划、依赖评审和分阶段验证，不能在一个小功能里半套新旧架构。

## 21. 项目练习

### 练习 A：追踪登录

画出 Activity、线程、ApiClient、SessionStore 和页面跳转，指出每一步在哪个线程。

验收：能解释为什么网络不能在 UI 线程、退出如何清返回栈。

### 练习 B：可空字段

给一个模型增加真实 API 的可空展示字段，补 JVM 解析测试（如建立 test 目录）或至少固定 JSON 验证。

验收：null/缺失/有值不崩溃，不显示 `"null"`。

### 练习 C：竞态

不改生产逻辑，描述快速连续搜索时两个请求逆序返回的错误，并给出 request generation/取消策略。

验收：方案包含 Activity 生命周期与 UI 线程。

### 练习 D：Release 审计

只读检查 Gradle/Manifest，列出上架前缺口：签名、version、HTTPS/cleartext、隐私、测试和商店资料。

验收：不把 debug APK 当生产产物。

## 22. 独立维护完成标准

- [ ] 能解释 Activity 生命周期、Context、Intent、Manifest。
- [ ] 能正确区分 UI/后台线程并处理生命周期竞态。
- [ ] 能维护 HttpURLConnection、JSON、Token 和 multipart。
- [ ] 能处理分页、空值、金额、图片内存和错误。
- [ ] 能执行 Gradle 构建并理解 SDK/build type/version。
- [ ] 能生成并安全管理签名，理解更新身份。
- [ ] 能评估 HTTPS、Keystore、权限、隐私和旧版本兼容。
- [ ] 能按 Play/国内渠道测试、灰度、监控和修复发布。
