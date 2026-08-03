# Fund Spider

完整中文开发、测试、Prefect 与生产运维教程见
[`docs/manuals/PYTHON.md`](../docs/manuals/PYTHON.md)；CLI/Deployment 摘要见
[`docs/reference/API.md`](../docs/reference/API.md)。

EastMoney/Tiantian Fund crawler with one supported command entrypoint:

```bash
python cli.py <command>
```

The crawler uses bounded concurrency where configured, bounded retries and
random request pacing, and writes idempotently to MySQL.

## Directory layout

```text
fund_spider/
├── cli.py, jobs.py, db.py, settings.py   # entrypoint and application layer
├── spiders/                               # source-specific collectors/parsers
├── runtime/                               # Prefect business-task runner
├── bin/                                   # executable Shell scripts
├── prefect_flows.py, prefect.yaml         # flows, deployments, schedules
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
mysql -uroot -p < sql/20260731_add_fund_scoring.sql
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
./bin/run_nav_history.sh 519674 --start-date 20260701 --end-date 20260728
./bin/run_nav_performance.sh
./bin/run_feature.sh --fund-code 519674
./bin/run_holdings.sh --fund-code 519674
./bin/run_rating.sh
./bin/run_rating.sh --mode history --fund-code 519674
./bin/run_score.sh
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

`--fund_code` is accepted as an alias for `--fund-code`. The shell wrapper also
accepts the fund code as its first positional argument. Historical NAV enables
SQL and bound-parameter logging by default; use `--log-sql 0` to disable it.

The date bounds are independently optional:

- neither bound: all available history;
- only `--start-date`: from that date through latest;
- only `--end-date`: earliest through that date;
- both bounds: closed date interval.

Use `--fund-limit` and `--fund-offset` for a bounded batch. Rows are upserted
into `fund_nav_history` by `(fund_code, nav_date)`. Remaining pages are fetched
concurrently and multiple pages are committed together. Tune this with
`--nav-page-workers` (default `4`) and `--nav-write-batch-size` (default `200`);
set workers to `1` for sequential requests.

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

### Fund scoring

The 0–100 score compares a fund only with funds in the same detailed type
(groups with fewer than 30 funds fall back to the parent type). The default
weights total 100:

- returns 25%;
- standard deviation 15%;
- Sharpe ratio 25%;
- maximum drawdown 20%;
- agency ratings 10%;
- fund scale 5%.

Apply the scoring schema once on an existing database, then calculate current
scores:

```bash
mysql -uroot -p < sql/20260731_add_fund_scoring.sql
python cli.py score --mode current
```

For a real future-12-month profitability backtest, first populate historical
NAV/rating/scale data, then build monthly point-in-time snapshots. Historical
snapshots use only data available on each snapshot date and attach the return
observed 365 days later:

```bash
python cli.py nav-history --start-date 20180101
python cli.py rating --mode history
python cli.py score --mode history --start-date 20180101 --step-months 1
```

Weight backtests and recommendation requests are submitted from the Web scoring
configuration dialog. `python cli.py score --mode jobs` processes those queued
requests. A profile can be activated only after it passes three rolling
chronological folds with a 12-month train/test embargo. The seed profile is
deliberately marked `UNVERIFIED`; until a profile passes the configured AUC,
Brier score, and top-20% win-rate-lift gates, clients show no profitability
probability.

The scheduled pipeline labels newly matured snapshots, calculates current
scores, and processes queued work in that order:

```bash
./bin/run_score.sh --foreground
```

## Prefect task platform

Recurring execution is managed by the self-hosted `Prefect==3.7.7` Server and
Process Worker. The built-in dashboard at `http://127.0.0.1:4200/` provides
deployment management, pause/resume, manual runs, parameters, run history,
task states, logs, retries, cancellation, and work-pool health.
Prefect metadata is persisted in a dedicated PostgreSQL container configured
by `deploy/prefect/docker-compose.yml`; CRM business data remains in MySQL.

Version-controlled defaults live in `prefect.yaml`:

| Deployment | Default schedule |
| --- | --- |
| `morning-fund-refresh` | Daily 08:00; NAV/performance then a 2,000-fund stale-first feature batch |
| `evening-nav-performance` | Daily 21:00 |
| `feature-refresh-manual` | Manual only |
| `score-pipeline` | Daily 10:00 and 22:30 |
| `sina-news` | Every 120 seconds |
| `stock-cn` | Weekdays every 5 minutes during configured A-share windows |
| `stock-hk` | Weekdays every 5 minutes during configured HK windows |

All schedules use `Asia/Shanghai`. The Process Worker has four flow slots.
Stocks and news use the high-priority `realtime` queue; fund and scoring work
uses the `batch` queue. The task runner uses one cross-process lock per data
type, so a long feature refresh cannot block stock quotes.

Start and register the local platform:

```bash
docker compose -f ../deploy/prefect/docker-compose.yml up -d
./bin/run_prefect_server.sh
./bin/deploy_prefect.sh
./bin/run_prefect_worker.sh
```

The Server and Worker are long-lived commands and should be run in separate
terminals or installed as services. A deployment can also be triggered from
the CLI; `dry_run=true` validates command selection without writing business
data:

```bash
PREFECT_API_URL=http://127.0.0.1:4200/api \
.venv/bin/prefect deployment run \
  'fund-feature-refresh/feature-refresh-manual' \
  --param dry_run=true --watch
```

Edit schedules in the Prefect UI for immediate operational changes. Update
`prefect.yaml` and rerun `bin/deploy_prefect.sh` when the change must remain in
source control. CentOS service installation is documented in
`deploy/centos/README.md`; macOS uses the two
`deploy/macos/com.crm.prefect-*.plist` files.

## Portfolio screenshot OCR

`tools/portfolio_holding_ocr.py` supports Alipay and Tencent Licaitong holding
snapshots and trade-detail screenshots:

```bash
python tools/portfolio_holding_ocr.py \
  --source-label alipay \
  --import-type holding \
  screenshot.png

python tools/portfolio_holding_ocr.py \
  --source-label tencent \
  --import-type trade \
  screenshot.png
```

`source-label` accepts `alipay` or `tencent`; `import-type` accepts `holding` or
`trade`. The parser can be unit tested without importing RapidOCR because the
OCR runtime is loaded only by the command entry point:

```bash
PYTHONPYCACHEPREFIX=/tmp/crm-pycache \
PYTHONPATH=.. python -m unittest tools.test_portfolio_holding_ocr
```

For real-image OCR, install `requirements.txt` into a compatible virtual
environment and point backend configuration `CRM_PYTHON_EXECUTABLE` to that
environment's Python executable.

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
delayed market `128` and normalizes it to market code `116`.

Yangjibao credentials must be supplied through `YJB_AUTHORIZATION`,
`YJB_REQUEST_SIGN`, and `YJB_REQUEST_TIME`; they are not stored in source
control.

## Validation

```bash
python -m unittest discover -s tests -p 'test_*.py'
./bin/deploy_prefect.sh
.venv/bin/prefect deployment ls
```
