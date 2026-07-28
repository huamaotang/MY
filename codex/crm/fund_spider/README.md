# Fund Spider

EastMoney/Tiantian Fund crawler with one supported command entrypoint:

```bash
python cli.py <command>
```

The crawler is single-threaded, uses bounded retries and random request pacing,
and writes idempotently to MySQL.

## Directory layout

```text
fund_spider/
├── cli.py, jobs.py, db.py, settings.py   # entrypoint and application layer
├── spiders/                               # source-specific collectors/parsers
├── runtime/                               # CLI and Web schedulers
├── bin/                                   # executable Shell scripts
├── config/                                # persisted scheduler configuration
├── tools/                                 # standalone support tools such as OCR
├── sql/                                   # schema and migrations
└── tests/                                 # unit tests
```

## Setup

```bash
cd fund_spider
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
mysql -uroot -p < sql/init.sql
```

Common request controls:

```env
REQUEST_MIN_DELAY_SECONDS=1.5
REQUEST_MAX_DELAY_SECONDS=4.0
REQUEST_TIMEOUT_SECONDS=10
REQUEST_MAX_RETRIES=3
```

## Fund commands

Every fund dataset also has a manually executable shell wrapper. Manual runs
start with `nohup` in the background, print their PID/log paths, and write
timestamped logs under `logs/`. Arguments are passed through unchanged to
`cli.py`, and the wrapper uses `.venv/bin/python` when available:

```bash
./bin/run_basic.sh
./bin/run_nav_history.sh --fund-code 519674 --start-date 20260701 --end-date 20260728
./bin/run_nav_performance.sh
./bin/run_feature.sh --fund-code 519674
./bin/run_holdings.sh --fund-code 519674
./bin/run_rating.sh
./bin/run_rating.sh --mode history --fund-code 519674
```

Set `PYTHON_EXECUTABLE` to override the interpreter. If the project virtual
environment is unavailable, the scripts fall back to `python3`. Use the
internal `--foreground` option when an external scheduler must wait for the
real exit status:

```bash
./bin/run_feature.sh --foreground --fund-code 519674
```

### Basic information (manual)

Refresh the complete fund list and then crawl every fund profile:

```bash
python cli.py basic
```

Refresh one existing fund without fetching the complete list:

```bash
python cli.py basic --fund-code 519674
```

`basic` updates fund code, name, purchase status, inception date, manager, type,
management company, asset scale, and scale date. Batch selection supports
`--fund-start-code`, `--fund-limit`, and `--fund-offset`.

### Current NAV and performance

Refresh the complete ranking snapshot:

```bash
python cli.py nav-performance
```

Each source page is committed as one transaction across:

- `fund_detail`: fund code, name, and purchase status.
- `fund_nav_history`: current NAV and daily growth.
- `fund_performance_history`: rolling returns, custom-period return, and fee fields.

The transaction is idempotent by fund code and NAV date. This command is the
only fund command scheduled twice per day.

### Historical NAV (manual)

Historical NAV accepts `YYYYMMDD` or `YYYY-MM-DD`:

```bash
python cli.py nav-history \
  --fund-code 519674 \
  --start-date 20260701 \
  --end-date 2026-07-28
```

The date bounds are independently optional:

- neither bound: all available history;
- only `--start-date`: from that date through latest;
- only `--end-date`: earliest through that date;
- both bounds: closed date interval.

Use `--fund-limit` and `--fund-offset` for a bounded batch. Rows are upserted
into `fund_nav_history` by `(fund_code, nav_date)`.

### Feature data

```bash
python cli.py feature --fund-code 519674
python cli.py feature --fund-limit 100 --fund-offset 0
```

This command stores standard deviation and Sharpe ratio rows in
`fund_feature_data`. It is scheduled once at 08:00.

### Holdings (manual)

```bash
python cli.py holdings --fund-code 003095
python cli.py holdings --fund-limit 100 --fund-offset 0
```

Optional `--year`, `--month`, and `--top-line` parameters are forwarded to the
holding source. The page label `截止至：YYYY-MM-DD` is stored as `cutoff_date`;
valuation selects the latest cutoff date not later than the quote date.

### Ratings (manual)

Refresh the complete current rating list:

```bash
python cli.py rating
```

Refresh historical ratings for selected funds:

```bash
python cli.py rating --mode history --fund-code 519674
python cli.py rating --mode history --fund-limit 100 --fund-offset 0
```

Neither rating mode is automatically scheduled.

## Scheduler

The scheduler uses `Asia/Shanghai` on every machine. Its defaults are:

```env
NAV_PERFORMANCE_SCHEDULE_TIMES=08:00,21:00
FEATURE_SCHEDULE_TIME=08:00
FEATURE_SCHEDULE_ENABLED=1
```

At 08:00 it executes `bin/run_nav_performance.sh` followed by
`bin/run_feature.sh`. At 21:00 it executes only
`bin/run_nav_performance.sh`. Jobs share one global lock. If a previous trigger
is still running, the new trigger is logged as
`skipped_overlap`.

A failed morning NAV job does not prevent the feature job from running. The
trigger is reported failed when either subprocess fails.

Dry-run both unique scheduled jobs:

```bash
python cli.py schedule --once 1 --dry-run 1 --trigger all
```

Run only the morning or evening plan immediately:

```bash
python cli.py schedule --once 1 --trigger morning
python cli.py schedule --once 1 --trigger evening
```

Start the long-lived scheduler:

```bash
python cli.py schedule
```

### Web scheduler

```bash
python cli.py web --host 127.0.0.1 --port 8088
```

Open `http://127.0.0.1:8088/`. The page configures the two NAV times, feature
time, request pacing, and optional feature batch limits. Useful APIs:

```bash
curl http://127.0.0.1:8088/api/status
curl -X POST http://127.0.0.1:8088/api/run \
  -H 'Content-Type: application/json' \
  -d '{"trigger":"morning","dry_run":true}'
curl -X POST http://127.0.0.1:8088/api/config \
  -H 'Content-Type: application/json' \
  -d '{"nav_performance_schedule_times":"08:00,21:00","feature_schedule_time":"08:00"}'
```

Configuration is persisted in `config/scheduler_config.json`. Trigger logs are
stored under `logs/fund_scheduler_*.log`.

### macOS LaunchAgent

The production-style local schedule uses
`deploy/macos/com.crm.fund-scheduler.plist`. Launchd executes
`bin/run_fund_scheduled.sh` at 08:00 and 21:00. The script applies one shared
lock, runs both morning jobs independently, aggregates their exit status, and
writes separate `nav_performance_scheduled_*.log` and
`feature_scheduled_*.log` files.

`com.crm.sina-news.plist` remains an independent 120-second news schedule, but
now executes `bin/run_sina_news.sh` instead of invoking Python directly.

## Other data commands

Fund data uses the unified CLI, while the existing stock and news capabilities
remain independently callable:

```bash
python cli.py stock --market cn
python cli.py stock --market hk
python cli.py stock --market all
python cli.py sina-news --max-pages 1
python cli.py news --score 2
```

Hong Kong real-time market `116` is preferred; the stock crawler falls back to
delayed market `128` and normalizes it to market code `116`. The existing
macOS stock LaunchAgent continues to call `bin/run_stock_market_update.sh`.

Yangjibao credentials must be supplied through `YJB_AUTHORIZATION`,
`YJB_REQUEST_SIGN`, and `YJB_REQUEST_TIME`; they are not stored in source
control.

## Validation

```bash
python -m unittest discover -s tests -p 'test_*.py'
python cli.py schedule --once 1 --dry-run 1 --trigger all
```
