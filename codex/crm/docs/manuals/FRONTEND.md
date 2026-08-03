# Web 管理台开发手册

本文覆盖 `frontend/` 的 React/TypeScript 开发、API 对接、构建与发布。路径与权限见 [API 参考](../reference/API.md)，字段类型与 JSON 示例见 [API 数据模型](../reference/API_MODELS.md)。

## 1. 技术栈与结构

| 项          | 当前实现                           |
| ---------- | ------------------------------ |
| React      | 18.2                           |
| TypeScript | 5.3                            |
| Vite       | 5.1                            |
| Ant Design | 5.15                           |
| 请求         | 浏览器 `fetch`，统一封装在 `src/api.ts` |
| 状态         | 组件状态 + 浏览器存储中的 Token           |

| 文件               | 职责                                     |
| ---------------- | -------------------------------------- |
| `src/api.ts`     | API 类型、Base URL、Token/Header、错误和全部请求函数 |
| `src/App.tsx`    | 登录、菜单、页面、表格、表单、弹窗和业务状态                 |
| `src/styles.css` | 全局与业务布局                                |
| `src/main.tsx`   | React 挂载入口                             |
| `vite.config.ts` | Vite 配置                                |

页面目前较集中，修改前先搜索现有类型/函数/菜单 key，避免重复实现。

## 2. 安装与运行

需要 Node.js 18+ 和 npm。

```bash
node --version
npm --version
cd frontend
npm install
npm run dev
```

Vite 监听 `0.0.0.0`，地址以终端输出为准，通常是 `http://127.0.0.1:5173`。

## 3. API 地址

复制本地配置：

```bash
cd frontend
cp .env.example .env.local
```

```env
VITE_API_BASE=http://127.0.0.1:8780/api
```

规则：

- Base 必须包含 `/api`。
- `api.ts` 中路径写 `/customers`，不能再写 `/api/customers`。
- 生产默认建议 `VITE_API_BASE=/api`，由同源 Nginx 转发，避免 CORS 和环境域名写死。
- Vite 变量在构建时固化；改环境变量后需要重新构建。

## 4. 登录与请求封装

`request<T>()` 负责：

1. 拼接 `VITE_API_BASE` 与相对路径。
2. 默认发送 JSON Content-Type。
3. 添加 `X-Client-Source: web`。
4. 从存储读取 Token 并添加 Bearer Header。
5. 解析 `ApiResponse<T>`，业务 `code != 0` 时抛错。

开发环境初始化账号 `admin/admin123` 仅用于本地。生产不能保留该密码。

浏览器排查：打开 DevTools → Network，检查 Request URL、Authorization、X-Client-Source、HTTP 状态和响应 `code/message`。

## 5. 当前功能

| 领域   | Web 能力                          |
| ---- | ------------------------------- |
| CRM  | 客户列表、搜索、新增、编辑、删除                |
| 权限   | 用户、角色、菜单管理；登录用户菜单               |
| 基金   | 列表、筛选、排序、详情、净值、持仓、估值、特征、评级、CRUD |
| 自选   | 加入/移出、独立自选列表                    |
| 评分   | 配置权重、入队回测/推荐、激活、查看任务和结果         |
| 用户持仓 | OCR 上传、预览校对、确认、批次和持仓列表          |
| 资讯   | 列表、频道/关键词筛选、删除                  |
| 股票   | 列表、排序、详情、历史行情                   |

完整接口覆盖见 [API 参考](../reference/API.md)。

## 6. 新增 API

顺序固定：

1. 确认 Java 接口已经通过 Gateway 可访问。
2. 在 `api.ts` 增加/修改 TypeScript 类型，字段可空性与 JSON 一致。
3. 增加请求函数，路径不带 `/api`。
4. 在页面调用并处理 loading、成功、空数据和错误。
5. 运行 TypeScript/Vite 构建。
6. 用普通权限账号做 403 验证。

示例：

```ts
export type Example = {
  id: number
  name: string
  note?: string
}

export function listExamples() {
  return request<Example[]>('/examples')
}
```

不要使用 `any` 掩盖 DTO 不一致；Decimal 后端字段通常作为 JSON number 或可空值，先看真实响应再定类型。

## 7. 新增页面

1. 定义页面/menu key，并确认权限菜单中存在对应项。
2. 添加组件与本地状态：数据、loading、错误、筛选、分页。
3. 使用已有 Ant Design 表格、表单、Modal 和消息模式。
4. 分页把 `current/size` 传给后端，使用返回的 `total`。
5. 排序只传后端允许的 `sortField/sortOrder`。
6. 操作按钮按权限隐藏，但后端仍必须做权限校验。
7. 处理空列表、慢请求、重复点击和组件卸载后的状态更新。

## 8. 表单与数据安全

- 新增和编辑共用表单时，打开弹窗前明确 reset/回填。
- 前端校验用于体验，后端校验才是安全边界。
- 删除使用确认弹窗，成功后重新拉取当前页。
- 不在日志/消息中展示 Token、密码、原始 OCR 图片或后端堆栈。
- 用户输入不要通过 `dangerouslySetInnerHTML` 渲染。
- 上传前提示支持格式/大小，后端仍需再次验证。

## 9. 基金与持仓特殊规则

- 基金名称和代码在二维表中固定，指标区可横向滚动。
- 排序字段必须与 Java 白名单同步。
- 自选是当前用户名维度，不要只在浏览器本地维护。
- 总分与未来一年盈利概率是不同字段；未验证 profile 不显示概率。
- 评分回测/推荐接口只入队，需要轮询/刷新任务状态。
- OCR 预览允许用户修正；只有 confirm 才影响最终持仓。
- 持仓快照和交易明细的覆盖/调整规则不可混用。

## 10. 联调

最短链路：

```text
MySQL -> Nacos/Redis -> system/customer/fund/gateway -> Vite
```

检查：

```bash
curl -fsS http://127.0.0.1:8780/actuator/health
curl -fsS http://127.0.0.1:8780/actuator/gateway/routes
```

浏览器常见状态：

| 状态        | 含义                       |
| --------- | ------------------------ |
| `401/403` | 未登录、Token 失效或权限不足        |
| `404`     | Base/路径错误或 Gateway 路由未覆盖 |
| `429`     | Gateway 限流               |
| `503`     | 下游未注册或不健康                |
| `500`     | 服务异常；结合响应 message 和服务日志  |

## 11. 测试与构建

当前 package scripts 没有独立 lint/unit test，最低验收是：

```bash
cd frontend
npm run build
```

它会先执行 `tsc`，再生成 `dist/`。手工冒烟至少覆盖：

```text
登录 -> 菜单 -> 客户列表 -> 基金列表/详情 -> 持仓列表 -> 退出/重新登录
```

修改相应功能时再覆盖创建/编辑/删除、筛选、分页、排序、上传和权限拒绝。

## 12. 生产构建与 Nginx

```bash
cd frontend
npm ci
VITE_API_BASE=/api npm run build
```

发布 `frontend/dist/` 到 Nginx 静态目录。推荐版本化目录和原子软链接切换：

```text
/srv/crm/frontend/releases/<release-id>/
/srv/crm/frontend/current -> releases/<release-id>
```

发布前检查：

- `index.html` 和静态资源能加载。
- SPA 未知路由回退 `index.html`。
- `/api/` 反代 Gateway，保留必要 Header 和超时。
- 生产为 HTTPS，不混用 HTTP API。
- Cache-Control：带 hash 资源可长缓存，`index.html` 不长缓存。

回滚只切回上一完整 `dist`，不要混合两个版本的文件。

## 13. 常见故障

| 现象                | 原因                         | 处理                          |
| ----------------- | -------------------------- | --------------------------- |
| Network Error     | Gateway 未启动/Base 错         | 检查 `.env.local` 与健康接口       |
| URL 出现 `/api/api` | Base 和函数路径重复               | 函数路径移除 `/api`               |
| 刷新页面 404          | Nginx 未配置 SPA fallback     | `try_files ... /index.html` |
| 修改环境变量无效          | Vite 已构建                   | 重新 build/deploy             |
| 页面无菜单             | Token 权限或 `/menus/mine`    | 重新登录并检查角色菜单                 |
| TypeScript 编译失败   | 后端字段与类型不同步                 | 先修 `api.ts` 类型              |
| 评分一直 pending      | Python Worker/pipeline 未消费 | 查 Prefect 和 score jobs      |

## 14. 提交前检查

- [ ] API 路径不重复 `/api`，Header 和 Token 流程未被破坏。
- [ ] 类型与后端真实 JSON 一致，没有用 `any` 规避错误。
- [ ] loading、空态、错误、分页和重复操作均处理。
- [ ] 权限按钮和后端权限同时验证。
- [ ] `npm run build` 通过。
- [ ] 受影响业务冒烟通过。
- [ ] API 和项目文档已同步。
- [ ] 未提交 `.env.local`、Token、用户数据或构建机绝对路径。
