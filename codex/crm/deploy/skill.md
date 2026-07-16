---
name: crm-deploy
description: Use when managing CRM deployment, local Nacos and Redis, Nacos YAML publication, Gateway runtime configuration, Nginx config, Jar packaging, or graceful service restart.
---

# CRM Deploy Skill

## First Steps

1. Read `deploy/agent.md`.
2. Identify whether the task is local infrastructure, Nacos config, Nginx, Jar startup, or restart.
3. Check current container and port status before changing runtime state.
4. Avoid destructive volume or process operations unless explicitly requested.

## Nacos Config Workflow

1. Edit the matching file under `deploy/nacos/`.
2. Publish it to Nacos when runtime validation is requested.
3. Read it back from Nacos to verify.
4. Restart or refresh affected services if needed.
5. Validate through actuator or a real request.

Publish example:

```bash
cd deploy/nacos
curl -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
  --data-urlencode 'dataId=gateway-dev.yaml' \
  --data-urlencode 'group=DEFAULT_GROUP' \
  --data-urlencode 'type=yaml' \
  --data-urlencode 'content@gateway-dev.yaml'
```

## Redis Validation

```bash
redis-cli -h 127.0.0.1 -p 6379 -a qwer8989 ping
```

For rate limiter proof, use Redis `MONITOR` briefly and look for `request_rate_limiter` keys.

## Jar Workflow

```bash
cd backend
mvn -pl system,customer,gateway -am -DskipTests package
```

Restart:

```bash
deploy/graceful-restart.sh gateway backend/gateway/target/gateway-0.1.0.jar 8780
```

## Common Failure Signals

| Signal | Meaning |
| --- | --- |
| Nacos API unreachable | Nacos container down or port unavailable |
| Gateway health `DOWN` | Redis config/auth issue |
| Service not discovered | Nacos registration issue |
| Frontend API 404 | Nginx or gateway route mismatch |
| `429` | Gateway limiter active |

## References

Read `docs/manuals/DEPLOYMENT.md` for full commands and production notes.
