# 前端工程化与实战：Vite、Ant Design、联调和发布

目标：掌握 `frontend/` 从依赖安装、本地开发、组件库使用、API 联调，到质量验证、性能、安全和生产发布的完整链路。读完应能把一个需求安全地交付，而不只是写出能显示的组件。

前置：[JavaScript](JAVASCRIPT.md)、[TypeScript](TYPESCRIPT.md)、[React](REACT.md)、[HTML 与 CSS](HTML_CSS.md)。详细项目命令另见 [Web 管理台开发手册](../manuals/FRONTEND.md)。

## 1. 当前工程全貌

| 能力     | 当前选择                   | 在项目中的位置                            |
| ------ | ---------------------- | ---------------------------------- |
| UI 运行时 | React 18               | `src/main.tsx`、`src/App.tsx`       |
| 类型     | TypeScript 5.3         | `.ts/.tsx`、`tsconfig.json`         |
| 开发/构建  | Vite 5                 | `vite.config.ts`                   |
| 组件库    | Ant Design 5           | Layout、Table、Form、Modal 等          |
| 图标     | `@ant-design/icons`    | 菜单与操作                              |
| 请求     | 浏览器 fetch              | `src/api.ts` 的 `request<T>`        |
| 状态     | React 本地 state + 浏览器存储 | 页面、Token、工作区                       |
| 包管理    | npm + lockfile         | `package.json`、`package-lock.json` |
| 最低验证   | tsc + Vite build       | `npm run build`                    |

当前没有路由库、全局状态库、请求缓存库、单测脚本或 lint 脚本。学习时要理解现状，新增工具必须由真实问题驱动并包含迁移/验证成本，不能照搬别的项目模板。

## 2. Node.js、npm 与 package.json

开发需要 Node.js 18+。确认环境：

```bash
node --version
npm --version
cd frontend
npm install
```

`package.json`：直接依赖和 scripts。`package-lock.json`：完整解析版本与完整性，团队/CI 必须提交。

常用：

```bash
npm run dev
npm run build
npm run preview
npm ls --depth=0
```

- `npm install` 适合本地增改依赖，会更新 lockfile。
- `npm ci` 要求 lockfile 与 package.json 一致，适合 CI/生产构建。
- 不手工编辑 `node_modules`。
- 不因 audit 提示就无验证执行大版本 `--force`；先看是否影响生产路径、升级说明和回归范围。
- dependencies/devDependencies 的打包结果由 import 和构建决定，但分类仍应准确表达运行/开发用途。

## 3. 语义化版本与依赖升级

`^5.15.3` 通常允许同一主版本内的新版本。新 minor/patch 也可能带行为或样式变化，组件库尤其需要视觉回归。

升级流程：

1. 明确升级原因（安全、Bug、兼容、新能力）。
2. 阅读目标版本 release notes/migration guide。
3. 在独立分支更新 package.json/lockfile。
4. 运行 build。
5. 回归登录、菜单、Table、Form、Modal、Drawer、Upload、响应式。
6. 对比生产 bundle 和浏览器兼容性。
7. 保留明确回滚提交。

React/AntD/Vite 跨主版本不与普通业务功能混合升级。

## 4. Vite 开发服务器

`vite.config.ts`：

```ts
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8780',
      changeOrigin: true
    }
  }
}
```

开发请求 `/api/...` 时由 Vite 代理到 Gateway，浏览器看来仍同源，可避免本地 CORS 配置负担。

理解边界：

- proxy 只在 Vite dev server 生效。
- `npm run preview`/生产静态服务器不是同一代理方案。
- 生产 `/api` 由 Nginx/入口网关反代。
- 前端 API 函数路径只写 `/customers` 等，`API_BASE` 已包含 `/api`。

## 5. 环境变量

```env
VITE_API_BASE=http://127.0.0.1:8780/api
```

规则：

- 客户端只暴露 `VITE_` 前缀。
- 值在构建时替换，不是部署后动态读取服务器环境。
- 任何进入前端环境变量的内容都可能出现在 JS 产物中，不能放密码、私钥、数据库凭据。
- `.env.local` 属于本机配置，不提交。
- 改值后重启 dev server；生产必须重新 build。

多环境应通过可审计构建参数、版本化发布物和部署配置管理，避免开发者本机随意生成不知来源的产物。

## 6. 构建过程

```bash
cd frontend
npm run build
```

实际顺序：

```text
tsc（严格类型检查，无输出）
-> vite build（解析模块、转换 TSX、打包、压缩、带 hash 资源）
-> dist/
```

构建成功证明：类型与打包通过。它不证明：接口可用、权限正确、页面无视觉问题、业务行为正确。

本地检查生产产物：

```bash
npm run preview
```

preview 用于快速检查静态产物，不替代 Nginx、HTTPS、真实 Gateway 和生产配置验证。

## 7. Ant Design 基础

项目通过 `ConfigProvider` 配置中文和主题 token。常用组件：

- Layout/Menu/Tabs：应用框架与工作区。
- Table：分页、排序、数据操作。
- Form/Input/InputNumber/Select：输入和校验。
- Modal/Drawer/Popconfirm：编辑、详情与高风险确认。
- Upload：持仓截图。
- Alert/Tag/Progress/Statistic/Typography：状态与指标。
- `App as AntApp`：提供 message/modal 等上下文能力。

先看当前版本组件 API 和现有用法，避免复制不同版本示例。TypeScript 报 overload/Props 错时，通常是版本或类型契约不一致，不用 `as any` 压掉。

## 8. Ant Design Table 实战

一张业务表需要：

```text
类型化 columns + 稳定 rowKey + loading + dataSource
+ 后端分页 total/current/pageSize
+ 排序/筛选映射 + 空态 + 操作确认
```

列定义使用 `ColumnsType<T>`。render 内要区分原始值、行对象和展示格式。

注意：

- Table 排序值 `ascend/descend` 要映射为后端允许格式。
- `dataIndex` 不一定等于后端 SQL 字段，必须白名单映射。
- 固定列/横向 scroll 要在小屏验证。
- 不在 render 中发请求或修改 state。
- 大表格先使用后端分页，不一次加载全量。

## 9. Form 实战

为 API 请求创建清晰表单类型，不默认复用完整响应 Entity。

校验分层：

- 即时体验：必填、长度、格式、范围。
- 跨字段业务：例如开始日期早于结束日期。
- 后端：权限、唯一性、数据存在和最终业务规则。

提交状态：

```text
validate -> set loading -> await API
-> success message/close/refresh
或 error message/保留用户输入
-> finally clear loading
```

编辑 Modal 打开时显式 reset/回填。字段若含服务端只读值，不应提交回去造成 over-posting。

## 10. API 封装与 Gateway

统一链路：

```text
页面 API 函数
-> request<T>
-> VITE_API_BASE + 相对 path
-> Content-Type / X-Client-Source / Bearer Token
-> Gateway /api/**
-> Java 服务
-> ApiResponse<T>
```

新增 API：

1. 先验证后端和 Gateway 路由。
2. 定义真实 TypeScript 请求/响应类型。
3. 路径不重复 `/api`。
4. query 使用编码，不拼用户输入。
5. JSON body `JSON.stringify`；FormData 不手写 Content-Type。
6. 页面处理 loading、空、错、成功。
7. 验证普通用户 403，而非只用管理员。

HTTP 状态与业务 code 是两个边界。Network 中同时检查 Request URL、Header、Payload、HTTP status 和响应 JSON。

## 11. 登录与权限

当前流程：

```text
login -> Token 存 localStorage
-> request 每次读取 Token 加 Authorization
-> App 有 Token 就显示管理台
-> 退出清 Token/工作区 state
```

风险和改进方向：

- localStorage Token 暴露给同源 JS，XSS 风险高。
- 仅判断 Token 存在，不代表未过期。
- 多页面可能重复处理 401/403。
- 前端菜单/按钮隐藏不能代替后端授权。
- 权限修改后旧 Token 可能需重新登录。

若统一处理认证失败，需设计 `request` 层事件/回调、刷新 Token 策略和并发失败行为，避免每个 API 同时触发多次退出或提示。

## 12. CORS、代理与 HTTPS

CORS 是浏览器对跨源读取的限制：origin 由协议、主机、端口组成。常见误区：

- Postman/curl 成功不代表浏览器 CORS 成功。
- CORS 允许不等于用户有权限。
- `mode: no-cors` 得到的 opaque 响应不能解决业务 API。
- 随意允许 `*` 与凭据会产生安全/兼容问题。

项目生产优先前端与 `/api` 同源，由 Nginx 转 Gateway，全链路 HTTPS，减少 CORS 与混合内容问题。

## 13. 浏览器兼容性

构建 target 是 ES2020，使用现代浏览器 API。上线前明确支持矩阵：Chrome/Edge/Safari/企业受管浏览器版本。

兼容性由两部分决定：

- 语法转换：Vite/esbuild target。
- Web API：fetch、FormData、Intl 等不会因语法转换自动获得 polyfill。

若必须支持旧环境，先统计用户与成本，再配置 target/polyfill 和自动化浏览器测试，不能只改一行 target 认为完成。

## 14. 代码组织与可维护性

当前 `App.tsx` 和 `api.ts` 已较大。合理演进方向：

```text
src/
  app/          应用壳、工作区、Provider
  features/     customers/funds/system 等领域
  api/          request 核心与领域 API
  components/   真正跨领域共享组件
  utils/        纯格式化/转换
```

但重构要满足：

- 以领域边界拆，不按“所有 hooks/所有 types”制造远距离跳转。
- 先有构建与关键冒烟保护。
- 保持 API path/Header/行为不变。
- 小步移动、每步可回滚。
- 不与无关功能混提交。

## 15. 质量门禁

当前最低：

```bash
cd frontend
npm run build
```

建议逐步补齐：

- ESLint：Hook 依赖、明显错误与一致性。
- formatter：减少格式争论。
- 单元/组件测试：Vitest + Testing Library 等适配 Vite 的方案。
- E2E：Playwright 等覆盖真实浏览器链路。
- CI：clean install、typecheck、test、build、产物保存。

引入工具时固定版本、提交配置、提供 scripts、修复/基线化现有问题，并写进开发手册。不能只在某个人 IDE 生效。

## 16. 测试金字塔与场景

### 纯函数

适合：`filterTrendPeriod`、日期/金额格式化、菜单树转换、排序映射。快、稳定、定位清楚。

### 组件

适合：

- loading/空/错/成功。
- 表单校验与提交。
- 筛选重置页码。
- 删除确认。
- 权限按钮隐藏。

使用 Mock Service Worker 或可控 API mock 比直接 mock 每个内部函数更贴近用户行为。

### E2E

最小主链：

```text
登录 -> 打开菜单 -> 客户列表 -> 搜索 -> 新增/编辑
-> 基金详情 -> 持仓 -> 退出 -> 无权限拒绝
```

测试账号和数据必须隔离、可重置，不连接生产。

## 17. 可观测性与故障排查

前端故障证据：

- JS error：异常类型、堆栈、source map 对应版本。
- Network：URL/status/timing/response/request ID。
- 资源错误：404、MIME、缓存、CSP。
- 性能：Core Web Vitals/自定义业务时延。
- 用户路径：脱敏且遵守隐私。

生产错误采集要带 release/version、页面、浏览器、request ID，但不带 Token、密码、完整表单、OCR 图或敏感响应。

典型分层：

```text
页面没发请求 -> React/事件/校验
请求 URL 错 -> env/API 拼接
浏览器拦截 -> CORS/TLS/CSP
401/403 -> 登录/权限
404 -> path/Gateway 路由
429 -> 限流
503 -> 下游注册/健康
500 -> 服务日志 + request ID
200 但 code != 0 -> 业务错误
```

## 18. 性能优化

关注：

- 首屏 JS/CSS 体积。
- API 数量、并发、重复和耗时。
- Table 行数与复杂 render。
- 趋势图计算与 SVG 节点。
- Tabs 保留的隐藏页面。
- 图片上传前大小和内存。

手段：

- 领域级懒加载和 code splitting（当前单入口尚未做）。
- 服务端分页/筛选/聚合。
- 请求缓存与失效（需统一方案）。
- memo/虚拟化（先 profile）。
- gzip/Brotli、hash 静态资源长缓存。
- `index.html` 不长缓存，避免引用不存在的旧/新 chunk。

性能预算应量化，例如首屏资源大小、关键接口 p95、最大表格行数，而不是“感觉更快”。

## 19. 安全工程

前端关键面：

- XSS：不渲染未净化 HTML；依赖也可能带风险。
- Token：不输出、不放 URL、不提交；评估更安全存储方案。
- 上传：前后端共同限制数量、MIME、大小；内容校验在服务端。
- 权限：UI 控制 + 后端强制授权。
- 供应链：lockfile、可信 registry、审计安装脚本和依赖更新。
- CSP：生产可逐步限制脚本/连接/图片来源，先报告模式验证。
- source map：是否公开取决于错误平台和源码策略，不能包含秘密。
- 环境变量：前端构建物中没有真正秘密。

安全修复要做负向测试：恶意 HTML、超大文件、无权限请求、过期 Token、重复提交。

## 20. 生产发布

推荐：

```bash
cd frontend
npm ci
VITE_API_BASE=/api npm run build
```

发布 `dist/` 到版本化目录，由 Nginx 提供静态文件并反代 `/api/`。

发布检查：

- 构建来源/commit/环境明确。
- `index.html` 和 hash 资源完整。
- `/api` 指向正确 Gateway。
- HTTPS、证书、CSP/安全 Header 符合要求。
- SPA fallback（若未来引入前端路由）正确。
- 静态缓存策略正确。
- 登录和主业务冒烟通过。
- 监控中 release 已标记。

回滚必须切换整套上一版本产物，不混合两个 `dist` 的 index/chunk。

## 21. 缓存策略

Vite 输出带内容 hash 的资源可：

```text
Cache-Control: public, max-age=31536000, immutable
```

`index.html` 应短缓存或 no-cache，确保用户拿到引用当前 hash 的入口。若只覆盖部分文件，用户可能拿到新 index + 缺失 chunk 或旧 index + 已删除 chunk。

API 缓存需按业务敏感性和用户维度设计；带认证的客户/持仓数据不交给公共缓存。

## 22. 真实场景一：新增后端字段

假设客户增加 `riskLevel`：

1. 核对 API 模型、可空性、枚举和权限。
2. `Customer`/请求 DTO 增加准确联合类型。
3. 列表增加 Tag 展示并处理未知/空值。
4. 表单增加 Select，编辑正确回填。
5. 保存请求只发送允许字段。
6. 小屏/长文案视觉验证。
7. 旧数据 null、非法值、普通权限测试。
8. build + Network + 数据库/接口结果联合验收。
9. 更新 API/前端手册。

## 23. 真实场景二：列表请求偶发覆盖

症状：快速搜索“张”再输入“张三”，最终显示“张”的结果。

诊断：

1. Network 看两个请求发出和完成顺序。
2. 在 load 记录脱敏 query/request ID。
3. 确认旧 Promise 最后执行 setRows。

修复选项：

- debounce 减少请求，但不能完全解决竞态。
- AbortController 取消旧请求。
- 请求序号只接受最新结果。
- 引入统一 query 库（规模足够时）。

验收：人工快速输入 + 自动控制响应逆序，证明旧结果不会覆盖。

## 24. 真实场景三：生产白屏

排查顺序：

1. 浏览器 Console 是否 chunk 404/JS 异常/CSP 拒绝。
2. Network 的 index 与静态资源是否来自同一 release。
3. 检查 Content-Type 和缓存 Header。
4. 比对部署 commit、构建环境和 `VITE_API_BASE`。
5. 若只是 API 故障，页面应显示错误而非白屏。
6. 影响扩大时切回上一整套 dist。

白屏恢复后保留证据，补原子发布、错误边界、监控和冒烟，不能只手工刷新缓存结束。

## 25. 真实场景四：接口 403

```text
请求是否有 Bearer Token
-> Token 是否过期/签名正确
-> X-Client-Source 是否保留
-> Gateway 是否放行/转发
-> 后端 @PreAuthorize 权限
-> 用户角色/菜单/authority 是否生效
```

前端显示了按钮不证明拥有权限。用管理员和受限账号对照同一请求，记录 HTTP status 与业务 message；修复权限模型时同步测试各客户端。

## 26. 交付清单

需求完成前：

- [ ] 修改范围与后端契约一致。
- [ ] TypeScript 没有用 `any`/断言隐藏问题。
- [ ] loading、空、错、成功、重复点击、竞态已考虑。
- [ ] 分页/排序/筛选和权限已验证。
- [ ] 小屏、长文本、键盘和主要弹窗已检查。
- [ ] API path、Header、Token、FormData 行为未破坏。
- [ ] `npm run build` 通过。
- [ ] 受影响业务冒烟通过，普通权限账号有负向验证。
- [ ] 未提交 `.env.local`、Token、用户数据、构建产物或本机路径。
- [ ] 文档/API 契约已同步，发布和回滚方式明确。

## 27. 完成标准

你应能独立：

- 解释 npm、lockfile、Vite dev/build/preview 的职责。
- 正确配置 `VITE_API_BASE` 并区分开发代理与生产反代。
- 用 Ant Design 构建类型化 Table/Form/Modal 工作流。
- 从浏览器请求定位到 Gateway/后端层次。
- 设计构建、组件、E2E 的分层验证。
- 诊断竞态、白屏、403、缓存和发布版本混合问题。
- 评估性能、安全、可访问性和依赖升级风险。
- 生成可追溯、可回滚、整套一致的生产静态产物。
