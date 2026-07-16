---
name: java-compile-services
description: Compile all Java backend services in this CRM repository through the Maven multi-module backend. Use when the user asks to one-click compile, build, or verify all Java services, especially requests like "一键编译java所有服务", "compile all Java services", "build backend modules", or "check all Maven services compile".
---

# Java Compile Services

## Overview

Use this skill to compile all Java backend services through the CRM repository's Maven parent project. Prefer the bundled script so the build entrypoint, flags, and result reporting stay consistent.

## Default Workflow

1. From the repository root, run:

```bash
python3 skills/java-compile-services/scripts/compile_java_services.py
```

2. Let the script locate `backend/pom.xml` and run the Maven reactor build for all modules.
3. Report whether the compile passed or failed. If it fails, summarize the first actionable error and the module where Maven stopped.

## Options

- Use `--with-tests` when the user explicitly asks to include tests.
- Use `--package` when the user asks for build artifacts or jars, not just compilation.
- Use `--install` only when downstream local Maven dependencies need the built artifacts installed.
- Use `--offline` only when dependencies are already cached or the user wants no network access.
- Use `--module <artifact-or-path>` only when the user asks for a subset; the default is always all modules.
- Use `--dry-run` to show the command without running it.

## CRM Repository Notes

The CRM workspace has a Maven parent at `backend/pom.xml` with these backend modules:

- `core`
- `gateway`
- `admin`
- `system`
- `customer`

The default command compiles the full reactor with `clean compile -DskipTests`. Do not use the Android Gradle project for this skill unless the user explicitly asks for Android.
