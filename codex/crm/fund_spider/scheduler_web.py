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

from scheduler import LOG_DIR, next_run_at, parse_schedule_time, run_daily_jobs
from settings import load_env_file, parse_bool


BASE_DIR = Path(__file__).resolve().parent
CONFIG_FILE = BASE_DIR / "scheduler_config.json"
logger = logging.getLogger(__name__)


DEFAULT_CONFIG: dict[str, Any] = {
    "enabled": True,
    "schedule_time": "20:00",
    "dry_run": False,
    "daily_crawl_fund_list": True,
    "daily_crawl_profile_nav": True,
    "daily_crawl_profile": True,
    "daily_crawl_nav": True,
    "daily_crawl_feature": True,
    "daily_crawl_rating": True,
    "daily_crawl_holdings": True,
    "daily_nav_start_date": "",
    "daily_nav_end_date": "",
    "daily_nav_lookback_days": "10",
    "daily_use_cursor": True,
    "daily_cursor_date": "",
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
    config: dict[str, Any] = field(default_factory=dict)
    next_run_time: datetime | None = None
    running: bool = False
    last_run_started_at: str | None = None
    last_run_finished_at: str | None = None
    last_run_status: str = "never"
    last_error: str | None = None
    lock: threading.Lock = field(default_factory=threading.Lock)
    wake_event: threading.Event = field(default_factory=threading.Event)

    def snapshot(self) -> dict[str, Any]:
        with self.lock:
            return {
                "config": dict(self.config),
                "next_run_time": self.next_run_time.strftime("%Y-%m-%d %H:%M:%S") if self.next_run_time else None,
                "running": self.running,
                "last_run_started_at": self.last_run_started_at,
                "last_run_finished_at": self.last_run_finished_at,
                "last_run_status": self.last_run_status,
                "last_error": self.last_error,
                "logs": list_log_files(),
            }

    def update_config(self, updates: dict[str, Any]) -> dict[str, Any]:
        with self.lock:
            config = dict(self.config)
            config.update(sanitize_config(updates))
            parse_schedule_time(str(config["schedule_time"]))
            self.config = config
            save_config(config)
            self.wake_event.set()
            return dict(config)

    def start_job(self, dry_run: bool | None = None) -> tuple[bool, str]:
        with self.lock:
            if self.running:
                return False, "job is already running"
            self.running = True
            self.last_run_status = "running"
            self.last_error = None
            self.last_run_started_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            config = dict(self.config)

        thread = threading.Thread(target=self._run_job, args=(config, dry_run), daemon=True)
        thread.start()
        return True, "job started"

    def _run_job(self, config: dict[str, Any], dry_run: bool | None) -> None:
        status = "success"
        error = None
        try:
            succeeded = run_daily_jobs(
                dry_run=config["dry_run"] if dry_run is None else dry_run,
                extra_env=build_runtime_env(config),
            )
            if not succeeded:
                status = "failed"
                error = "one or more subprocess jobs failed; check the latest log file"
        except Exception:
            status = "failed"
            error = traceback.format_exc()
            logger.exception("scheduled fund job failed")

        with self.lock:
            self.running = False
            self.last_run_status = status
            self.last_error = error
            self.last_run_finished_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    load_env_file(BASE_DIR / ".env")
    LOG_DIR.mkdir(parents=True, exist_ok=True)

    state = SchedulerState(config=load_config())
    scheduler_thread = threading.Thread(target=scheduler_loop, args=(state,), daemon=True)
    scheduler_thread.start()

    host = os.getenv("SCHEDULER_WEB_HOST", "127.0.0.1")
    port = int(os.getenv("SCHEDULER_WEB_PORT", "8088"))
    server = ThreadingHTTPServer((host, port), make_handler(state))
    logger.info("scheduler web service started at http://%s:%s", host, port)
    server.serve_forever()


def scheduler_loop(state: SchedulerState) -> None:
    while True:
        with state.lock:
            config = dict(state.config)
            enabled = bool(config["enabled"])

        if not enabled:
            with state.lock:
                state.next_run_time = None
            state.wake_event.wait(60)
            state.wake_event.clear()
            continue

        schedule_time = parse_schedule_time(str(config["schedule_time"]))
        next_time = next_run_at(schedule_time)
        with state.lock:
            state.next_run_time = next_time

        timeout = max(0.0, (next_time - datetime.now()).total_seconds())
        woke = state.wake_event.wait(timeout)
        state.wake_event.clear()
        if woke:
            continue
        state.start_job()


def make_handler(state: SchedulerState):
    class SchedulerHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/":
                self.send_html(render_index(state.snapshot()))
                return
            if parsed.path == "/api/status":
                self.send_json(state.snapshot())
                return
            if parsed.path.startswith("/logs/"):
                self.send_log_file(parsed.path.removeprefix("/logs/"))
                return
            self.send_error(HTTPStatus.NOT_FOUND)

        def do_POST(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/api/config":
                updates = self.read_payload()
                try:
                    config = state.update_config(updates)
                    self.send_json({"ok": True, "config": config})
                except ValueError as exc:
                    self.send_json({"ok": False, "error": str(exc)}, HTTPStatus.BAD_REQUEST)
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
                dry_run = payload.get("dry_run")
                if dry_run is not None:
                    dry_run = parse_bool(str(dry_run))
                ok, message = state.start_job(dry_run=dry_run)
                status = HTTPStatus.OK if ok else HTTPStatus.CONFLICT
                self.send_json({"ok": ok, "message": message, "status": state.snapshot()}, status)
                return
            self.send_error(HTTPStatus.NOT_FOUND)

        def read_payload(self) -> dict[str, Any]:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0:
                return {}
            raw = self.rfile.read(length).decode("utf-8")
            content_type = self.headers.get("Content-Type", "")
            if "application/json" in content_type:
                return json.loads(raw or "{}")
            return {key: values[-1] for key, values in parse_qs(raw, keep_blank_values=True).items()}

        def send_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
            body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def send_html(self, html_text: str) -> None:
            body = html_text.encode("utf-8")
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def send_log_file(self, filename: str) -> None:
            safe_name = Path(filename).name
            path = LOG_DIR / safe_name
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
    parse_schedule_time(str(config["schedule_time"]))
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
        "daily_crawl_fund_list",
        "daily_crawl_profile_nav",
        "daily_crawl_profile",
        "daily_crawl_nav",
        "daily_crawl_feature",
        "daily_crawl_rating",
        "daily_crawl_holdings",
        "daily_use_cursor",
        "log_sql",
        "log_sql_params",
    }
    text_fields = set(DEFAULT_CONFIG) - bool_fields

    for field_name in bool_fields:
        if field_name in updates:
            result[field_name] = parse_bool(str(updates[field_name]))
    for field_name in text_fields:
        if field_name in updates:
            result[field_name] = str(updates[field_name]).strip()

    if "schedule_time" in result:
        parse_schedule_time(result["schedule_time"])
    if "daily_nav_lookback_days" in result:
        if int(result["daily_nav_lookback_days"]) < 0:
            raise ValueError("daily_nav_lookback_days must be greater than or equal to 0")
    return result


def env_config_overrides() -> dict[str, Any]:
    mapping = {
        "SCHEDULE_TIME": "schedule_time",
        "SCHEDULER_DRY_RUN": "dry_run",
        "DAILY_CRAWL_FUND_LIST": "daily_crawl_fund_list",
        "DAILY_CRAWL_PROFILE_NAV": "daily_crawl_profile_nav",
        "DAILY_CRAWL_PROFILE": "daily_crawl_profile",
        "DAILY_CRAWL_NAV": "daily_crawl_nav",
        "DAILY_CRAWL_FEATURE": "daily_crawl_feature",
        "DAILY_CRAWL_RATING": "daily_crawl_rating",
        "DAILY_CRAWL_HOLDINGS": "daily_crawl_holdings",
        "DAILY_NAV_START_DATE": "daily_nav_start_date",
        "DAILY_NAV_END_DATE": "daily_nav_end_date",
        "DAILY_NAV_LOOKBACK_DAYS": "daily_nav_lookback_days",
        "DAILY_USE_CURSOR": "daily_use_cursor",
        "DAILY_CURSOR_DATE": "daily_cursor_date",
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
        "DAILY_CRAWL_FUND_LIST": bool_env(config["daily_crawl_fund_list"]),
        "DAILY_CRAWL_PROFILE_NAV": bool_env(config["daily_crawl_profile_nav"]),
        "DAILY_CRAWL_PROFILE": bool_env(config["daily_crawl_profile"]),
        "DAILY_CRAWL_NAV": bool_env(config["daily_crawl_nav"]),
        "DAILY_CRAWL_FEATURE": bool_env(config["daily_crawl_feature"]),
        "DAILY_CRAWL_RATING": bool_env(config["daily_crawl_rating"]),
        "DAILY_CRAWL_HOLDINGS": bool_env(config["daily_crawl_holdings"]),
        "DAILY_NAV_START_DATE": str(config["daily_nav_start_date"]),
        "DAILY_NAV_END_DATE": str(config["daily_nav_end_date"]),
        "DAILY_NAV_LOOKBACK_DAYS": str(config["daily_nav_lookback_days"]),
        "DAILY_USE_CURSOR": bool_env(config["daily_use_cursor"]),
        "DAILY_CURSOR_DATE": str(config["daily_cursor_date"]),
        "FUND_LIMIT": str(config["fund_limit"]),
        "FUND_OFFSET": str(config["fund_offset"]),
        "REQUEST_MIN_DELAY_SECONDS": str(config["request_min_delay_seconds"]),
        "REQUEST_MAX_DELAY_SECONDS": str(config["request_max_delay_seconds"]),
        "REQUEST_TIMEOUT_SECONDS": str(config["request_timeout_seconds"]),
        "REQUEST_MAX_RETRIES": str(config["request_max_retries"]),
        "LOG_SQL": bool_env(config["log_sql"]),
        "LOG_SQL_PARAMS": bool_env(config["log_sql_params"]),
        "LOG_SQL_MAX_PARAMS": str(config["log_sql_max_params"]),
    }


def bool_env(value: Any) -> str:
    return "1" if bool(value) else "0"


def list_log_files() -> list[dict[str, Any]]:
    if not LOG_DIR.exists():
        return []
    rows = []
    for path in sorted(LOG_DIR.glob("daily_fund_job_*.log"), reverse=True)[:20]:
        rows.append(
            {
                "name": path.name,
                "size": path.stat().st_size,
                "modified_at": datetime.fromtimestamp(path.stat().st_mtime).strftime("%Y-%m-%d %H:%M:%S"),
            }
        )
    return rows


def render_index(snapshot: dict[str, Any]) -> str:
    config = snapshot["config"]
    logs = snapshot["logs"]
    log_items = "\n".join(
        f'<li><a href="/logs/{html.escape(row["name"])}" target="_blank">{html.escape(row["name"])}</a> '
        f'<span>{html.escape(row["modified_at"])}, {row["size"]} bytes</span></li>'
        for row in logs
    )
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>Fund Scheduler</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f5f7fb; color: #1f2937; }}
    main {{ max-width: 980px; margin: 32px auto; padding: 0 20px; }}
    section {{ background: #fff; border: 1px solid #dbe2ea; border-radius: 8px; padding: 20px; margin-bottom: 16px; }}
    h1 {{ font-size: 24px; margin: 0 0 16px; }}
    h2 {{ font-size: 18px; margin: 0 0 14px; }}
    .grid {{ display: grid; grid-template-columns: repeat(2, minmax(240px, 1fr)); gap: 14px 20px; }}
    label {{ display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #4b5563; }}
    input[type="text"], input[type="number"], input[type="time"] {{ padding: 9px 10px; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 14px; }}
    .checks {{ display: grid; grid-template-columns: repeat(2, minmax(220px, 1fr)); gap: 10px 18px; }}
    .checks label {{ flex-direction: row; align-items: center; color: #1f2937; }}
    button {{ border: 1px solid #2563eb; background: #2563eb; color: #fff; border-radius: 6px; padding: 9px 14px; cursor: pointer; }}
    button.secondary {{ background: #fff; color: #2563eb; }}
    button.danger {{ background: #b91c1c; border-color: #b91c1c; }}
    .actions {{ display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px; }}
    .status {{ display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 12px; }}
    .status div {{ background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px; }}
    .status strong {{ display: block; font-size: 12px; color: #64748b; margin-bottom: 4px; }}
    ul {{ padding-left: 20px; }}
    li span {{ color: #64748b; margin-left: 8px; font-size: 12px; }}
  </style>
</head>
<body>
<main>
  <h1>Fund Scheduler</h1>
  <section>
    <h2>状态</h2>
    <div class="status">
      <div><strong>启用</strong>{format_bool(config["enabled"])}</div>
      <div><strong>运行中</strong>{format_bool(snapshot["running"])}</div>
      <div><strong>下次执行</strong>{escape(snapshot["next_run_time"] or "-")}</div>
      <div><strong>上次开始</strong>{escape(snapshot["last_run_started_at"] or "-")}</div>
      <div><strong>上次结束</strong>{escape(snapshot["last_run_finished_at"] or "-")}</div>
      <div><strong>上次状态</strong>{escape(snapshot["last_run_status"])}</div>
    </div>
  </section>
  <section>
    <h2>配置</h2>
    <form id="configForm">
      <div class="grid">
        <label>执行时间 <input type="time" name="schedule_time" value="{escape(config["schedule_time"])}"></label>
        <label>净值回看天数 <input type="number" min="0" name="daily_nav_lookback_days" value="{escape(config["daily_nav_lookback_days"])}"></label>
        <label>净值开始日期 <input type="text" name="daily_nav_start_date" value="{escape(config["daily_nav_start_date"])}" placeholder="YYYYMMDD"></label>
        <label>净值结束日期 <input type="text" name="daily_nav_end_date" value="{escape(config["daily_nav_end_date"])}" placeholder="YYYYMMDD"></label>
        <label>游标日期 <input type="text" name="daily_cursor_date" value="{escape(config["daily_cursor_date"])}" placeholder="默认当天 YYYYMMDD"></label>
        <label>基金数量限制 <input type="text" name="fund_limit" value="{escape(config["fund_limit"])}"></label>
        <label>基金偏移量 <input type="number" min="0" name="fund_offset" value="{escape(config["fund_offset"])}"></label>
        <label>最小请求间隔秒 <input type="text" name="request_min_delay_seconds" value="{escape(config["request_min_delay_seconds"])}"></label>
        <label>最大请求间隔秒 <input type="text" name="request_max_delay_seconds" value="{escape(config["request_max_delay_seconds"])}"></label>
        <label>SQL参数样例数量 <input type="number" min="1" name="log_sql_max_params" value="{escape(config["log_sql_max_params"])}"></label>
      </div>
      <div class="checks" style="margin-top: 16px;">
        {checkbox("enabled", "启用调度", config["enabled"])}
        {checkbox("dry_run", "Dry-run", config["dry_run"])}
        {checkbox("daily_crawl_fund_list", "基金列表", config["daily_crawl_fund_list"])}
        {checkbox("daily_crawl_profile_nav", "基础信息和净值", config["daily_crawl_profile_nav"])}
        {checkbox("daily_crawl_profile", "基础信息", config["daily_crawl_profile"])}
        {checkbox("daily_crawl_nav", "每日净值", config["daily_crawl_nav"])}
        {checkbox("daily_crawl_feature", "特色数据", config["daily_crawl_feature"])}
        {checkbox("daily_crawl_rating", "基金评级", config["daily_crawl_rating"])}
        {checkbox("daily_crawl_holdings", "基金持仓", config["daily_crawl_holdings"])}
        {checkbox("daily_use_cursor", "启用游标续跑", config["daily_use_cursor"])}
        {checkbox("log_sql", "打印SQL", config["log_sql"])}
        {checkbox("log_sql_params", "打印SQL参数样例", config["log_sql_params"])}
      </div>
      <div class="actions">
        <button type="submit">保存配置</button>
        <button type="button" class="secondary" onclick="postAction('/api/run')">立即执行</button>
        <button type="button" class="secondary" onclick="postAction('/api/run', {{dry_run: true}})">Dry-run 一次</button>
        <button type="button" class="secondary" onclick="postAction('/api/start')">启用</button>
        <button type="button" class="danger" onclick="postAction('/api/stop')">停用</button>
      </div>
    </form>
  </section>
  <section>
    <h2>日志</h2>
    <ul>{log_items or "<li>暂无日志</li>"}</ul>
  </section>
</main>
<script>
  const form = document.getElementById('configForm');
  form.addEventListener('submit', async (event) => {{
    event.preventDefault();
    const data = new FormData(form);
    const payload = Object.fromEntries(data.entries());
    for (const name of form.querySelectorAll('input[type=checkbox]')) {{
      payload[name.name] = name.checked;
    }}
    const response = await fetch('/api/config', {{
      method: 'POST',
      headers: {{'Content-Type': 'application/json'}},
      body: JSON.stringify(payload)
    }});
    if (!response.ok) {{
      const body = await response.json();
      alert(body.error || '保存失败');
      return;
    }}
    location.reload();
  }});
  async function postAction(url, payload = {{}}) {{
    const response = await fetch(url, {{
      method: 'POST',
      headers: {{'Content-Type': 'application/json'}},
      body: JSON.stringify(payload)
    }});
    if (!response.ok) {{
      const body = await response.json();
      alert(body.message || body.error || '操作失败');
      return;
    }}
    location.reload();
  }}
</script>
</body>
</html>"""


def checkbox(name: str, label: str, checked: bool) -> str:
    checked_attr = " checked" if checked else ""
    return f'<label><input type="checkbox" name="{escape(name)}"{checked_attr}> {escape(label)}</label>'


def format_bool(value: Any) -> str:
    return "是" if value else "否"


def escape(value: Any) -> str:
    return html.escape(str(value), quote=True)


if __name__ == "__main__":
    main()
