# HTML 与 CSS 基础到进阶：结合 CRM 界面

目标：从理解页面结构、盒模型和布局，进阶到响应式、层叠、可访问性和性能，能安全维护 `frontend/index.html` 与 `frontend/src/styles.css`，并处理 Ant Design 组件外层布局。

## 1. 页面从哪里开始

`frontend/index.html` 提供：

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>CRM 管理系统</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

Vite 以它为构建入口，React 把 UI 挂载到 `#root`。大部分业务 HTML 由 TSX 组件生成，但语义、DOM、CSS 和浏览器规则完全相同。

## 2. HTML 语义

HTML 描述内容结构，不只是给 CSS 提供盒子。

常见语义：

- `header/nav/main/section/aside/footer`：页面区域。
- `h1`～`h6`：有层级的标题。
- `button`：执行动作。
- `a`：导航到地址。
- `form/label/input`：输入与提交。
- `table`：真正的二维数据。

不要用带 `onClick` 的 `div` 代替按钮。原生 button 自带键盘、焦点和可访问语义；Ant Design Button 最终也基于这些能力。

项目使用 Ant Design Layout/Menu/Table/Form，因此优先通过组件 Props 和语义配置解决，再写少量布局 CSS。

## 3. DOM 与属性

React JSX 最终形成 DOM 树。浏览器基于 DOM 和 CSSOM 计算布局与绘制。

重要属性：

- `id`：页面内唯一；不拿它做大规模样式耦合。
- `class`/React `className`：可复用样式钩子。
- `name`：表单提交和字段识别。
- `type`：button/input 的行为。
- `aria-*`：补足控件名称、状态和关系。
- `data-*`：必要的应用元数据，不用来存敏感信息。

表格、弹窗等复杂控件先看实际 DOM，再选择稳定的外层 class。不要依赖 Ant Design 内部很深、可能随版本改变的临时节点结构。

## 4. 表单与输入

表单基础：label 与输入关联、字段类型正确、必填和错误信息清晰。

```html
<label for="customer-name">客户名称</label>
<input id="customer-name" name="customerName" autocomplete="organization" />
```

React/Ant Design Form 管理状态时，HTML 语义仍重要：

- 搜索使用合适 input 和提交方式，Enter 可触发。
- 保存按钮在提交期间禁用/loading。
- 删除不能做成误触的默认主操作。
- 浏览器校验和前端规则只是体验，后端必须校验。
- 密码字段不读取/回显现有密码，不记录输入。

## 5. CSS 选择器与层叠

```css
.page { padding: 20px; }
.workspace-tabs > .ant-tabs-nav { margin: 0 -24px 16px; }
.fund-favorite-button:hover { color: #d89614; }
```

最终样式由来源、层、`!important`、特异性和出现顺序共同决定。维护原则：

- 使用清晰的业务 class，避免长串 DOM 选择器。
- 特异性保持低且稳定。
- 不用 `!important` 层层对抗；项目仅在明确覆盖组件 hover 等必要位置谨慎使用。
- 修改 Ant Design 主题优先用 `ConfigProvider` token，而不是散落覆盖。
- DevTools Computed 面板确认是哪条规则获胜。

## 6. 盒模型

每个元素由 content、padding、border、margin 构成。项目全局：

```css
* {
  box-sizing: border-box;
}
```

`border-box` 让声明的 width/height 包含 padding 与 border，更适合组件布局。

常见问题：

- 子元素 width 100% 加 padding 溢出。
- margin 折叠导致间距意外。
- 固定高度遇到长文本截断。
- flex/grid 子项默认 `min-width: auto`，内容撑破容器；项目多处使用 `min-width: 0` 解决。

## 7. 长度单位

| 单位      | 含义        | 使用建议         |
| ------- | --------- | ------------ |
| `px`    | CSS 像素    | 边框、精确组件尺寸    |
| `%`     | 相对包含块     | 流式宽度         |
| `rem`   | 相对根字体     | 字号/间距体系可用    |
| `em`    | 相对当前字体    | 局部随字号缩放      |
| `vw/vh` | 视口比例      | 全屏区域，注意移动端视口 |
| `fr`    | Grid 剩余空间 | 网格列          |

当前项目大量使用 px，与 Ant Design 管理台密度一致。新增样式应保持现有节奏，不为理论纯粹混入另一套尺度系统。

## 8. Flexbox

Flex 适合一维排列。项目头部：

```css
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
```

必须理解：

- 主轴/交叉轴。
- `justify-content` 与 `align-items`。
- `gap`。
- `flex-grow/shrink/basis`。
- 子元素 `min-width: 0`。
- `flex-wrap` 处理内容不足。

按钮组、标题与操作、图例适合 Flex。二维卡片矩阵更适合 Grid。

## 9. CSS Grid

项目指标卡：

```css
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 16px;
}
```

`minmax(160px, 1fr)` 表示列至少 160px，并均分剩余空间。当前固定 4 列，再用媒体查询变为 2/1 列。

可响应式自动填充：

```css
grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
```

是否改用 auto-fit 取决于卡片最小宽度和设计要求，不能仅因为代码更短。

## 10. 定位、层叠上下文与浮层

`position`：static、relative、absolute、fixed、sticky。浮层问题不仅是 `z-index` 数字大小，还受层叠上下文和祖先的 transform/opacity/overflow 影响。

Ant Design Modal/Drawer/Tooltip 通常使用 Portal 放到 body 附近，减少祖先裁切。若浮层被遮挡：

1. 看元素实际挂载位置。
2. 检查祖先 overflow/transform。
3. 检查层叠上下文。
4. 使用组件的 container/zIndex 配置。
5. 最后才加全局 CSS。

## 11. 响应式设计

当前断点：

```text
<= 900px：页头纵向、指标 2 列、趋势图 1 列
<= 640px：隐藏侧栏、缩小边距、指标与评分 1 列
```

媒体查询应围绕内容何时放不下，而不只是某款设备。测试至少：

- 320/375px 窄屏。
- 768px 平板。
- 1280/1440px 常见桌面。
- 浏览器缩放 200%。
- 中文长文本、长邮箱和大数字。

当前小屏直接隐藏侧栏，意味着移动端无法通过它导航。这是一个真实产品限制；若要支持移动操作，应增加 Drawer/折叠菜单并做完整交互设计，不只是把 sidebar 改成 `display:block`。

## 12. 溢出与数据表格

管理台表格字段多，常见策略：

- 关键身份列固定。
- 指标区允许横向滚动。
- 明确最小列宽。
- 非关键长文本省略 + Tooltip/详情。
- 操作列不能被完全挤掉。
- 不用全局 `overflow:hidden` 掩盖问题。

文字省略：

```css
.single-line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

省略后必须仍有获取完整内容的方式，并注意敏感字段不应因为 Tooltip 而无条件暴露。

## 13. 颜色、排版与设计 Token

`main.tsx` 通过 `ConfigProvider` 设置主色和圆角；Ant Design 组件应优先继承 token。

自定义区域保持：

- 字体栈与中文回退一致。
- 正文/次要文字对比度足够。
- 状态不只靠红绿，配合文字/图标。
- 数字列对齐，金额/百分比格式统一。
- 标题层级靠字号、字重和间距表达。

若重复颜色和间距继续增长，可在 `:root` 定义语义变量；迁移要统一完成一个小范围，不制造 token 与硬编码并存的更多混乱。

## 14. 可访问性

WCAG 思路：可感知、可操作、可理解、健壮。项目重点：

- 键盘能到达菜单、Tabs、按钮、表单和弹窗。
- 焦点轮廓可见，不全局 `outline:none`。
- 图标按钮有 accessible name。
- 表单错误与字段关联，不能只 toast。
- loading/成功/失败有文本反馈。
- 颜色对比足够，涨跌不只用颜色。
- 页面缩放与窄屏下不丢功能。
- Drawer/Modal 关闭后焦点回到合理触发点。

自动检查只能发现部分问题，必须键盘操作和屏幕阅读器抽查。

## 15. CSS 与 Ant Design

优先顺序：

1. 组件官方 Props（size、scroll、responsive、className）。
2. `ConfigProvider` token/theme。
3. 外层业务容器布局。
4. 有限、稳定的组件 class 覆盖。

不要编辑 `node_modules`。版本升级后重点回归依赖 `.ant-*` 内部类的规则，如工作区 Tabs 导航样式。

CSS 全局引入意味着所有页面共享作用域。命名使用业务前缀/组件根 class，避免 `.header`、`.title` 这类过宽选择器污染其他区域。

## 16. 动画与用户偏好

动画用于表达状态变化，不用于拖慢高频操作。若增加动画：

```css
@media (prefers-reduced-motion: reduce) {
  .animated {
    animation: none;
    transition: none;
  }
}
```

优先动画 transform/opacity，减少频繁布局。表格大批量行不要做复杂入场动画。

## 17. 性能：布局、绘制与资源

常见代价：

- 读取布局后立刻写样式并循环，造成 layout thrashing。
- 大面积阴影、滤镜和固定背景频繁重绘。
- DOM 节点过多，例如一次渲染超大表格。
- 未压缩的大图片/字体阻塞加载。
- CSS 选择器虽不是首要瓶颈，但深层结构增加维护风险。

使用 Performance 的 Layout/Paint 证据定位。大列表优先分页/虚拟化，不能仅靠 CSS 隐藏不可见行。

## 18. 打印、深色模式与国际化

当前项目没有专门打印/深色主题，但进阶设计要知道：

- 打印报表需 `@media print` 隐藏交互、处理分页和颜色。
- 深色模式应通过 token 体系切换，不是全局 invert。
- 中文、英文、长文案会改变布局；不要用固定高度假设文案一行。
- 日期、数字、货币优先 `Intl`，不要依赖机器默认区域。

这些能力只有在产品明确需要时实现，不作为普通页面改动的附带重构。

## 19. 项目实战：让指标卡在平板更稳健

练习步骤：

1. DevTools 调到 640～1000px，记录出现挤压的准确宽度。
2. 检查每个 Statistic 的最长标题/数值。
3. 决定是调整断点、`minmax`、换行还是卡片内容。
4. 只修改 `.metrics` 相关规则。
5. 验证 320、640、900、1280px，以及 200% 缩放。
6. 验证中文长文本与大数字没有遮挡。
7. 用 Elements/Computed 确认规则来源。
8. `npm run build`，避免 CSS 修改之外的 TS/构建回归。

## 20. 项目实战：移动端导航设计

当前 `<=640px` 隐藏 sidebar。完整改进应：

1. 在 Header 增加有名称的菜单按钮。
2. 用 Ant Design Drawer 承载与桌面相同的菜单数据。
3. 选择菜单后关闭 Drawer 并打开工作区页。
4. 保持选中项、权限和键盘焦点。
5. 防止 Drawer 与内容双重滚动。
6. 测试旋转、返回、Tab 多开和超长菜单。

这是一项 React + CSS + 可访问性联合练习，不应只写媒体查询。

## 21. 调试清单

- Elements：DOM 是否与预期一致。
- Styles：规则是否命中、被覆盖或无效。
- Computed：最终尺寸、颜色和 box model。
- Layout：Grid/Flex overlay。
- Accessibility：名称、角色、焦点与对比度。
- Rendering：paint flashing、布局边界。
- Responsive mode：多宽度、缩放和触摸模拟。

先删除/禁用一条规则做最小验证，再改源码。不要靠不断追加更高特异性碰运气。

## 22. 完成标准

你应能独立：

- 解释 `index.html`、React Root 与最终 DOM 的关系。
- 选择语义元素而不是万能 div。
- 使用盒模型、Flex、Grid 和 media query 完成布局。
- 诊断特异性、overflow、min-width 和 z-index 问题。
- 通过 Ant Design Props/token 与有限 CSS 共同定制。
- 验证键盘、焦点、对比度、长文本和窄屏。
- 用 DevTools 证明规则来源和布局原因。

继续阅读 [React](REACT.md) 和 [前端工程化](FRONTEND_ENGINEERING.md)，把视觉结构连接到业务状态、构建与发布。
