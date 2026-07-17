from __future__ import annotations

import os
import re
from pathlib import Path

from spider import RequestConfig


BASE_DIR = Path(__file__).resolve().parent


def load_env_file(path: Path) -> None:
    if not path.exists():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def load_project_env() -> None:
    load_env_file(BASE_DIR / ".env")


def request_config_from_env() -> RequestConfig:
    return RequestConfig(
        min_delay_seconds=float(os.getenv("REQUEST_MIN_DELAY_SECONDS", "1.5")),
        max_delay_seconds=float(os.getenv("REQUEST_MAX_DELAY_SECONDS", "4.0")),
        timeout_seconds=float(os.getenv("REQUEST_TIMEOUT_SECONDS", "10")),
        max_retries=int(os.getenv("REQUEST_MAX_RETRIES", "3")),
    )


def parse_bool(value: str | None) -> bool:
    if value is None:
        return False
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def parse_optional_int(value: str | None, name: str = "value", min_value: int = 1) -> int | None:
    if value is None or value.strip() == "":
        return None
    parsed = int(value)
    if parsed < min_value:
        raise ValueError(f"{name} must be greater than or equal to {min_value}")
    return parsed


def normalize_query_date(value: str | None) -> str:
    if value is None:
        return ""
    text = value.strip()
    if not text:
        return ""
    if re.fullmatch(r"\d{8}", text):
        return f"{text[:4]}-{text[4:6]}-{text[6:]}"
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", text):
        return text
    raise ValueError(f"invalid date format: {value}; expected YYYYMMDD or YYYY-MM-DD")


def apply_env_overrides(values: dict[str, str | None]) -> None:
    for key, value in values.items():
        if value is not None:
            os.environ[key] = value
