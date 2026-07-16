# Frontend Agent Guide

## Role

You are working on the CRM web admin project. Keep the UI practical, dense enough for operations, and consistent with Ant Design.

## Project Shape

| File | Purpose |
| --- | --- |
| `src/api.ts` | API types and request functions |
| `src/App.tsx` | Current page composition, menu, tables, forms |
| `src/styles.css` | Global styling |
| `vite.config.ts` | Vite config |

## Rules

- API functions in `api.ts` should not include `/api`; `VITE_API_BASE` already includes it.
- Keep request headers `Content-Type`, `X-Client-Source: web`, and `Authorization` behavior intact.
- Use Ant Design components consistently.
- Keep operational pages compact and scannable.
- Avoid unrelated visual redesigns when implementing business features.
- If backend response types change, update TypeScript types first.

## Commands

```bash
cd frontend
npm install
npm run dev
npm run build
```

## Validation

Minimum check:

```bash
cd frontend
npm run build
```

Runtime check:

1. Start backend gateway.
2. Set `VITE_API_BASE=http://127.0.0.1:8780/api` if needed.
3. Run `npm run dev`.
4. Login with `admin/admin123`.

## References

- `docs/manuals/FRONTEND.md`
- `frontend/src/api.ts`
- `frontend/src/App.tsx`
