from __future__ import annotations

import html
import json
import logging
import os
import threading
import traceback
from dataclasses import dataclass, field
from datetime import datetime
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

from apscheduler.executors.pool import ThreadPoolExecutor as APSchedulerThreadPool
from apscheduler.schedulers.background import BackgroundScheduler

from runtime.scheduler import (
    JOB_DEFAULTS,
    LOG_DIR,
    SCHEDULER_ENGINE,
    TIMEZONE,
    configure_apscheduler_jobs,
    jobs_for_manual_trigger,
    next_jobs_from_snapshot,
    parse_schedule_time,
    parse_schedule_times,
    run_scheduled_jobs,
    scheduled_jobs_snapshot,
)
from settings import load_env_file, parse_bool


BASE_DIR = Path(__file__).resolve().parents[1]
CONFIG_FILE = BASE_DIR / "config" / "scheduler_config.json"
logger = logging.getLogger(__name__)

DEFAULT_CONFIG: dict[str, Any] = {
    "enabled": True,
    "dry_run": False,
    "nav_performance_schedule_times": "08:00,21:00",
    "feature_schedule_time": "08:00",
    "feature_enabled": True,
    "score_schedule_time": "22:30",
    "sina_news_enabled": True,
    "sina_news_interval_seconds": "120",
    "stock_market_enabled": True,
    "stock_market_interval_seconds": "300",
    "fund_limit": "",
    "fund_offset": "0",
    "request_min_delay_seconds": "1.5",
    "request_max_delay_seconds": "4.0",
    "request_timeout_seconds": "10",
    "request_max_retries": "3",
    "log_sql": False,
    "log_sql_params": False,
    "log_sql_max_params": "3",
}


@dataclass
class SchedulerState:
    config: dict[str, Any]
    running: bool = False
    last_run_started_at: str | None = None
    last_run_finished_at: str | None = None
    last_run_status: str = "never"
    last_error: str | None = None
    job_statuses: dict[str, str] = field(
        default_factory=lambda: {
            "nav-performance": "never",
            "feature": "never",
            "score": "never",
            "sina-news": "never",
            "stock-cn": "never",
            "stock-hk": "never",
        }
    )
    lock: threading.Lock = field(default_factory=threading.Lock)
    apscheduler: BackgroundScheduler | None = field(default=None, repr=False)

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            snapshot = {
                "engine": SCHEDULER_ENGINE,
                "config": dict(self.config),
                "running": self.running,
                "last_run_started_at": self.last_run_started_at,
                "last_run_finished_at": self.last_run_finished_at,
                "last_run_status": self.last_run_status,
                "last_error": self.last_error,
                "job_statuses": dict(self.job_statuses),
                "logs": list_log_files(),
            }
            apscheduler = self.apscheduler
        scheduled_jobs = scheduled_jobs_snapshot(apscheduler)
        next_run_time, next_jobs = next_jobs_from_snapshot(scheduled_jobs)
        snapshot.update(
            {
                "next_run_time": next_run_time,
                "next_jobs": next_jobs,
                "scheduled_jobs": scheduled_jobs,
            }
        )
        return snapshot

    def attach_scheduler(self, apscheduler: BackgroundScheduler) -> None:
        with self.lock:
            self.apscheduler = apscheduler

    def update_config(self, updates: dict[str, Any]) -> dict[str, Any]:
        with self.lock:
            config = dict(self.config)
            config.update(sanitize_config(updates))
            validate_config(config)
            self.config = config
            save_config(config)
            apscheduler = self.apscheduler
        if apscheduler is not None:
            configure_apscheduler_jobs(apscheduler, config, self.run_job_sync)
        return dict(config)

    def start_job(
        self,
        job_names: tuple[str, ...],
        dry_run: bool | None = None,
    ) -> tuple[bool, str]:
        config = self._claim_job(job_names)
        if config is None:
            return False, "job is already running"
        thread = threading.Thread(
            target=self._run_job,
            args=(job_names, config, dry_run),
            daemon=True,
        )
        thread.start()
        return True, "job started"

    def run_job_sync(self, job_names: tuple[str, ...]) -> bool:
        config = self._claim_job(job_names)
        if config is None:
            logger.warning(
                "APScheduler job skipped because another job is running: %s",
                ",".join(job_names),
            )
            return False
        self._run_job(job_names, config, None)
        with self.lock:
            succeeded = self.last_run_status == "success"
            error = self.last_error
        if not succeeded:
            raise RuntimeError(error or f"scheduled job failed: {','.join(job_names)}")
        return True

    def _claim_job(self, job_names: tuple[str, ...]) -> dict[str, Any] | None:
        with self.lock:
            if self.running:
                return None
            self.running = True
            self.last_run_status = "running"
            self.last_error = None
            self.last_run_started_at = datetime.now(TIMEZONE).isoformat()
            for name in job_names:
                self.job_statuses[name] = "running"
            return dict(self.config)

    def _run_job(
        self,
        job_names: tuple[str, ...],
        config: dict[str, Any],
        dry_run: bool | None,
    ) -> None:
        status = "success"
        error = None

        def update_job_status(name: str, value: str) -> None:
            with self.lock:
                self.job_statuses[name] = value

        try:
            succeeded = run_scheduled_jobs(
                job_names,
                dry_run=config["dry_run"] if dry_run is None else dry_run,
                extra_env=build_runtime_env(config),
                status_callback=update_job_status,
            )
            if not succeeded:
                status = "failed"
                error = "one or more jobs failed or an overlapping run was skipped"
                with self.lock:
                    for name in job_names:
                        if self.job_statuses.get(name) == "running":
                            self.job_statuses[name] = "skipped_overlap"
        except Exception:
            status = "failed"
            error = traceback.format_exc()
            logger.exception("scheduled fund job failed")
            with self.lock:
                for name in job_names:
                    if self.job_statuses.get(name) == "running":
                        self.job_statuses[name] = "failed"
        with self.lock:
            self.running = False
            self.last_run_status = status
            self.last_error = error
            self.last_run_finished_at = datetime.now(TIMEZONE).isoformat()


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    load_env_file(BASE_DIR / ".env")
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    state = SchedulerState(config=load_config())
    apscheduler = BackgroundScheduler(
        timezone=TIMEZONE,
        executors={"default": APSchedulerThreadPool(max_workers=1)},
        job_defaults=JOB_DEFAULTS,
        daemon=True,
    )
    state.attach_scheduler(apscheduler)
    configure_apscheduler_jobs(apscheduler, state.config, state.run_job_sync)
    apscheduler.start()
    host = os.getenv("SCHEDULER_WEB_HOST", "127.0.0.1")
    port = int(os.getenv("SCHEDULER_WEB_PORT", "8088"))
    server = ThreadingHTTPServer((host, port), make_handler(state))
    logger.info(
        "%s web service started at http://%s:%s",
        SCHEDULER_ENGINE,
        host,
        port,
    )
    try:
        server.serve_forever()
    finally:
        apscheduler.shutdown(wait=False)


def make_handler(state: SchedulerState):
    class SchedulerHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/":
                self.send_html(render_index(state.snapshot()))
            elif parsed.path == "/api/status":
                self.send_json(state.snapshot())
            elif parsed.path.startswith("/logs/"):
                self.send_log_file(parsed.path.removeprefix("/logs/"))
            else:
                self.send_error(HTTPStatus.NOT_FOUND)

        def do_POST(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/api/config":
                try:
                    config = state.update_config(self.read_payload())
                    self.send_json({"ok": True, "config": config})
                except ValueError as exc:
                    self.send_json(
                        {"ok": False, "error": str(exc)},
                        HTTPStatus.BAD_REQUEST,
                    )
                return
            if parsed.path == "/api/start":
                state.update_config({"enabled": True})
                self.send_json({"ok": True, "status": state.snapshot()})
                return
            if parsed.path == "/api/stop":
                state.update_config({"enabled": False})
                self.send_json({"ok": True, "status": state.snapshot()})
                return
            if parsed.path == "/api/run":
                payload = self.read_payload()
                trigger_name = str(payload.get("trigger", "all"))
                try:
                    jobs = jobs_for_manual_trigger(trigger_name)
                except ValueError as exc:
                    self.send_json(
                        {"ok": False, "error": str(exc)},
                        HTTPStatus.BAD_REQUEST,
                    )
                    return
                dry_run = payload.get("dry_run")
                if dry_run is not None:
                    dry_run = parse_bool(str(dry_run))
                ok, message = state.start_job(jobs, dry_run=dry_run)
                self.send_json(
                    {
                        "ok": ok,
                        "message": message,
                        "status": state.snapshot(),
                    },
                    HTTPStatus.OK if ok else HTTPStatus.CONFLICT,
                )
                return
            self.send_error(HTTPStatus.NOT_FOUND)

        def read_payload(self) -> dict[str, Any]:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0:
                return {}
            raw = self.rfile.read(length).decode("utf-8")
            if "application/json" in self.headers.get("Content-Type", ""):
                return json.loads(raw or "{}")
            return {
                key: values[-1]
                for key, values in parse_qs(raw, keep_blank_values=True).items()
            }

        def send_json(
            self,
            payload: dict[str, Any],
            status: HTTPStatus = HTTPStatus.OK,
        ) -> None:
            body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def send_html(self, text: str) -> None:
            body = text.encode("utf-8")
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def send_log_file(self, filename: str) -> None:
            path = LOG_DIR / Path(filename).name
            if not path.exists() or path.suffix != ".log":
                self.send_error(HTTPStatus.NOT_FOUND)
                return
            body = path.read_bytes()
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format: str, *args) -> None:
            logger.info("%s - %s", self.address_string(), format % args)

    return SchedulerHandler


def load_config() -> dict[str, Any]:
    config = dict(DEFAULT_CONFIG)
    if CONFIG_FILE.exists():
        data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            config.update(sanitize_config(data))
    config.update(env_config_overrides())
    validate_config(config)
    return config


def save_config(config: dict[str, Any]) -> None:
    CONFIG_FILE.write_text(
        json.dumps(config, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def sanitize_config(updates: dict[str, Any]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    bool_fields = {
        "enabled",
        "dry_run",
        "feature_enabled",
        "sina_news_enabled",
        "stock_market_enabled",
        "log_sql",
        "log_sql_params",
    }
    for field_name in bool_fields:
        if field_name in updates:
            result[field_name] = parse_bool(str(updates[field_name]))
    for field_name in set(DEFAULT_CONFIG) - bool_fields:
        if field_name in updates:
            result[field_name] = str(updates[field_name]).strip()
    return result


def validate_config(config: dict[str, Any]) -> None:
    parse_schedule_times(config["nav_performance_schedule_times"])
    parse_schedule_time(config["feature_schedule_time"])
    parse_schedule_time(config["score_schedule_time"])
    for field_name in ("sina_news_interval_seconds", "stock_market_interval_seconds"):
        if int(config[field_name]) < 1:
            raise ValueError(f"{field_name} must be greater than or equal to 1")
    if config["fund_limit"] and int(config["fund_limit"]) < 1:
        raise ValueError("fund_limit must be greater than or equal to 1")
    if int(config["fund_offset"]) < 0:
        raise ValueError("fund_offset must be greater than or equal to 0")


def env_config_overrides() -> dict[str, Any]:
    mapping = {
        "NAV_PERFORMANCE_SCHEDULE_TIMES": "nav_performance_schedule_times",
        "FEATURE_SCHEDULE_TIME": "feature_schedule_time",
        "FEATURE_SCHEDULE_ENABLED": "feature_enabled",
        "SCORE_SCHEDULE_TIME": "score_schedule_time",
        "SINA_NEWS_SCHEDULE_ENABLED": "sina_news_enabled",
        "SINA_NEWS_INTERVAL_SECONDS": "sina_news_interval_seconds",
        "STOCK_MARKET_SCHEDULE_ENABLED": "stock_market_enabled",
        "STOCK_MARKET_INTERVAL_SECONDS": "stock_market_interval_seconds",
        "SCHEDULER_DRY_RUN": "dry_run",
        "FUND_LIMIT": "fund_limit",
        "FUND_OFFSET": "fund_offset",
        "REQUEST_MIN_DELAY_SECONDS": "request_min_delay_seconds",
        "REQUEST_MAX_DELAY_SECONDS": "request_max_delay_seconds",
        "REQUEST_TIMEOUT_SECONDS": "request_timeout_seconds",
        "REQUEST_MAX_RETRIES": "request_max_retries",
        "LOG_SQL": "log_sql",
        "LOG_SQL_PARAMS": "log_sql_params",
        "LOG_SQL_MAX_PARAMS": "log_sql_max_params",
    }
    updates: dict[str, Any] = {}
    for env_name, config_name in mapping.items():
        value = os.getenv(env_name)
        if value is None:
            continue
        if isinstance(DEFAULT_CONFIG[config_name], bool):
            updates[config_name] = parse_bool(value)
        else:
            updates[config_name] = value
    return updates


def build_runtime_env(config: dict[str, Any]) -> dict[str, str]:
    return {
        "FUND_LIMIT": str(config["fund_limit"]),
        "FUND_OFFSET": str(config["fund_offset"]),
        "REQUEST_MIN_DELAY_SECONDS": str(config["request_min_delay_seconds"]),
        "REQUEST_MAX_DELAY_SECONDS": str(config["request_max_delay_seconds"]),
        "REQUEST_TIMEOUT_SECONDS": str(config["request_timeout_seconds"]),
        "REQUEST_MAX_RETRIES": str(config["request_max_retries"]),
        "LOG_SQL": "1" if config["log_sql"] else "0",
        "LOG_SQL_PARAMS": "1" if config["log_sql_params"] else "0",
        "LOG_SQL_MAX_PARAMS": str(config["log_sql_max_params"]),
    }


def list_log_files() -> list[dict[str, Any]]:
    if not LOG_DIR.exists():
        return []
    return [
        {
            "name": path.name,
            "size": path.stat().st_size,
            "modified_at": datetime.fromtimestamp(
                path.stat().st_mtime,
                tz=TIMEZONE,
            ).isoformat(),
        }
        for path in sorted(LOG_DIR.glob("fund_scheduler_*.log"), reverse=True)[:20]
    ]


def render_index(snapshot: dict[str, Any]) -> str:
    config = snapshot["config"]
    logs = snapshot["logs"]
    job_statuses = snapshot["job_statuses"]
    log_items = "\n".join(
        f'<li><a href="/logs/{html.escape(row["name"])}" target="_blank">'
        f'{html.escape(row["name"])}</a> <span>{html.escape(row["modified_at"])}, '
        f'{row["size"]} bytes</span></li>'
        for row in logs
    )
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>CRM APScheduler</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f5f7fb; color: #1f2937; }}
    main {{ max-width: 900px; margin: 32px auto; padding: 0 20px; }}
    section {{ background: #fff; border: 1px solid #dbe2ea; border-radius: 8px; padding: 20px; margin-bottom: 16px; }}
    h1 {{ font-size: 24px; }} h2 {{ font-size: 18px; }}
    .grid, .status {{ display: grid; grid-template-columns: repeat(2, minmax(240px, 1fr)); gap: 14px 20px; }}
    label {{ display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #4b5563; }}
    input {{ padding: 9px 10px; border: 1px solid #cbd5e1; border-radius: 6px; }}
    .checks label {{ flex-direction: row; margin: 10px 0; }}
    button {{ border: 1px solid #2563eb; background: #2563eb; color: #fff; border-radius: 6px; padding: 9px 14px; cursor: pointer; margin: 8px 6px 0 0; }}
    button.secondary {{ background: #fff; color: #2563eb; }}
    .status div {{ background: #f8fafc; padding: 10px; border-radius: 6px; }}
    .status strong {{ display: block; color: #64748b; font-size: 12px; }}
  </style>
</head>
<body><main>
  <h1>CRM APScheduler 任务调度</h1>
  <section><h2>状态</h2><div class="status">
    <div><strong>调度引擎</strong>{escape(snapshot["engine"])}</div>
    <div><strong>时区</strong>Asia/Shanghai</div>
    <div><strong>下次执行</strong>{escape(snapshot["next_run_time"] or "-")}</div>
    <div><strong>下次任务</strong>{escape(",".join(snapshot["next_jobs"]) or "-")}</div>
    <div><strong>总体状态</strong>{escape(snapshot["last_run_status"])}</div>
    <div><strong>净值/业绩</strong>{escape(job_statuses["nav-performance"])}</div>
    <div><strong>特色数据</strong>{escape(job_statuses["feature"])}</div>
    <div><strong>基金评分</strong>{escape(job_statuses["score"])}</div>
    <div><strong>新浪资讯</strong>{escape(job_statuses["sina-news"])}</div>
    <div><strong>A股行情</strong>{escape(job_statuses["stock-cn"])}</div>
    <div><strong>港股行情</strong>{escape(job_statuses["stock-hk"])}</div>
  </div></section>
  <section><h2>配置</h2><form id="configForm">
    <div class="grid">
      <label>净值/业绩执行时间（逗号分隔）<input name="nav_performance_schedule_times" value="{escape(config["nav_performance_schedule_times"])}"></label>
      <label>特色数据执行时间<input type="time" name="feature_schedule_time" value="{escape(config["feature_schedule_time"])}"></label>
      <label>基金评分执行时间<input type="time" name="score_schedule_time" value="{escape(config["score_schedule_time"])}"></label>
      <label>新浪资讯间隔秒<input type="number" min="1" name="sina_news_interval_seconds" value="{escape(config["sina_news_interval_seconds"])}"></label>
      <label>股票行情间隔秒<input type="number" min="1" name="stock_market_interval_seconds" value="{escape(config["stock_market_interval_seconds"])}"></label>
      <label>基金数量限制<input name="fund_limit" value="{escape(config["fund_limit"])}"></label>
      <label>基金偏移量<input type="number" min="0" name="fund_offset" value="{escape(config["fund_offset"])}"></label>
      <label>最小请求间隔秒<input name="request_min_delay_seconds" value="{escape(config["request_min_delay_seconds"])}"></label>
      <label>最大请求间隔秒<input name="request_max_delay_seconds" value="{escape(config["request_max_delay_seconds"])}"></label>
    </div>
    <div class="checks">
      {checkbox("enabled", "启用调度", config["enabled"])}
      {checkbox("feature_enabled", "启用特色数据日更", config["feature_enabled"])}
      {checkbox("sina_news_enabled", "启用新浪资讯任务", config["sina_news_enabled"])}
      {checkbox("stock_market_enabled", "启用交易时段行情任务", config["stock_market_enabled"])}
      {checkbox("dry_run", "Dry-run", config["dry_run"])}
      {checkbox("log_sql", "打印 SQL", config["log_sql"])}
      {checkbox("log_sql_params", "打印 SQL 参数", config["log_sql_params"])}
    </div>
    <button type="submit">保存配置</button>
    <button type="button" class="secondary" onclick="runJob('morning')">立即执行早间任务</button>
    <button type="button" class="secondary" onclick="runJob('evening')">立即执行晚间任务</button>
    <button type="button" class="secondary" onclick="runJob('news')">立即执行新浪资讯</button>
    <button type="button" class="secondary" onclick="runJob('stock')">立即执行全部行情</button>
  </form></section>
  <section><h2>日志</h2><ul>{log_items or "<li>暂无日志</li>"}</ul></section>
</main>
<script>
const form = document.getElementById('configForm');
form.addEventListener('submit', async event => {{
  event.preventDefault();
  const payload = Object.fromEntries(new FormData(form).entries());
  for (const input of form.querySelectorAll('input[type=checkbox]')) payload[input.name] = input.checked;
  const response = await fetch('/api/config', {{method:'POST', headers:{{'Content-Type':'application/json'}}, body:JSON.stringify(payload)}});
  if (!response.ok) {{ const body = await response.json(); alert(body.error || '保存失败'); return; }}
  location.reload();
}});
async function runJob(trigger) {{
  const response = await fetch('/api/run', {{method:'POST', headers:{{'Content-Type':'application/json'}}, body:JSON.stringify({{trigger}})}});
  if (!response.ok) {{ const body = await response.json(); alert(body.error || body.message || '执行失败'); return; }}
  location.reload();
}}
</script></body></html>"""


def checkbox(name: str, label: str, checked: bool) -> str:
    checked_attr = " checked" if checked else ""
    return (
        f'<label><input type="checkbox" name="{html.escape(name)}"'
        f'{checked_attr}> {html.escape(label)}</label>'
    )


def escape(value: Any) -> str:
    return html.escape(str(value))


if __name__ == "__main__":
    main()
