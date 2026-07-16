# Android Agent Guide

## Role

You are working on the native CRM Android app. Keep changes compatible with the existing Java, Activity, SharedPreferences, and HttpURLConnection structure.

## Project Shape

| File | Purpose |
| --- | --- |
| `LoginActivity.java` | login screen |
| `CustomerListActivity.java` | customer list |
| `CustomerDetailActivity.java` | customer detail |
| `ApiClient.java` | API calls |
| `SessionStore.java` | base URL, token, username persistence |
| `Customer.java` | customer model |
| `Ui.java` | UI helpers |
| `AndroidManifest.xml` | app/activity config |

## Rules

- Base URL entered by users should include `/api`.
- Do not hardcode a developer machine IP.
- Keep `X-Client-Source: android`.
- Do not run network requests on the main thread.
- Keep JSON parsing explicit and defensive.
- Use existing `ApiException` for user-facing request failures.

## Commands

```bash
cd android/CrmMobileAndroid
gradle assembleDebug
```

## Validation

Preferred validation is Android Studio run on emulator or device.

CLI checks:

```bash
adb devices
gradle assembleDebug
```

## References

- `docs/manuals/ANDROID.md`
- `android/CrmMobileAndroid/README.md`
