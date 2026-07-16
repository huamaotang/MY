---
name: crm-ios
description: Use when implementing, debugging, or validating the CRM native iOS SwiftUI app, including ApiClient changes, Codable models, login/session handling, Keychain token storage, and Gateway API integration.
---

# CRM iOS Skill

## First Steps

1. Read `ios/CrmMobile/agent.md`.
2. Inspect `ApiClient.swift` and `Models.swift`.
3. Confirm backend path and response shape.
4. Check whether the target is simulator or real device.

## API Change Workflow

1. Add or update Codable model in `Models.swift`.
2. Add async function in `ApiClient.swift`.
3. Ensure request path omits `/api`; base URL contains `/api`.
4. Preserve headers: JSON, `X-Client-Source: ios`, user agent, bearer token.
5. Update the SwiftUI view and session handling if needed.

## UI Change Workflow

1. Keep state local unless it is login/session state.
2. Use existing shared views where possible.
3. Show loading and error states.
4. Avoid blocking the main thread with network work.

## Validation

```bash
xcodebuild -project ios/CrmMobile/CrmMobile.xcodeproj \
  -scheme CrmMobile \
  -destination 'generic/platform=iOS' \
  build
```

Manual smoke test:

```text
Launch -> enter http://<LAN-IP>:8780/api -> login -> customer list -> customer detail
```

## Common Failure Signals

| Signal | Meaning |
| --- | --- |
| Cannot connect on device | Used `127.0.0.1` instead of Mac LAN IP |
| Decode error | `Codable` model does not match response |
| `403` | Token missing or expired |
| HTTP blocked | ATS or network policy issue |

## References

Read `docs/manuals/IOS.md` for detailed setup and troubleshooting.
