# CRM 配置与环境变量参考

本文集中记录仓库当前代码会读取的配置。配置值的事实来源是 Java `bootstrap.yml`、Nacos YAML、Python/脚本、Compose、Web 构建和移动端登录页；示例值只适用于本地开发。

## 1. 配置加载总览

| 子系统 | 配置入口 | 优先级/生效时机 |
| --- | --- | --- |
| Java | 进程环境变量、`bootstrap.yml`、Nacos `<service>-<profile>.yaml` | 环境变量替换 Nacos 占位符；配置刷新能力取决于属性是否支持动态刷新，关键配置变更后应重启验证 |
| Python CLI | 命令行参数、进程环境、`fund_spider/.env`、代码默认值 | 命令行最高；进程环境高于 `.env` |
| Prefect 脚本 | 当前 Shell/systemd 环境和脚本默认值 | `run_prefect_*.sh` **不会主动读取** `fund_spider/.env` |
| Compose | Compose 同目录 `.env`、当前 Shell、Compose 默认值 | 当前 Shell 通常覆盖 `.env`；只影响该 Compose 项目 |
| Web | `VITE_API_BASE` | 构建时固化，修改后必须重新构建 |
| iOS/Android | 登录页输入并由 SessionStore 保存 | 运行时生效；生产包不应依赖用户输入开发地址 |

不要假设同名 `.env` 会跨目录或跨进程自动生效。排查配置时同时检查“版本库默认值、进程实际环境、Nacos 运行态、容器实际环境”。

## 2. Java 与 Nacos

### 2.1 启动连接

| 变量 | 默认值 | 使用方 | 说明 |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | 所有 Java 服务 | 决定读取 `<service>-<profile>.yaml` |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | 所有 Java 服务 | Nacos Config/Discovery 地址 |
| `NACOS_GROUP` | `DEFAULT_GROUP` | 所有 Java 服务 | 配置和注册分组 |

当前仓库只提供 `*-dev.yaml`。生产必须创建隔离的 profile/namespace/group 和受保护配置，不能把开发 YAML 原样复制上线。

### 2.2 CRM 与基金数据库

| 变量 | 使用方 | 说明 |
| --- | --- | --- |
| `MYSQL_URL` | `system/customer/admin` | `crm` JDBC URL |
| `MYSQL_USER` | `system/customer/admin`；也可被 fund 兜底使用 | CRM MySQL 用户 |
| `MYSQL_PASSWORD` | `system/customer/admin`；也可被 fund 兜底使用 | CRM MySQL 密码 |
| `FUND_MYSQL_URL` | `fund` | `fund` JDBC URL |
| `FUND_MYSQL_USER` | `fund` | 未设置时回退 `MYSQL_USER` |
| `FUND_MYSQL_PASSWORD` | `fund` | 未设置时回退 `MYSQL_PASSWORD` |

生产建议拆分 Java 读写账号和 Python 写入账号。修改 JDBC URL 时保留正确字符集、时区和 TLS 参数。

### 2.3 JWT、访问日志与限流

| 变量 | 默认值/风险 | 使用方 |
| --- | --- | --- |
| `CRM_JWT_SECRET` | 开发占位密钥，生产必须替换 | `system/customer/fund/admin` |
| `CRM_JWT_EXPIRE_SECONDS` | `86400` | 同上 |
| `CRM_ACCESS_LOG_ADMIN_URL` | `http://127.0.0.1:8781/api` | Gateway |
| `CRM_ACCESS_LOG_TOKEN` | 开发占位 Token，生产必须替换且两侧一致 | Gateway/Admin |
| `REDIS_HOST` | `127.0.0.1` | Gateway |
| `REDIS_PORT` | `6379` | Gateway |
| `REDIS_PASSWORD` | Nacos 开发 YAML 回退为 `qwer8989` | Gateway |
| `GATEWAY_RATE_LIMIT_REPLENISH_RATE` | `20` | Gateway，每秒补充令牌 |
| `GATEWAY_RATE_LIMIT_BURST_CAPACITY` | `40` | Gateway，突发容量 |

当前 `deploy/nacos/docker-compose.yml` 启动的 Redis **没有配置密码**，而 `gateway-dev.yaml` 默认填写了 `qwer8989`。本地必须二选一并保持一致：

1. 为 Redis 配置 `requirepass`，然后把相同密码传给 Gateway；或
2. 保持无密码 Redis，并以空 `REDIS_PASSWORD` 启动 Gateway，例如：

```bash
cd backend
REDIS_PASSWORD='' mvn -pl gateway spring-boot:run
```

验证时不要只看容器是否运行：

```bash
redis-cli -h 127.0.0.1 -p 6379 ping
curl -fsS http://127.0.0.1:8780/actuator/health
```

### 2.4 OCR 子进程

| 变量 | 默认值 | 使用方 | 说明 |
| --- | --- | --- | --- |
| `CRM_PYTHON_EXECUTABLE` | `python3` | Java `fund` | 必须能导入 RapidOCR 依赖 |
| `CRM_PYTHON_OCR_SCRIPT` | `fund_spider/tools/portfolio_holding_ocr.py` | Java `fund` | 可用绝对路径；相对路径会从进程工作目录向上最多查找 5 层 |

生产推荐显式配置：

```env
CRM_PYTHON_EXECUTABLE=/opt/crm/fund_spider/.venv/bin/python
CRM_PYTHON_OCR_SCRIPT=/opt/crm/fund_spider/tools/portfolio_holding_ocr.py
```

## 3. Python 业务任务

### 3.1 数据库与 HTTP

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `127.0.0.1` | Fund MySQL 主机 |
| `DB_PORT` | `3306` | 端口 |
| `DB_USER` | `root` | 本地兜底；生产不得使用 root |
| `DB_PASSWORD` | 开发占位值 | 密码，必须受保护 |
| `DB_NAME` | `fund` | 数据库名 |
| `REQUEST_MIN_DELAY_SECONDS` | `1.5` | 请求最小随机延迟 |
| `REQUEST_MAX_DELAY_SECONDS` | `4.0` | 请求最大随机延迟 |
| `REQUEST_TIMEOUT_SECONDS` | `10` | 单请求超时 |
| `REQUEST_MAX_RETRIES` | `3` | 最大重试次数 |

### 3.2 通用批次与日志

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PAGE_SIZE` | `50` | 基金排行类任务页大小 |
| `START_PAGE` | `1` | 起始页 |
| `MAX_PAGES` | 空 | 空表示按源端页数继续 |
| `FUND_CODE` | 空 | 只选择一个基金；部分任务使用领域别名 |
| `FUND_START_CODE` | 空 | 从指定基金代码开始 |
| `FUND_LIMIT` | 空 | 限制基金数量，试跑应设置小值 |
| `FUND_OFFSET` | `0` | 跳过前 N 个基金 |
| `BASIC_REFRESH_LIST` | 空 | `1/0`，是否先刷新基金列表 |
| `LOG_SQL` | `0` | SQL 摘要日志 |
| `LOG_SQL_PARAMS` | `0` | SQL 参数日志，可能含大量业务数据 |
| `LOG_SQL_MAX_PARAMS` | `3` | 参数日志样本上限 |

领域选择器 `NAV_FUND_CODE`、`FEATURE_FUND_CODE`、`RATING_FUND_CODE`、`HOLDING_FUND_CODE` 优先用于对应任务；为空时回退通用批次选择。

### 3.3 各任务变量

| 任务 | 变量 | 默认/取值 |
| --- | --- | --- |
| 历史净值 | `NAV_PAGE_SIZE`、`NAV_START_PAGE`、`NAV_MAX_PAGES` | `20`、`1`、空 |
| 历史净值 | `NAV_PAGE_WORKERS`、`NAV_WRITE_BATCH_SIZE` | `4`、`200` |
| 历史净值 | `NAV_START_DATE`、`NAV_END_DATE` | 空；支持 `YYYYMMDD`/`YYYY-MM-DD` |
| 基金特征 | `FEATURE_STALE_FIRST`、`FEATURE_SCHEDULE_FUND_LIMIT` | `0`、`2000`；自动任务按最久未刷新优先分批 |
| 评级 | `RATING_PAGE_SIZE`、`RATING_MAX_PAGES` | `50`、空 |
| 持仓 | `HOLDING_TOP_LINE`、`HOLDING_YEAR`、`HOLDING_MONTH` | `10`、空、空 |
| 养基宝资讯 | `YJB_NEWS_SCORE` | `2` |
| 新浪资讯 | `SINA_NEWS_PAGE_SIZE`、`SINA_NEWS_MAX_PAGES`、`SINA_NEWS_TAG` | `20`、`1`、`0` |
| 股票 | `STOCK_PAGE_SIZE`、`STOCK_MARKET` | `100`；`cn/hk/all` |

### 3.4 第三方敏感值

| 变量 | 说明 |
| --- | --- |
| `YJB_AUTHORIZATION` | 授权 Header |
| `YJB_REQUEST_SIGN` | 请求签名 |
| `YJB_REQUEST_TIME` | 请求时间字段 |
| `YJB_USER_AGENT` | 合法客户端 User-Agent |
| `YJB_COOKIE` | Cookie |

这些值不得进入 Git、日志、截图、Issue 或命令历史。授权失败应停止任务，不得靠高频重试绕过。

## 4. Prefect 与脚本

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PREFECT_EXECUTABLE` | `fund_spider/.venv/bin/prefect` | Prefect 可执行文件 |
| `PREFECT_HOME` | `fund_spider/.prefect` | 本机 Prefect 状态目录 |
| `PREFECT_API_URL` | `http://127.0.0.1:4200/api` | Worker/CLI 使用的 Server API |
| `PREFECT_SERVER_HOST` | `127.0.0.1` | Server 监听地址 |
| `PREFECT_SERVER_PORT` | `4200` | Server 端口 |
| `PREFECT_UI_API_URL` | 根据 host/port 生成 | 浏览器 UI 请求 API 的地址 |
| `PREFECT_SERVER_DATABASE_CONNECTION_URL` | 本地 `prefect:prefect@127.0.0.1:5433` | PostgreSQL async URL，密码需 URL 编码 |
| `PREFECT_SERVER_ANALYTICS_ENABLED` | `false` | 自托管 Server 遥测开关 |
| `PREFECT_SERVER_UI_SHOW_PROMOTIONAL_CONTENT` | `false` | UI 推广内容开关 |
| `PREFECT_WORK_POOL` | `crm-process-pool` | Process Work Pool |
| `PREFECT_WORKER_NAME` | `crm-worker` | Worker 名称 |
| `PREFECT_WORKER_LIMIT` | `4` | 同时运行 Flow 数；为行情、资讯和批处理保留独立槽位 |
| `CRM_FUND_SPIDER_DIR` | 自动推导项目目录 | `prefect.yaml` 工作目录模板 |
| `PYTHON_EXECUTABLE` | `.venv/bin/python` | `run_*.sh` 业务命令解释器 |
| `PYTHON_FALLBACK` | `python3` | venv 不存在时兜底 |

Compose 的 PostgreSQL 变量：

| 变量 | 默认值 |
| --- | --- |
| `PREFECT_POSTGRES_DB` | `prefect` |
| `PREFECT_POSTGRES_USER` | `prefect` |
| `PREFECT_POSTGRES_PASSWORD` | `prefect`，必须替换 |
| `PREFECT_POSTGRES_PORT` | `5433` |

重要：`deploy/prefect/.env` 只会被该目录的 Docker Compose 自动读取，不会自动导出到 `run_prefect_server.sh`。修改 PostgreSQL 密码后，必须把匹配且 URL 编码后的 `PREFECT_SERVER_DATABASE_CONNECTION_URL` 注入 Server 进程。CentOS systemd 通过 `/etc/crm/fund-spider.env` 注入；本地终端需要显式 `export`。

## 5. Web、iOS 与 Android

| 平台 | 配置 | 当前规则 |
| --- | --- | --- |
| Web | `VITE_API_BASE` | 默认 `/api`；必须包含 `/api`，构建后不可动态改变 |
| iOS | 登录页 Base URL | Simulator 可用 `127.0.0.1`；真机用局域网 IP；生产 HTTPS |
| Android | 登录页 Base URL | Emulator 用 `10.0.2.2`；真机用局域网 IP；生产 HTTPS |

iOS 当前 Bundle ID 为 `com.example.crm.mobile`，Android applicationId 为 `com.example.crm.android`。正式发布前必须确认组织所有权、签名与商店记录，不能上线后随意修改。

## 6. 部署脚本变量

`deploy/graceful-restart.sh` 还读取：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `JAVA_BIN` | `java` | Java 命令 |
| `JAVA_OPTS` | 空 | JVM 参数；不要把秘密放进可见进程参数 |
| `ACTUATOR_HOST` | `127.0.0.1` | 本机探测地址 |
| `ACTUATOR_BASE` | 根据 host/port 生成 | Admin 因 context path 需显式 `/api/actuator` |
| `DRAIN_SECONDS` | `10` | 标记 DOWN 后等待时间 |
| `STOP_TIMEOUT` | `45` | 超时后会 SIGKILL |
| `LOG_DIR` | `deploy/logs` | 标准输出日志目录 |

该脚本不负责等待新实例健康，也不提供单实例零停机保证；启动后必须人工/自动执行健康与业务冒烟。

## 7. 本地基线的已知注意项

- Nacos Compose 关闭鉴权并将端口发布到宿主机，只能在可信开发网络使用。
- Nacos Compose 固定 `linux/arm64`，x86_64 主机可能需要移除/调整 `platform` 后再验证镜像。
- Redis Compose 无密码，与 Gateway 开发默认值不一致，必须按第 2.3 节处理。
- Prefect PostgreSQL 示例密码、连接 URL 和 Server 进程环境必须同步。
- `deploy/macos/*.plist` 含当前开发机绝对路径，复制到其他机器前必须替换全部路径。
- Nginx 示例只监听 HTTP 80；生产 TLS、域名、证书和安全 Header 需由部署方补齐。

完整上线阻塞项见 [已知限制与技术债](KNOWN_LIMITATIONS.md)。

## 8. 配置变更检查表

- [ ] 明确修改的是 local/test/production，Data ID、profile、namespace 和 group 正确。
- [ ] 版本库配置与 Nacos/容器/进程运行态分别核对。
- [ ] 密码、URL、用户名的生产值没有进入 Git、日志或命令历史。
- [ ] Redis、JWT、访问日志 Token 等多端共享值保持一致。
- [ ] Prefect PostgreSQL 密码已 URL 编码且 Compose/Server 一致。
- [ ] 配置变更后执行健康检查、登录、关键业务和任务 dry-run。
- [ ] 记录变更前后值的脱敏摘要、操作者、时间和回滚值。
