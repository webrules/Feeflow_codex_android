# Feedflow Android technical design specs v2

Status: draft for review. This v2 set supersedes the earlier high-level `non-page-specs/` drafts. It reverse-engineers the iOS implementation into reproducible Android design contracts: exact IDs, routes, DB schemas, cookie formats, parser rules, state transitions, cache keys, and test mappings.

## Source-of-truth inputs

- iOS app source under `Feedflow/`
- iOS XCTest coverage under `FeedflowTests/`
- Earlier page specs under session artifact `page-specs/`
- Screenshot/design artifacts under session artifact `page-screenshots/`

## Approval gate

No Android implementation should continue until these specs and the page specs are approved. Each Android implementation slice must cite:

1. Page/view spec, when UI is involved.
2. Relevant v2 technical design spec below.
3. iOS source anchor or fixture.
4. Android test parity mapping.

## Files

1. `01-domain-service-contract.md`
2. `02-persistence-security-auth.md`
3. `03-source-services-4d4y-linux.md`
4. `04-source-services-hn-v2ex-zhihu-rss.md`
5. `05-network-parsing-normalization.md`
6. `06-state-navigation-cache-prefetch.md`
7. `07-settings-theme-localization-ai-tts.md`
8. `08-test-parity-matrix.md`

## Android stack decisions to freeze for implementation

| Concern | Required Android shape |
|---|---|
| UI | Kotlin + Jetpack Compose + Material 3, with Feedflow tokens instead of generic defaults. |
| Navigation | Navigation Compose with stable route arguments and restoration. |
| State | ViewModels + coroutines + StateFlow. |
| Persistence | Room or SQLDelight over SQLite, preserving iOS table names/columns/keys. |
| Networking | OkHttp preferred because cookie/header control and interceptors are critical. |
| HTML/XML parsing | JSoup plus explicit regex fallbacks where iOS depends on brittle scraper behavior. |
| Secret storage | Android Keystore-backed AES-GCM. |
| Web login | Android WebView + CookieManager, with source-specific cookie validation. |
| Tests | JVM parser/store tests first; instrumentation only for Android platform APIs. |
