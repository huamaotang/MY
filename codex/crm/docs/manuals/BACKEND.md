# 后端微服务开发手册

本文覆盖 `backend/` 下所有 Java 服务的本地启动、配置、接口开发、验证和排错。

## 1. 技术栈

| 项 | 说明 |
| --- | --- |
| Java | 工程按 Java 8 编译，`backend/pom.xml` 配置了 `source/target=1.8` |
| 框架 | Spring Boot 2.7.18 |
| 微服务 | Spring Cloud 2021.0.8 |
| 注册配置 | Spring Cloud Alibaba Nacos |
| 网关 | Spring Cloud Gateway |
| 数据库 | MySQL |
| ORM | MyBatis-Plus + XML Mapper |
| 安全 | Spring Security + JWT |
| 限流 | Gateway + Redis RateLimiter |
| 构建 | Maven 多模块 |

## 2. 模块说明

| 模块 | 启动类 | 服务名 | 端口 | 职责 |
| --- | --- | --- | --- | --- |
| `core` | 无 | 无 | 无 | 公共库，不单独启动 |
| `gateway` | `CrmGatewayApplication` | `gateway` | `8780` | 统一入口、路由、CORS、限流 |
| `system` | `CrmSystemApplication` | `system` | `8782` | 登录、用户、角色、菜单 |
| `customer` | `CrmCustomerApplication` | `customer` | `8783` | 客户、联系人、跟进记录 |
| `admin` | `CrmApplication` | `admin` | `8781` | 原单体兼容服务 |

默认业务访问走 `gateway`，不要让前端和移动端直连 `system` 或 `customer`。

## 3. 本地环境准备

必须准备：

```text
JDK 8 或更高版本
Maven
MySQL
Docker Desktop
```

本机 Java 版本高于 8 也能编译，因为 Maven 已指定目标字节码为 1.8。如果生产要求严格 Java 8，先切换：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
java -version
```

## 4. 初始化数据库

登录 MySQL 后执行：

```sql
CREATE DATABASE crm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crm;
SOURCE sql/schema.sql;
```

如果已经有旧库，`schema.sql` 会先 `DROP TABLE`，不要在生产库直接执行。

关键表：

| 表 | 说明 |
| --- | --- |
| `sys_user` | 用户 |
| `sys_role` | 角色 |
| `sys_menu` | 菜单和按钮权限 |
| `sys_user_role` | 用户角色关系 |
| `sys_role_menu` | 角色菜单关系 |
| `crm_customer` | 客户 |
| `crm_contact` | 联系人 |
| `crm_follow_record` | 跟进记录 |
| `sys_api_log` | 接口访问日志 |

## 5. 启动基础设施

启动 Nacos 和 Redis：

```bash
cd deploy/nacos
docker compose up -d
docker compose ps
```

确认 Nacos：

```bash
curl -fsS http://127.0.0.1:8848/nacos/v1/ns/operator/metrics
```

确认 Redis：

```bash
redis-cli -h 127.0.0.1 -p 6379 -a qwer8989 ping
```

期望输出：

```text
PONG
```

## 6. 发布 Nacos 配置

配置文件在 `deploy/nacos/`：

```text
gateway-dev.yaml
system-dev.yaml
customer-dev.yaml
admin-dev.yaml
```

导入全部配置：

```bash
cd deploy/nacos
for data_id in gateway-dev.yaml admin-dev.yaml system-dev.yaml customer-dev.yaml; do
  curl -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode 'group=DEFAULT_GROUP' \
    --data-urlencode 'type=yaml' \
    --data-urlencode "content@${data_id}"
done
```

读取某个配置验证：

```bash
curl -fsS 'http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=gateway-dev.yaml&group=DEFAULT_GROUP'
```

## 7. 配置项说明

数据库配置在 `system-dev.yaml`、`customer-dev.yaml`、`admin-dev.yaml`：

```yaml
spring:
  datasource:
    url: "${MYSQL_URL:jdbc:mysql://localhost:3306/crm?...}"
    username: "${MYSQL_USER:root}"
    password: "${MYSQL_PASSWORD:qwer8989}"
```

JWT 配置：

```yaml
crm:
  jwt:
    secret: "${CRM_JWT_SECRET:change-this-nacos-development-secret}"
    expire-seconds: "${CRM_JWT_EXPIRE_SECONDS:86400}"
```

Gateway Redis 限流配置在 `gateway-dev.yaml`：

```yaml
spring:
  redis:
    host: ${REDIS_HOST:127.0.0.1}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:qwer8989}
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            key-resolver: "#{@ipKeyResolver}"
            redis-rate-limiter.replenishRate: ${GATEWAY_RATE_LIMIT_REPLENISH_RATE:20}
            redis-rate-limiter.burstCapacity: ${GATEWAY_RATE_LIMIT_BURST_CAPACITY:40}
```

含义：

| 配置 | 说明 |
| --- | --- |
| `replenishRate` | 每秒补充多少令牌 |
| `burstCapacity` | 令牌桶最大容量 |
| `requestedTokens` | 每个请求消耗令牌数 |
| `ipKeyResolver` | 按客户端 IP 做限流 key |

## 8. 启动服务

建议开三个终端：

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
mvn -pl gateway spring-boot:run
```

注意：`gateway` 可以用 `mvn -pl gateway spring-boot:run` 启动。不要用 `mvn -pl gateway -am spring-boot:run`，否则 Maven 可能会先在父 POM 上执行 Boot 插件并报没有 main class。

可选启动兼容单体：

```bash
cd backend
mvn -pl admin -am spring-boot:run
```

## 9. 启动后验证

检查 Nacos 注册：

```bash
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=system&groupName=DEFAULT_GROUP'
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=customer&groupName=DEFAULT_GROUP'
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=gateway&groupName=DEFAULT_GROUP'
```

检查健康：

```bash
curl -i http://127.0.0.1:8780/actuator/health
curl -i http://127.0.0.1:8782/actuator/health
curl -i http://127.0.0.1:8783/actuator/health
```

登录拿 token：

```bash
curl -s -X POST 'http://127.0.0.1:8780/api/auth/login' \
  -H 'Content-Type: application/json' \
  -H 'X-Client-Source: curl' \
  -d '{"username":"admin","password":"admin123"}'
```

访问客户列表：

```bash
TOKEN='把登录返回的 token 放这里'
curl -s 'http://127.0.0.1:8780/api/customers?current=1&size=10' \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'X-Client-Source: curl'
```

## 10. 网关路由

外部统一带 `/api`，网关通过 `StripPrefix=1` 去掉 `/api` 后转给下游。

| 外部路径 | 下游服务 | 下游路径 |
| --- | --- | --- |
| `/api/auth/**` | `system` | `/auth/**` |
| `/api/users/**` | `system` | `/users/**` |
| `/api/roles/**` | `system` | `/roles/**` |
| `/api/menus/**` | `system` | `/menus/**` |
| `/api/customers/**` | `customer` | `/customers/**` |
| `/api/contacts/**` | `customer` | `/contacts/**` |
| `/api/follow-records/**` | `customer` | `/follow-records/**` |

新增接口后，如果路径不在这些谓词内，必须改 `deploy/nacos/gateway-dev.yaml` 并重新发布到 Nacos。

## 11. 当前接口清单

系统服务：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 登录 |
| `GET` | `/api/auth/me` | 当前用户 |
| `GET` | `/api/users` | 用户列表 |
| `POST` | `/api/users` | 新增用户 |
| `PUT` | `/api/users/{id}` | 修改用户 |
| `DELETE` | `/api/users/{id}` | 删除用户 |
| `GET` | `/api/roles` | 角色列表 |
| `POST` | `/api/roles` | 新增角色 |
| `PUT` | `/api/roles/{id}` | 修改角色 |
| `DELETE` | `/api/roles/{id}` | 删除角色 |
| `GET` | `/api/menus/mine` | 当前用户菜单 |
| `GET` | `/api/menus` | 菜单树 |
| `POST` | `/api/menus` | 新增菜单 |
| `PUT` | `/api/menus/{id}` | 修改菜单 |
| `DELETE` | `/api/menus/{id}` | 删除菜单 |

客户服务：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/customers` | 客户分页 |
| `GET` | `/api/customers/{id}` | 客户详情 |
| `POST` | `/api/customers` | 新增客户 |
| `PUT` | `/api/customers/{id}` | 修改客户 |
| `DELETE` | `/api/customers/{id}` | 删除客户 |
| `GET` | `/api/contacts` | 联系人列表 |
| `POST` | `/api/contacts` | 新增联系人 |
| `GET` | `/api/follow-records` | 跟进记录列表 |
| `POST` | `/api/follow-records` | 新增跟进记录 |

## 12. 新增接口流程

以在 `customer` 服务新增客户标签接口为例：

1. 数据库加表或加字段，放到新的 `sql/yyyymmdd_xxx.sql`。
2. 在 `backend/customer/src/main/java/com/example/crm/entity/` 增加实体。
3. 在 `mapper/` 增加 Mapper 接口。
4. 在 `resources/mapper/` 增加 XML SQL。
5. 在 `service/` 定义接口。
6. 在 `service/impl/` 实现业务逻辑。
7. 在 `controller/` 增加 REST 接口。
8. 如果路径是新前缀，比如 `/customer-tags/**`，更新 `gateway-dev.yaml`：

```yaml
predicates:
  - Path=/api/customers/**,/api/contacts/**,/api/follow-records/**,/api/customer-tags/**
```

9. 发布 Nacos 配置。
10. 重启或刷新 gateway。
11. 用 curl 验证。
12. 前端或移动端接入。

## 13. 安全与鉴权

所有服务引入 `core` 后都会使用统一安全配置。默认除登录和健康检查外都需要 JWT。

请求头格式：

```text
Authorization: Bearer <token>
```

调用来源建议带：

```text
X-Client-Source: web
X-Client-Source: ios
X-Client-Source: android
```

访问日志会记录到 `sys_api_log.source`，方便区分调用端。

## 14. 限流验证

查看 Gateway 路由是否挂了限流过滤器：

```bash
curl -fsS http://127.0.0.1:8780/actuator/gateway/routes
```

并发测试：

```bash
seq 1 80 | xargs -n 1 -P 40 -I {} \
  curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'X-Forwarded-For: 198.51.100.120' \
  http://127.0.0.1:8780/api/customers | sort | uniq -c
```

看到 `429` 说明限流生效。看到 `403` 通常是未带 token 进入了业务鉴权。

## 15. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| `gateway` health 是 `DOWN` | Redis 不通或密码错误 | `redis-cli -a qwer8989 ping`，检查 `spring.redis.password` |
| 请求返回 `503` | Nacos 找不到下游实例 | 检查 `system/customer` 是否注册且 healthy |
| 请求返回 `403` | 未带 token 或 token 无效 | 重新登录，带 `Authorization` |
| 登录返回 500 | 数据库、密码、用户数据异常 | 看 `system` 日志和 `sys_user` 初始化数据 |
| 修改 Nacos YAML 不生效 | 没发布到 Nacos 或服务未刷新 | 重新 POST 配置，必要时重启服务 |
| Mapper SQL 不生效 | XML 路径或方法名不匹配 | 检查 `mapper-locations` 和 Mapper 方法 |
| 时间格式不对 | Jackson 配置不一致 | 检查 Nacos YAML 的 `time-zone` 和 `date-format` |

## 16. 提交前检查

```bash
cd backend
mvn -pl system,customer,gateway -am -DskipTests package
```

如果改了 `admin`：

```bash
cd backend
mvn -pl admin -am -DskipTests package
```

文档、配置、SQL 和代码要一起提交，不要只提交 Java 文件。
