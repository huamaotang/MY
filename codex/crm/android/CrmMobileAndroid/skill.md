---
name: crm-android
description: Use when implementing, debugging, or validating the CRM native Android Java app, including ApiClient changes, Activity screens, SharedPreferences session storage, JSON parsing, and Gateway API integration.
---

# CRM Android Skill

## First Steps

1. Read `android/CrmMobileAndroid/agent.md`.
2. Inspect `ApiClient.java`, model classes, and the affected Activity.
3. Confirm backend path and response JSON.
4. Check whether testing uses emulator or real device.

## API Change Workflow

1. Add or update model parsing.
2. Add method in `ApiClient.java`.
3. Ensure request path omits `/api`; base URL contains `/api`.
4. Preserve headers: JSON, `X-Client-Source: android`, user agent, bearer token.
5. Use `ApiException` for failures.

## UI Change Workflow

1. Read session from `SessionStore`.
2. Build view state before network call.
3. Run network off the main thread.
4. Update UI via `runOnUiThread()`.
5. Show clear error messages.

## Validation

```bash
cd android/CrmMobileAndroid
gradle assembleDebug
```

Manual smoke test:

```text
Launch -> enter http://<LAN-IP>:8780/api or emulator http://10.0.2.2:8780/api -> login -> customer list -> customer detail
```

## Common Failure Signals

| Signal | Meaning |
| --- | --- |
| Cannot connect on real device | Used `127.0.0.1` instead of LAN IP |
| Cannot connect on emulator | Should use `10.0.2.2` for host |
| `403` | Token missing or expired |
| `429` | Gateway rate limit |
| JSON error | Parser and backend response mismatch |

## References

Read `docs/manuals/ANDROID.md` for detailed setup and troubleshooting.
