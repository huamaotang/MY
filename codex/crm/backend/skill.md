---
name: crm-backend
description: Use when implementing, debugging, or validating CRM backend Java microservices, including Spring Boot services, Gateway routing, Nacos config, Redis rate limiting, MyBatis mappers, security, and API behavior.
---

# CRM Backend Skill

## First Steps

1. Read `backend/agent.md`.
2. Identify the owning module: `core`, `gateway`, `system`, `customer`, or `admin`.
3. Inspect the matching controller, service, mapper, entity, and Nacos YAML before editing.
4. Check if the change affects gateway route predicates or frontend/mobile clients.

## Implementation Patterns

### New API

1. Add or update SQL in `sql/` if schema changes.
2. Update entity under the service module.
3. Update Mapper interface and XML.
4. Update service interface and implementation.
5. Add controller endpoint.
6. If a new external path prefix is introduced, update `deploy/nacos/gateway-dev.yaml`.
7. Validate through `gateway` using `/api/**`.

### Shared Behavior

Put code in `backend/core/` only when at least two services need it. Otherwise keep it local to the service.

### Gateway Changes

- Use reactive Gateway APIs.
- Keep route config in Nacos YAML.
- Rate limiting uses Redis and `RateLimiterConfig.ipKeyResolver`.
- Validate route filters through `/actuator/gateway/routes`.

## Validation Commands

```bash
cd backend
mvn -pl <module> -am -DskipTests package
```

For full default backend chain:

```bash
cd backend
mvn -pl system,customer,gateway -am -DskipTests package
```

Runtime smoke test:

```bash
curl -i http://127.0.0.1:8780/actuator/health
curl -s -X POST http://127.0.0.1:8780/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

## Common Failure Signals

| Signal | Meaning |
| --- | --- |
| `503` from gateway | Downstream service not registered or unhealthy |
| `403` from service | Missing or invalid JWT |
| `429` from gateway | Rate limiter active |
| Gateway health `DOWN` | Usually Redis connection or password |
| Mapper error | XML namespace, method, or field mismatch |

## References

Read `docs/manuals/BACKEND.md` for detailed setup, interface lists, and troubleshooting.
