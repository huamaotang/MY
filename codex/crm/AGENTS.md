# CRM Monorepo

Multi-project product (CRM microservices + fund data platform), not a single service. Docs are in Chinese. This file is a fast-facts layer; routing and workflow detail lives in `agent.md` / `skill.md` at the repo root and per subproject. **Read those files (they are not auto-loaded) before editing:**

| Request           | Read first                                                           |
| ----------------- | -------------------------------------------------------------------- |
| Backend Java      | `backend/agent.md`, `backend/skill.md`, `docs/manuals/BACKEND.md`    |
| Python data tasks | `docs/manuals/PYTHON.md` (no `fund_spider/agent.md`)                 |
| Web admin         | `frontend/agent.md`, `frontend/skill.md`, `docs/manuals/FRONTEND.md` |
| iOS               | `ios/CrmMobile/agent.md`                                             |
| Android           | `android/CrmMobileAndroid/agent.md`                                  |
| Deploy/infra      | `deploy/agent.md`, `deploy/skill.md`                                 |

## Architecture (non-obvious)

- All client traffic enters only through Gateway `:8780` under `/api/**` (`StripPrefix=1`); external callers never hit downstream services directly. New path prefixes need a route in `deploy/nacos/gateway-dev.yaml`; existing prefixes do not.
- `backend/pom.xml` is the Maven parent (Java 8, Spring Boot 2.7, MyBatis-Plus). Modules: `core` (shared jar, no app), `gateway` (reactive/WebFlux — no servlet filters), `system :8782`, `customer :8783`, `fund :8784`, `admin :8781` (legacy, not the default path).
- Python `fund_spider/` is NOT an online API; it crawls external sources and writes the `fund` MySQL, which the Java `fund` service reads. `cli.py` is the only manual entrypoint; Prefect is the only scheduler (do not add crontab for business jobs).
- Frontend is single-file-centric: all API types/functions in `src/api.ts`, pages in `src/App.tsx` — update TS types first when backend DTOs change.
- iOS business logic concentrates in `CrmMobileApp.swift` (large file: narrow edits, full build). Android is plain Java Activities + `HttpURLConnection` (no Kotlin/Compose/Retrofit/Room).
- Config precedence: Nacos `<service>-<profile>.yaml` (source under `deploy/nacos/`) > `bootstrap.yml` (app name/profile/Nacos address only) > `${ENV:default}`. **Editing local YAML does not change running services — must be published to Nacos.**

## Local stack

Minimal chain: MySQL (crm + fund) → Nacos :8848 + Redis :6379 → system/customer/fund → gateway :8780 → frontend :5173. Prefect :4200 with its own PostgreSQL :5433. See `README.md` for full start order.

- `sql/schema.sql` **DROPs tables** — local/dev only, never for production. Production uses incremental scripts `sql/*.sql` and `fund_spider/sql/*.sql`; there is no migration tool, so order/application is manual.
- Nacos + Redis: `cd deploy/nacos && docker compose up -d`. Compose Redis has no password, but `gateway-dev.yaml` falls back to `qwer8989`; run `REDIS_PASSWORD='' mvn -pl gateway spring-boot:run` locally.
- Dev login `admin/admin123` is `{noop}`-encoded and for local use only.

## Verification (no CI — build/test is manual)

Use the narrowest check that proves the change:

- Java module: `cd backend && mvn -pl <module> -am -DskipTests package`. Tests live mainly in `fund`: `mvn -pl fund -am test`. Run: `mvn -pl <module> -am spring-boot:run`.
- All Java services one-click: `python3 skills/java-compile-services/scripts/compile_java_services.py` (repo root).
- Python: `cd fund_spider && python -m unittest discover -s tests -p 'test_*.py'`.
- Frontend: `cd frontend && npm run build` (= `tsc && vite build`). No lint or test scripts exist.
- Gateway: `curl -i http://127.0.0.1:8780/actuator/health` and `/actuator/gateway/routes`.

## Conventions & gotchas

- Response envelope `{"code":0,"message":"ok","data":...}` — clients consume `data` only; some failures return HTTP 200 with `code=500`, so check both.
- Requests: `Authorization: Bearer <token>` (JWT validated in each service with one shared secret — keep consistent) and `X-Client-Source: web|ios|android` or a clear test value. Frontend API functions must NOT include `/api` (it is already in `VITE_API_BASE`).
- Prefect schedules live in `fund_spider/prefect.yaml`; UI edits are runtime-only — write long-term changes back and redeploy via `bin/deploy_prefect.sh`.
- When Controllers/APIs/env vars/tables/ports change, update `docs/manuals/*` / `docs/reference/*` in the same commit. Never commit real secrets or developer-absolute paths.
- `docs/reference/KNOWN_LIMITATIONS.md` records unresolved production blockers — update it in the same commit that fixes/adds any listed risk.
