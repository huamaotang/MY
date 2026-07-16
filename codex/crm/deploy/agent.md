# Deploy Agent Guide

## Role

You are working on CRM deployment and local operations. Be careful with runtime config, credentials, service restarts, and volume data.

## Project Shape

| Path | Purpose |
| --- | --- |
| `nacos/docker-compose.yml` | local Nacos and Redis |
| `nacos/*-dev.yaml` | Nacos config payloads |
| `nginx.conf` | Nginx deployment config |
| `homebrew-crm.conf` | macOS Homebrew Nginx config |
| `graceful-restart.sh` | Jar restart helper |

## Rules

- Do not delete Docker volumes unless explicitly requested.
- Local YAML changes do not affect running services until published to Nacos.
- Keep secrets overridable by environment variables.
- Gateway depends on Redis for health and rate limiting.
- Gateway route changes should be validated through actuator routes.
- Production must not reuse development defaults for passwords or JWT secret.

## Common Commands

```bash
cd deploy/nacos
docker compose up -d
docker compose ps

curl -fsS http://127.0.0.1:8848/nacos/v1/ns/operator/metrics
redis-cli -h 127.0.0.1 -p 6379 -a qwer8989 ping
```

## References

- `docs/manuals/DEPLOYMENT.md`
- `deploy/README.md`
- `deploy/nacos/README.md`
