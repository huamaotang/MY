# EastMoney Fund Crawler Reference

## Local Project

Work in `fund_spider/`.

Important files:

- `cli.py`: the only supported command entrypoint
- `jobs.py`: reusable command implementations
- `spiders/profile_spider.py`: basic profile parser
- `spiders/nav_spider.py`: historical NAV parser
- `spiders/feature_spider.py`: feature data parser
- `db.py`: MySQL schema and upsert helpers
- `bin/`: executable manual and scheduled Shell scripts
- `runtime/`: APScheduler-backed CLI and Web scheduling services
- `tools/portfolio_holding_ocr.py`: portfolio screenshot OCR tool
- `sql/init.sql`: full schema bootstrap

## Database

Database: `fund`

Tables currently used:

- `fund_detail`: fund code, name, profile fields
- `fund_nav_history`: daily NAV history
- `fund_feature_data`: standard deviation and Sharpe ratio
- `fund_stock_holding`: stock holdings

Keep `db.py` and `sql/init.sql` synchronized whenever schema changes.

## Endpoints

Fund list:

```text
https://fund.eastmoney.com/Data/Fund_JJJZ_Data.aspx
```

The list response is JavaScript shaped like `var db={datas:[...]}`. `page=<page>,<page_size>` controls pagination.

Basic profile:

```text
https://fundf10.eastmoney.com/jjjz_<fund_code>.html
```

The profile block contains labels for:

- 成立日期
- 基金经理
- 类型
- 管理人
- 净资产规模
- 截止至

Historical NAV:

```text
https://api.fund.eastmoney.com/f10/lsjz?fundCode=<code>&pageIndex=<n>&pageSize=20&startDate=&endDate=
```

Observed behavior: this endpoint effectively returns up to 20 rows per page even if larger `pageSize` is requested. Use `NAV_START_DATE=YYYYMMDD` or `YYYY-MM-DD` for bounded crawls; code normalizes to API format and stores dates as `YYYYMMDD`.

Feature data:

```text
https://fundf10.eastmoney.com/tsdata_<fund_code>.html
```

Risk metrics are in table `class="fxtb"`:

- columns: `近1年`, `近2年`, `近3年`
- rows: `标准差`, `夏普比率`
- cutoff date in `div.limit-time`, text `截止至：YYYY-MM-DD`

Stock holdings:

```text
https://fundf10.eastmoney.com/ccmx_<fund_code>.html
https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=<code>&topline=10&year=&month=&rt=<random>
```

The holdings endpoint returns JavaScript `var apidata={ content:"<html table>", arryear:..., curyear:... }`.

The table columns are:

- 序号
- 股票代码
- 股票名称
- 最新价
- 涨跌幅
- 相关资讯
- 占净值比例
- 持股数（万股）
- 持仓市值（万元）

Report metadata appears in the HTML title text, for example `2026年1季度股票投资明细`, and cutoff date appears as `截止至：YYYY-MM-DD`.
Store the explicit cutoff date in `fund_stock_holding.cutoff_date`; valuation
uses the latest cutoff-date snapshot available on or before the quote date.

## Validation Pattern

Before batch work, validate one fund:

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py feature --fund-code 519674
```

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py nav-history --fund-code 519674 --start-date 20250101
```

For batch work:

```bash
DB_PASSWORD=qwer8989 fund_spider/.venv/bin/python fund_spider/cli.py feature --fund-limit 100 --fund-offset 0
```

Use `screen` for long-running full crawls only when the user explicitly asks for background execution.

## Implementation Rules

- Keep all dates stored as `YYYYMMDD` strings where existing tables do so.
- Parse percentages as decimals without `%`; for example `45.12%` becomes `45.12`.
- Keep raw Chinese labels in `period_label` when they are part of the source page, such as `近1年`.
- Avoid concurrent crawling unless explicitly requested.
- Do not bypass captcha or access controls.
- Prefer idempotent `INSERT ... ON DUPLICATE KEY UPDATE`.
