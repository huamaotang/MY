# Prefect PostgreSQL

Prefect Server uses a dedicated PostgreSQL 17 database for Deployment, run,
task, event, and log metadata. This database is independent from the CRM
business MySQL database.

For local development, the Compose defaults expose PostgreSQL only on
`127.0.0.1:5433`:

```bash
docker compose -f deploy/prefect/docker-compose.yml up -d
```

Production must copy `.env.example` to a protected `.env`, replace the default
password, and set the matching URL in `/etc/crm/fund-spider.env`:

```env
PREFECT_SERVER_DATABASE_CONNECTION_URL=postgresql+asyncpg://prefect:URL_ENCODED_PASSWORD@127.0.0.1:5433/prefect
```

Back up the named volume `crm-prefect-postgres-data`. Do not use
`docker compose down -v` during normal upgrades because `-v` removes the
Prefect metadata volume.
