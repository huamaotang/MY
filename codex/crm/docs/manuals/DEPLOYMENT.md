# 部署与运维手册

本文覆盖本地基础设施、Nacos 配置、Redis、Nginx、Jar 启动和平滑重启。

## 1. 本地基础设施

路径：

```text
deploy/nacos/docker-compose.yml
```

包含：

| 服务 | 容器名 | 端口 | 说明 |
| --- | --- | --- | --- |
| Nacos | `crm-nacos` | `8848`, `9848`, `9849` | 配置中心和注册中心 |
| Redis | `crm-redis` | `6379` | Gateway 分布式限流 |

启动：

```bash
cd deploy/nacos
docker compose up -d
```

查看：

```bash
docker compose ps
```

停止：

```bash
docker compose down
```

不要随便加 `-v`，否则会删除 volume 里的 Nacos 数据。

## 2. Nacos 控制台

地址：

```text
http://127.0.0.1:8848/nacos
```

当前本地 Compose 关闭了 Nacos 鉴权，便于开发。

检查 Nacos 是否启动：

```bash
curl -fsS http://127.0.0.1:8848/nacos/v1/ns/operator/metrics
```

期望：

```json
{"status":"UP"}
```

## 3. Nacos Data ID

| Data ID | 对应服务 |
| --- | --- |
| `gateway-dev.yaml` | `gateway` |
| `system-dev.yaml` | `system` |
| `customer-dev.yaml` | `customer` |
| `admin-dev.yaml` | `admin` |

Group：

```text
DEFAULT_GROUP
```

Format：

```text
YAML
```

## 4. 发布配置

发布全部：

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

发布单个：

```bash
cd deploy/nacos
curl -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
  --data-urlencode 'dataId=gateway-dev.yaml' \
  --data-urlencode 'group=DEFAULT_GROUP' \
  --data-urlencode 'type=yaml' \
  --data-urlencode 'content@gateway-dev.yaml'
```

读取验证：

```bash
curl -fsS 'http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=gateway-dev.yaml&group=DEFAULT_GROUP'
```

## 5. 环境变量

本地可以直接用默认值。生产或测试环境建议显式设置：

```bash
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_GROUP=DEFAULT_GROUP
export SPRING_PROFILES_ACTIVE=dev

export MYSQL_URL='jdbc:mysql://localhost:3306/crm?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export MYSQL_USER=root
export MYSQL_PASSWORD='替换为真实密码'

export CRM_JWT_SECRET='替换为足够长的随机字符串'
export CRM_JWT_EXPIRE_SECONDS=86400

export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD=qwer8989
export GATEWAY_RATE_LIMIT_REPLENISH_RATE=20
export GATEWAY_RATE_LIMIT_BURST_CAPACITY=40
```

生产环境必须替换：

```text
MYSQL_PASSWORD
CRM_JWT_SECRET
REDIS_PASSWORD
```

## 6. Redis 运维

检查认证：

```bash
redis-cli -h 127.0.0.1 -p 6379 ping
```

如果返回：

```text
NOAUTH Authentication required.
```

说明 Redis 要求密码。

带密码检查：

```bash
redis-cli -h 127.0.0.1 -p 6379 -a qwer8989 ping
```

期望：

```text
PONG
```

Gateway 限流会写入类似 key：

```text
request_rate_limiter.{198.51.100.88}.tokens
request_rate_limiter.{198.51.100.88}.timestamp
```

这些 key TTL 很短，压测后可能马上消失。

抓取限流命令：

```bash
redis-cli -h 127.0.0.1 -p 6379 -a qwer8989 monitor
```

看到 `EVALSHA`、`setex request_rate_limiter...` 说明 Gateway 正在使用 Redis 限流。

## 7. 构建后端 Jar

构建全部：

```bash
cd backend
mvn -DskipTests package
```

构建核心运行链路：

```bash
cd backend
mvn -pl system,customer,gateway -am -DskipTests package
```

Jar 输出：

```text
backend/system/target/system-0.1.0.jar
backend/customer/target/customer-0.1.0.jar
backend/gateway/target/gateway-0.1.0.jar
backend/admin/target/admin-0.1.0.jar
```

## 8. 直接运行 Jar

```bash
java -jar backend/system/target/system-0.1.0.jar
java -jar backend/customer/target/customer-0.1.0.jar
java -jar backend/gateway/target/gateway-0.1.0.jar
```

如果 Nacos 不在本机：

```bash
NACOS_SERVER_ADDR=192.168.1.20:8848 java -jar backend/gateway/target/gateway-0.1.0.jar
```

## 9. 平滑重启

脚本：

```text
deploy/graceful-restart.sh
```

示例：

```bash
deploy/graceful-restart.sh system backend/system/target/system-0.1.0.jar 8782
deploy/graceful-restart.sh customer backend/customer/target/customer-0.1.0.jar 8783
deploy/graceful-restart.sh gateway backend/gateway/target/gateway-0.1.0.jar 8780
```

服务 YAML 中已经配置：

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

## 10. Nginx

配置文件：

```text
deploy/nginx.conf
deploy/homebrew-crm.conf
```

典型反代关系：

```text
/       -> frontend dist
/api/   -> gateway:8780/api/
```

如果前端部署到 Nginx 后接口 404，优先检查：

1. Nginx 是否把 `/api/` 转发到 gateway。
2. 前端 `VITE_API_BASE` 是否为空或 `/api`。
3. Gateway 路由是否覆盖对应接口。

## 11. 发布前检查

| 检查项 | 命令 |
| --- | --- |
| Nacos UP | `curl http://127.0.0.1:8848/nacos/v1/ns/operator/metrics` |
| Redis PONG | `redis-cli -a qwer8989 ping` |
| Gateway UP | `curl http://127.0.0.1:8780/actuator/health` |
| System 注册 | Nacos instance list |
| Customer 注册 | Nacos instance list |
| 登录 | `POST /api/auth/login` |
| 客户列表 | `GET /api/customers?current=1&size=10` |
| 限流 | 并发请求出现 `429` |

## 12. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| Nacos 访问失败 | 容器未启动或端口占用 | `docker compose ps`，看 `crm-nacos` 日志 |
| 服务启动但读不到配置 | Data ID 没发布或 profile 不一致 | 检查 `spring.profiles.active` 和 Nacos Data ID |
| Gateway health DOWN | Redis 密码错误 | 检查 `REDIS_PASSWORD` 和 `gateway-dev.yaml` |
| 业务接口 503 | 下游未注册 | 看 Nacos `system/customer` 是否 healthy |
| 业务接口 403 | 未登录或权限不足 | 带 token 请求 |
| 前端刷新 404 | Nginx history fallback 未配置 | 配置 `try_files` 回落到 `index.html` |
| 移动端连不上 | 地址填了本机回环 | 改成局域网 IP |

## 13. 生产注意事项

生产环境不要直接复用开发默认值：

```text
root / qwer8989
change-this-nacos-development-secret
Nacos 关闭鉴权
HTTP 明文访问
```

至少要做：

1. MySQL 独立账号和强密码。
2. Redis 开启强密码并限制网络访问。
3. Nacos 开启鉴权。
4. JWT secret 使用足够长的随机字符串。
5. Gateway 和前端走 HTTPS。
6. 日志目录和备份策略明确。
7. Nginx、后端、数据库都设置监控。
