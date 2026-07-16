# iOS Agent Guide

## Role

You are working on the native CRM iPhone app. Keep the implementation aligned with the existing SwiftUI structure and Gateway API contract.

## Project Shape

| File | Purpose |
| --- | --- |
| `CrmMobileApp.swift` | App entry |
| `SessionStore.swift` | login state |
| `KeychainStore.swift` | token persistence |
| `ApiClient.swift` | API calls |
| `Models.swift` | Codable models |
| `LoginView.swift` | login screen |
| `CustomerListView.swift` | customer list |
| `CustomerDetailView.swift` | customer detail |

## Rules

- Base URL entered by users should include `/api`.
- Do not hardcode a developer machine IP in source.
- Keep `X-Client-Source: ios`.
- Store tokens through the existing Keychain flow.
- Network APIs should remain async/await.
- Make model fields match backend JSON exactly enough for `Codable`.

## Validation

Preferred:

```bash
xcodebuild -project ios/CrmMobile/CrmMobile.xcodeproj \
  -scheme CrmMobile \
  -destination 'generic/platform=iOS' \
  build
```

If full Xcode is unavailable, say so and validate by code inspection.

## References

- `docs/manuals/IOS.md`
- `ios/CrmMobile/README.md`
