# CRM 项目模块维护说明

本文档按模块说明当前 CRM 项目的职责、关键代码、接口、配置和维护注意点，便于后续接手、排障和扩展功能。

## 1. 总体架构

本项目是前后端分离的 CRM MVP，后端采用 Spring Boot + Spring Cloud Gateway + Nacos 的微服务结构，前端使用 React + TypeScript + Vite + Ant Design，iOS 端使用 SwiftUI。

核心访问链路：

```text
Web 管理台 / iOS App
        |
        v
gateway 服务: /api/**
        |
        +--> system 服务: 认证、用户、角色、菜单
        |
        +--> customer 服务: 客户、联系人、跟进记录
```

`admin` 模块是原单体迁移后的服务，包含 `system` 和 `customer` 的合并能力，当前作为兼容或对照模块保留；默认微服务启动链路使用 `gateway + system + customer`。

## 2. 后端父工程 `backend`

路径：`backend/`

职责：

- 统一管理 Maven 多模块工程。
- 固定 Java 8 编译目标。
- 统一 Spring Boot、Spring Cloud、Spring Cloud Alibaba、MyBatis-Plus、Nacos Client 等版本。

子模块：

| 模块 | artifactId | 说明 |
| --- | --- | --- |
| `core` | `core` | 后端公共库，提供响应结构、异常、安全、CORS、MyBatis-Plus 配置 |
| `gateway` | `gateway` | API 网关，负责 `/api/**` 路由和跨域 |
| `system` | `system` | 系统服务，负责认证、用户、角色、菜单 |
| `customer` | `customer` | 客户服务，负责客户、联系人、跟进记录 |
| `admin` | `admin` | 原单体版后端，保留完整 CRM 能力 |

常用命令：

```bash
cd backend
mvn -DskipTests package
mvn -pl system -am spring-boot:run
mvn -pl customer -am spring-boot:run
mvn -pl gateway -am spring-boot:run
```

维护注意：

- 新增微服务时，需要在 `backend/pom.xml` 的 `<modules>` 中登记模块。
- 新增公共依赖优先放在父 POM 的 `dependencyManagement`，业务模块再按需引入。
- 生产环境建议显式设置 `JAVA_HOME` 到 Java 8 或兼容 Java 8 字节码的运行环境。

## 3. 公共模块 `backend/core`

路径：`backend/core/`

职责：

- 统一 API 响应结构。
- 统一业务异常和全局异常处理。
- 提供 Spring Security + JWT 的基础配置。
- 提供 CORS 和 MyBatis-Plus 分页配置。
- 提供公共 DTO。

关键代码：

| 文件 | 说明 |
| --- | --- |
| `common/ApiResponse.java` | 统一返回结构：`code/message/data`，成功 `code=0` |
| `common/BusinessException.java` | 业务异常，通常返回失败响应 |
| `common/GlobalExceptionHandler.java` | 全局异常处理，覆盖业务异常、参数校验、权限异常和兜底异常 |
| `security/SecurityConfig.java` | 无状态安全配置，放行 `/auth/login`、健康检查和 OPTIONS |
| `security/JwtAuthenticationFilter.java` | 从 `Authorization: Bearer <token>` 中解析 JWT 并写入认证上下文 |
| `security/JwtTokenProvider.java` | 生成、校验、解析 HS256 JWT |
| `config/CorsConfig.java` | MVC 跨域配置 |
| `config/MybatisPlusConfig.java` | MyBatis-Plus 分页插件 |
| `dto/IdsRequest.java` | 通用 ID 列表请求体 |

权限模型：

- 登录成功后，JWT payload 内包含 `permissions` 数组。
- 角色编码会在登录时转换成 `ROLE_<role_code>`，例如数据库角色 `ADMIN` 会成为 `ROLE_ADMIN`。
- 菜单和按钮权限使用 `sys_menu.permission_code`，例如 `crm:customer:list`。
- Controller 使用 `@PreAuthorize` 控制接口权限，例如：
  - `hasRole('ADMIN')` 对应 `ROLE_ADMIN`
  - `hasAuthority('crm:customer:list')` 对应权限码 `crm:customer:list`

维护注意：

- 所有引入 `core` 的服务都会自动获得安全和异常处理能力。
- `JwtTokenProvider` 当前手写 JWT 编解码逻辑，若后续接入标准 JWT 库，需要同步保证旧 token 兼容或安排重新登录。
- `SecurityConfig` 默认要求除登录和健康检查外全部认证；新增公开接口时需要明确加入放行规则。
- JWT 密钥和过期时间来自 Nacos：`crm.jwt.secret`、`crm.jwt.expire-seconds`。

## 4. 网关模块 `backend/gateway`

路径：`backend/gateway/`

职责：

- 统一对外暴露 `/api/**`。
- 通过 Nacos Discovery 按服务名发现下游服务。
- 按路径把请求转发到 `system` 或 `customer`。
- 处理跨域和响应头去重。
- 基于 Redis 对入口请求做全局限流。

关键代码和配置：

| 文件 | 说明 |
| --- | --- |
| `CrmGatewayApplication.java` | 网关启动类，启用服务发现 |
| `RateLimiterConfig.java` | 解析客户端 IP，作为网关限流 key |
| `bootstrap.yml` | 连接 Nacos Config 和 Discovery |
| `deploy/nacos/gateway-dev.yaml` | 网关端口、路由、跨域、限流、Actuator 配置 |

当前路由：

| 外部路径 | 下游服务 | 下游实际路径 |
| --- | --- | --- |
| `/api/auth/**` | `system` | `/auth/**` |
| `/api/users/**` | `system` | `/users/**` |
| `/api/roles/**` | `system` | `/roles/**` |
| `/api/menus/**` | `system` | `/menus/**` |
| `/api/customers/**` | `customer` | `/customers/**` |
| `/api/contacts/**` | `customer` | `/contacts/**` |
| `/api/follow-records/**` | `customer` | `/follow-records/**` |

维护注意：

- 新增后端接口后，如果需要通过前端或 iOS 访问，必须同步更新 `gateway-dev.yaml` 的路由谓词。
- 网关使用 `StripPrefix=1` 去掉 `/api`，因此下游 Controller 不需要写 `/api` 前缀。
- `gateway` 是 reactive 应用，不应直接复用 MVC 过滤器或 Servlet 组件。
- 限流默认按客户端 IP 生效，默认每秒补充 20 个令牌、桶容量 40；可用 `GATEWAY_RATE_LIMIT_REPLENISH_RATE` 和 `GATEWAY_RATE_LIMIT_BURST_CAPACITY` 覆盖。
- 更新 Nacos 配置后，需要重新发布 Data ID；路由类配置建议重启网关验证。

## 5. 系统服务 `backend/system`

路径：`backend/system/`

职责：

- 登录认证。
- 用户管理。
- 角色管理。
- 菜单和按钮权限管理。
- 为前端提供当前用户菜单。

关键代码：

| 分层 | 路径 | 说明 |
| --- | --- | --- |
| 启动类 | `CrmSystemApplication.java` | 启动 system 服务，启用 Nacos Discovery 和 Mapper 扫描 |
| Controller | `controller/AuthController.java` | 登录、当前用户信息 |
| Controller | `controller/UserController.java` | 用户增删改查 |
| Controller | `controller/RoleController.java` | 角色增删改查 |
| Controller | `controller/MenuController.java` | 菜单增删改查、我的菜单 |
| Service | `service/impl/AuthServiceImpl.java` | 调用 Spring Security 认证并签发 JWT |
| Service | `service/impl/UserServiceImpl.java` | 用户保存、密码编码、用户角色关系维护 |
| Service | `service/impl/RoleServiceImpl.java` | 角色保存、角色菜单关系维护 |
| Service | `service/impl/MenuServiceImpl.java` | 菜单树查询和菜单维护 |
| Security | `security/CrmUserDetailsService.java` | 加载用户、角色和权限码 |
| Mapper XML | `resources/mapper/*.xml` | 自定义用户、角色、菜单 SQL |

对外接口：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | 公开 | 登录，返回 token、username、permissions |
| `GET` | `/auth/me` | 登录用户 | 返回当前 Principal |
| `GET` | `/menus/mine` | 登录用户 | 返回当前用户可见菜单 |
| `GET` | `/menus` | `ROLE_ADMIN` | 菜单列表 |
| `POST` | `/menus` | `ROLE_ADMIN` | 新增菜单 |
| `PUT` | `/menus/{id}` | `ROLE_ADMIN` | 更新菜单 |
| `DELETE` | `/menus/{id}` | `ROLE_ADMIN` | 删除菜单 |
| `GET` | `/users?keyword=` | `ROLE_ADMIN` | 用户列表，支持用户名和姓名模糊搜索 |
| `POST` | `/users` | `ROLE_ADMIN` | 新增用户 |
| `PUT` | `/users/{id}` | `ROLE_ADMIN` | 更新用户 |
| `DELETE` | `/users/{id}` | `ROLE_ADMIN` | 删除用户 |
| `GET` | `/roles` | `ROLE_ADMIN` | 角色列表 |
| `POST` | `/roles` | `ROLE_ADMIN` | 新增角色 |
| `PUT` | `/roles/{id}` | `ROLE_ADMIN` | 更新角色 |
| `DELETE` | `/roles/{id}` | `ROLE_ADMIN` | 删除角色 |

主要数据表：

| 表 | 说明 |
| --- | --- |
| `sys_user` | 用户基本信息、密码、状态 |
| `sys_role` | 角色和数据范围 |
| `sys_menu` | 菜单、按钮和权限码 |
| `sys_user_role` | 用户角色关系 |
| `sys_role_menu` | 角色菜单权限关系 |
| `sys_dept` | 部门，目前建表和初始化数据已存在，业务接口暂未实现 |
| `sys_login_log` | 登录日志表，目前建表已存在，业务写入暂未实现 |
| `sys_dict_type` / `sys_dict_data` | 字典表，目前建表和初始化数据已存在，业务接口暂未实现 |

业务规则：

- 默认管理员用户 `id=1` 不能删除。
- 默认管理员角色 `id=1` 不能删除。
- 新增用户未填写密码时默认密码为 `123456`。
- 编辑用户时密码为空表示不修改原密码。
- 菜单类型包括 `CATALOG`、`MENU`、`BUTTON`。
- 只有 `visible=1` 且分配给用户角色的菜单或按钮权限会进入登录权限集合。

维护注意：

- 新增管理后台页面时，应同时维护 `sys_menu` 初始化数据、前端菜单、角色授权和接口 `@PreAuthorize`。
- 新增权限码后，需要给角色分配对应菜单或按钮，否则用户登录后的 JWT 不会包含该权限。
- 修改用户角色、角色菜单后，已登录用户的旧 JWT 不会自动刷新，需要重新登录才能拿到新权限。
- `data_scope` 字段目前仅保存，未在客户查询中做数据权限过滤；实现数据隔离时需要联动 `customer` 服务。

## 6. 客户服务 `backend/customer`

路径：`backend/customer/`

职责：

- 客户列表、详情、新增、更新、删除。
- 联系人列表和新增。
- 跟进记录列表和新增。

关键代码：

| 分层 | 路径 | 说明 |
| --- | --- | --- |
| 启动类 | `CrmCustomerApplication.java` | 启动 customer 服务，启用 Nacos Discovery 和 Mapper 扫描 |
| Controller | `controller/CustomerController.java` | 客户接口 |
| Controller | `controller/ContactController.java` | 联系人接口 |
| Controller | `controller/FollowRecordController.java` | 跟进记录接口 |
| Service | `service/impl/CustomerServiceImpl.java` | 客户分页、详情和 CRUD |
| Service | `service/impl/ContactServiceImpl.java` | 联系人列表和新增 |
| Service | `service/impl/FollowRecordServiceImpl.java` | 跟进记录列表和新增 |
| Entity | `entity/*.java` | 对应 `crm_*` 表 |
| Mapper XML | `resources/mapper/*.xml` | 字段映射 |

对外接口：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/customers?current=1&size=10&keyword=` | `crm:customer:list` | 客户分页，按客户名称模糊搜索 |
| `GET` | `/customers/{id}` | `crm:customer:list` | 客户详情 |
| `POST` | `/customers` | `crm:customer:create` | 新增客户 |
| `PUT` | `/customers/{id}` | `crm:customer:update` | 更新客户 |
| `DELETE` | `/customers/{id}` | `crm:customer:delete` | 删除客户 |
| `GET` | `/contacts?customerId=` | `crm:contact:list` | 联系人列表，可按客户过滤 |
| `POST` | `/contacts` | `crm:customer:update` | 新增联系人 |
| `GET` | `/follow-records?customerId=` | `crm:follow:list` | 跟进记录列表，可按客户过滤 |
| `POST` | `/follow-records` | `crm:customer:update` | 新增跟进记录 |

主要数据表：

| 表 | 说明 |
| --- | --- |
| `crm_customer` | 客户主表，保存名称、行业、来源、等级、状态、负责人、联系方式等 |
| `crm_contact` | 联系人表，按 `customer_id` 关联客户 |
| `crm_follow_record` | 跟进记录表，按 `customer_id` 关联客户，可选关联联系人 |
| `crm_opportunity` | 商机表，已建表但当前没有后端接口 |
| `crm_contract` | 合同表，已建表但当前没有后端接口 |
| `crm_payment` | 回款表，已建表但当前没有后端接口 |

业务规则：

- 客户列表默认按 `updated_at` 倒序。
- 联系人列表默认按 `updated_at` 倒序。
- 跟进记录默认按 `created_at` 倒序。
- 客户状态初始化值包括 `POTENTIAL`、`DEAL`、`LOST`。
- 客户级别初始化值包括 `A`、`B`、`C`。
- 跟进方式初始化值包括 `PHONE`、`WECHAT`、`VISIT`。

维护注意：

- 删除客户当前只删除 `crm_customer`，没有级联删除联系人、跟进记录、商机、合同和回款；生产化前需要明确删除策略。
- 新增联系人和跟进记录接口当前复用 `crm:customer:update` 权限，若后续要精细控制，应新增按钮权限码并同步菜单初始化数据。
- `owner_user_id` 和 `created_by` 目前由请求体或数据库保存，不会自动取当前登录用户；如需强制归属，应从 Spring Security 上下文读取用户信息。
- `crm_opportunity`、`crm_contract`、`crm_payment` 已有表结构，新增模块时可沿用 customer 服务分层模式。

## 7. 单体兼容模块 `backend/admin`

路径：`backend/admin/`

职责：

- 保留原单体 CRM 后端能力。
- 同时包含认证、用户、角色、菜单、客户、联系人、跟进记录。
- 可单独启动作为 `/api` 上下文路径的后端服务。

当前配置：

- 默认端口：`8781`
- 上下文路径：`/api`
- Nacos Data ID：`admin-dev.yaml`
- 启动命令：`mvn -pl admin -am spring-boot:run`

维护注意：

- `admin` 中存在与 `system`、`customer` 近似重复的代码。默认微服务链路不依赖它。
- 修复通用问题时，如果仍需支持单体部署，需要同步检查 `admin` 模块里的同名 Controller、Service、Entity、Mapper。
- `admin` 直接暴露 `/api/**`，不需要经过 `gateway` 的 `StripPrefix=1`。
- 如果团队后续确定只保留微服务版本，可考虑归档或移除该模块，减少重复维护成本。

## 8. 前端管理台 `frontend`

路径：`frontend/`

职责：

- 提供 Web 管理台。
- 负责登录、客户列表、用户管理、角色管理、菜单管理。
- 通过 `VITE_API_BASE` 或默认 `/api` 调用网关。

技术栈：

- React 18
- TypeScript
- Vite 5
- Ant Design 5
- `@ant-design/icons`

关键代码：

| 文件 | 说明 |
| --- | --- |
| `src/main.tsx` | React 应用入口 |
| `src/App.tsx` | 页面布局、登录页、工作台、客户、用户、角色、菜单页面 |
| `src/api.ts` | API 封装、类型声明、token 注入、响应错误处理 |
| `src/styles.css` | 全局样式 |
| `vite.config.ts` | Vite 配置 |

当前页面：

| 页面 | 状态 | 后端接口 |
| --- | --- | --- |
| 登录 | 已实现 | `POST /auth/login` |
| 工作台 | 静态指标 | 无实时接口 |
| 客户列表 | 已实现 | `/customers` |
| 联系人 | 占位 | 后端已有 `/contacts` |
| 跟进记录 | 占位 | 后端已有 `/follow-records` |
| 用户管理 | 已实现 | `/users`、`/roles` |
| 角色管理 | 已实现 | `/roles`、`/menus` |
| 菜单管理 | 已实现 | `/menus` |

API 调用约定：

- `src/api.ts` 从 `localStorage.crm_token` 读取 token。
- 所有请求默认设置 `Content-Type: application/json`。
- 成功响应要求 HTTP 状态为 2xx 且业务响应 `code=0`。
- 后端返回失败时抛出 `Error`，页面使用 Ant Design message 展示。

维护注意：

- 默认接口地址是 `/api`，本地开发可用 `.env.local` 设置 `VITE_API_BASE=http://127.0.0.1:8780/api`。
- 当前侧边栏菜单是前端静态 `menuItems`，未使用 `/menus/mine` 动态渲染；若要做真正 RBAC 菜单，需要改为登录后加载用户菜单。
- 新增页面时通常需要同步：
  - `ViewKey`
  - `menuItems`
  - `src/api.ts` 类型和请求函数
  - `App.tsx` 页面组件
  - 后端菜单和权限初始化数据
- 联系人和跟进记录已有后端接口，但前端还未实现列表和新增页面，是较低成本的后续扩展点。

常用命令：

```bash
cd frontend
npm install
npm run dev
npm run build
```

## 9. iOS 客户端 `ios/CrmMobile`

路径：`ios/CrmMobile/`

职责：

- 原生 iPhone CRM 客户信息 App。
- 使用现有 CRM 账号登录。
- 支持配置网关 API 地址。
- 展示客户列表、客户详情。
- 将 token 存储到 Keychain。

关键代码：

| 文件 | 说明 |
| --- | --- |
| `CrmMobileApp.swift` | SwiftUI App 入口 |
| `SessionStore.swift` | 登录态、服务器地址、token 恢复和退出 |
| `ApiClient.swift` | HTTP 请求封装，登录、客户列表、客户详情 |
| `Models.swift` | API 响应、分页、登录结果、客户模型 |
| `LoginView.swift` | 登录页和服务器地址输入 |
| `CustomerListView.swift` | 客户列表、搜索、刷新、分页加载 |
| `CustomerDetailView.swift` | 客户详情 |
| `KeychainStore.swift` | Keychain 读写封装 |
| `SharedViews.swift` | 共享 UI 组件 |

当前接口：

| 功能 | 接口 |
| --- | --- |
| 登录 | `POST /auth/login` |
| 客户列表 | `GET /customers?current=&size=&keyword=` |
| 客户详情 | `GET /customers/{id}` |

维护注意：

- 真机访问后端时不能填写 `127.0.0.1`，需要填写 Mac 或服务器局域网 IP，例如 `http://192.168.1.10:8780/api`。
- `SessionStore` 默认服务器地址是 `http://192.168.1.100:8780/api`，首次登录可在登录页覆盖。
- token 保存在 Keychain，服务器地址和用户名保存在 `UserDefaults`。
- 当前 App 只读客户信息，没有客户新增、编辑、联系人和跟进记录功能。
- 当前机器如果只有 Command Line Tools，`xcodebuild` 不能完整构建 iOS App，需要安装完整 Xcode。

## 10. 数据库模块 `sql`

路径：`sql/schema.sql`

职责：

- 创建 CRM 所需 MySQL 表。
- 初始化部门、管理员、角色、菜单权限、字典和示例客户数据。

初始化数据：

- 默认用户：`admin`
- 默认密码：`admin123`
- 默认角色：
  - `ADMIN`：超级管理员，拥有全部初始化菜单权限。
  - `SALES`：销售，初始化存在但默认未分配给示例用户。
- 示例客户：`示例科技有限公司`

表按领域划分：

| 领域 | 表 |
| --- | --- |
| 组织和账号 | `sys_dept`、`sys_user`、`sys_role`、`sys_user_role` |
| 权限 | `sys_menu`、`sys_role_menu` |
| 审计和字典 | `sys_login_log`、`sys_dict_type`、`sys_dict_data` |
| 客户 | `crm_customer`、`crm_contact`、`crm_follow_record` |
| 销售扩展 | `crm_opportunity`、`crm_contract`、`crm_payment` |

维护注意：

- 修改表结构后，应同步检查 Entity、Mapper XML、前端 TypeScript 类型和 iOS Swift 模型。
- 新增权限码时，应同步插入 `sys_menu` 并为目标角色插入 `sys_role_menu`。
- 当前 SQL 会先 `DROP TABLE`，只适合初始化开发环境；生产变更应使用迁移脚本。
- 当前没有外键约束，应用层需要负责删除和关联完整性。

## 11. 部署模块 `deploy`

路径：`deploy/`

职责：

- 提供 Nginx 静态资源和反向代理配置。
- 提供 Nacos 本地 Docker Compose 和配置样例。
- 提供微服务平滑重启脚本。

关键文件：

| 文件 | 说明 |
| --- | --- |
| `deploy/README.md` | Nginx、后端 Jar、平滑重启说明 |
| `deploy/nginx.conf` | Nginx 站点配置 |
| `deploy/homebrew-crm.conf` | 本机 Homebrew Nginx 配置 |
| `deploy/graceful-restart.sh` | 平滑重启脚本 |
| `deploy/nacos/docker-compose.yml` | 本地 Nacos |
| `deploy/nacos/*-dev.yaml` | 各服务 Nacos 配置 |
| `deploy/nacos/README.md` | Nacos 配置导入和启动说明 |

Nacos Data ID：

| Data ID | 服务 |
| --- | --- |
| `gateway-dev.yaml` | `gateway` |
| `system-dev.yaml` | `system` |
| `customer-dev.yaml` | `customer` |
| `admin-dev.yaml` | `admin` |

关键环境变量：

| 变量 | 说明 |
| --- | --- |
| `NACOS_SERVER_ADDR` | Nacos 地址，默认 `127.0.0.1:8848` |
| `NACOS_GROUP` | Nacos 分组，默认 `DEFAULT_GROUP` |
| `SPRING_PROFILES_ACTIVE` | Spring profile，默认 `dev` |
| `MYSQL_URL` | MySQL JDBC URL |
| `MYSQL_USER` | MySQL 用户名 |
| `MYSQL_PASSWORD` | MySQL 密码 |
| `CRM_JWT_SECRET` | JWT 签名密钥 |
| `CRM_JWT_EXPIRE_SECONDS` | JWT 过期秒数 |

维护注意：

- Nacos 配置文件是运行时配置的事实来源，本地服务模块只保留 `bootstrap.yml`。
- 数据库密码、JWT 密钥等生产敏感配置不要直接提交到仓库。
- 平滑重启脚本依赖 Actuator `serviceregistry` 端点，相关端点需要在 Nacos 配置中暴露。
- 多实例滚动重启时，确保同一服务至少保留一个健康实例承接流量。

## 12. 新增功能维护清单

新增一个业务功能时，建议按以下顺序检查：

1. 数据库：是否需要新增表、字段、索引、字典或初始化权限。
2. 后端 Entity/Mapper：字段名是否和 MyBatis-Plus 驼峰映射一致，特殊 SQL 是否需要 XML。
3. 后端 Service：是否需要事务、默认值、删除策略和权限归属。
4. 后端 Controller：路径是否清晰，是否加 `@PreAuthorize`。
5. 网关：是否需要在 `gateway-dev.yaml` 增加 `/api/**` 路由。
6. 权限：是否需要新增 `sys_menu.permission_code` 和角色授权。
7. 前端：是否需要新增类型、API 方法、菜单项、页面组件和表单校验。
8. iOS：如果移动端需要支持，是否同步 Swift 模型和 `ApiClient`。
9. 部署：是否需要新增 Nacos 配置、环境变量或 Nginx 规则。
10. 文档：同步更新本文档和 README。

## 13. 常见排障入口

| 现象 | 优先检查 |
| --- | --- |
| 前端 404 | `VITE_API_BASE`、Nginx 代理、gateway 路由是否覆盖路径 |
| 前端 401 | token 是否存在、JWT 是否过期、后端 `crm.jwt.secret` 是否一致 |
| 前端 403 | 用户角色是否分配了对应 `permission_code`，重新登录是否刷新权限 |
| 服务启动但无配置 | Nacos Data ID、Group、profile、`NACOS_SERVER_ADDR` |
| 网关找不到服务 | Nacos Discovery 服务名、下游服务是否健康注册 |
| 数据字段为空 | Entity 字段、Mapper XML、MyBatis 驼峰映射、前端类型字段名 |
| 删除失败或脏数据 | 是否存在关联数据，当前数据库没有外键兜底 |
