# CentOS APScheduler 任务调度部署

原来分散在 CentOS `crontab` 或 `systemd timer` 中的 CRM 数据任务，统一由
`APScheduler==3.11.3` 调度。自研时间轮询已经废弃；CentOS 只需要守护一个
常驻的 Python Web 服务，不再分别维护每项任务的执行时间。

调度控制接口默认只监听 `127.0.0.1:8088`，不要在没有认证和访问控制的
情况下直接暴露到公网。

## APScheduler 接管的任务

| Python 任务 | 默认时间/间隔 | 执行命令 |
| --- | --- | --- |
| 净值与业绩 | 每天 08:00、21:00 | `run_nav_performance.sh` |
| 特色数据 | 每天 08:00 | `run_feature.sh` |
| 基金评分 | 每天 22:30 | `run_score.sh` |
| 新浪资讯 | 每 120 秒 | `run_sina_news.sh` |
| A 股行情 | 交易日交易时段，每 300 秒 | `run_stock.sh cn` |
| 港股行情 | 交易日交易时段，每 300 秒 | `run_stock.sh hk` |

所有任务使用 `Asia/Shanghai` 时区。APScheduler 负责 Cron/Interval
触发、合并补跑和单实例限制；行情开闭市资格由任务回调判断，不依赖
CentOS Shell 脚本。

## 安装服务

以下命令假设项目目录为 `/opt/crm`、运行用户为 `crm`：

```bash
cd /opt/crm/fund_spider
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp -n .env.example .env

sudo cp /opt/crm/deploy/centos/crm-task-scheduler.service \
  /etc/systemd/system/crm-task-scheduler.service
sudo systemctl daemon-reload
sudo systemctl enable --now crm-task-scheduler
```

生产数据库连接、请求节流和调度覆盖项放到
`/etc/crm/fund-spider.env`，不要把密码写入 service 文件。常用调度配置：

```env
NAV_PERFORMANCE_SCHEDULE_TIMES=08:00,21:00
FEATURE_SCHEDULE_TIME=08:00
FEATURE_SCHEDULE_ENABLED=1
SCORE_SCHEDULE_TIME=22:30
SINA_NEWS_SCHEDULE_ENABLED=1
SINA_NEWS_INTERVAL_SECONDS=120
STOCK_MARKET_SCHEDULE_ENABLED=1
STOCK_MARKET_INTERVAL_SECONDS=300
```

## 验证与切换

先确认新服务及全部任务定义健康：

```bash
systemctl status crm-task-scheduler --no-pager
curl -fsS http://127.0.0.1:8088/api/status
curl -fsS -X POST http://127.0.0.1:8088/api/run \
  -H 'Content-Type: application/json' \
  -d '{"trigger":"all","dry_run":true}'
journalctl -u crm-task-scheduler -n 100 --no-pager
```

状态接口的 `engine` 应为 `APScheduler 3.11.3`，`scheduled_jobs` 应列出
实际 Trigger；`job_statuses` 应包含 `nav-performance`、`feature`、`score`、
`sina-news`、`stock-cn` 和 `stock-hk`。确认新服务已持续运行且实际任务日志
正常后，再删除或注释 CentOS 上对应的旧 crontab 项，并停用同名旧 timer。
切换前后不要让旧任务与 APScheduler 同时运行，否则会产生重复抓取；不要
删除数据库或日志作为迁移步骤。
