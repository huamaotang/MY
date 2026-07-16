# Android 开发手册

本文覆盖 `android/CrmMobileAndroid/` 原生 Android App 的运行、联调、代码结构和排错。

## 1. 技术栈

| 项 | 说明 |
| --- | --- |
| 语言 | Java |
| UI | 原生 Android View |
| 网络 | `HttpURLConnection` |
| JSON | `org.json` |
| Token 存储 | `SharedPreferences` |
| 构建 | Gradle + Android Gradle Plugin 8.5.2 |

## 2. 目录结构

```text
android/CrmMobileAndroid/
  README.md
  settings.gradle
  build.gradle
  app/
    build.gradle
    src/main/
      AndroidManifest.xml
      java/com/example/crm/android/
        LoginActivity.java
        CustomerListActivity.java
        CustomerDetailActivity.java
        ApiClient.java
        SessionStore.java
        Customer.java
        LoginResult.java
        PageResult.java
        ApiException.java
        Ui.java
      res/
        values/
        mipmap-*/
```

## 3. 运行前准备

必须启动后端：

```text
Nacos + Redis + system + customer + gateway
```

电脑和 Android 手机必须在同一局域网。

查看电脑局域网 IP：

```bash
ipconfig getifaddr en0
```

假设输出：

```text
192.168.1.10
```

登录页服务器地址填写：

```text
http://192.168.1.10:8780/api
```

不要填写：

```text
http://127.0.0.1:8780/api
```

真机上的 `127.0.0.1` 指向手机自己。

Android 模拟器如果访问宿主机，可尝试：

```text
http://10.0.2.2:8780/api
```

## 4. 用 Android Studio 运行

1. 打开 Android Studio。
2. Open 项目：`android/CrmMobileAndroid`。
3. 等 Gradle Sync 完成。
4. 选择真机或模拟器。
5. 点击 Run。
6. 登录页输入服务器地址、用户名、密码。

默认账号：

```text
admin / admin123
```

## 5. 命令行构建

如果本机已有 Gradle 和 Android SDK：

```bash
cd android/CrmMobileAndroid
gradle assembleDebug
```

产物通常在：

```text
android/CrmMobileAndroid/app/build/outputs/apk/debug/
```

## 6. 网络层说明

`ApiClient.java` 负责所有请求。

初始化：

```java
new ApiClient(baseUrl, token)
```

`setBaseUrl()` 会做两件事：

1. 去掉结尾 `/`。
2. 如果没写协议，自动补 `http://`。

当前 API：

| 方法 | 后端路径 | 说明 |
| --- | --- | --- |
| `login` | `POST /auth/login` | 登录 |
| `listCustomers` | `GET /customers` | 客户分页 |
| `customerDetail` | `GET /customers/{id}` | 客户详情 |

公共请求头：

```text
Accept: application/json
Content-Type: application/json
X-Client-Source: android
User-Agent: CrmMobile/Android
Authorization: Bearer <token>
```

注意：`baseUrl` 应包含 `/api`，方法里的 `path` 不包含 `/api`。

## 7. 登录态

`SessionStore` 使用 `SharedPreferences` 保存：

```text
baseUrl
token
username
```

登录成功后客户列表页复用 token。退出登录或 token 失效时，需要清理本地 session 后重新登录。

## 8. 新增接口

以新增联系人列表为例：

1. 后端确认 `/api/contacts` 可用。
2. 新建模型 `Contact.java`：

```java
public class Contact {
    public Integer id;
    public Integer customerId;
    public String contactName;
    public String mobile;
}
```

3. 在 `ApiClient.java` 增加：

```java
public JSONArray listContacts(int customerId) throws ApiException {
    return requestArray("GET", "/contacts?customerId=" + customerId, null);
}
```

当前 `ApiClient` 主要返回 `JSONObject`，如果要返回数组，可以补一个 `requestArray`，或让后端统一包成对象。

4. 新建 Activity 或在客户详情页中增加联系人区域。
5. 在 `AndroidManifest.xml` 注册 Activity。
6. 真机验证。

## 9. 新增页面建议

原生 Activity 建议按这个顺序写：

1. `onCreate()` 读取 session。
2. 初始化 View。
3. 绑定点击事件。
4. 后台线程请求网络。
5. `runOnUiThread()` 更新 UI。
6. 失败时展示 Toast 或错误视图。

不要在主线程直接请求网络。

## 10. HTTP 明文访问

本地后端是 HTTP。若 Android 版本限制明文流量，检查 `AndroidManifest.xml` 是否允许 cleartext。生产环境建议统一 HTTPS。

## 11. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 真机连不上 | 地址填了 `127.0.0.1` | 改成电脑局域网 IP |
| 模拟器连不上 | 地址错误 | 用 `10.0.2.2` 访问宿主机 |
| 登录后列表 403 | token 未保存或过期 | 清理 session 重新登录 |
| 返回 429 | Gateway 限流 | 降低请求频率或调 Nacos 限流配置 |
| JSON 解析异常 | 字段名不一致 | 对照后端返回和 `Customer.fromJson` |
| Gradle Sync 失败 | SDK/AGP/网络问题 | 检查 Android Studio SDK 和 Gradle 配置 |
| 安装失败 | 包名或签名问题 | Clean Project 后重装 |

## 12. 调试命令

查看设备：

```bash
adb devices
```

查看日志：

```bash
adb logcat | grep CrmMobile
```

安装 APK：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 13. 提交前检查

1. 真机或模拟器能登录。
2. 客户列表能加载。
3. 搜索能工作。
4. 分页加载能工作。
5. 客户详情能打开。
6. 断网和服务器地址错误时有可见提示。
7. 后端 `sys_api_log.source` 能看到 `android`。
