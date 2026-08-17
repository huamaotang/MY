---
name: crm-monorepo
description: Use when working across the CRM monorepo, choosing the correct subproject, coordinating backend/frontend/mobile/deployment changes, or validating cross-project behavior.
---

# CRM Monorepo Skill

## Quick Routing

Choose the smallest owning project:

| Request type                           | Use                                 |
| -------------------------------------- | ----------------------------------- |
| API, database, security, gateway, logs | `backend/skill.md`                  |
| React admin UI                         | `frontend/skill.md`                 |
| iPhone app                             | `ios/CrmMobile/skill.md`            |
| Android app                            | `android/CrmMobileAndroid/skill.md` |
| Nacos, Redis, Nginx, Jar deployment    | `deploy/skill.md`                   |

## Required First Steps

1. Check `git status --short`.
2. Read the relevant project `agent.md`.
3. Inspect existing code before designing a change.
4. Prefer local patterns over new abstractions.
5. Keep documentation updated when behavior or commands change.

## Cross-Project Change Workflow

For a feature that touches multiple projects:

1. Backend: add or update API and validate with curl.
2. Gateway: update `deploy/nacos/gateway-dev.yaml` if a new path prefix is exposed.
3. Web: update `frontend/src/api.ts` types/functions and UI.
4. Mobile: update `ApiClient` and models only when mobile needs the feature.
5. Docs: update `docs/manuals/*` or `docs/MODULES.md`.
6. Validation: run each affected project's minimum check.

## Runtime Assumptions

Default local chain:

```text
MySQL -> Nacos + Redis -> system + customer + gateway -> frontend/iOS/Android
```

Default ports:

```text
Nacos 8848
Redis 6379
gateway 8780
system 8782
customer 8783
admin 8781
```

## Completion Checklist

- Relevant code or docs changed.
- No unrelated user changes reverted.
- Validation command run or explicitly reported as unavailable.
- If runtime config changed, Nacos publish step is documented or performed when requested.
