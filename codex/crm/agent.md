# CRM Repository Agent Guide

## Role

You are working in the CRM monorepo. Treat this repository as a multi-project product, not a single service.

## Project Map

| Area | Path | Guide |
| --- | --- | --- |
| Backend microservices | `backend/` | `backend/agent.md`, `backend/skill.md`, `docs/manuals/BACKEND.md` |
| Web admin | `frontend/` | `frontend/agent.md`, `frontend/skill.md`, `docs/manuals/FRONTEND.md` |
| iOS app | `ios/CrmMobile/` | `ios/CrmMobile/agent.md`, `ios/CrmMobile/skill.md`, `docs/manuals/IOS.md` |
| Android app | `android/CrmMobileAndroid/` | `android/CrmMobileAndroid/agent.md`, `android/CrmMobileAndroid/skill.md`, `docs/manuals/ANDROID.md` |
| Deploy and operations | `deploy/` | `deploy/agent.md`, `deploy/skill.md`, `docs/manuals/DEPLOYMENT.md` |

## Default Workflow

1. Identify which project owns the requested change.
2. Read that project's `agent.md` before editing.
3. Use that project's `skill.md` workflow for implementation and validation.
4. Keep changes inside the owning project unless the feature crosses API boundaries.
5. If backend APIs change, update web/mobile clients and docs as needed.
6. Do not revert unrelated local changes.

## Validation Expectations

Use the narrowest validation that proves the change:

| Change | Minimum validation |
| --- | --- |
| Backend Java | `mvn -pl <module> -am -DskipTests package` |
| Gateway/Nacos | Read Nacos config, start gateway if needed, test route or actuator |
| Frontend | `npm run build` |
| iOS | Xcode build or document why unavailable |
| Android | Gradle assemble or Android Studio run |
| Docs only | Check links and affected file list |

## Cross-Project Rules

- External API paths must go through `gateway` as `/api/**`.
- Frontend and mobile API base URLs should include `/api`.
- Protected backend APIs require `Authorization: Bearer <token>`.
- Request clients should set `X-Client-Source` as `web`, `ios`, `android`, or a clear test source.
- Nacos YAML under `deploy/nacos/` is the source for local shared config; publishing to Nacos is a separate runtime action.

## Key References

- `README.md`
- `docs/MODULES.md`
- `docs/manuals/README.md`
