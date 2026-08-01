# CRM Web Admin

React + TypeScript + Vite + Ant Design 管理台。

- 完整开发与发布：[Web 管理台手册](../docs/manuals/FRONTEND.md)
- REST 契约：[API 参考](../docs/reference/API.md)
- 总体架构：[系统架构与模块说明](../docs/MODULES.md)

## 快速开始

```bash
cd frontend
npm install
cp .env.example .env.local
npm run dev
```

开发 API Base 默认 `http://127.0.0.1:8780/api`。生产建议以 `VITE_API_BASE=/api` 构建并由 Nginx 同源转发。

## 验证与构建

```bash
cd frontend
npm run build
```

接口类型和请求函数统一放 `src/api.ts`；路径只写 `/customers` 等相对 API 路径，不重复添加 `/api`。
