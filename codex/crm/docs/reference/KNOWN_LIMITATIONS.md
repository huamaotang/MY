# 已知限制、上线阻塞项与技术债

本文记录二次代码审计确认的“当前事实”。它不是缺陷已经修复的声明，而是维护人员在开发、发布和事故处理中必须显式处理的风险清单。状态变化时应在同一提交更新代码、验证证据和本文。

## 1. 生产发布前必须关闭的风险

| 项目 | 当前事实 | 风险 | 上线门槛 |
| --- | --- | --- | --- |
| 默认账号 | `sql/schema.sql` 使用 `admin/admin123`，密码格式为 `{noop}` | 默认管理员被直接接管 | 生产不执行开发种子或首次启动立即替换；使用强哈希并验证旧密码失效 |
| 新用户默认密码 | 创建用户未传密码时，Service 会编码固定值 `123456` | 批量弱口令 | 生产要求显式随机初始密码、首次登录改密或邀请流程；不能依赖当前默认值 |
| Java 默认秘密 | Nacos 开发 YAML 包含 JWT、Redis、访问日志占位秘密 | 配置遗漏时服务仍可能启动 | 生产秘密必须由秘密管理器/受保护环境注入，并做“开发默认值不存在”门禁 |
| Nacos | 本地 Compose 关闭鉴权，端口发布到宿主机 | 配置泄露/篡改 | 生产启用鉴权、隔离 namespace/network/ACL，禁止公网暴露 |
| CORS | Gateway 和 MVC 当前允许任意 origin pattern，并允许凭据 | 非预期站点可发起跨域调用 | 生产改为明确 HTTPS 域名白名单，验证预检和旧客户端 |
| Actuator | health/info/serviceregistry 可免登录；Gateway 还暴露 routes 相关端点 | 拓扑泄露或实例状态被修改 | 只允许监控/管理网络访问；公网 Nginx 不转发 Actuator |
| iOS 网络 | `NSAllowsArbitraryLoads=true` | 明文/不可信网络可用 | 使用可信 HTTPS，移除全局例外，完成真机与商店包复测 |
| Android 网络 | `usesCleartextTraffic=true`，Token 存 SharedPreferences | 明文传输和会话保护不足 | Release 禁用全局明文，评估 Keystore 加密并完成安全测试 |
| 移动标识/签名 | iOS/Android 使用 `com.example...`；Android 无 release signingConfig | 无法形成可持续升级链 | 确认组织 Bundle/Application ID、签名资产、备份和商店记录 |
| Prefect | 默认 PostgreSQL 密码为 `prefect`，UI/API 无业务级公网认证 | 调度被接管、运行记录泄露 | 替换密码；UI 仅 SSH/VPN 或带身份认证的反代访问 |
| Nginx | 仓库示例只监听 HTTP 80 | 不满足生产传输安全 | 增加 TLS、证书续期、安全 Header、可信代理边界并完成扫描 |

## 2. 数据完整性限制

### 2.1 删除并非完整级联

- 当前客户删除只删除 `crm_customer`，不会清理 `crm_contact`、`crm_follow_record`、商机、合同或回款。
- 当前基金删除只清理用户自选和 `fund_detail`，不会清理净值、业绩、评级、披露持仓、评分、用户持仓等关联数据。
- 数据库多数业务关系没有外键级联。

因此，在明确归档/级联/拒绝策略并补测试前，生产 UI/API 的客户和基金删除应视为高风险操作。处理真实数据前先查询关联行、备份，并优先实现 Service 层事务策略，而不是临时手写多表删除。

### 2.2 数据范围尚未强制执行

`sys_role.data_scope` 已存储，但客户查询没有完整按 `SELF/部门/全部` 过滤。RBAC 权限码控制“能否调用”，不能等同于“只能看到自己数据”。涉及多租户、销售私域或隐私隔离时必须先补行级授权设计和测试。

### 2.3 缺少自动保留/清理任务

仓库当前没有统一的数据保留任务。以下数据会持续增长：

- `sys_api_log`、Java/Python/Nginx/systemd 日志；
- `stock_daily_history`、资讯原始 JSON、基金历史快照；
- OCR 导入批次、原始识别文本和图片哈希；
- Prefect Flow/Task/事件/日志元数据。

上传的 OCR 图片使用临时文件并在请求结束时尝试删除，但识别结果、原始文本和哈希会写入 MySQL。上线前由业务、安全和运维共同定义每类数据的保留期、归档、删除、法律留存和恢复策略，再实现可审计、可试跑的清理任务。

### 2.4 评分模型限制

- 短期跌幅因子（`decline_today` 当日预估、`decline_1d/1w/2w/3w/4w` 净值窗口）按"跌幅越大分值越高"构建，属逆向/超跌信号，与回测使用的"未来12月盈利"标签不一定同向；方案未通过回测门槛时，客户端只展示总分、不展示盈利概率（前端已兼容空概率）。
- 因子 key 集合在 `scoring.py`、`db.py` 种子、`sql/*.sql`、Java `FACTOR_KEYS` 与前端 `SCORE_FACTORS` 多处重复维护，改动必须同步，否则 `validate_weights`/`validateProfile` 会拒绝方案。

## 3. API 与安全模型限制

| 项目 | 当前事实 | 维护要求 |
| --- | --- | --- |
| 错误语义 | 部分业务失败以 HTTP 200 + `code=500` 返回 | 客户端同时检查 HTTP 和业务 code；长期应统一错误规范 |
| 接口契约 | 没有 OpenAPI/Swagger 生成和契约测试 | `API.md`/`API_MODELS.md` 手工维护；Controller/DTO 变更必须做漂移检查 |
| 输入校验 | 登录 DTO 有 Bean Validation，部分 CRUD/OCR 输入仍主要靠 Service/数据库 | 新接口用专用 DTO、长度/格式/范围校验；不要直接暴露 Entity |
| JWT 权限刷新 | 角色/菜单变化不会刷新已签发 Token | 权限变更后重新登录；密钥轮换和强制失效需有运维预案 |
| JWT 轮换 | 所有服务必须同时使用同一密钥，当前无双密钥过渡 | 轮换会使旧 Token 失效，必须通知并滚动验证 |
| Admin 日志入口 | `/api-logs` 在 Admin 安全配置中 permitAll，靠内部 Token 校验 | 必须限制网络并替换 Token；不能暴露公网 |

## 4. 构建、测试与发布限制

| 项目 | 当前状态 | 影响/下一步 |
| --- | --- | --- |
| CI/CD | 仓库没有 GitHub Actions/GitLab/Jenkins 配置 | 构建和测试依赖人工执行；应补跨技术栈门禁和产物留档 |
| Java 测试 | 主要集中在 `fund` 计算，共 22 项；System/Customer/Gateway 缺少覆盖 | 鉴权、CRUD、路由和数据库集成回归风险高 |
| Python 测试 | 单元测试较完整，但真实数据源和 MySQL 集成仍需受控环境 | 外部页面变化不能只靠 fixture 发现 |
| Web | 无 lint/unit/e2e script，`App.tsx` 和 `api.ts` 较集中 | 最低只有 TypeScript build + 手工冒烟；应补组件/契约/E2E 测试 |
| iOS | 无 XCTest target，业务页面集中于 `CrmMobileApp.swift` | 模型/金额/日期和并发竞态主要靠人工发现 |
| Android | 无 `test/androidTest`，仓库无 Gradle Wrapper | 构建不可完全复现，页面/JSON/lifecycle 缺自动回归 |
| Java 进程托管 | 无完整生产 systemd unit | 当前重启脚本是辅助工具，不是生产进程治理方案 |
| 数据库迁移 | 无 Flyway/Liquibase 或迁移状态表 | 增量脚本顺序、幂等和执行记录靠人工台账 |

`deploy/graceful-restart.sh` 会摘流、等待、停止并后台启动 Jar，但不会等待新实例健康，也不能让单实例实现零停机；超时还会发送 SIGKILL。生产需用进程管理器、多实例滚动和自动健康门禁补齐。

## 5. 平台与调度限制

- `deploy/nacos/docker-compose.yml` 固定 `linux/arm64`，在 x86_64 主机可能无法直接使用。
- `deploy/macos/*.plist` 包含当前开发机绝对路径，不能直接复制到其他用户/目录。
- 股票 Deployment 使用工作日 Cron，不识别中国内地/香港交易所节假日、临时休市或半日市；业务代码必须容忍空数据，长期应接入交易日历。
- 新浪资讯每 120 秒执行，源端限流、许可、网络或解析结构变化都可能使计划持续失败；必须配置失败告警和退避。
- 养基宝凭据是短期敏感值，`news` 当前未注册自动 Deployment。
- Prefect Deployment/UI 的运行态修改可能与 `prefect.yaml` 漂移，长期变更必须回写版本库并重新部署。

## 6. OCR 链路限制

- 一次最多 3 张图片，仅支持 JPG/JPEG/PNG。
- Spring 单文件限制 10 MB、单请求限制 20 MB；Nginx 示例总请求限制 20 MB。
- Java 启动 Python 子进程，单次等待上限 120 秒；需要正确的 Python 解释器和 RapidOCR 依赖。
- 来源只支持 `alipay/tencent`，导入类型只支持 `holding/trade`。
- OCR 是候选结果，不是可信账务数据；必须预览、校正、确认。
- 当前 Java 请求事务覆盖 OCR 子进程等待，长时间识别会占用请求线程和数据库事务资源；高并发生产场景应演进为异步任务。

## 7. 前端与移动端限制

- Web Token 存在 `localStorage`，需要严格防 XSS；当前没有 CSP 配置示例和自动安全测试。
- iOS/Android 登录页允许用户输入服务器地址，方便联调，但生产应固定/受控域名并防止连接恶意地址。
- Android 用 Java 代码动态创建大量 UI、手工 JSON 和 `ExecutorService`；生命周期、旋转和并发请求容易产生旧页面更新问题。
- iOS/Android 当前没有远程功能开关、最低版本策略或 API 版本协商。商店发布后后端必须保持旧客户端兼容。
- 当前没有移动端崩溃/ANR SDK 配置；文档中的监控要求需要部署方选择合规方案后落地。

## 8. 关闭技术债的标准流程

1. 创建明确任务：现状、影响、负责人、目标版本和验收证据。
2. 先补测试或可复现步骤，再修改代码/配置。
3. 在 staging 验证兼容、迁移、性能、安全和回滚。
4. 更新本文件对应行；没有证据不得写“已解决”。
5. 发布后观察完整周期，记录指标和残余风险。

建议每次发布评审本文件，每月至少复核一次高风险项。日常巡检和交接模板见 [维护与交接手册](../manuals/MAINTENANCE.md)。
