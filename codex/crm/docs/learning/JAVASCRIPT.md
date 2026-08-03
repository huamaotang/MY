# JavaScript 基础与进阶：结合 CRM Web 管理台

目标：从能读 JavaScript 基础语法，进阶到理解浏览器运行时、异步请求和状态更新，并能看懂 `frontend/src/api.ts` 与 `frontend/src/App.tsx` 中 TypeScript 背后的 JavaScript 行为。

> 本项目源码使用 TypeScript/TSX，但 TypeScript 最终仍编译为 JavaScript。先掌握本篇，再读 [TypeScript](TYPESCRIPT.md) 和 [React](REACT.md)。

## 1. 从一条登录链路认识 JavaScript

```text
点击“登录”
-> Ant Design Form 调用 onFinish
-> async 函数 await login(username, password)
-> request() 调用 fetch
-> Promise 完成后保存 localStorage
-> React setToken 触发界面更新
```

先定位：

- `frontend/src/App.tsx` 的 `LoginPage`：事件、异步流程和状态。
- `frontend/src/api.ts` 的 `login`：参数如何变成 JSON 请求体。
- `frontend/src/api.ts` 的 `request`：Header、Token、响应和异常。

不要一开始通读近 3000 行的 `App.tsx`。先从一个事件处理函数追到一个 API 函数，再回到渲染结果。

## 2. 变量、值与严格相等

### const 与 let

- `const`：变量不能重新指向其他值，默认优先。
- `let`：确实需要重新赋值时使用。
- 不使用 `var`：它是函数作用域且存在提升，容易产生意外行为。

```js
const pageSize = 20;
let current = 1;
current += 1;
```

`const` 对象仍可修改内部属性。若不希望修改旧状态，应创建新对象：

```js
const nextWorkspace = {
  ...workspace,
  activeView: 'customers'
};
```

### 常见类型

JavaScript 基本类型包括 `string`、`number`、`boolean`、`undefined`、`null`、`bigint`、`symbol`，对象类型包括普通对象、数组、函数、Date 等。

本项目常见边界：

- 后端可空字段可能是 `null`，未提供的对象属性通常是 `undefined`。
- 所有普通数字都使用 `number`；金额展示可用它，精确记账不能依赖二进制浮点累计。
- 浏览器输入值通常先得到字符串，即使界面看起来是数字。

使用 `===`/`!==`，避免 `==` 的隐式类型转换：

```js
0 == false   // true，容易隐藏错误
0 === false  // false
```

## 3. 真值、空值与默认值

`false`、`0`、`''`、`null`、`undefined`、`NaN` 是假值，但业务含义不同。

```js
const label = value ?? '-'; // 只有 null/undefined 才使用 '-'
const wrong = value || '-'; // 0 和空字符串也会被替换
```

项目中的净值、收益率、评分可能合法地为 0，所以展示函数应优先判断 `null/undefined`，不要把 0 当作“无数据”。

可选链用于安全读取：

```js
const message = body?.message;
const latestScore = fund.latestScore?.totalScore;
```

可选链只能避免空引用，不能证明数据类型和业务语义正确。

## 4. 字符串、模板字符串与 URL

模板字符串用于可读拼接：

```js
const url = `${apiBase}/customers/${id}`;
```

用户输入放入 query/path 时必须编码。推荐 `URLSearchParams` 或 `URL`/`URLSearchParams`，不要直接拼：

```js
const query = new URLSearchParams({
  current: String(current),
  size: String(size),
  keyword
});
```

模板字符串不是 SQL 注入问题的唯一来源，但错误的 URL 编码会造成 `&`、`#`、中文或空格被误解。本项目 API 函数应沿用 `api.ts` 现有参数构造方式。

## 5. 数组与对象

### 常用数组方法

| 方法 | 用途 | 是否产生新数组 |
| --- | --- | --- |
| `map` | 一对一转换 | 是 |
| `filter` | 保留符合条件的元素 | 是 |
| `find` | 找第一个元素 | 不适用 |
| `some/every` | 判断任一/全部 | 不适用 |
| `reduce` | 累计为一个结果 | 取决于实现 |
| `sort` | 排序 | 否，会修改原数组 |

React 状态中避免直接修改数组：

```js
// 错误：修改旧数组
openViews.push(view);

// 正确：创建新数组
const nextViews = openViews.includes(view)
  ? openViews
  : [...openViews, view];
```

`sort()` 会原地修改。若数据来自 state，先复制：

```js
const sorted = [...rows].sort((a, b) => a.navDate.localeCompare(b.navDate));
```

### 解构与展开

```js
const { current, size, keyword } = params;
const next = { ...customer, customerName: name.trim() };
```

展开只是浅拷贝。嵌套对象仍共享引用；修改深层字段时要逐层复制，或重新设计更扁平的状态。

## 6. 函数、作用域与闭包

函数是一等值，可以作为参数和返回值。React 事件与 Hook 大量使用箭头函数：

```js
const handleDelete = async (id) => {
  await deleteCustomer(id);
  await load(current);
};
```

词法作用域意味着内部函数能访问定义位置的变量，这就是闭包。闭包很有用，也会捕获旧值：

```js
setWorkspace((current) => ({
  ...current,
  activeView: view
}));
```

这里使用函数式更新，保证读取的是 React 提供的最新状态。若异步回调直接使用旧 `current`，可能产生竞态。

## 7. 模块

浏览器工程使用 ES Module：

```js
export function listCustomers() {}
import { listCustomers } from './api';
```

- named export 名称必须匹配。
- default export 导入时可重命名；`App.tsx` 默认导出 `App`。
- 模块只在首次加载时执行一次，顶层可变状态会被所有导入者共享。
- 不在模块顶层发请求或写存储，否则导入本身产生难测试副作用。

`index.html` 中的 `<script type="module">` 让 Vite 从 `src/main.tsx` 建立依赖图。

## 8. Promise 与 async/await

`fetch` 返回 Promise。`async` 函数一定返回 Promise，`await` 暂停该函数的后续逻辑，但不会冻结整个浏览器。

```js
async function loadCustomers() {
  try {
    setLoading(true);
    const page = await listCustomers({ current: 1, size: 20 });
    setCustomers(page.records);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载失败');
  } finally {
    setLoading(false);
  }
}
```

要点：

- `try/catch` 只能捕获 `await` 的拒绝或同步抛错。
- `finally` 适合恢复 loading，但并发请求会让一个布尔 loading 不够精确。
- 没有 `await`/`return` 的 Promise 可能成为未处理拒绝。
- 相互独立的请求可用 `Promise.all`，其中任一失败会整体拒绝。

项目在基金详情、用户/角色加载等场景使用 `Promise.all`。若业务允许部分成功，应显式设计 `Promise.allSettled` 后的降级界面，而不是悄悄吞错。

## 9. 事件循环与渲染时机

JavaScript 主线程执行调用栈；Promise 回调进入微任务队列，计时器和用户事件进入任务队列。浏览器通常在合适时机进行样式计算、布局和绘制。

这解释了：

- 大循环会阻塞点击与绘制。
- `await` 不会自动把 CPU 密集计算移到后台。
- 多次状态更新可能被 React 批处理，更新后立即读取旧变量仍可能是旧值。
- `setTimeout(fn, 0)` 只是排到后续任务，不保证立即执行。

项目的趋势图数据量受 `CHART_NAV_SIZE` 限制。若未来转换变得昂贵，先 profile，再考虑缓存、Web Worker 或服务端聚合。

## 10. fetch、HTTP 与错误

`fetch` 只有网络失败等情况才拒绝；HTTP 404/500 仍会完成 Promise，所以 `request()` 同时检查：

```text
response.ok
响应体是否存在
业务 code 是否为 0
```

还要理解：

- Header 名称大小写不敏感，`Headers` 可安全合并调用方 Header。
- JSON 请求需要 `Content-Type: application/json`。
- `FormData` 上传时不要手工设置 multipart boundary，浏览器会生成。
- 读取 `response.text()` 后不能再对同一 body 调 `response.json()`。
- JSON 解析也可能失败，例如代理返回 HTML；当前封装会抛解析异常，排错要检查 Network 原始响应。

请求取消可使用 `AbortController`：

```js
const controller = new AbortController();
fetch(url, { signal: controller.signal });
controller.abort();
```

当前项目尚未统一实现取消。筛选快速切换、组件卸载或搜索联想场景增加取消前，需要区分真正错误与 `AbortError`。

## 11. 浏览器存储与事件

| API | 生命周期 | 当前用途 |
| --- | --- | --- |
| `localStorage` | 关闭浏览器后仍存在 | JWT Token |
| `sessionStorage` | 当前标签页会话 | 工作区标签状态 |
| 内存 state | 页面刷新即失 | 页面数据、loading、弹窗 |

存储值都是字符串，对象要 `JSON.stringify`/`JSON.parse`。解析外部存储必须 `try/catch` 并验证结构，`loadWorkspaceState` 就承担这个边界。

`localStorage` 中 Token 可被同源 JavaScript 读取，因此 XSS 会造成泄露。不要用 `dangerouslySetInnerHTML` 渲染用户内容，不在日志输出 Token；生产认证方案需要结合后端评估 HttpOnly Cookie、CSRF 与刷新机制。

项目用 `CustomEvent` 同步不同基金视图的自选状态。注册全局监听后必须移除：

```js
window.addEventListener(name, handler);
return () => window.removeEventListener(name, handler);
```

事件名和 payload 应有稳定契约；复杂跨页面状态增长后再评估 Context/状态库，不要让全局事件形成隐式网络。

## 12. 错误处理与可观测性

可恢复错误显示给用户，不可恢复错误至少保留开发诊断上下文：

- 网络/业务错误：友好 message，保留 HTTP 状态和接口信息用于调试。
- JSON/类型错误：不要伪装成空列表。
- 用户输入错误：就近表单校验。
- 未预期渲染错误：进阶可增加 Error Boundary。

不要写 `catch {}` 或永远返回空数组。那会把“服务故障”显示成“没有数据”。

## 13. 性能与内存

先用浏览器 Performance、Network、React DevTools 证明瓶颈。常见风险：

- 在每次渲染中重复排序/聚合大数组。
- Table 一次渲染过多行或复杂单元格。
- 未清理事件、计时器或未完成请求。
- 重复发相同请求。
- 把大图片完整读入内存并长期保留。

不要把所有函数都机械缓存。缓存有依赖维护成本，只有计算昂贵或引用稳定性确有价值时再使用。

## 14. 安全基础

- XSS：用户内容默认让 React 作为文本渲染，不拼 HTML。
- CSRF：Bearer Header 与 Cookie 认证的风险不同，改变认证方式需重新设计。
- CORS：浏览器安全策略，不是权限系统；生产优先同源 `/api`。
- 敏感数据：Token、密码、OCR 图片、完整错误响应不进日志。
- 依赖：锁定 `package-lock.json`，定期审计但不要无验证自动大版本升级。
- 前端权限：隐藏按钮只改善体验，后端必须校验权限。

## 15. 项目实战：增加“客户等级”筛选

练习目标不是直接提交功能，而是完整走一次链路：

1. 确认后端 `/customers` 是否接受 `level`，不要仅改前端。
2. 在 `listCustomers` 参数类型中增加可选 `level`。
3. 使用 URL 参数构造逻辑，仅在有值时追加。
4. `CustomerList` 增加状态和 Select。
5. 筛选变化时回到第 1 页，避免当前页超过结果页数。
6. loading 期间防重复操作，错误时保留筛选值。
7. Network 验证 URL、Header、响应和分页。
8. `npm run build`，再做空值、中文、无结果、无权限测试。

这个练习覆盖变量、对象、异步、事件、HTTP、状态和错误边界。

## 16. 调试方法

浏览器 DevTools：

- Elements：DOM、样式来源与盒模型。
- Console：异常和临时表达式；不要在生产粘贴未知代码。
- Network：请求 URL/Header/Payload/响应/耗时。
- Sources：断点、调用栈、作用域和 source map。
- Performance：长任务、布局和绘制。
- Application：local/session storage；截图或分享时注意 Token。

排查顺序：复现最小问题 → 看 Console/Network → 确认输入 → 断点跟调用链 → 再修改代码。

## 17. 完成标准

你应能独立解释并验证：

- `const`、不可变更新和浅拷贝的区别。
- `null`、`undefined`、0 与空字符串为什么不能混用。
- Promise、`async/await`、事件循环和竞态的关系。
- `request()` 为什么同时检查 HTTP 状态和业务 code。
- localStorage/sessionStorage 的用途和安全风险。
- 一次点击如何经过事件、API、状态更新并重新渲染。
- 如何用 DevTools 证明问题在浏览器、Gateway 还是下游服务。

接下来阅读 [TypeScript](TYPESCRIPT.md)，把运行时理解升级为编译期契约。
