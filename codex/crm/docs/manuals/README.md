# CRM 开发手册总览

这套手册面向第一次接手仓库、需要独立开发和生产维护的人员。所有命令默认从仓库根目录开始，除非代码块先执行了 `cd`。

## 推荐阅读路径

### 第一天：把系统跑起来

1. 阅读 [系统架构与模块说明](../MODULES.md)，理解服务、数据库和数据任务的边界。
2. 按 [部署与运维手册](DEPLOYMENT.md) 启动 MySQL、Nacos、Redis 和 Prefect PostgreSQL。
3. 按 [Java 后端手册](BACKEND.md) 启动 `system/customer/fund/gateway`。
4. 选择 [Web](FRONTEND.md)、[iOS](IOS.md) 或 [Android](ANDROID.md) 运行一个客户端。
5. 用 [API 参考](../reference/API.md) 完成登录和一个受保护请求。

### 第一周：能安全修改代码

- Java 开发者：阅读 [Java 学习教程](../learning/JAVA.md)，再从 Controller 追到 Service、Mapper 和表。
- Python 开发者：阅读 [Python 数据任务手册](PYTHON.md) 和 [Python 学习教程](../learning/PYTHON.md)。
- iOS 开发者：阅读 [iOS 手册](IOS.md) 与 [iOS 学习教程](../learning/IOS.md)。
- Android 开发者：阅读 [Android 手册](ANDROID.md) 与 [Android 学习教程](../learning/ANDROID.md)。
- 所有人：知道如何查 [数据库参考](../reference/DATABASE.md) 和执行发布前检查。

## 文档地图

| 文档 | 解决的问题 |
| --- | --- |
| [系统架构与模块说明](../MODULES.md) | 服务为什么这样拆、请求和数据怎样流动、哪里是唯一事实来源 |
| [Java 后端手册](BACKEND.md) | Java 环境、分层开发、鉴权、构建、运行与排错 |
| [Python 数据任务手册](PYTHON.md) | 爬虫、评分、数据库、Prefect、测试与发布 |
| [Web 前端手册](FRONTEND.md) | React 管理台、API 封装、页面开发与发布 |
| [iOS 手册](IOS.md) | SwiftUI、网络、登录态、真机和 App Store 发布 |
| [Android 手册](ANDROID.md) | Java Activity、网络、线程、签名和多渠道发布 |
| [部署与运维手册](DEPLOYMENT.md) | 本地/生产拓扑、发布顺序、备份、回滚和巡检 |
| [API 参考](../reference/API.md) | REST、客户端覆盖、Python CLI 与 Prefect 运维接口 |
| [数据库参考](../reference/DATABASE.md) | CRM/Fund 表、归属、初始化、迁移与备份 |
| [学习教程总览](../learning/README.md) | 四条从基础到能提交代码的学习路线 |

## 系统组件与默认地址

| 组件 | 默认地址 | 是否对公网开放 |
| --- | --- | --- |
| Nacos | `http://127.0.0.1:8848/nacos` | 否；生产必须启用鉴权并限制网络 |
| Redis | `127.0.0.1:6379` | 否 |
| Gateway | `http://127.0.0.1:8780` | 生产只通过 HTTPS/Nginx 暴露 |
| System | `http://127.0.0.1:8782` | 否 |
| Customer | `http://127.0.0.1:8783` | 否 |
| Fund | `http://127.0.0.1:8784` | 否 |
| Admin | `http://127.0.0.1:8781/api` | 否；兼容与日志接收用途 |
| Frontend | 通常 `http://127.0.0.1:5173` | 开发环境 |
| Prefect | `http://127.0.0.1:4200` | 否；经 SSH/VPN/认证代理访问 |

## 新功能的标准顺序

1. 确认业务归属：系统、客户、基金、数据采集还是客户端展示。
2. 先确认表结构与迁移是否需要变化；生产永远使用增量迁移。
3. 后端按 Entity/DTO → Mapper → Service → Controller 编写并测试。
4. 新路径前缀才修改 Gateway 路由；已有 `/funds/**` 等路径不需重复配置。
5. 先更新客户端类型，再更新 API 调用和 UI。
6. 从 Gateway 做端到端验证，不以直连下游服务作为最终验收。
7. 同步 API、数据库、项目手册和发布检查项。

## 常见故障入口

| 现象 | 第一检查点 | 详细说明 |
| --- | --- | --- |
| Gateway `503` | 下游是否注册到 Nacos | [BACKEND.md](BACKEND.md) |
| Gateway `429` | Redis 与限流参数 | [DEPLOYMENT.md](DEPLOYMENT.md) |
| API `401/403` | Token、角色、权限码 | [API.md](../reference/API.md) |
| Python 任务失败 | Prefect Flow 日志与 `.env` | [PYTHON.md](PYTHON.md) |
| 真机连不上 | 地址是否用了电脑局域网 IP | [IOS.md](IOS.md) / [ANDROID.md](ANDROID.md) |
| 数据字段为空 | 爬虫刷新时间、表和 Java DTO | [DATABASE.md](../reference/DATABASE.md) |

## 文档变更检查

- 所有链接使用相对路径，不包含某位开发者的主目录。
- 示例密钥必须是明显的占位符；本地默认值必须标记“不可用于生产”。
- 新增 Controller 方法后更新 API 参考和所有调用该接口的客户端表。
- 新增环境变量后更新 `.env.example` 和对应项目手册。
- 新增表或迁移后更新数据库参考、发布顺序和回滚说明。
- 命令经过实际执行，或明确注明因缺少 SDK/账号未执行。
