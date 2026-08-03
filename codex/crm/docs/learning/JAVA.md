# Java 基础与进阶：结合 CRM 后端

目标：从能读 Java 语法，进阶到能安全维护 `backend/` 的 Spring Boot 微服务。项目以 Java 8 为兼容基线，示例不要使用更高版本语言特性。

## 1. 先建立全局认识

一次客户列表请求：

```text
GET /api/customers
-> Gateway 路由并移除 /api
-> CustomerController
-> ICustomerService / CustomerServiceImpl
-> CrmCustomerMapper / XML
-> crm_customer
-> ApiResponse<Page<CrmCustomer>>
```

先打开：

- `backend/pom.xml`：父工程、版本和模块。
- `backend/customer/.../CustomerController.java`：HTTP 入口。
- `backend/customer/.../CustomerServiceImpl.java`：业务实现。
- `backend/customer/.../CrmCustomerMapper.xml`：SQL/字段映射。
- `backend/core/.../ApiResponse.java`：统一返回。

## 2. 基础语法必须掌握

### 变量与基本类型

Java 区分基本类型与引用类型：

| 基本类型      | 包装类型      | 项目常见用途                |
| --------- | --------- | --------------------- |
| `long`    | `Long`    | ID、分页；包装类型可为 null     |
| `int`     | `Integer` | 状态、标签                 |
| `boolean` | `Boolean` | 查询条件中的“未传”和 false 要区分 |
| `double`  | `Double`  | 不适合精确金额               |

金额/净值优先 `BigDecimal`，不要用 `double` 累计。数据库可空列映射包装类型，不要用基本类型把 null 偷偷变成 0。

### String

- 用 `equals` 比较内容，不用 `==`。
- 用户输入先判断 null，再 `trim()`。
- 不把用户输入直接拼到 SQL/ORDER BY；排序必须白名单。
- String 不可变，循环拼接用 `StringBuilder`。

项目例子：`StockController` 用 `Map<String,String>` 将前端排序字段映射为固定 SQL 列。

### 条件、循环与方法

必须能读懂 `if/else`、`switch`、增强 `for`、`while`、早返回和方法重载。复杂方法先识别：输入、前置校验、主流程、事务/副作用和返回。

练习：在纸上追踪 `FundController.page` 的每个查询参数如何传给 Service，不改代码。

## 3. 面向对象

### 类、对象和封装

- Entity：数据库行，例如 `CrmCustomer`。
- DTO/Request/Response：API 输入输出，不等同于表。
- Service：业务动作。
- Controller：HTTP 适配。
- Mapper：数据库访问。

字段应通过清晰类型表达约束。不要把所有数据装进 `Map<String,Object>`；当前股票接口使用 Map 是局部历史实现，不是新代码默认模板。

### 接口与实现

`ICustomerService` 定义能力，`CustomerServiceImpl` 实现。Controller 依赖接口，有利于替换实现和测试。

```java
public interface ExampleService {
    ExampleResponse detail(Long id);
}
```

接口不是为了“每个类都多一层”，而是稳定边界。局部无替换价值的纯工具类可不机械创建接口。

### 继承与组合

优先组合：Service 注入 Mapper/其他 Service。继承只用于真正的“is-a”和框架扩展。避免为了复用几行代码建立深继承树。

## 4. 集合、泛型与 Optional

### 集合

| 类型         | 特点     | 项目用途        |
| ---------- | ------ | ----------- |
| `List<T>`  | 有序、可重复 | 查询结果、DTO 列表 |
| `Set<T>`   | 去重     | 权限码、菜单 ID   |
| `Map<K,V>` | Key 查找 | 白名单、聚合      |

选择集合前明确顺序、重复和查找需求。不要在循环中不断查询数据库形成 N+1。

### 泛型

`ApiResponse<T>` 和 `Page<T>` 让编译器知道 data/records 的真实类型。看到嵌套类型从外向内读：

```text
ApiResponse<Page<CfgFund>>
响应 -> data 是分页 -> records 是基金
```

避免 raw type，如 `List`、`Map`，因为会丢失编译期检查。

### Optional

Java 8 的 `Optional` 适合返回“可能不存在”，不建议用作 Entity 字段或到处作为方法参数。项目更多使用 null，维护时必须清楚每个字段可空性。

## 5. 异常

| 类型      | 例子                         | 处理原则           |
| ------- | -------------------------- | -------------- |
| Checked | `IOException`              | 调用者必须处理/声明     |
| Runtime | `IllegalArgumentException` | 参数/编程错误，统一转换响应 |
| 业务异常    | `BusinessException`        | 给用户可理解信息       |

`GlobalExceptionHandler` 将异常转成 `ApiResponse`。不要 `catch (Exception) {}` 静默吞掉，也不要把数据库堆栈发给客户端。

练习：追踪一个不存在股票如何从 `IllegalArgumentException` 变成 HTTP/JSON，记录当前 HTTP 状态与业务 code 的差异。

## 6. Lambda、Stream 与日期

Java 8 Lambda 用于集合转换/过滤，但复杂业务不应写成难调试的长 Stream 链。判断标准：代码是否比普通循环更清楚，异常和空值是否明确。

日期：

- 新代码优先 `java.time`。
- 明确时区 `Asia/Shanghai`。
- API 日期格式写进契约。
- `Date`、时间戳、业务日不能混为一谈。

评分历史数据尤其要区分“快照日”“数据可得日”“未来标签日”。

## 7. Maven

必须理解：父 POM、module、dependency、dependencyManagement、plugin/pluginManagement、scope、生命周期。

常用：

```bash
cd backend
mvn -pl customer -am test
mvn -pl fund -am -DskipTests package
mvn dependency:tree
```

- `-pl` 选择模块。
- `-am` 同时构建依赖模块。
- `test` 跑测试，`package` 生成 Jar。
- `-DskipTests` 不执行测试，不代表测试已通过。

不要靠删除本地 Maven 仓库解决所有依赖问题；先看错误、依赖树和版本管理。

## 8. Spring 基础

### IoC/依赖注入

Spring 创建 Controller/Service/Mapper 等 Bean，通过构造器注入依赖。构造器注入让依赖不可缺、易测试，优先于字段 `@Autowired`。

### 常用注解

| 注解                                       | 作用              |
| ---------------------------------------- | --------------- |
| `@SpringBootApplication`                 | 启动、扫描、自动配置      |
| `@RestController`                        | JSON Controller |
| `@RequestMapping/GetMapping/...`         | 路由              |
| `@RequestBody/PathVariable/RequestParam` | 参数绑定            |
| `@Service`                               | 业务 Bean         |
| `@Transactional`                         | 事务边界            |
| `@Configuration/@Bean`                   | 显式配置            |
| `@PreAuthorize`                          | 方法权限            |

注解不是魔法：遇到问题要知道 Bean 扫描、代理、过滤器链和异常处理在哪一步发生。

### Spring Boot 启动

启动大致经历环境/配置加载、自动配置、Bean 创建、Web Server 启动、Nacos 注册。Nacos 配置在 Bean 初始化前不足会直接启动失败。

## 9. HTTP 与 Controller

掌握 GET/POST/PUT/DELETE、Path/Query/Body/Header、Content-Type、HTTP 状态、幂等性。

- GET 不应修改数据。
- PUT/DELETE 的重试语义要清楚。
- POST 创建/入队可能不可幂等，需业务请求 ID 或重复保护。
- Controller 不放复杂 SQL/算法。
- API 输入使用独立 Request DTO + 校验。

项目所有外部请求经 Gateway `/api/**`，下游 Controller 不写 `/api`。

## 10. MyBatis、SQL 与事务

### MyBatis-Plus

提供基础 CRUD、Wrapper 和 Page。动态条件要保证参数绑定，复杂关联/性能敏感查询使用审阅过的 XML/SQL。

### SQL 必会

- SELECT/INSERT/UPDATE/DELETE。
- JOIN、GROUP BY、ORDER BY、LIMIT。
- 主键、唯一键、普通/组合索引。
- 事务 ACID、隔离级别、锁与死锁。
- `EXPLAIN` 看执行计划。

### 事务边界

一次用户确认写多表应在 Service 事务中。外部 OCR/HTTP 不要包在长数据库事务里：先解析/校验，再短事务落库。

练习：画出持仓 OCR preview 与 confirm 分别写哪些表、哪里需要事务。

## 11. Spring Security、JWT 与 RBAC

链路：

```text
请求 -> JwtAuthenticationFilter -> SecurityContext
-> URL authenticated -> @PreAuthorize -> Controller
```

必须掌握：

- 认证：你是谁；授权：你能做什么。
- JWT 可验证但默认不可撤回；过期/密钥轮换要设计。
- 角色常带 `ROLE_` 前缀；authority 精确匹配。
- 只在前端隐藏按钮不是权限控制。
- 修改角色菜单后旧 JWT 需要重新登录。

安全练习：用管理员和无权限测试用户分别访问同一接口，记录 0/403 结果，不修改生产账号。

## 12. 微服务、Nacos 与 Gateway

### 服务发现

实例向 Nacos 注册服务名、IP、端口和健康状态；Gateway 用 `lb://service` 找实例。`503` 优先查注册，不先改 Controller。

### 配置中心

`bootstrap.yml` 连接 Nacos，运行配置在 Data ID。修改仓库 YAML 后要发布并读回，运行实例是否刷新取决于配置和重启策略。

### Gateway

Gateway 基于 WebFlux：

- Filter 是 reactive，不使用 Servlet API。
- 路由、StripPrefix、CORS、限流在 Nacos。
- Redis RateLimiter 故障可影响入口健康。

## 13. 并发与线程安全

Spring Bean 默认单例。不要把一次请求的可变状态放 Service 字段中。局部变量一般安全，共享集合/缓存需要并发设计。

掌握：

- 线程、线程池、Future/CompletableFuture。
- `synchronized`、Lock、原子类、并发集合。
- 可见性、原子性、竞态和死锁。
- 数据库唯一键/事务常比 JVM 锁更适合跨实例一致性。

微服务多实例时，单机 `synchronized` 不能保证全局互斥。

## 14. JVM、内存与性能

基础：堆、栈、Metaspace、GC、类加载。排查顺序先用指标和 profile 证明瓶颈，再优化。

常见问题：

- N+1/无索引查询。
- 大分页和全表排序。
- 大 multipart/大 JSON 占堆。
- 无界集合/线程池。
- 阻塞 I/O 堆积线程。
- 日志打印大对象和参数。

工具：Actuator、JVM 指标、线程 dump、heap dump、慢查询、`EXPLAIN`。生产抓 dump 前评估磁盘和敏感数据。

## 15. 测试

### 单元测试

纯计算优先单元测试。`fund` 已有估值、持仓成本和组合计算测试，可作为模板。

```bash
cd backend
mvn -pl fund -am test
```

### 集成测试

验证 Spring Context、Mapper、数据库和安全过滤器。使用隔离数据库/事务清理，不依赖生产。

### 端到端

从 Gateway 登录并访问接口，覆盖正常、未登录、无权限、错误输入和下游不可用。

好的测试验证业务不变量，不只追求代码覆盖率。

## 16. 日志与调试

日志应包含 trace、服务、请求、用户标识（非秘密）、耗时和结果。禁止记录密码、JWT、Cookie、数据库连接串、完整 OCR 内容。

排查顺序：

1. HTTP 状态与 `ApiResponse.code/message`。
2. Gateway 日志/route。
3. 下游日志和 trace。
4. Nacos/Redis/数据库状态。
5. SQL、锁、数据内容。

## 17. 进阶设计原则

- 模块边界按业务所有权，不按技术类名随意拆。
- DTO 稳定 API，Entity 表达持久化，二者不要无条件共用。
- 幂等、重试、事务和唯一键一起设计。
- API 演进优先向后兼容；移动端升级慢，字段删除需兼容窗口。
- 配置与代码一同版本化，但秘密不版本化。
- 发布前先定义回滚，不在故障时首次思考。

## 18. 项目练习

### 练习 A：追踪登录

不改代码，画出 Gateway → AuthController → AuthService → UserDetails/Mapper → JWT → 客户端的链路，并指出认证/授权分界。

验收：能解释错误密码、过期 Token、缺权限分别在哪层失败。

### 练习 B：新增只读字段

在独立分支为客户增加一个可空只读展示字段：先设计增量 SQL，再同步 Entity/Mapper/API/Web，补测试和文档。

验收：旧数据为空不报错；旧客户端仍可工作；模块构建和接口成功。

### 练习 C：诊断 503

在本地停止 customer 服务，不改代码观察 Gateway 503、Nacos 实例和日志，再恢复服务。

验收：能证明根因是服务发现/健康，而不是前端。

### 练习 D：事务评审

阅读持仓 confirm，实现不变更，只列出所有写表、失败点和回滚需求。

验收：能指出为什么 OCR 网络请求不应放在确认事务中。

## 19. 独立维护完成标准

- [ ] 能从外部 URL 找到 Gateway 路由和 Controller。
- [ ] 能解释 DTO、Entity、Service、Mapper 的边界。
- [ ] 能写参数化 SQL/索引并识别事务范围。
- [ ] 能正确添加 JWT/RBAC 权限及测试 403。
- [ ] 能构建指定模块并从 Gateway 做端到端验证。
- [ ] 能排查 Nacos、Redis、MySQL 和下游健康。
- [ ] 能评估 Web/iOS/Android/Python/数据库的联动影响。
- [ ] 能给出备份、发布、监控和回滚方案。
