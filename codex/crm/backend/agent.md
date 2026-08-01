# Backend Agent Guide

## Role

You are working on the CRM Java backend. Preserve the microservice boundary between `gateway`, `system`, `customer`, `fund`, `admin`, and `core`.

## Module Ownership

| Module | Owns |
| --- | --- |
| `core` | shared response, exception, security, logging, MyBatis config |
| `gateway` | `/api/**` routing, CORS, Redis rate limiting |
| `system` | auth, users, roles, menus |
| `customer` | customers, contacts, follow records |
| `fund` | funds, scoring, portfolio holdings, finance news, stocks |
| `admin` | legacy combined service, not the default microservice path |

## Rules

- Keep shared behavior in `core` only when more than one service needs it.
- Do not put Servlet/MVC filters into `gateway`; it is reactive.
- External callers should use `/api/**` through `gateway`.
- Downstream controllers should not include `/api` in mappings.
- Nacos YAML under `deploy/nacos/` is runtime config; `bootstrap.yml` only connects services to Nacos.
- JWT-protected endpoints require `Authorization: Bearer <token>`.
- Preserve the `ApiResponse<T>` response contract.

## Common Commands

```bash
cd backend
mvn -pl system -am spring-boot:run
mvn -pl customer -am spring-boot:run
mvn -pl fund -am spring-boot:run
mvn -pl gateway spring-boot:run
mvn -pl system,customer,fund,gateway -am -DskipTests package
```

## Validation

Use the smallest module check:

```bash
cd backend
mvn -pl <module> -am -DskipTests package
```

For gateway routing or rate limiting, validate with:

```bash
curl -i http://127.0.0.1:8780/actuator/health
curl -fsS http://127.0.0.1:8780/actuator/gateway/routes
```

## References

- `docs/manuals/BACKEND.md`
- `docs/MODULES.md`
- `docs/reference/API.md`
- `docs/reference/DATABASE.md`
- `deploy/nacos/*.yaml`
