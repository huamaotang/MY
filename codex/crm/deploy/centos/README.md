# CentOS Prefect 任务管理平台

CRM 的基金、评分、资讯和行情任务统一注册为 Prefect 3 Deployment。Prefect
Server 提供任务控制台和 API，Process Worker 负责启动实际 Python Flow。

控制台默认只监听 `127.0.0.1:4200`。生产环境建议通过 SSH 隧道、VPN 或带
认证的 Nginx 访问，不要直接把无认证的控制台暴露到公网。

## 已注册任务

| Deployment | 默认时间/间隔 | 实际任务 |
| --- | --- | --- |
| `morning-fund-refresh` | 每天 08:00 | 净值/业绩完成后刷新最久未更新的特征批次 |
| `evening-nav-performance` | 每天 21:00 | 净值与阶段收益 |
| `feature-refresh-manual` | 仅手动 | 特征数据 |
| `score-pipeline` | 每天 10:00、22:30 | 历史标签、评分计算、评分队列 |
| `sina-news` | 每 120 秒 | 新浪财经资讯 |
| `stock-cn` | A 股交易窗口每 5 分钟 | A 股行情 |
| `stock-hk` | 港股交易窗口每 5 分钟 | 港股行情 |

所有 Cron 使用 `Asia/Shanghai` 时区。Worker 默认提供 4 个槽位，实时行情/资讯
使用高优先级队列，批处理使用低优先级队列；业务执行器按数据类型加锁。

## 安装

以下命令假设项目位于 `/opt/crm`，运行用户和组均为 `crm`：

```bash
cd /opt/crm
docker compose -f deploy/prefect/docker-compose.yml up -d

cd /opt/crm/fund_spider
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp -n .env.example .env
sudo chown -R crm:crm /opt/crm/fund_spider

sudo cp /opt/crm/deploy/centos/crm-prefect-server.service \
  /etc/systemd/system/crm-prefect-server.service
sudo cp /opt/crm/deploy/centos/crm-prefect-worker.service \
  /etc/systemd/system/crm-prefect-worker.service
sudo systemctl daemon-reload
sudo systemctl enable --now crm-prefect-server
```

Server 健康后注册工作池、Flow 和 Deployment：

```bash
cd /opt/crm/fund_spider
sudo -u crm PREFECT_API_URL=http://127.0.0.1:4200/api \
  ./bin/deploy_prefect.sh
sudo systemctl enable --now crm-prefect-worker
```

数据库密码、请求节流等业务配置继续放在
`/etc/crm/fund-spider.env`。Prefect 常用覆盖项如下：

```env
PREFECT_API_URL=http://127.0.0.1:4200/api
PREFECT_SERVER_HOST=127.0.0.1
PREFECT_SERVER_PORT=4200
PREFECT_UI_API_URL=http://127.0.0.1:4200/api
PREFECT_SERVER_DATABASE_CONNECTION_URL=postgresql+asyncpg://prefect:URL_ENCODED_PASSWORD@127.0.0.1:5433/prefect
PREFECT_WORK_POOL=crm-process-pool
PREFECT_WORKER_NAME=crm-centos-worker
PREFECT_WORKER_LIMIT=4
FEATURE_SCHEDULE_FUND_LIMIT=2000
CRM_LOG_ROOT=/var/log/crm
CRM_LOG_RETENTION_DAYS=14
```

持久化 Deployment 和运行历史存储在 PostgreSQL 命名卷
`crm-prefect-postgres-data`。生产环境必须修改 Compose 默认密码，并确保
连接 URL 中的密码已进行 URL 编码。业务调度的版本化默认值位于
`fund_spider/prefect.yaml`。

## 验证

```bash
curl -fsS http://127.0.0.1:4200/api/health
cd /opt/crm/fund_spider
PREFECT_API_URL=http://127.0.0.1:4200/api \
  .venv/bin/prefect deployment ls
PREFECT_API_URL=http://127.0.0.1:4200/api \
  .venv/bin/prefect work-pool inspect crm-process-pool
PREFECT_API_URL=http://127.0.0.1:4200/api \
  .venv/bin/prefect deployment run \
  'fund-feature-refresh/feature-refresh-manual' \
  --param dry_run=true --watch
systemctl status crm-prefect-server crm-prefect-worker --no-pager
journalctl -u crm-prefect-worker -n 100 --no-pager
```

本地电脑可用 SSH 隧道打开控制台：

```bash
ssh -L 4200:127.0.0.1:4200 user@centos-host
```

随后访问 `http://127.0.0.1:4200/`。控制台中可以暂停/恢复调度、修改参数、
手动执行、取消运行，并查看 Flow/Task 状态和完整日志。
