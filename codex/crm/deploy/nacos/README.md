# Nacos 配置说明

后端微服务已接入 Nacos Config 和 Nacos Discovery。

## 本地保留配置

各模块本地只保留 `bootstrap.yml`，用于启动时连接 Nacos Config 和 Nacos Discovery。端口、数据库、JWT、网关路由、Jackson、Actuator 等运行配置全部放在 Nacos：

```yaml
spring.application.name: system
spring.profiles.active: dev
spring.cloud.nacos.config.server-addr: 127.0.0.1:8848
spring.cloud.nacos.discovery.server-addr: 127.0.0.1:8848
```

可以通过环境变量覆盖：

```bash
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_GROUP=DEFAULT_GROUP
export SPRING_PROFILES_ACTIVE=dev
```

## Nacos Data ID

当前 profile 默认是 `dev`，所以 Nacos 里新增配置：

```text
Data ID: gateway-dev.yaml
Data ID: admin-dev.yaml
Data ID: system-dev.yaml
Data ID: customer-dev.yaml
Group: DEFAULT_GROUP
Format: YAML
```

配置内容使用本目录下同名 YAML 文件。

本地服务模块不再维护 `application.yml`。如果要调整端口、路由或数据库配置，请改 Nacos 对应 Data ID 后重启服务，或对支持刷新的配置使用 Nacos 发布刷新。

数据库和 JWT 配置支持通过环境变量覆盖：

```bash
export MYSQL_URL='jdbc:mysql://localhost:3306/crm?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export MYSQL_USER=root
export MYSQL_PASSWORD=你的数据库密码
export CRM_JWT_SECRET=请换成足够长的随机字符串
export CRM_JWT_EXPIRE_SECONDS=86400
```

## 本地 Docker 启动 Nacos

当前使用 `nacos/nacos-server:v2.4.3`，Maven 父工程中也显式覆盖 `nacos-client` 到 `2.4.3`，避免 2.2.x 客户端读取 2.4.x 服务端配置为空。

```bash
cd deploy/nacos
docker compose up -d
```

访问控制台：

```text
http://127.0.0.1:8848/nacos
```

当前本地 Compose 关闭了 Nacos 鉴权，便于开发验证。

导入配置：

```bash
for data_id in gateway-dev.yaml admin-dev.yaml system-dev.yaml customer-dev.yaml; do
  curl -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode 'group=DEFAULT_GROUP' \
    --data-urlencode 'type=yaml' \
    --data-urlencode "content@${data_id}"
done
```

## 启动

先启动 Nacos，再分别启动服务：

```bash
cd backend
# 分别在三个终端执行
mvn -pl system -am spring-boot:run
mvn -pl customer -am spring-boot:run
mvn -pl gateway -am spring-boot:run

# 可选：原单体迁移后的 admin 服务
mvn -pl admin -am spring-boot:run
```

如果 Nacos 不在本机：

```bash
NACOS_SERVER_ADDR=192.168.1.10:8848 mvn -pl system -am spring-boot:run
```

## 配置加载顺序

Spring Cloud 会按应用名和 profile 从 Nacos 读取：

```text
${spring.application.name}.yaml
${spring.application.name}-${spring.profiles.active}.yaml
```

后者会覆盖前者，适合区分 dev/test/prod。
