# CRM 开发手册总览

这组文档按项目边界拆分，适合新同事从零搭环境，也适合已有开发者按步骤排查问题。

## 阅读顺序

1. 先读本页，确认系统由哪些项目组成。
2. 后端开发先读 [后端微服务开发手册](BACKEND.md)。
3. 管理台开发先读 [前端管理台开发手册](FRONTEND.md)。
4. iPhone App 开发先读 [iOS 开发手册](IOS.md)。
5. Android App 开发先读 [Android 开发手册](ANDROID.md)。
6. 本地 Nacos、Redis、Nginx、生产部署相关读 [部署与运维手册](DEPLOYMENT.md)。

## 项目组成

| 项目 | 路径 | 说明 |
| --- | --- | --- |
| 后端父工程 | `backend/` | Maven 多模块工程 |
| 公共库 | `backend/core/` | 通用响应、异常、安全、日志、MyBatis 配置 |
| API 网关 | `backend/gateway/` | Spring Cloud Gateway，统一暴露 `/api/**`，做路由、CORS、限流 |
| 系统服务 | `backend/system/` | 登录、用户、角色、菜单 |
| 客户服务 | `backend/customer/` | 客户、联系人、跟进记录 |
| 兼容单体 | `backend/admin/` | 原单体迁移后的服务，默认微服务链路不依赖它 |
| 前端管理台 | `frontend/` | React + TypeScript + Vite + Ant Design |
| iOS App | `ios/CrmMobile/` | SwiftUI 原生 iPhone App |
| Android App | `android/CrmMobileAndroid/` | 原生 Android App |
| 数据库脚本 | `sql/` | MySQL 建表和初始化数据 |
| 部署配置 | `deploy/` | Nacos、Nginx、平滑重启脚本 |

## 默认本地地址

| 服务 | 地址 |
| --- | --- |
| Nacos | `http://127.0.0.1:8848/nacos` |
| Redis | `127.0.0.1:6379` |
| Gateway | `http://127.0.0.1:8780` |
| System | `http://127.0.0.1:8782` |
| Customer | `http://127.0.0.1:8783` |
| Admin | `http://127.0.0.1:8781/api` |
| Frontend | Vite 启动后终端输出的地址，通常是 `http://127.0.0.1:5173` |

## 最小可运行链路

最小链路是：

```text
MySQL -> Nacos + Redis -> system + customer + gateway -> frontend/iOS/Android
```

`gateway` 不直接访问数据库；它依赖 Nacos 发现 `system` 和 `customer`，并依赖 Redis 做分布式限流。

## 常用账号

初始化数据提供默认账号：

```text
用户名：admin
密码：admin123
```

生产环境不要继续使用默认密码，也不要继续使用 Nacos 配置里的开发默认 JWT 密钥。

## 新功能开发建议顺序

1. 先确认需求属于系统域还是客户域。
2. 后端先建表或改表，补实体、Mapper、Service、Controller。
3. 如果接口要从前端或移动端访问，更新 `deploy/nacos/gateway-dev.yaml` 路由。
4. 前端在 `frontend/src/api.ts` 增加 API 函数，再接入页面。
5. 移动端在 `ApiClient`、模型和页面中同步新增能力。
6. 本地从接口到页面完整跑一遍。
7. 更新对应手册或 `docs/MODULES.md`。

## 常见判断

| 现象 | 优先检查 |
| --- | --- |
| 前端提示请求失败 | Gateway 是否启动、`VITE_API_BASE` 是否指向 `/api` |
| Gateway 返回 `503` | Nacos 里下游服务是否 healthy |
| Gateway 返回 `429` | 限流生效，检查 Redis、限流配置和客户端 IP |
| Gateway health 是 `DOWN` | Redis 是否可连接、密码是否正确 |
| 下游返回 `403` | 是否缺少 `Authorization: Bearer <token>` |
| 登录失败 | 数据库初始化、密码、JWT 配置、system 服务日志 |
| 真机 App 连不上 | 不要填 `127.0.0.1`，要填电脑局域网 IP |
