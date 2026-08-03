# TypeScript 基础与进阶：结合 CRM API 契约

目标：从理解基础类型，进阶到能准确表达 API、React Props 和业务状态，安全修改 `frontend/src/api.ts`，并利用严格模式在构建期发现问题。

先读 [JavaScript](JAVASCRIPT.md)。TypeScript 只在开发/构建阶段检查类型，不会自动验证服务器返回的 JSON。

## 1. 项目中的 TypeScript

当前配置重点：

```text
target: ES2020
lib: DOM + DOM.Iterable + ES2020
strict: true
module: ESNext
noEmit: true
jsx: react-jsx
```

`npm run build` 先运行 `tsc` 做类型检查，再由 Vite 构建。`noEmit` 表示 TypeScript 本身不输出 JS；真正的转换和打包交给 Vite。

常见文件：

- `.ts`：普通 TypeScript，如 `src/api.ts`。
- `.tsx`：包含 JSX，如 `src/App.tsx`。
- `.d.ts`：类型声明，如 Vite 环境类型。

## 2. 基础类型与类型推断

```ts
const username: string = 'admin';
const current: number = 1;
const loading: boolean = false;
const permissions: string[] = [];
```

简单局部值让编译器推断即可。公共函数参数、返回值、跨层对象和空数组应明确类型：

```ts
const [customers, setCustomers] = useState<Customer[]>([]);

function formatDateTime(value?: string | null): string {
  // ...
}
```

`number` 包含整数与小数，也可能是 `NaN`/`Infinity`。类型正确不代表数值有效，外部输入仍需 `Number.isFinite` 等运行时校验。

## 3. 对象类型、type 与 interface

项目主要使用 `type`：

```ts
export type Customer = {
  id?: number;
  customerName: string;
  phone?: string;
  updatedAt?: string;
};
```

`type` 适合对象、联合、映射和组合；`interface` 适合可扩展对象契约。两者都能表达大多数对象，不要只为风格在现有代码中来回改写。

区分：

```ts
id?: number;              // 属性可以不存在；读取为 number | undefined
note: string | null;      // 属性必须存在，但值可为 null
email?: string | null;    // 属性可不存在，也可明确为 null
```

必须以真实后端 JSON 和接口契约为准，不能为了消除错误把所有字段都加 `?`。

## 4. 联合类型与字面量类型

联合类型表达有限状态：

```ts
type TrendPeriod = '1M' | '3M' | '6M' | '1Y' | '3Y' | 'ALL';
type ImportType = 'holding' | 'trade';
```

相比任意 `string`，它能阻止拼写错误并提供自动补全。项目的 `ViewKey`、评分验证状态、置信度都适合有限集合。

若状态与数据必须成套出现，使用可辨识联合：

```ts
type LoadState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string };
```

这比 `loading + data + error` 三个可能互相矛盾的变量更能表达不变量。是否重构现有页面应单独评估，不作为普通小需求的附带改动。

## 5. 数组、元组、Record 与索引

```ts
const rows: Customer[] = [];
const point: [number, number] = [x, y];
const weights: Record<string, number> = {};
```

`Record<string, number>` 允许任意字符串 key。若 key 集合固定，可更严格：

```ts
type FactorKey = 'return_1m' | 'return_1y' | 'scale';
type FactorWeights = Record<FactorKey, number>;
```

索引对象时，编译器要求 key 确实合法。不要用 `as any` 绕过；先缩窄 key 或建立白名单映射。

## 6. 函数类型

函数签名同时表达输入、异步和返回：

```ts
function listCustomers(params: {
  current: number;
  size: number;
  keyword?: string;
}): Promise<Page<Customer>> {
  // ...
}
```

React Props 也用函数类型：

```ts
function LoginPage({ onLogin }: { onLogin: (token: string) => void }) {
  // ...
}
```

`void` 表示调用者不使用返回值，不等于函数不能实际返回任何值。事件回调如果启动异步工作，要明确异常如何处理，避免未处理 Promise。

可选参数与默认参数不同：

```ts
function listHistory(code: string, current = 1, size = 50) {}
```

调用者可省略，函数内部的 `current` 会被推断为 `number`，而不是 `number | undefined`。

## 7. 泛型

泛型让同一结构保留具体数据类型：

```ts
export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export async function request<T>(...): Promise<T> {}
```

看到 `request<Page<Customer>>` 时从外向内读：请求成功返回分页，分页的 `records` 是客户。

泛型参数只是编译期承诺：

```ts
const body = JSON.parse(text) as ApiResponse<T>;
```

这里的 `as` 不会验证 JSON。若后端返回错误字段，编译器也不知道。高风险/不稳定外部边界应考虑运行时 Schema 校验；当前仓库未引入相关库，先通过 API 契约、真实响应和集成测试保证。

## 8. unknown、any 与类型缩窄

- `any`：关闭检查，可传播污染，业务代码应避免。
- `unknown`：值未知，使用前必须证明类型。
- `never`：不可能发生的值，可做穷尽检查。

```ts
function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '未知错误';
}
```

常用缩窄方式：

- `typeof value === 'string'`
- `value instanceof Error`
- `value !== null`
- `'field' in value`
- 自定义类型守卫

项目的 `isViewKey(value): value is ViewKey` 是类型守卫：它既做运行时白名单判断，也让后续代码获得更窄类型。

## 9. 类型断言与非空断言

```ts
document.getElementById('root') as HTMLElement
```

断言是“我比编译器更清楚”，不是转换和校验。断言过多通常说明边界没有建模。

非空断言 `value!` 更危险：若运行时为空仍会崩溃。DOM 根节点由项目 `index.html` 固定提供时可接受；API 数据、用户输入和异步状态不能随意断言。

优先选择：

1. 明确空值分支。
2. 改进类型定义。
3. 使用类型守卫。
4. 最后才是有证据的断言。

## 10. 枚举还是联合

本项目更适合字符串字面量联合：它与 JSON/表单值直接一致，构建产物无额外运行时代码。

```ts
type SortOrder = 'ascend' | 'descend';
```

当需要运行时反向映射、命名空间或与既有库对接时可考虑 enum。不要为每个常量集合机械创建 enum。

## 11. 工具类型

常用内置工具：

| 工具 | 含义 | 适用场景 |
| --- | --- | --- |
| `Partial<T>` | 所有属性可选 | 局部草稿；不等同 API 更新契约 |
| `Required<T>` | 所有属性必填 | 内部归一化结果 |
| `Pick<T, K>` | 选择字段 | 表单/摘要的稳定子集 |
| `Omit<T, K>` | 排除字段 | 创建请求排除服务端字段 |
| `Readonly<T>` | 顶层只读 | 表达不可修改意图 |
| `ReturnType<F>` | 函数返回类型 | 从已有函数派生 |

不要用 `Partial<Entity>` 偷懒表示所有更新请求。API 若只允许修改特定字段，应定义独立 Request 类型：

```ts
type SaveCustomerRequest = Pick<Customer,
  'customerName' | 'customerType' | 'phone' | 'email'
> & { id?: number };
```

## 12. API 契约建模

`src/api.ts` 同时包含：

1. 统一响应与分页结构。
2. 业务 Entity/DTO 类型。
3. 请求参数类型。
4. API 调用函数。

新增字段时按顺序核对：

- JSON 字段名与大小写。
- 必填、可省略、可为 null。
- number/string/date 的真实表示。
- 枚举可取值。
- 写请求和读响应是否同一形状。
- 后端 Decimal/Long 在 JavaScript 的精度风险。

Java `long` 超过 `Number.MAX_SAFE_INTEGER` 时不能安全表示；若业务 ID 可能越界，应由 API 用字符串传输并同步修改所有客户端，不能只在前端强行标为 `number`。

## 13. DOM、浏览器与第三方类型

`lib: DOM` 提供 `fetch`、`FormData`、`Headers`、`HTMLElement`、`localStorage` 等类型。Node API 不会自动可用；前端源码不能假设存在 `fs`、`process`。

Vite 客户端环境变量通过：

```ts
import.meta.env.VITE_API_BASE
```

只有 `VITE_` 前缀变量会暴露给客户端。它们会进入构建产物，绝不能存秘密。

Ant Design 的 `ColumnsType<Customer>` 让列配置的 `dataIndex`、render 值和表格行类型关联。若用宽泛 `object`/`any`，拼错字段也可能直到运行时才暴露。

## 14. React Hook 的类型

```ts
const [editing, setEditing] = useState<Customer | null>(null);
const fileInputRef = useRef<HTMLInputElement>(null);
const columns = useMemo<ColumnsType<Customer>>(() => [...], [dependencies]);
```

注意：

- `useState(null)` 若无泛型，状态可能只推断为 null。
- 空数组可能推断成 `never[]` 或过窄类型，显式指定元素类型。
- ref 初始为 null，读取时使用 `fileInputRef.current?.click()`。
- 事件类型按元素选择，如 `React.ChangeEvent<HTMLInputElement>`；不要统一写 `any`。

## 15. TSX 与 JSX 类型

JSX 不是字符串模板，而是编译为 React 元素创建调用。组件名首字母大写，HTML 标签小写。

条件渲染：

```tsx
{loading ? <Spin /> : <CustomerTable rows={customers} />}
```

数组渲染需要稳定 `key`，业务 ID 优先。不要用会变化的数组下标作为可编辑/可重排列表的 key，否则 React 可能复用错误的组件状态。

组件 Props 尽量表达必要输入和回调：

```ts
type DetailProps = {
  fundCode: string | null;
  open: boolean;
  onClose: () => void;
};
```

## 16. 高级：穷尽检查

联合类型增加分支后，应让编译器提醒所有使用者：

```ts
function assertNever(value: never): never {
  throw new Error(`未处理的状态: ${String(value)}`);
}

function labelOf(view: ViewKey): string {
  switch (view) {
    case 'dashboard': return '工作台';
    case 'customers': return '客户列表';
    // ...
    default: return assertNever(view);
  }
}
```

当前项目部分映射使用对象/条件分支。新增 `ViewKey` 时要同步菜单、标签、渲染组件和存储白名单；类型系统只能发现被准确建模的遗漏。

## 17. 高级：类型与运行时校验的边界

TypeScript 会在这些位置失去保证：

- `JSON.parse`
- local/session storage
- URL 参数
- 用户输入和文件
- 后端响应
- 第三方脚本/库

处理方式：

```text
unknown 输入 -> 运行时检查/解析 -> 形成已知类型 -> 业务内部使用
```

`loadWorkspaceState` 应验证解析对象的 activeView/openViews 是否属于 `VIEW_KEYS`，而不是只 `as WorkspaceState`。这类边界函数是练习类型守卫的好位置。

## 18. 项目实战：新增客户摘要卡片

假设后端提供：

```json
{"total": 120, "active": 90, "newThisMonth": 12}
```

实现顺序：

1. 定义 `CustomerSummary`，明确每个字段是否可空。
2. 增加 `getCustomerSummary(): Promise<CustomerSummary>`。
3. 页面 state 写成 `CustomerSummary | null`，不要先填虚假 0。
4. loading/error/成功分别渲染。
5. 用 `Promise.all` 与列表并发加载前，决定一个失败是否允许另一个展示。
6. 验证后端缺字段、字段为 null、401/403 和正常结果。
7. 执行 `npm run build`。

评审时重点检查：有没有 `any`、不真实的可选字段、过度断言和把失败伪装成 0。

## 19. 常见编译错误如何读

| 错误方向 | 常见原因 | 正确动作 |
| --- | --- | --- |
| Property does not exist | 字段名错或类型未同步 | 对照真实契约更新类型/代码 |
| Type X is not assignable to Y | 可空性、联合或对象形状不匹配 | 找到数据边界，不盲目断言 |
| Object is possibly undefined | 未处理可选值 | 明确空态/默认值 |
| No overload matches | 第三方组件 Props 不满足 | 查看库类型与当前版本文档 |
| Implicitly has an any type | 参数缺少可推断上下文 | 补精确类型 |

从最上游/第一个错误开始修，后续错误可能只是连锁反应。

## 20. 验收与完成标准

```bash
cd frontend
npm run build
```

你应能独立做到：

- 准确区分可选属性、`null` 与 `undefined`。
- 使用联合类型表示有限状态并完成缩窄。
- 解释 `request<T>` 提供什么保证、没有提供什么保证。
- 为 API 响应、请求、Props、state 和 ref 选择合适类型。
- 不依赖 `any`、`!` 或连续 `as` 掩盖契约问题。
- 从 TypeScript 错误定位到真正的数据边界。
- 新增 API 类型后通过构建并用真实响应验证。

接下来阅读 [React](REACT.md)，学习这些类型如何参与组件状态与渲染。
