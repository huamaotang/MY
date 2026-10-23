# Bootstrap 基础与进阶：样式框架与组件库对比

目标：从理解 Bootstrap 的栅格系统、组件和工具类，进阶到对比 Bootstrap 与 Ant Design 的差异，理解当前项目选择 Ant Design 的原因，并能安全地在项目中覆盖样式或引入 Bootstrap 组件。

前置：[HTML 与 CSS](HTML_CSS.md)。框架对比见 [Vue](VUE.md)。

## 1. Bootstrap 是什么

Bootstrap 是 Twitter 开源的 CSS 组件库，提供预定义的样式、布局系统和可复用组件。

| 维度 | Bootstrap | Ant Design（当前项目） |
| --- | --- | --- |
| 类型 | CSS 框架 + JS 组件 | React 组件库 |
| 框架依赖 | 无（可与任意框架搭配） | 依赖 React |
| 设计语言 | Bootstrap 自有风格 | Ant Design 企业级设计 |
| 栅格系统 | 内置 12 列栅格 | 使用 CSS Grid/Flex |
| 当前项目 | ❌ 未使用 | ✅ 使用 |
| 适用场景 | 快速原型、全栈项目、Vue/React/原生均可 | React 项目中的企业级管理台 |

Bootstrap 的核心优势是"零配置就能好看"：引入 CSS 文件后，按类名即可获得样式。Ant Design 的核心优势是"React 原生"：组件与 React 状态管理无缝集成。

## 2. 核心概念：栅格系统

Bootstrap 的布局基于 12 列栅格系统：

```html
<div class="container">
  <div class="row">
    <div class="col-md-8">主内容</div>
    <div class="col-md-4">侧边栏</div>
  </div>
</div>
```

断点：

| 类前缀 | 最小宽度 | 用途 |
| --- | --- | --- |
| `col-` | `<576px` | 手机 |
| `col-sm-` | `≥576px` | 小屏 |
| `col-md-` | `≥768px` | 中屏 |
| `col-lg-` | `≥992px` | 大屏 |
| `col-xl-` | `≥1200px` | 超大屏 |

与当前项目 CSS Grid 对比：

```css
/* Bootstrap 方式 */
.col-md-8 { flex: 0 0 66.6667%; max-width: 66.6667%; }

/* 当前项目方式 */
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 16px;
}
```

Bootstrap 的栅格通过预定义类名实现，当前项目通过 CSS Grid 代码实现。前者更快上手，后者更灵活。

## 3. 常用组件与类名

### 按钮与表单

```html
<button class="btn btn-primary">主要按钮</button>
<button class="btn btn-outline-secondary">次要按钮</button>
<input class="form-control" type="text" placeholder="输入">
<select class="form-select">
  <option>选项</option>
</select>
```

对比 Ant Design：

```tsx
<Button type="primary">主要按钮</Button>
<Button>次要按钮</Button>
<Input placeholder="输入" />
<Select options={[...]} />
```

Bootstrap 使用 HTML 类名，Ant Design 使用 React Props。Bootstrap 组件不感知 React 状态，Ant Design 组件天然响应式。

### 卡片与布局

```html
<div class="card">
  <div class="card-body">
    <h5 class="card-title">标题</h5>
    <p class="card-text">内容</p>
    <button class="btn btn-primary">操作</button>
  </div>
</div>
```

### 导航与表格

```html
<ul class="nav nav-tabs">
  <li class="nav-item"><a class="nav-link active">客户</a></li>
  <li class="nav-item"><a class="nav-link">基金</a></li>
</ul>

<table class="table table-striped">
  <thead><tr><th>名称</th></tr></thead>
  <tbody>
    <tr><td>客户 A</td></tr>
  </tbody>
</table>
```

## 4. 实用工具类（Utilities）

Bootstrap 提供了大量工具类，是其最大亮点之一：

```html
<!-- 间距 -->
<div class="mt-3 mb-2 p-3">元素</div>

<!-- 文字 -->
<p class="text-primary fs-4 fw-bold">重要文字</p>
<p class="text-muted fst-italic">次要文字</p>

<!-- 颜色背景 -->
<div class="bg-success text-white p-2">成功</div>
<div class="bg-danger text-white p-2">失败</div>

<!-- 显示与隐藏 -->
<div class="d-none d-md-block">仅中大屏显示</div>
<div class="d-flex justify-content-between">两端对齐</div>

<!-- 定位 -->
<div class="position-fixed top-0 end-0">右上角</div>
```

对比 Ant Design：这些样式在 Ant Design 中通常通过组件 Props 或自定义 CSS 实现，而非类名组合。

## 5. JavaScript 组件

Bootstrap 5 自带 JS 组件（不再依赖 jQuery）：

```js
import { Modal, Toast, Collapse, Tab } from 'bootstrap'

// 模态框
const modal = new Modal('#myModal')
modal.show()

// 提示
const toast = new Toast('#myToast')
toast.show()

// 折叠
const collapse = new Collapse('#myCollapse')
collapse.toggle()
```

对比 Ant Design 的 React 组件：

```tsx
// Ant Design 组件是 React 组件，受状态管理
const [open, setOpen] = useState(false)
<Modal open={open} onCancel={() => setOpen(false)}>
  内容
</Modal>
```

核心差异：

- Bootstrap JS 组件通过 DOM 操作和类名切换状态，不感知框架状态。
- Ant Design 组件通过 Props 接收状态，状态变化自动触发重新渲染。
- Bootstrap 组件可被任何框架使用，Ant Design 只能在 React 中使用。

## 6. 主题与定制

### Bootstrap 变量覆盖

Bootstrap 通过 Sass 变量定制：

```scss
// 自定义变量，需在 Bootstrap 前引入
$primary: #1890ff;  // 改为 Ant Design 主色
$font-family-base: 'PingFang SC', sans-serif;
$border-radius: 4px;

@import "bootstrap";
```

### 当前项目的主题方式

项目通过 `ConfigProvider` 设置 Ant Design 主题：

```tsx
import { ConfigProvider } from 'antd'

<ConfigProvider theme={{ token: { colorPrimary: '#1890ff' } }}>
  <App />
</ConfigProvider>
```

对比：

- Bootstrap 定制需构建流程（Sass 编译）。
- Ant Design 定制在运行时通过 Props/ConfigProvider 生效，无需重新构建。

## 7. Bootstrap 与当前项目的共存

可以在 React 项目中同时使用 Ant Design 和 Bootstrap 的 CSS：

```tsx
// 只引入 Bootstrap CSS，不引入 JS
import 'bootstrap/dist/css/bootstrap.min.css'

function Page() {
  return (
    <div>
      <div className="row">
        <div className="col-md-6">Bootstrap 布局</div>
        <div className="col-md-6">
          <Button type="primary">Ant Design 按钮</Button>
        </div>
      </div>
    </div>
  )
}
```

注意事项：

- 不要同时引入 Bootstrap 和 Ant Design 的 JS，二者操作 DOM 的方式冲突。
- 类名冲突：`.btn`、`.card`、`.modal` 等类名可能被覆盖。
- 建议只用 Bootstrap 的 CSS 栅格和工具类，组件用 Ant Design。
- 如需完整使用 Bootstrap 组件库，应选择 React 版本的组件库（如 `react-bootstrap`）。

## 8. 响应式：Bootstrap 媒体查询 vs 当前项目

Bootstrap 内置响应式断点：

```css
/* Bootstrap 源码中的断点 */
@media (min-width: 576px) { ... }  /* sm */
@media (min-width: 768px) { ... }  /* md */
@media (min-width: 992px) { ... }  /* lg */
@media (min-width: 1200px) { ... } /* xl */
```

当前项目断点（自定义）：

```css
@media (max-width: 900px) { ... }  /* 侧栏纵向 */
@media (max-width: 640px) { ... }  /* 隐藏侧栏 */
```

Bootstrap 的断点是固定的且有标准命名；当前项目根据实际业务需求自定义。

## 9. 项目实战：为 CRM 页面添加 Bootstrap 栅格布局

练习步骤：

1. 引入 Bootstrap CSS：`npm install bootstrap`，`import 'bootstrap/dist/css/bootstrap.min.css'`。
2. 将指标卡区域改为 Bootstrap 栅格：

```tsx
<div className="container">
  <div className="row g-3">
    <div className="col-md-3 col-sm-6">
      <div className="card"><div className="card-body">指标 1</div></div>
    </div>
    <div className="col-md-3 col-sm-6">
      <div className="card"><div className="card-body">指标 2</div></div>
    </div>
    <div className="col-md-3 col-sm-6">
      <div className="card"><div className="card-body">指标 3</div></div>
    </div>
    <div className="col-md-3 col-sm-6">
      <div className="card"><div className="card-body">指标 4</div></div>
    </div>
  </div>
</div>
```

3. 验证：小屏下 2 列、大屏下 4 列。
4. 运行 `npm run build`，确认无 CSS 冲突。
5. 检查 Ant Design 组件样式是否被 Bootstrap 覆盖（如 `.btn` 类名冲突）。

## 10. 调试清单

- Elements：确认类名来源（Bootstrap 还是 Ant Design）。
- Computed：检查被覆盖的样式和最终生效规则。
- Sources：确认 CSS 文件加载顺序（后加载的覆盖先加载的）。
- Network：检查 Bootstrap CSS 是否成功加载，大小是否合理。
- 使用 `!important` 临时调试后应重构选择器，而非永久保留。

## 11. 完成标准

你应能独立：

- 解释 Bootstrap 栅格系统的工作原理。
- 使用 Bootstrap 工具类快速搭建页面布局。
- 对比 Bootstrap 与 Ant Design 在项目中的优劣。
- 安全地在 React 项目中引入 Bootstrap CSS，避免类名冲突。
- 解释为什么当前项目选择 Ant Design 而非 Bootstrap。
- 使用 Bootstrap 的 JavaScript 组件并理解其与 React 组件的区别。
- 独立完成一个混合布局（Bootstrap 栅格 + Ant Design 组件）的页面。

继续阅读 [React](REACT.md) 和 [前端工程化](FRONTEND_ENGINEERING.md)，理解组件库如何与框架和构建工具协同工作。
