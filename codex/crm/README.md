# CRM 管理系统

前后端分离的客户管理系统 MVP。

## 技术栈

- 后端：Java 8、Spring Boot 2.7.x、Spring Security、MyBatis-Plus、MySQL、JWT
- 前端：React、TypeScript、Vite、Ant Design
- 权限：RBAC，支持菜单权限和按钮权限编码
- 部署：后端独立 Jar，前端静态资源可由 Nginx 部署

## 目录

```text
backend/     后端服务
frontend/    前端管理台
sql/         MySQL 建表和初始化数据
deploy/      前端 Nginx 示例配置
```

## 本地启动

1. 创建数据库并导入表结构：

```sql
CREATE DATABASE crm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crm;
SOURCE sql/schema.sql;
```

2. 启动后端：

```bash
cd backend
mvn spring-boot:run
```

3. 启动前端：

```bash
cd frontend
npm install
npm run dev
```

默认账号：

- 用户名：`admin`
- 密码：`admin123`

## 配置

后端数据库配置在 [backend/src/main/resources/application.yml](/Users/thm/MY/codex/crm/backend/src/main/resources/application.yml)。

Nginx 配置和部署说明在 [deploy/README.md](/Users/thm/MY/codex/crm/deploy/README.md)。

本机当前默认 Java 版本高于 Java 8，但 Maven 工程已配置 `source/target=1.8`。如果生产或测试环境必须用 Java 8，请切换 `JAVA_HOME` 后再运行。
