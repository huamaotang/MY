---
name: crm-frontend
description: Use when implementing, debugging, or validating the CRM React admin frontend, including API typing, Ant Design pages, Vite configuration, login state, and backend integration through Gateway.
---

# CRM Frontend Skill

## First Steps

1. Read `frontend/agent.md`.
2. Inspect `src/api.ts` for existing API patterns.
3. Inspect `src/App.tsx` for page and menu structure.
4. Confirm whether the backend API already exists.

## API Workflow

1. Add or update TypeScript types in `src/api.ts`.
2. Add request function using `request<T>()`.
3. Keep paths relative to API base, for example `/customers`, not `/api/customers`.
4. Handle errors in page components with Ant Design message.

## Page Workflow

1. Add the view key if a new page is needed.
2. Add menu item.
3. Add component rendering in the content area.
4. Use Ant Design tables, forms, modals, tags, and buttons consistently.
5. Keep loading and error states visible.

## Validation

```bash
cd frontend
npm run build
```

Manual smoke test:

```text
Login -> open affected menu -> load data -> create/edit/delete if applicable -> refresh page
```

## Common Failure Signals

| Signal | Meaning |
| --- | --- |
| Network error | Gateway down or wrong `VITE_API_BASE` |
| `403` | Missing/expired token |
| `429` | Gateway rate limit |
| CORS error | Request bypassed gateway or gateway CORS config issue |
| TypeScript build failure | API type or component prop mismatch |

## References

Read `docs/manuals/FRONTEND.md` for detailed setup and examples.
