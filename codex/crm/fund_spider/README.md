# Fund Spider

Python crawler for EastMoney fund data. The daily fund-list step reads every page of the open-fund ranking, uses a rolling one-year query window, and atomically writes fund configuration, current NAV, and performance history.

## Files

- `cli.py`: preferred unified command entrypoint.
- `jobs.py`: reusable crawl job functions.
- `settings.py`: shared env, argument, and request configuration helpers.
- `main.py`, `crawl_nav.py`, `crawl_all_funds.py`, `crawl_feature_data.py`, `crawl_holdings.py`: compatibility wrappers around `cli.py`.
- `scheduler.py`: lightweight Python daily job scheduler.
- `scheduler_web.py`: lightweight scheduler web service for configuring and triggering jobs.
- `spider.py`: browser-like request headers, random delay, retry, and response parsing.
- `nav_spider.py`: historical NAV page-by-page crawler for `fundf10.eastmoney.com/jjjz_*.html`.
- `db.py`: MySQL connection and upsert logic.
- `sql/init.sql`: creates the `fund` database and crawler tables.
- `.env.example`: local configuration template.

## Setup

```bash
cd fund_spider
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Edit `.env` with your MySQL credentials.

Initialize database manually if your MySQL account cannot create databases from the script:

```bash
mysql -uroot -p < sql/init.sql
```

Run all fund-list pages:

```bash
python cli.py fund-list
```

Run only the first 2 pages for testing:

```bash
python cli.py fund-list --max-pages 2
```

Run selected jobs through one entrypoint:

```bash
python cli.py all --jobs fund-list,profile-nav,feature,rating-list,holdings --nav-start-date 20260701
```

Run the daily incremental update in one pass:

```bash
python cli.py daily --fund-limit 100 --nav-start-date 20260701
```

Verify an Alipay holding screenshot from the command line:

```bash
python portfolio_holding_ocr.py --json --image ../screenshots/ali_001.jpg
```

Pass `--image` up to three times for scrolling screenshots. The Web, iOS, and
Android clients upload JPG/PNG images to `/portfolio/imports/ocr`, show the OCR
and fund-code match preview, and call `/portfolio/imports/{importId}/confirm`
only after user confirmation. The fund service can override the local runtime
with `CRM_PYTHON_EXECUTABLE` and `CRM_PYTHON_OCR_SCRIPT`.

Print insert/update SQL templates:

```bash
python cli.py daily --fund-code 519674 --log-sql 1
```

Print SQL parameter samples too:

```bash
python cli.py daily --fund-code 519674 --log-sql 1 --log-sql-params 1 --log-sql-max-params 3
```

## Rate Limiting

The crawler is intentionally single-threaded. Each request waits for a random delay controlled by:

```env
REQUEST_MIN_DELAY_SECONDS=1.5
REQUEST_MAX_DELAY_SECONDS=4.0
REQUEST_TIMEOUT_SECONDS=10
REQUEST_MAX_RETRIES=3
LOG_SQL=0
LOG_SQL_PARAMS=0
LOG_SQL_MAX_PARAMS=3
```

Pagination is controlled by:

```env
PAGE_SIZE=50
START_PAGE=1
MAX_PAGES=
```

When `MAX_PAGES` is empty, the crawler reads EastMoney's `allPages` field from the first response and crawls every page sequentially. One page is committed as one transaction across these tables:

- `fund_detail`: fund code, short name, and `can_buy`.
- `fund_nav_history`: NAV date, unit NAV, accumulated NAV, and daily growth rate.
- `fund_performance_history`: rolling-period returns, custom-period return, sale/fee fields, and the original source row.

The custom ranking window is calculated in `Asia/Shanghai` as task date minus one year through task date. Performance rows are idempotent on `(fund_code, nav_date)`, so weekend and retry runs update the same source-date snapshot. EastMoney sale statuses `1`, `2`, `3`, `8`, and `9` are treated as purchasable.

## Historical NAV

The page `https://fundf10.eastmoney.com/jjjz_519674.html` loads historical NAV by calling EastMoney's `f10/lsjz` JSON API. `crawl_nav.py` follows the same pagination parameters used by the page:

```env
NAV_FUND_CODE=519674
NAV_PAGE_SIZE=20
NAV_START_PAGE=1
NAV_MAX_PAGES=
NAV_START_DATE=
NAV_END_DATE=
```

Run all historical NAV pages for fund `519674`:

```bash
python cli.py nav --fund-code 519674
```

Run only 2 pages for testing:

```bash
python cli.py nav --fund-code 519674 --nav-max-pages 2
```

Rows are written to `fund_nav_history` with a unique key on `(fund_code, nav_date)`. `nav_date` is stored as `yyyyMMdd`, for example `20260715`.

## Feature Data

`crawl_feature_data.py` crawls `https://fundf10.eastmoney.com/tsdata_<fund_code>.html` and stores standard deviation and Sharpe ratio rows in `fund_feature_data`.

Run one fund:

```bash
python cli.py feature --fund-code 519674
```

Run a batch from `fund_detail`:

```bash
python cli.py feature --fund-limit 100 --fund-offset 0
```

## Ratings

`rating_spider.py` crawls the complete current rating list at `https://fund.eastmoney.com/data/fundrating.html` and stores the four institution ratings in `fund_rating`. The original per-fund API remains available for historical quarterly ratings.

Current complete rating list (also used by the daily update):

```bash
python cli.py rating-list
```

Historical ratings for a single fund:

Run one fund:

```bash
python cli.py rating --fund-code 519674
```

Run a batch from `fund_detail`:

```bash
python cli.py rating --fund-limit 100 --fund-offset 0
```

## Holdings

`crawl_holdings.py` crawls the holding data behind `https://fundf10.eastmoney.com/ccmx_<fund_code>.html` and stores rows in `fund_stock_holding`.

## Yangjibao news

`yangjibao_news_spider.py` crawls `https://app-api.yangjibao.com/news?score=2` and upserts records into `yangjibao_news` by the source `_id`.

Configure fresh request headers captured from the official app, then run:

```bash
export YJB_AUTHORIZATION='ios:...'
export YJB_REQUEST_SIGN='...'
export YJB_REQUEST_TIME='...'
python cli.py news --score 2
```

Set `DAILY_CRAWL_NEWS=1` to include the same idempotent fetch in the daily task. Credentials are intentionally not stored in source control.

Run one fund:

```bash
python cli.py holdings --fund-code 519674
```

Run a batch from `fund_detail`:

```bash
python cli.py holdings --fund-limit 100 --fund-offset 0
```

## Batch From `fund_detail`

`crawl_all_funds.py` reads existing fund codes from `fund_detail`, opens each corresponding detail page such as `https://fundf10.eastmoney.com/jjjz_519674.html`, saves profile fields back to `fund_detail`, and crawls paginated historical NAV rows.

Useful controls:

```env
FUND_CODE=
FUND_LIMIT=
FUND_OFFSET=0
CRAWL_PROFILE=1
CRAWL_NAV=1
NAV_MAX_PAGES=
```

Test one fund and one NAV page:

```bash
python cli.py profile-nav --fund-code 519674 --nav-max-pages 1
```

Continue from a later slice:

```bash
python cli.py profile-nav --fund-offset 100 --fund-limit 50
```

Use conservative values and comply with the target site's terms and robots/access policies.

## Python Daily Scheduler

`scheduler.py` is a lightweight local job runner similar to a single-machine xxl-job executor. It runs one combined daily job, writes one log file per trigger under `logs/`, and uses a lock file to avoid overlapping runs.

On macOS, install `deploy/macos/com.crm.sina-news.plist` as a LaunchAgent to let `launchd` fetch the newest Sina page every 120 seconds independently of the daily fund scheduler.

Default daily job:

```text
daily_update
```

`daily_update` optionally refreshes the complete ranking first, then scans selected `fund_detail` codes once and updates profile, recent NAV, feature data, ratings, and holdings for each fund in the same loop.

Funds are processed in ascending `fund_code` order from `fund_detail`.

Important scheduler configuration:

```env
SCHEDULE_TIME=20:00
DAILY_CRAWL_FUND_LIST=1
DAILY_CRAWL_PROFILE_NAV=1
DAILY_CRAWL_PROFILE=1
DAILY_CRAWL_NAV=1
DAILY_CRAWL_FEATURE=1
DAILY_CRAWL_RATING=1
DAILY_CRAWL_HOLDINGS=1
DAILY_NAV_REFRESH_DAYS=1
DAILY_PROFILE_REFRESH_DAYS=30
DAILY_FEATURE_REFRESH_DAYS=7
DAILY_RATING_REFRESH_DAYS=7
DAILY_HOLDING_REFRESH_DAYS=7
DAILY_RATING_MAX_PAGES=1
DAILY_NAV_LOOKBACK_DAYS=10
DAILY_NAV_MAX_PAGES=
DAILY_USE_CURSOR=1
DAILY_CURSOR_DATE=
```

When `DAILY_NAV_START_DATE` is empty, the scheduler sets the NAV start date to today minus `DAILY_NAV_LOOKBACK_DAYS`. If no NAV date window is configured, `daily` defaults to one NAV page per fund.

The refresh-day settings skip data that was updated recently. Set a value to `0` to force that data type on every run. `DAILY_NAV_REFRESH_DAYS` only skips the default recent-one-page NAV crawl; explicit NAV date windows are always crawled.

`DAILY_USE_CURSOR=1` stores progress in `fund_crawl_cursor` by `job_name + cursor_date`. Same-day reruns resume after the last successful `fund_code`. If a fund fails, the run stops at that fund so the cursor will not skip it. To force a same-day rerun from the beginning:

```bash
python cli.py daily --use-cursor 0
```

Dry-run once without touching the network or database:

```bash
python cli.py schedule --run-on-start 1 --once 1 --dry-run 1
```

Run once immediately:

```bash
python cli.py schedule --run-on-start 1 --once 1
```

Run as a long-lived daily scheduler:

```bash
python cli.py schedule
```

Run in the background with `screen`:

```bash
screen -S fund_scheduler
cd /Users/thm/MY/codex/crm/fund_spider
source .venv/bin/activate
python cli.py schedule
```

## Web Scheduler

`scheduler_web.py` starts a local web service for configuring the same daily job. It persists UI changes to `scheduler_config.json`.

Start the web service:

```bash
cd /Users/thm/MY/codex/crm/fund_spider
source .venv/bin/activate
python cli.py web
```

Open:

```text
http://127.0.0.1:8088/
```

Useful APIs:

```bash
curl http://127.0.0.1:8088/api/status
curl -X POST http://127.0.0.1:8088/api/run -H 'Content-Type: application/json' -d '{"dry_run": true}'
curl -X POST http://127.0.0.1:8088/api/stop
curl -X POST http://127.0.0.1:8088/api/start
```

Update schedule time through API:

```bash
curl -X POST http://127.0.0.1:8088/api/config \
  -H 'Content-Type: application/json' \
  -d '{"schedule_time":"20:00","enabled":true,"daily_nav_lookback_days":"10"}'
```
