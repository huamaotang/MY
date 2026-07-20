# CRM 管理系统

前后端分离的客户管理系统 MVP。

## 技术栈

- 后端：Java 8、Spring Boot 2.7.x、Spring Cloud Gateway、Spring Cloud Alibaba Nacos、Spring Security、MyBatis-Plus、MySQL、Redis、JWT
- 前端：React、TypeScript、Vite、Ant Design
- 权限：RBAC，支持菜单权限和按钮权限编码
- 部署：后端独立 Jar，前端静态资源可由 Nginx 部署

## 目录

```text
backend/                       后端父工程
backend/core/                  后端公共库
backend/gateway/               API 网关服务
backend/admin/                 原单体后端迁移后的 admin 服务
backend/system/                系统服务：认证、用户、角色、菜单
backend/customer/              客户服务：客户、联系人、跟进记录
backend/fund/                  基金产品服务：基金基础信息、净值、持仓、特色数据
frontend/                      前端管理台
ios/                           iPhone 原生移动端
android/                       Android 原生移动端
sql/                           MySQL 建表和初始化数据
deploy/                        部署配置
```

## 维护文档

- [各模块详细说明](docs/MODULES.md)：按模块整理职责、接口、配置、数据表和维护注意点。
- [开发手册总览](docs/manuals/README.md)：按后端、前端、iOS、Android、部署拆分的保姆级开发手册。

## 本地启动

1. 创建数据库并导入表结构：

```sql
CREATE DATABASE crm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crm;
SOURCE sql/schema.sql;
```

2. 启动 Nacos 和 Redis 并导入配置，详见 [deploy/nacos/README.md](/Users/thm/MY/codex/crm/deploy/nacos/README.md)。

3. 启动后端微服务：

```bash
cd backend
# 分别在三个终端执行
mvn -pl system -am spring-boot:run
mvn -pl customer -am spring-boot:run
mvn -pl gateway -am spring-boot:run
mvn -pl fund -am spring-boot:run

# 如需启动原单体迁移后的 admin 服务，单独执行
mvn -pl admin -am spring-boot:run
```

默认服务端口：

```text
gateway:      http://127.0.0.1:8780
admin:        http://127.0.0.1:8781/api
system:       http://127.0.0.1:8782
customer:     http://127.0.0.1:8783
fund:         http://127.0.0.1:8784
```

4. 启动前端：

```bash
cd frontend
npm install
npm run dev
```

前端默认直接请求 gateway：

```text
VITE_API_BASE=http://127.0.0.1:8780/api
```

如需改成其他网关地址，复制 [frontend/.env.example](/Users/thm/MY/codex/crm/frontend/.env.example) 为 `.env.local` 后修改。

默认账号：

- 用户名：`admin`
- 密码：`admin123`

## 配置

后端数据库和 JWT 配置由 Nacos 管理，示例配置在 [deploy/nacos](/Users/thm/MY/codex/crm/deploy/nacos)。

Nginx 配置和部署说明在 [deploy/README.md](/Users/thm/MY/codex/crm/deploy/README.md)。

Nacos 配置说明在 [deploy/nacos/README.md](/Users/thm/MY/codex/crm/deploy/nacos/README.md)。

本机当前默认 Java 版本高于 Java 8，但 Maven 工程已配置 `source/target=1.8`。如果生产或测试环境必须用 Java 8，请切换 `JAVA_HOME` 后再运行。
