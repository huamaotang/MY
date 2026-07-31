---
name: crawl-eastmoney-funds
description: Use this skill when working on the local fund_spider project to crawl EastMoney/Tiantian Fund data, including daily historical NAV, feature data such as standard deviation and Sharpe ratio, basic fund profile information, and fund stock holdings. Use it for adding, fixing, running, or validating Python crawlers and MySQL persistence for these fund datasets.
---

# Crawl EastMoney Funds

## Purpose

Use this skill for the local `fund_spider/` Python crawler that collects EastMoney/Tiantian Fund data into MySQL database `fund`.

Supported data areas:

- Basic fund profile from `fundf10.eastmoney.com/jjjz_<code>.html`
- Historical daily NAV from `api.fund.eastmoney.com/f10/lsjz`
- Feature data from `fundf10.eastmoney.com/tsdata_<code>.html`
- Stock holdings from `fundf10.eastmoney.com/ccmx_<code>.html`

## Workflow

1. Inspect the existing `fund_spider/` code before changing behavior.
2. Reuse the existing request pacing model:
   - browser-like headers
   - single-threaded crawling unless explicitly changed
   - random delay via `REQUEST_MIN_DELAY_SECONDS` and `REQUEST_MAX_DELAY_SECONDS`
   - bounded retries
3. Ensure schemas are created through both:
   - `fund_spider/db.py` `ensure_schema`
   - `fund_spider/sql/init.sql`
4. Use upsert semantics with unique keys, so reruns are idempotent.
5. Validate with one fund first, usually `519674`, before batch execution.
6. For long batch crawls, expose `FUND_LIMIT`, `FUND_OFFSET`, and a single-fund override.
7. Use `fund_spider/cli.py` as the only command entrypoint.
8. Use `nav-performance` for the twice-daily NAV/performance snapshot and `feature` for the 08:00 feature refresh.
9. Keep full basic profiles, holdings, ratings, and historical NAV as manual commands.
10. All recurring schedules must use APScheduler through `fund_spider/cli.py schedule`, or `fund_spider/cli.py web` when a Web configuration service is needed. Do not add custom polling loops or OS-level per-task timers.

## Commands

Use the project virtualenv:

```bash
fund_spider/.venv/bin/python fund_spider/cli.py <command>
```

Common examples:

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py basic --fund-code 519674
```

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py nav-history --fund-code 519674 --start-date 20250101
```

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py feature --fund-code 519674
```

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py holdings --fund-code 519674
```

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py rating --mode history --fund-code 519674
```

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py nav-performance
```

```bash
fund_spider/.venv/bin/python fund_spider/cli.py schedule --run-on-start 1 --once 1 --dry-run 1
```

```bash
fund_spider/.venv/bin/python fund_spider/cli.py web --host 127.0.0.1 --port 8088
```

Shell entrypoints are organized under `fund_spider/bin/`, collectors under
`fund_spider/spiders/`, APScheduler integration under `fund_spider/runtime/`, and standalone
tools under `fund_spider/tools/`.

## Detailed Reference

Read [references/eastmoney-fund-crawler.md](references/eastmoney-fund-crawler.md) when you need endpoint details, table schemas, parser expectations, or batch execution guidance.
