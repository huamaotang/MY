# Vue 3 基础与进阶：对比 React 理解前端框架差异

目标：从理解 Vue 3 的模板语法、响应式系统和组件模型，进阶到掌握 Options API 与 Composition API 的取舍，能对比 React 与 Vue 的设计差异，并理解当前项目为何选择 React + Ant Design。

前置：[JavaScript](JAVASCRIPT.md)、[TypeScript](TYPESCRIPT.md)。框架对比参考本篇；React 深入见 [React](REACT.md)。

## 1. Vue 与 React 的定位差异

Vue 和 React 都解决同一个问题：如何用组件化方式构建用户界面。但它们的设计哲学不同。

| 维度 | React | Vue |
| --- | --- | --- |
| 授权方 | Meta（Facebook） | 尤雨溪及社区 |
| 范式 | JavaScript 中心（JSX） | 模板语法中心（HTML 扩展） |
| 状态响应式 | 手动管理（useState/setState） | 自动追踪（ref/reactive） |
| 学习曲线 | 较陡：需理解 JSX、Hooks 规则 | 较缓：模板接近原生 HTML |
| 官方生态 | 路由、状态库由社区主导 | 官方提供路由（Vue Router）、状态（Pinia） |
| 当前项目 | ✅ 使用 | ❌ 未使用 |

Vue 的设计更偏向"渐进式"：可以只用来增强页面，也可以组成完整单页应用。React 更偏向"库"：只负责视图层，其他能力需自行组合。

## 2. 模板语法：Vue 的核心差异

Vue 使用模板语法，类似 HTML 但增加了指令。React 使用 JSX，在 JavaScript 中写 HTML 结构。

Vue 模板：

```vue
<template>
  <div class="page">
    <h1>{{ title }}</h1>
    <button @click="handleClick">点击</button>
    <ul>
      <li v-for="item in items" :key="item.id">{{ item.name }}</li>
    </ul>
  </div>
</template>
```

React JSX：

```tsx
function Page({ title }) {
  return (
    <div className="page">
      <h1>{title}</h1>
      <button onClick={handleClick}>点击</button>
      <ul>
        {items.map((item) => (
          <li key={item.id}>{item.name}</li>
        ))}
      </ul>
    </div>
  );
}
```

关键差异：

- Vue 模板中 `{{ }}` 插值、`v-for` 指令、`@click` 事件由 Vue 编译为渲染函数。
- React JSX 直接是 JavaScript，`{}` 中放表达式，`.map()` 由数组方法生成。
- Vue 模板语法对新手更友好；React JSX 更灵活，能任意使用 JavaScript 逻辑。
- React 的条件渲染用 `{condition && <Component />}`，Vue 用 `v-if`/`v-show` 指令。

## 3. 响应式系统：自动 vs 手动

这是 Vue 和 React 最大的架构差异。

Vue 3 使用 Proxy 实现响应式：

```vue
<script setup>
import { ref, reactive, computed } from 'vue'

const count = ref(0)
const user = reactive({ name: 'CRM', level: 1 })
const doubled = computed(() => count.value * 2)

function increment() {
  count.value++
}
</script>
```

React 使用 setState 触发更新：

```tsx
const [count, setCount] = useState(0)
const user = { name: 'CRM', level: 1 };
const doubled = count * 2;

function increment() {
  setCount(prev => prev + 1);
}
```

差异总结：

- Vue 的 `ref`/`reactive` 会自动追踪依赖，值变化时自动触发相关视图更新。
- React 需要显式调用 setter，React 才知道状态变了。
- Vue 的响应式基于 Proxy，能检测对象属性的增减；React 的不可变更新需要手动创建新对象。
- Vue 的 `computed` 自动缓存，React 需 `useMemo` 手动缓存。
- Vue 中修改 `user.name` 会自动触发更新；React 中 `user.name = 'X'` 无效，必须 `setUser({...user, name: 'X'})`。

## 4. Composition API 与 Hooks

Vue 3 推荐 Composition API，与 React Hooks 在思想上非常接近：

Vue Composition API：

```vue
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const loading = ref(false)
const data = ref(null)

async function fetchData() {
  loading.value = true
  try {
    data.value = await fetch('/api/items').then(r => r.json())
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
onUnmounted(() => { /* 清理 */ })
</script>
```

React 对应写法：

```tsx
function Component() {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState(null);

  useEffect(() => {
    let cancelled = false;
    async function fetchData() {
      setLoading(true);
      try {
        const result = await fetch('/api/items').then(r => r.json());
        if (!cancelled) setData(result);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchData();
    return () => { cancelled = true; };
  }, []);
}
```

相似点：

- 都使用组合式函数组织逻辑。
- 都有生命周期对应的 Hook（onMounted vs useEffect）。
- 都有响应式值（ref vs useState）。

差异点：

- Vue 模板中直接读 `data.value`，React JSX 中直接读 `data`。
- Vue 自动处理依赖追踪，React 需手动维护依赖数组。
- Vue 的 `script setup` 是编译时语法糖，更简洁。

## 5. Options API 与组件组织

Vue 也支持 Options API（Vue 2 风格），将组件拆分为 data、methods、computed、watch 等选项：

```vue
<script>
export default {
  data() {
    return { items: [], loading: false }
  },
  computed: {
    filteredItems() {
      return this.items.filter(i => i.active)
    }
  },
  methods: {
    async loadItems() {
      this.loading = true
      this.items = await fetchItems()
      this.loading = false
    }
  },
  mounted() {
    this.loadItems()
  }
}
</script>
```

Options API 更直观，适合小型组件和从 Vue 2 迁移的场景。Composition API 更适合复杂逻辑复用和类型推导。

当前项目使用 React 函数组件 + Hooks，与 Vue Composition API 思路相近，但实现机制完全不同。

## 6. 组件通信

| 方式 | Vue | React |
| --- | --- | --- |
| 父传子 | Props | Props |
| 子传父 | `$emit` / `defineEmits` | 回调函数 |
| 跨组件 | Provide/Inject、事件总线 | Context、状态库 |
| 全局状态 | Pinia | Redux / Zustand / Context |
| 路由 | Vue Router | React Router |

Vue 的 Provide/Inject 类似 React 的 Context，但使用更方便：

```vue
<!-- 父组件 -->
<script setup>
import { provide } from 'vue'
provide('theme', 'dark')
</script>

<!-- 子组件 -->
<script setup>
import { inject } from 'vue'
const theme = inject('theme')
</script>
```

Vue 的 `v-model` 是双向绑定的语法糖，React 中需要手动维护值和 onChange。

## 7. Vue 生态与工具链

| 能力 | Vue 方案 | 当前项目方案 |
| --- | --- | --- |
| 构建工具 | Vite（Vue 官方推荐） | Vite |
| UI 组件库 | Element Plus、Naive UI | Ant Design（React 版） |
| 状态管理 | Pinia | 组件状态 + Context |
| 路由 | Vue Router | React Router（本项目未使用路由库） |
| 类型支持 | `<script setup lang="ts">` | TypeScript/TSX |

Vite 对 Vue 和 React 都提供了官方插件，构建体验相近。`vite.config.ts` 中通过插件区分：

```ts
// Vue
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({ plugins: [vue()] })

// React（当前项目）
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig({ plugins: [react()] })
```

## 8. Vue 3 新特性

- `<script setup>`：编译时语法糖，自动暴露顶层变量和函数。
- `<Suspense>`：异步依赖的加载状态处理，类似 React 的 Suspense。
- Teleport：将组件渲染到 DOM 树的任意位置，类似 React 的 Portal。
- `<Transition>`：内置过渡动画支持。
- 更好的 TypeScript 集成：`<script setup lang="ts">`。

## 9. 项目实战：在 Vue 中实现分页列表

对比当前 React 项目中的分页列表，Vue 实现：

```vue
<template>
  <div class="page">
    <table v-if="items.length">
      <thead><tr><th>名称</th><th>状态</th></tr></thead>
      <tbody>
        <tr v-for="item in paginatedItems" :key="item.id">
          <td>{{ item.name }}</td>
          <td>{{ item.status }}</td>
        </tr>
      </tbody>
    </table>
    <div v-if="loading">加载中...</div>
    <div v-if="!loading && !items.length">无数据</div>
    <button @click="current--" :disabled="current === 1">上一页</button>
    <span>{{ current }} / {{ totalPages }}</span>
    <button @click="current++" :disabled="current === totalPages">下一页</button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const items = ref([])
const loading = ref(false)
const current = ref(1)
const pageSize = ref(10)

const totalPages = computed(() => Math.ceil(items.value.length / pageSize.value))
const paginatedItems = computed(() =>
  items.value.slice((current.value - 1) * pageSize.value, current.value * pageSize.value)
)

async function loadItems() {
  loading.value = true
  items.value = await fetchItems()
  loading.value = false
}

onMounted(loadItems)
</script>
```

对比 React 版本，核心差异在状态更新的语法和模板的写法。功能逻辑完全等价。

## 10. 为什么当前项目选择 React 而非 Vue

回顾 [React](REACT.md) 学习文档，当前项目的技术选择有其原因：

- **团队熟悉度**：维护者已掌握 React + TypeScript 体系。
- **Ant Design**：企业级 UI 组件库，React 版 `antd` 与项目需求匹配度高。
- **生态一致性**：Vite + React + antd + TypeScript 的工具链成熟、文档完善。
- **TypeScript 集成**：React + TSX 的类型推导在复杂项目中更稳定。

但这不代表 Vue 不适合：

- Vue 3 的 `<script setup>` + TypeScript 同样简洁。
- Element Plus 提供了完整的企业级组件库。
- Pinia 的类型支持也很好。
- 项目初期选择 Vue 同样可以构建高质量管理台。

选择框架的核心标准是团队熟练度、组件库可用性和项目规模，而非技术本身的高低。

## 11. 调试方法

Vue 项目使用 Vue DevTools 扩展：

- 查看组件树、Props 和状态。
- 检查响应式依赖和触发更新的源头。
- 时间旅行调试（Pinia 状态变化记录）。
- 浏览器 Performance 面板同样适用于 Vue。

React 项目使用 React DevTools。两者调试思路类似，都是观察组件树和状态变化。

## 12. 完成标准

你应能独立解释和实现：

- Vue 模板语法与 JSX 的本质区别。
- `ref`、`reactive`、`computed` 的用法和响应式原理。
- Composition API 与 React Hooks 的对应关系。
- Vue 组件间通信的常见方式。
- 用 Vue 实现一个分页列表，对比 React 版本。
- 解释当前项目选择 React 而非 Vue 的原因。
- 在 Vue DevTools 中查看组件状态和依赖。

继续阅读 [前端工程化](FRONTEND_ENGINEERING.md)，了解构建工具和部署流程，不因框架不同而改变核心链路。
