# CRM Java Backend

Maven 多模块后端，包含 `core`、`gateway`、`system`、`customer`、`fund` 和兼容 `admin`。

- 完整开发、测试、构建和发布：[Java 后端手册](../docs/manuals/BACKEND.md)
- REST 契约：[API 参考](../docs/reference/API.md)
- 表与迁移：[数据库参考](../docs/reference/DATABASE.md)
- Java 基础与进阶：[项目导向教程](../docs/learning/JAVA.md)

## 快速构建

```bash
cd backend
mvn test
mvn -DskipTests package
```

## 本地启动

先启动 MySQL、Nacos、Redis 并发布 `deploy/nacos/*-dev.yaml`。分别打开终端：

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
mvn -pl gateway spring-boot:run
```

外部请求统一访问 `http://127.0.0.1:8780/api/**`，不要让客户端直连下游服务。
