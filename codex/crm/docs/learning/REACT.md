# React 基础与进阶：结合 CRM 管理页面

目标：从理解组件和 JSX，进阶到正确管理异步状态、表格/表单、跨视图同步和性能，并能在当前 React 18 管理台中新增或维护真实业务页面。

前置：[JavaScript](JAVASCRIPT.md)、[TypeScript](TYPESCRIPT.md)。UI 和布局基础见 [HTML 与 CSS](HTML_CSS.md)。

## 1. 从入口理解 React

```text
index.html 的 #root
-> src/main.tsx 创建 React Root
-> StrictMode
-> Ant Design ConfigProvider
-> App
-> Layout/Menu/Tabs
-> 具体业务页面
```

`main.tsx` 负责一次性挂载和全局 Provider；`App.tsx` 负责登录态、工作区和当前所有页面。当前页面集中在单文件，学习时按函数名定位，不从头顺序阅读。

## 2. 组件与 JSX

函数组件接收 Props，返回 JSX：

```tsx
function Placeholder({ title }: { title: string }) {
  return <div className="page">{title}</div>;
}
```

JSX 要点：

- `{}` 中放 JavaScript 表达式。
- 用户字符串会被 React 转义，默认不会作为 HTML 执行。
- `className` 对应 DOM 的 class。
- 组件名大写，原生元素小写。
- `null`、`false` 可表示“不渲染”。
- 返回多个相邻节点需父元素或 Fragment。

组件渲染必须保持纯：同样的 Props/state 应得到同样的 UI。不要在组件函数主体中发请求、写存储、注册事件或调用 `setState`。

## 3. Props、state 与普通变量

| 数据 | 谁拥有 | 是否触发渲染 | 示例 |
| --- | --- | --- | --- |
| Props | 父组件 | 父传新值时 | `fundCode`、`open`、`onClose` |
| state | 当前组件 | `setState` 时 | rows、loading、current |
| 普通局部变量 | 本次渲染 | 否 | 临时格式化结果 |
| ref | 当前组件 | 修改 `.current` 不触发 | 文件 input、请求序号 |

状态应放在需要共同读取/修改它们的最近共同父组件，不要过早全部提升为全局状态。

`FundDetailDrawer` 接收选中基金代码和开关，内部拥有详情/分页状态；这是合理的职责边界。

## 4. useState 与不可变更新

```tsx
const [customers, setCustomers] = useState<Customer[]>([]);
const [editing, setEditing] = useState<Customer | null>(null);
```

state 是某次渲染的快照，调用 setter 请求下一次渲染，不会立刻修改当前变量。

依赖旧值时使用函数式更新：

```tsx
setWorkspace((current) => ({
  activeView: view,
  openViews: current.openViews.includes(view)
    ? current.openViews
    : [...current.openViews, view]
}));
```

不要原地修改 state 对象/数组。React 依赖引用变化判断更新，原地修改也会让时间顺序和调试变得不可靠。

## 5. 状态设计

不要保存可计算的重复状态：

```tsx
const visibleRows = rows.filter(matchesFilter); // 可以由 rows/filter 得到
```

若同时保存 `rows`、`filter` 和 `visibleRows`，很容易忘记同步。昂贵计算再用 `useMemo`。

把相关状态的不变量写清：

- 打开编辑弹窗时，`editing` 决定新增或编辑。
- 筛选变化时，页码必须回 1。
- 删除最后一条导致当前页为空时，可能需要退一页。
- Drawer 关闭后，详情数据是否清理取决于缓存与隐私需求。

简单页面多个 `useState` 足够；状态转换复杂时可用 `useReducer` 明确事件，不要仅因 state 数量多就引入状态库。

## 6. 事件处理

传函数而不是调用结果：

```tsx
<Button onClick={() => openView('customers')}>客户</Button>
```

异步事件要处理 loading 和错误：

```tsx
const handleSave = async (values: Customer) => {
  try {
    setSaving(true);
    await saveCustomer(values);
    message.success('保存成功');
    setModalOpen(false);
    await load(current);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    setSaving(false);
  }
};
```

用户可能双击、快速切换和返回页面。按钮 loading/disabled 是基本保护；后端仍需幂等或重复约束。

## 7. useEffect 的正确模型

Effect 用于组件与外部系统同步，例如网络、浏览器存储、全局事件。它不是“任何后续逻辑”的容器。

```tsx
useEffect(() => {
  sessionStorage.setItem(WORKSPACE_STORAGE_KEY, JSON.stringify(workspace));
}, [workspace]);
```

依赖数组含义：

- 无数组：每次 commit 后执行。
- `[]`：挂载后执行；开发 StrictMode 下可能进行额外执行来发现不安全副作用。
- `[value]`：挂载和 value 变化后执行。

Effect 使用的响应式值原则上都应出现在依赖中。若添加依赖造成无限循环，通常说明 Effect 职责或函数身份需要重构，而不是简单忽略规则。

## 8. Effect 清理

注册什么就清理什么：

```tsx
useEffect(() => {
  const handler = (event: Event) => { /* ... */ };
  window.addEventListener(EVENT_NAME, handler);
  return () => window.removeEventListener(EVENT_NAME, handler);
}, []);
```

计时器用 `clearInterval/clearTimeout`，订阅调用 unsubscribe，请求可用 AbortController 取消。

没有清理可能导致：重复响应、内存泄漏、卸载后更新、旧筛选结果覆盖新结果。

## 9. 数据获取与竞态

当前项目页面通常定义 `load(page)` 并在 Effect/事件中调用。最小状态包括：

```text
loading + data + error/消息 + 分页/筛选参数
```

快速搜索时请求 A 先发、请求 B 后发，A 可能最后返回并覆盖 B。处理方式：

- AbortController 取消旧请求。
- ref 保存请求序号，只接受最后一次。
- 服务端/查询库提供的 query key 与取消机制。

示例请求序号：

```tsx
const requestIdRef = useRef(0);

async function load(params: Query) {
  const requestId = ++requestIdRef.current;
  const result = await listCustomers(params);
  if (requestId === requestIdRef.current) {
    setCustomers(result.records);
  }
}
```

当前封装没有 signal 参数。要统一支持取消，应修改 `request`/API 函数契约并回归所有调用，不在单个页面做互不兼容的临时封装。

## 10. useMemo 与 useRef

`useMemo` 缓存计算结果：

```tsx
const trendRows = useMemo(
  () => buildTrendRows(chartNavs, trendPeriod),
  [chartNavs, trendPeriod]
);
```

适用：有实际成本的转换，或第三方组件确实依赖稳定引用。它不是语义保证，不能用来修复有副作用的渲染逻辑。

`useRef` 用于：

- 访问 DOM，如隐藏文件 input。
- 保存不会驱动 UI 的可变值，如请求序号。
- 保存定时器/上一次值。

若值变化应该展示在界面，用 state，不用 ref 隐藏更新。

## 11. 条件渲染与 UI 状态

业务页面至少考虑：

```text
首次加载 / 有数据 / 空数据 / 错误 / 刷新中 / 无权限
```

不要把错误和空数据都显示成空表。首次 loading 可用骨架/Spin；后台刷新可保留旧数据并显示局部 loading；403 应明确提示权限问题。

登录状态由 Token 是否存在决定。仅存在 Token 不代表仍有效；请求遇到 401/403 后的统一退出/续期策略是后续可改进点，不能由各页面各自实现不一致行为。

## 12. 列表、key 与分页

Ant Design Table 使用稳定业务 ID 作为 `rowKey`。key 影响 React 复用：

- 不用随机数。
- 可编辑/排序列表避免数组下标。
- 后端 ID 缺失时先确认创建前临时行的稳定 key 设计。

分页状态：

- `current`：当前页。
- `pageSize`：每页数量。
- `total`：后端总数。
- 筛选/排序改变时回第一页。
- pageSize 改变通常回第一页。
- 响应只更新对应查询，避免竞态。

Table 的排序字段必须映射到后端白名单；不能把任意列名直接透传为 SQL 排序。

## 13. Ant Design Form、Modal 与 Drawer

表单常见生命周期：

```text
点击新增 -> 清空 editing -> resetFields -> 打开 Modal
点击编辑 -> 设置 editing -> setFieldsValue -> 打开 Modal
提交 -> validate -> API -> 成功关闭 -> 刷新列表
取消 -> 关闭/必要时重置
```

注意：

- Form 的 `initialValues` 通常只在首次挂载生效，编辑回填使用 `setFieldsValue`。
- Modal 关闭是否销毁子组件会影响表单/Effect 状态。
- 前端校验用于体验；后端必须再次校验。
- 删除使用 Popconfirm，但真正授权和数据完整性仍在后端。
- Drawer 切换不同详情时，旧请求不能覆盖新 fundCode。

## 14. 组件拆分与复用

拆分信号：

- 某块有明确业务名和 Props 边界。
- 自己拥有复杂状态/Effect。
- 可独立测试或复用。
- 单文件已难以定位和评审。

不要只按行数机械拆分，也不要创建只转发所有 Props 的空壳组件。

当前 `App.tsx` 很大。新增复杂功能时可优先提取：页面组件、共享格式化函数、通用分页查询 Hook、业务表单；但应在独立重构中保持行为不变并验证，避免与紧急业务改动混合。

## 15. 跨组件状态

选择顺序：

1. Props 向下、回调向上。
2. 提升到最近共同父组件。
3. Context 提供低频全局依赖（认证、主题、权限）。
4. 外部状态库/服务端查询库用于确有规模的问题。

当前自选列表通过 `CustomEvent` 在两个已挂载视图间同步。它能工作，但 payload/来源难追踪。若跨页面共享状态继续增长，可评估 Context 或服务端状态查询库；迁移要统一数据来源和失效策略。

## 16. React 18 StrictMode

开发入口使用 `React.StrictMode`。开发模式可能额外调用渲染或 Effect setup/cleanup，目的是暴露副作用问题，不会以相同方式出现在生产构建。

如果看到开发环境请求两次：

1. 检查 Effect 是否幂等、是否有清理。
2. 不要以移除 StrictMode 作为默认修复。
3. 对写操作不要放在挂载 Effect 中无保护执行。
4. 后端写接口仍需设计幂等性。

## 17. 错误边界

事件和异步错误应由调用处捕获。渲染阶段意外异常可由 Error Boundary 隔离，防止整个管理台白屏。

Error Boundary 不会自动捕获：

- 事件处理器错误。
- `setTimeout` 内错误。
- 普通异步 Promise 拒绝。
- 服务端/网络业务失败。

为页面级业务错误使用明确状态；为不可预期渲染异常在合适层级提供恢复入口和诊断 ID。

## 18. 性能

React 性能优化先 profile：

- 是否重复渲染大量 Table 行。
- 列配置/回调引用是否导致第三方组件重算。
- 趋势数据计算是否占用长任务。
- 隐藏但未销毁的 Tabs 是否保留大量页面/请求。

当前工作区 `destroyInactiveTabPane={false}`，切换标签会保留页面状态，同时也保留内存和订阅。页面数量/数据量增加后，需要在“保留操作现场”和“释放资源”之间作明确取舍。

可用手段：拆组件、稳定 Props、`useMemo`、`memo`、虚拟列表、分页、延迟加载。每种都有复杂度，不做无测量的全局 memo 化。

## 19. 可访问性与交互

Ant Design 提供基础语义，但业务组合仍需检查：

- 输入有 label 和错误提示。
- 纯图标按钮有可读名称/Tooltip，不能只靠颜色表达状态。
- 键盘可聚焦和触发。
- Modal 打开/关闭后的焦点合理。
- loading 不让屏幕阅读器误以为操作无反馈。
- 表格移动端溢出可滚动，不截断关键操作。

## 20. 测试策略

当前脚本没有独立前端测试，最低是构建和手工冒烟。后续引入测试时分层：

- 纯函数：趋势过滤、格式化、菜单树转换。
- 组件：表单校验、loading/空态/错误、用户事件。
- API：请求 path/header/body 与响应错误。
- E2E：登录、列表、增删改查、权限拒绝。

测试用户可见行为，少断言组件内部 state。网络使用可控 mock/测试环境，不依赖生产数据。

## 21. 项目实战：新增一个分页管理页

完整步骤：

1. 后端接口与权限先确认可用。
2. 在 `api.ts` 定义响应/请求类型和函数。
3. 扩展 `ViewKey`、`VIEW_KEYS`、菜单、`labelOf` 和 `WorkspaceView`。
4. 页面定义查询条件、分页、data、loading、editing/modal 状态。
5. 实现 load，处理错误和只接受当前查询结果。
6. Table 使用稳定 rowKey、类型化 Columns、后端 total。
7. Form 区分新增/编辑，成功后刷新正确页。
8. 删除二次确认，并处理删除后空页。
9. 验证标签切换后状态保留是否符合预期。
10. 运行构建并做登录、无权限、空数据、慢请求、重复点击冒烟。

## 22. 常见问题

| 现象 | 常见原因 | 排查 |
| --- | --- | --- |
| state 更新后读到旧值 | 当前渲染快照 | 使用新值参数或函数式更新 |
| Effect 无限请求 | 依赖每次变或 Effect 又改依赖 | 重构数据流/稳定依赖 |
| 编辑表单显示上次数据 | 未 reset/setFieldsValue | 明确 Modal 生命周期 |
| 快筛结果跳回旧数据 | 请求竞态 | 取消或请求序号 |
| 列表行状态错位 | key 不稳定 | 使用业务 ID |
| 开发环境请求两次 | StrictMode 暴露副作用 | 保证 Effect 幂等与清理 |
| 切 Tab 后页面仍占内存 | inactive pane 未销毁 | 评估保留状态策略 |

## 23. 完成标准

你应能独立解释和实现：

- 组件、Props、state、ref 与普通变量的边界。
- 不可变更新和函数式 setter 的必要性。
- Effect 何时需要、依赖如何确定、怎样清理。
- loading/空/错/成功与分页筛选的状态设计。
- 异步请求竞态及至少一种解决方案。
- Ant Design Table/Form/Modal 的常见生命周期。
- 新增页面需要同步的菜单、工作区、类型和 API 链路。
- 用构建和真实冒烟证明页面可用。

继续阅读 [前端工程化](FRONTEND_ENGINEERING.md)，完成从本地开发到生产发布的全链路。
