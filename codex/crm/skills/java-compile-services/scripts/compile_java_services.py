#!/usr/bin/env python3
"""Compile all Java backend services in a Maven multi-module repository."""

from __future__ import annotations

import argparse
import os
import shlex
import subprocess
import sys
from pathlib import Path


def find_repo_root(start: Path) -> Path:
    for candidate in [start, *start.parents]:
        if (candidate / "backend" / "pom.xml").is_file():
            return candidate
        if (candidate / "pom.xml").is_file():
            return candidate
    raise SystemExit(
        "Could not find a Maven project. Run from the repository or pass --repo."
    )


def choose_project_pom(repo: Path, backend_dir: str) -> Path:
    backend_pom = repo / backend_dir / "pom.xml"
    if backend_pom.is_file():
        return backend_pom
    root_pom = repo / "pom.xml"
    if root_pom.is_file():
        return root_pom
    raise SystemExit(f"Could not find {backend_pom} or {root_pom}.")


def choose_maven(repo: Path, project_pom: Path) -> str:
    candidates = [
        project_pom.parent / "mvnw",
        repo / "mvnw",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    return "mvn"


def build_command(args: argparse.Namespace) -> list[str]:
    repo = Path(args.repo).expanduser().resolve() if args.repo else find_repo_root(Path.cwd())
    project_pom = choose_project_pom(repo, args.backend_dir)
    maven = choose_maven(repo, project_pom)

    goals = ["clean", "compile"]
    if args.package:
        goals = ["clean", "package"]
    if args.install:
        goals = ["clean", "install"]

    command = [maven, "-f", str(project_pom), *goals]

    if not args.with_tests:
        command.append("-DskipTests")
    if args.offline:
        command.append("-o")
    if args.threads:
        command.extend(["-T", args.threads])
    if args.module:
        command.extend(["-pl", args.module, "-am"])

    return command


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compile all Java backend services using the Maven reactor."
    )
    parser.add_argument("--repo", help="Repository root. Defaults to searching upward.")
    parser.add_argument(
        "--backend-dir",
        default="backend",
        help="Directory containing the backend parent pom.xml. Defaults to backend.",
    )
    parser.add_argument(
        "--with-tests",
        action="store_true",
        help="Run tests instead of passing -DskipTests.",
    )
    parser.add_argument(
        "--package",
        action="store_true",
        help="Run clean package instead of clean compile.",
    )
    parser.add_argument(
        "--install",
        action="store_true",
        help="Run clean install instead of clean compile.",
    )
    parser.add_argument("--offline", action="store_true", help="Pass Maven -o.")
    parser.add_argument("--threads", help="Pass Maven -T, for example 1C or 4.")
    parser.add_argument(
        "--module",
        help="Build a specific module with -pl <module> -am. Default builds all modules.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the Maven command without running it.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.package and args.install:
        raise SystemExit("Choose only one of --package or --install.")

    command = build_command(args)
    printable = " ".join(shlex.quote(part) for part in command)
    print(f"Compile command: {printable}", flush=True)

    if args.dry_run:
        return 0

    env = os.environ.copy()
    env.setdefault("MAVEN_OPTS", "-Dfile.encoding=UTF-8")
    completed = subprocess.run(command, env=env)
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
