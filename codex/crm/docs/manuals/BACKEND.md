# Java 后端微服务开发手册

本文覆盖 `backend/` 的环境、架构、开发、测试、运行、发布和排错。接口契约见 [API 参考](../reference/API.md)，表与迁移见 [数据库参考](../reference/DATABASE.md)。

## 1. 技术栈与模块

| 项目 | 当前版本/实现 |
| --- | --- |
| Java | 源码/字节码目标 1.8 |
| Spring Boot | 2.7.18 |
| Spring Cloud | 2021.0.8 |
| Spring Cloud Alibaba | 2021.0.5.0 |
| Nacos Client | 2.4.3 |
| ORM | MyBatis-Plus 3.5.5 + XML Mapper |
| 安全 | Spring Security + JWT + `@PreAuthorize` |
| 网关 | Spring Cloud Gateway/WebFlux + Redis RateLimiter |
| 构建 | Maven 多模块 |

| 模块 | 启动类 | 服务名 | 端口 | 数据库 |
| --- | --- | --- | ---: | --- |
| `core` | 无 | 无 | - | 无 |
| `gateway` | `CrmGatewayApplication` | `gateway` | 8780 | 无 |
| `system` | `CrmSystemApplication` | `system` | 8782 | `crm` |
| `customer` | `CrmCustomerApplication` | `customer` | 8783 | `crm` |
| `fund` | `CrmFundApplication` | `fund` | 8784 | `fund` |
| `admin` | `CrmApplication` | `admin` | 8781 | `crm` |

默认在线链路依赖 `system/customer/fund/gateway`。`admin` 是原单体兼容服务并接收 Gateway 访问日志，不应作为新业务默认归属。

## 2. 环境准备

需要：

- JDK 8 或能生成 Java 8 字节码的更高 JDK。
- Maven 3.8+。
- MySQL、Docker/Docker Compose。
- 本地 Nacos 2.4.3 和 Redis 7（仓库 Compose 已提供）。

```bash
java -version
mvn -version
docker version
docker compose version
```

父 POM 的 `source/target=1.8` 不保证所有高版本 JDK 行为与生产 JDK 8 完全一致。生产使用 Java 8 时，应在 Java 8 CI/环境做最终构建验证。

## 3. 初始化数据库

新环境按 [数据库参考](../reference/DATABASE.md) 初始化 `crm` 和 `fund`。不要对生产执行 `sql/schema.sql`，因为它会删表重建。

确认 Nacos 中：

- `system/customer/admin` 使用 `MYSQL_URL` 指向 `crm`。
- `fund` 使用 `FUND_MYSQL_URL` 指向 `fund`。
- 所有校验 JWT 的服务使用同一个 `CRM_JWT_SECRET`。

## 4. 启动基础设施与发布配置

```bash
cd deploy/nacos
docker compose up -d
docker compose ps
```

期望 `crm-nacos` 和 `crm-redis` 为运行/健康状态。

发布全部开发配置：

```bash
cd deploy/nacos
for data_id in gateway-dev.yaml admin-dev.yaml system-dev.yaml customer-dev.yaml fund-dev.yaml; do
  curl -fsS -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode 'group=DEFAULT_GROUP' \
    --data-urlencode 'type=yaml' \
    --data-urlencode "content@${data_id}"
done
```

读取验证：

```bash
curl -fsS 'http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=gateway-dev.yaml&group=DEFAULT_GROUP'
```

本地 YAML 是版本库事实来源，Nacos 中的内容是运行态事实来源。两者必须分别检查。

## 5. 配置加载

每个 `bootstrap.yml` 只定义应用名、profile、Nacos 地址和 Config/Discovery 连接。Nacos Data ID 为：

```text
<spring.application.name>-<spring.profiles.active>.yaml
```

常用环境变量：

| 变量 | 服务 | 作用 |
| --- | --- | --- |
| `NACOS_SERVER_ADDR` | 全部 | Nacos 地址 |
| `NACOS_GROUP` | 全部 | 配置/注册分组 |
| `SPRING_PROFILES_ACTIVE` | 全部 | `dev/test/prod` |
| `MYSQL_URL/USER/PASSWORD` | system/customer/admin | CRM 数据库 |
| `FUND_MYSQL_URL/USER/PASSWORD` | fund | 基金数据库 |
| `CRM_JWT_SECRET` | 业务服务 | JWT 签名密钥 |
| `CRM_JWT_EXPIRE_SECONDS` | 业务服务 | Token 有效期 |
| `REDIS_HOST/PORT/PASSWORD` | gateway | 限流 Redis |
| `GATEWAY_RATE_LIMIT_*` | gateway | 令牌桶参数 |
| `CRM_ACCESS_LOG_ADMIN_URL/TOKEN` | gateway/admin | 访问日志写入 |
| `CRM_PYTHON_EXECUTABLE/CRM_PYTHON_OCR_SCRIPT` | fund | OCR Python 解释器和脚本 |

完整变量、默认值和 Redis 本地密码不一致问题见 [配置参考](../reference/CONFIGURATION.md)。生产不要依赖 `${VAR:development-default}` 的默认密码或密钥。

## 6. 本地启动

分别打开终端：

```bash
cd backend
mvn -pl system -am spring-boot:run
```

```bash
cd backend
mvn -pl customer -am spring-boot:run
```

```bash
cd backend
mvn -pl fund -am spring-boot:run
```

```bash
cd backend
REDIS_PASSWORD='' mvn -pl gateway spring-boot:run
```

上面的空密码只匹配仓库当前“无密码 Redis”本地 Compose；如果已经为 Redis 设置密码，必须给 Gateway 传相同值。

可选兼容服务：

```bash
cd backend
mvn -pl admin -am spring-boot:run
```

Gateway 使用 `mvn -pl gateway spring-boot:run`。对父聚合项目执行 Boot 插件可能因没有 main class 失败。

## 7. 启动验证

### 进程健康

```bash
curl -fsS http://127.0.0.1:8780/actuator/health
curl -fsS http://127.0.0.1:8782/actuator/health
curl -fsS http://127.0.0.1:8783/actuator/health
curl -fsS http://127.0.0.1:8784/actuator/health
```

### Nacos 注册

```bash
for service in gateway system customer fund; do
  curl -fsS "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=${service}&groupName=DEFAULT_GROUP"
done
```

检查响应中的实例 `healthy=true`。

### 路由与业务

```bash
curl -fsS http://127.0.0.1:8780/actuator/gateway/routes
```

然后按 [API 登录示例](../reference/API.md) 获取 Token，并从 Gateway 访问客户和基金列表。

## 8. 分层开发

### 新增普通 CRUD

1. 确认模块和数据库归属。
2. 新增增量 SQL，不改历史迁移；更新数据库文档。
3. 增加 Entity/DTO。对外请求不要直接复用包含敏感或内部字段的 Entity。
4. 增加 Mapper 接口及 XML/Plus 查询。
5. 在 Service 实现校验、事务和业务规则。
6. Controller 只负责 HTTP 映射、参数和权限，返回 `ApiResponse<T>`。
7. 新路径前缀才更新 Gateway Nacos 路由。
8. 更新 Web/iOS/Android 模型和接口。
9. 补单元测试、接口示例和手册。

示意：

```java
@GetMapping("/{id}")
@PreAuthorize("hasAuthority('domain:item:list')")
public ApiResponse<ItemResponse> detail(@PathVariable Long id) {
    return ApiResponse.ok(itemService.detail(id));
}
```

### 事务

- 一次用户动作需要多表一致时在 Service 层使用 `@Transactional`。
- 不在事务中做不可控的长时间外部网络请求。
- 捕获异常后若仍需回滚，重新抛出或显式标记 rollback-only。
- 批量导入先验证，再在短事务内确认写入。

### Mapper

- Mapper XML 的 `namespace` 必须等于接口全限定名。
- 参数名和 `@Param` 一致；列名显式映射驼峰字段。
- 排序字段必须白名单映射，不能拼接用户输入。
- 分页和批量查询避免 N+1。

## 9. 安全模型

### JWT

登录由 `system` 签发，`core` 中的过滤器在 `system/customer/fund` 校验。Token 至少包含用户名、角色/权限及过期时间。密钥轮换会使旧 Token 失效，应纳入发布通知。

### 权限

- `hasRole('ADMIN')` 实际匹配 `ROLE_ADMIN`。
- `hasAuthority('fund:list')` 精确匹配权限字符串。
- 新权限码需以增量 SQL 加入 `sys_menu` 并授权角色。
- 授权变更后重新登录，旧 JWT 不刷新。
- 当前 `data_scope` 主要保存于角色，客户查询尚未实现完整数据范围过滤；不要把它描述成已完成的数据隔离。

### 输入与错误

- 请求 DTO 使用 Bean Validation；不要只依赖前端校验。
- 业务异常不应泄露 SQL、文件路径、密钥或第三方原始响应。
- 当前 `ApiResponse.fail` 的业务码为 500，且部分业务异常 HTTP 状态仍为 200；新增客户端必须检查两层状态。
- OCR 文件需限制格式、数量、尺寸和解析资源；当前规则变化时同步接口文档。

## 10. Gateway 与访问日志

Gateway 是 WebFlux 应用，不得放 Servlet Filter。它负责：

- Nacos 负载均衡路由。
- `/api` 前缀移除。
- CORS。
- Redis 令牌桶限流，当前按客户端 IP 取 key。
- 访问日志，并可向 Admin 的内部 `/api/api-logs` 写入。

限流验证：

```bash
seq 1 60 | xargs -n1 -P20 -I{} \
  curl -sS -o /dev/null -w '%{http_code}\n' \
  http://127.0.0.1:8780/api/customers | sort | uniq -c
```

运行前需准备 Token，否则结果可能首先是鉴权失败。需要精确验证时使用带 Token 的测试脚本，并在非生产环境临时降低限流参数。

## 11. 基金服务维护要点

- `fund` 使用独立 `fund` MySQL。
- 基础/净值/评级/资讯/股票主要由 Python 写，Java 以读和用户交互为主。
- 评分 API 创建异步任务，Python pipeline 才执行回测/推荐。
- 激活评分配置必须满足代码中的回测门槛，不能人工只改数据库状态。
- 用户持仓按用户名和来源平台隔离；OCR 预览后才确认。
- 股票 Controller 当前用 `JdbcTemplate` 和排序白名单，新增字段需同步 SQL alias、Web/移动模型。
- 客户和基金删除当前都不是完整业务级联，生产操作前阅读 [已知限制](../reference/KNOWN_LIMITATIONS.md)。

### OCR 运行链路

`fund` 服务不是在 JVM 内完成 OCR，而是启动 Python 子进程：

```text
multipart 图片 -> Java 临时文件 -> Python RapidOCR -> JSON -> Java 预览 DTO
```

生产显式配置：

```env
CRM_PYTHON_EXECUTABLE=/opt/crm/fund_spider/.venv/bin/python
CRM_PYTHON_OCR_SCRIPT=/opt/crm/fund_spider/tools/portfolio_holding_ocr.py
```

验证解释器和脚本：

```bash
/opt/crm/fund_spider/.venv/bin/python \
  /opt/crm/fund_spider/tools/portfolio_holding_ocr.py --help
```

当前一次最多 3 张 JPG/JPEG/PNG，单文件 10 MB、总请求 20 MB，子进程超时 120 秒。图片临时文件会尝试删除，但识别文本/哈希会入库；OCR 预览必须由用户确认。

## 12. 测试与构建

### 最小模块构建

```bash
cd backend
mvn -pl system -am -DskipTests package
mvn -pl customer -am -DskipTests package
mvn -pl fund -am -DskipTests package
mvn -pl gateway -am -DskipTests package
```

### 全量

```bash
cd backend
mvn test
mvn -DskipTests package
```

基金已有计算测试：

```bash
cd backend
mvn -pl fund -am test
```

修改鉴权、SQL 或网关时还需实际启动依赖并走 Gateway 做端到端测试。

## 13. 打包与产物

```bash
cd backend
mvn -DskipTests package
```

主要产物：

```text
gateway/target/gateway-0.1.0.jar
system/target/system-0.1.0.jar
customer/target/customer-0.1.0.jar
fund/target/fund-0.1.0.jar
admin/target/admin-0.1.0.jar
```

发布前记录 Git commit、JDK、Maven、依赖锁定状态和每个 Jar 的 SHA-256；不同环境不得现场重新编译出不同 Jar。

## 14. 生产发布与回滚

完整顺序见 [部署手册](DEPLOYMENT.md)。Java 部分要点：

1. 先备份并执行已审阅的数据库增量迁移。
2. 发布生产 Nacos 配置，读回核对，不使用 dev 默认值。
3. 上传带版本号或校验和的 Jar，不覆盖唯一可回滚副本。
4. 按 `system/customer/fund/admin/gateway` 的影响评估滚动重启；同服务多实例时始终保留健康实例。
5. 每个实例验证 Actuator、Nacos healthy、关键 API 和日志后再继续。
6. 发布后做登录、客户列表、基金列表、评分任务读取、OCR 预览（测试账号）冒烟。

仓库脚本示例：

```bash
deploy/graceful-restart.sh system backend/system/target/system-0.1.0.jar 8782
```

回滚：停止新实例、恢复上一 Jar、恢复匹配配置并重启。数据库回滚只能按迁移预案执行；不要在故障中临时逆写破坏性 SQL。

## 15. 常见故障

| 现象 | 常见原因 | 检查 |
| --- | --- | --- |
| 启动提示配置为空 | Data ID/group/profile 不匹配 | 读 Nacos 配置，核对应用名 |
| Gateway `503` | 服务未注册或 unhealthy | Nacos 实例、下游 Actuator/日志 |
| Gateway health `DOWN` | Redis 地址/密码错误 | `redis-cli`、Gateway 日志 |
| `401/403` | Token 缺失/过期、角色或权限不足 | 重新登录、JWT、`sys_role_menu` |
| `429` | 令牌桶生效 | Redis、限流参数、来源 IP |
| MyBatis binding error | XML namespace/方法/参数不一致 | Mapper 接口与 XML |
| 基金为空 | `fund` 库错误或 Python 未刷新 | JDBC URL、表新鲜度、Prefect |
| 评分任务一直 pending | Python pipeline/worker 未运行 | Prefect Deployment 与 Worker |
| 日志写入失败 | Admin 未启动或 token 不一致 | Gateway 的 admin URL/token |

## 16. 提交前检查

- [ ] 修改在正确模块，未把新业务塞进 `admin` 或 `core`。
- [ ] 数据库只使用增量迁移，并在副本验证。
- [ ] Controller、Service、Mapper、事务和权限都有对应测试。
- [ ] Gateway 路由仅在新前缀时修改并发布验证。
- [ ] Java DTO 与所有受影响客户端类型一致。
- [ ] 模块/全量 Maven 构建通过。
- [ ] 通过 Gateway 完成成功、无 Token、无权限和错误输入验证。
- [ ] API、数据库、项目和部署文档已同步。
- [ ] 没有提交密码、Token、Cookie、日志或本机配置。
