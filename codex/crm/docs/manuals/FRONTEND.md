# 前端管理台开发手册

本文覆盖 `frontend/` 的本地启动、接口调用、页面开发和排错。

## 1. 技术栈

| 项 | 说明 |
| --- | --- |
| 框架 | React 18 |
| 语言 | TypeScript |
| 构建 | Vite 5 |
| UI | Ant Design 5 |
| 图标 | `@ant-design/icons` |
| API | 原生 `fetch` 封装 |

## 2. 目录结构

```text
frontend/
  index.html
  package.json
  package-lock.json
  vite.config.ts
  tsconfig.json
  src/
    api.ts          API 封装、类型定义
    App.tsx         页面、菜单、表格、表单
    main.tsx        React 入口
    styles.css      全局样式
    vite-env.d.ts   Vite 类型声明
```

当前页面集中在 `App.tsx`，后续页面变多时建议拆成 `src/pages/`、`src/components/`、`src/hooks/`。

## 3. 启动前准备

先确认后端链路可用：

```bash
curl -i http://127.0.0.1:8780/actuator/health
```

期望：

```text
HTTP/1.1 200 OK
{"status":"UP"}
```

如果返回 `DOWN`，先处理后端 Redis/Nacos 问题。

## 4. 安装依赖

```bash
cd frontend
npm install
```

如果依赖异常，先删除 `node_modules` 后重装：

```bash
rm -rf node_modules
npm install
```

不要随意删除 `package-lock.json`，除非明确要整体升级依赖。

## 5. 配置 API 地址

默认 API 基址在 `frontend/src/api.ts`：

```ts
const API_BASE = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '');
```

本地开发推荐建 `frontend/.env.local`：

```text
VITE_API_BASE=http://127.0.0.1:8780/api
```

如果前端由 Nginx 反代到 gateway，也可以不配置，走默认 `/api`。

## 6. 启动开发服务器

```bash
cd frontend
npm run dev
```

Vite 会输出访问地址，例如：

```text
Local:   http://localhost:5173/
Network: http://192.168.1.10:5173/
```

浏览器打开 Local 地址即可。

## 7. 登录

默认账号：

```text
用户名：admin
密码：admin123
```

登录成功后 token 存在：

```text
localStorage.crm_token
```

退出时会删除这个 token。

## 8. API 封装说明

所有请求走 `request<T>()`：

```ts
export async function request<T>(path: string, options: RequestInit = {}): Promise<T>
```

它会自动处理：

| 行为 | 说明 |
| --- | --- |
| 拼接地址 | `${API_BASE}${path}` |
| JSON 请求头 | `Content-Type: application/json` |
| 来源标记 | `X-Client-Source: web` |
| JWT | 从 `localStorage.crm_token` 读取并设置 `Authorization` |
| 统一响应 | 要求 HTTP 成功且 `body.code === 0` |
| 错误提示 | 抛出 `Error(message)`，页面用 Ant Design message 展示 |

后端统一响应结构：

```ts
type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};
```

## 9. 当前 API 函数

| 函数 | 方法和路径 | 说明 |
| --- | --- | --- |
| `login` | `POST /auth/login` | 登录 |
| `listCustomers` | `GET /customers` | 客户分页 |
| `saveCustomer` | `POST/PUT /customers` | 新增或修改客户 |
| `deleteCustomer` | `DELETE /customers/{id}` | 删除客户 |
| `listUsers` | `GET /users` | 用户列表 |
| `saveUser` | `POST/PUT /users` | 新增或修改用户 |
| `deleteUser` | `DELETE /users/{id}` | 删除用户 |
| `listRoles` | `GET /roles` | 角色列表 |
| `saveRole` | `POST/PUT /roles` | 新增或修改角色 |
| `deleteRole` | `DELETE /roles/{id}` | 删除角色 |
| `listMenus` | `GET /menus` | 菜单树 |
| `saveMenu` | `POST/PUT /menus` | 新增或修改菜单 |
| `deleteMenu` | `DELETE /menus/{id}` | 删除菜单 |

注意：`api.ts` 里的路径不带 `/api`，因为 `API_BASE` 已经包含 `/api`。

## 10. 页面结构

`App.tsx` 维护整体布局和当前视图：

```ts
type ViewKey = 'dashboard' | 'customers' | 'contacts' | 'follows' | 'users' | 'roles' | 'menus';
```

左侧菜单由 `menuItems` 定义：

```ts
const menuItems = [
  { key: 'dashboard', label: '工作台' },
  { key: 'crm', children: [...] },
  { key: 'system', children: [...] }
];
```

内容区按 `view` 渲染：

```tsx
{view === 'dashboard' && <Dashboard />}
{view === 'customers' && <CustomerList />}
{view === 'users' && <UserAdmin />}
```

## 11. 新增一个页面

以新增“联系人管理”为例：

1. 在 `api.ts` 增加类型：

```ts
export type Contact = {
  id?: number;
  customerId: number;
  contactName: string;
  mobile?: string;
};
```

2. 增加 API 函数：

```ts
export function listContacts(customerId?: number) {
  const search = new URLSearchParams();
  if (customerId) search.set('customerId', String(customerId));
  return request<Contact[]>(`/contacts${search.toString() ? `?${search.toString()}` : ''}`);
}
```

3. 在 `App.tsx` 增加页面组件，例如 `ContactList()`。
4. 确认 `ViewKey` 已包含 `contacts`。
5. 确认左侧菜单有 `{ key: 'contacts' }`。
6. 在内容区加入：

```tsx
{view === 'contacts' && <ContactList />}
```

7. 浏览器验证列表、空状态、错误提示。

## 12. 新增表单字段

以客户增加 `taxNo` 字段为例：

1. 后端数据库加字段。
2. 后端实体、Mapper、Service、Controller 支持该字段。
3. `frontend/src/api.ts` 的 `Customer` 类型加：

```ts
taxNo?: string;
```

4. `CustomerList` 表格 columns 加一列。
5. 新增/编辑 Modal 的 Form 加 `Form.Item`。
6. 保存时确认 `form.validateFields()` 返回的值包含新字段。
7. 浏览器保存后刷新，确认字段回显。

## 13. 构建

```bash
cd frontend
npm run build
```

输出目录：

```text
frontend/dist/
```

本地预览生产包：

```bash
npm run preview
```

## 14. 联调检查清单

浏览器 DevTools 里重点看：

| 项 | 正常表现 |
| --- | --- |
| Request URL | 指向 `http://127.0.0.1:8780/api/...` 或同源 `/api/...` |
| Request Headers | 有 `Authorization: Bearer ...` |
| Request Headers | 有 `X-Client-Source: web` |
| Response | JSON，`code` 为 `0` 表示业务成功 |
| Status | `401/403` 通常是登录态或权限问题 |
| Status | `429` 是 Gateway 限流 |
| Status | `503` 是 Gateway 找不到下游服务 |

## 15. 常见问题

| 问题 | 处理 |
| --- | --- |
| 页面打开空白 | 看浏览器 Console，通常是 TS/运行时错误 |
| 登录报网络错误 | 检查 `VITE_API_BASE` 和 gateway 是否启动 |
| 登录成功后接口 403 | token 可能过期，退出重新登录 |
| 接口 429 | 限流触发，降低请求频率或调整 Nacos 限流配置 |
| CORS 报错 | 确认请求经过 gateway，检查 `gateway-dev.yaml` 的 `globalcors` |
| 修改 `.env.local` 不生效 | 重启 `npm run dev` |
| 新菜单点击没反应 | 检查 `ViewKey`、`menuItems`、内容区条件渲染是否一致 |

## 16. 提交前检查

```bash
cd frontend
npm run build
```

同时手动点一遍：

```text
登录 -> 客户列表 -> 新增客户 -> 编辑客户 -> 删除客户 -> 用户/角色/菜单页面
```
