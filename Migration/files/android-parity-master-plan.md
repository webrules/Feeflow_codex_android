# Feedflow Android parity master plan

Status: active working plan. This is the streamlined spec entry point for the Android port. The older high-level non-page drafts were removed from the active artifact set; use only the files listed below.

## Active spec set

| Area | Required artifact |
|---|---|
| Master plan | `android-parity-master-plan.md` |
| UI visual design | `page-screenshots/` |
| Per-page functional behavior | `page-specs/` |
| Technical/non-page behavior | `non-page-specs-v2/` |

## Parity rule

The Android app must be treated as a reimplementation of the iOS app, not a generic Feedflow-inspired app. Every feature slice must trace to:

1. iOS source file/function or XCTest.
2. Page spec when UI is involved.
3. Screenshot/design artifact when UI is involved.
4. v2 technical spec when models, services, persistence, auth, networking, state, AI, TTS, or tests are involved.
5. Android unit/UI/screenshot test proving the behavior.

## Implementation order

### Phase 0: replace prototype assumptions

- Remove or quarantine mock-data assumptions from core layers.
- Keep the Android project compileable at all times.
- Make domain/service/database contracts match iOS before improving UI.

### Phase 1: parity foundation

Scope:

- Domain models and service interfaces.
- Source registry/capability table.
- Error model.
- Cookie/security primitives.
- SQLite schema contract and migration tests.
- XCTest parity manifest structure.

Exit criteria:

- JVM tests prove models, service defaults, source capabilities, cookie matching, encryption, schema constants, and cache key rules.
- No UI claims real source support before service behavior exists.

### Phase 2: parser and read-only data slices

Implement deterministic fixture-backed parsers before live networking:

1. RSS/OPML.
2. Hacker News JSON.
3. Linux.do JSON mapping.
4. V2EX HTML parsing.
5. Zhihu JSON/search normalization.
6. 4D4Y GB18030/Discuz parsing.

Exit criteria:

- Fixture tests pass for each parser.
- Repository can render cached/offline data from parsed domain objects.

### Phase 3: persistence and offline behavior

- Room/SQLDelight database with iOS-compatible tables.
- Communities, cached topics, cached threads, bookmarks, URL bookmarks, summaries, filtered posts, settings, cookies, RSS feeds.
- Cache-first ViewModel state machines.
- Offline/stale indicators.

Exit criteria:

- DB migration tests pass.
- Offline thread list/detail/bookmark flows work from cached data.

### Phase 4: auth/session/web login

- Android WebView login sheet.
- Cookie capture, filtering, 30-day upgrade, encrypted persistence, restore, validation, logout.
- 4D4Y native credential login where parity requires it.
- Linux.do CSRF/session validation.
- Zhihu `z_c0` validation.

Exit criteria:

- Instrumentation tests prove CookieManager and encrypted storage behavior.
- Login-required flows return to the originating screen.

### Phase 5: UI parity shell

Rebuild UI from page specs and screenshots, not from the rejected prototype:

1. Site list/home.
2. Communities.
3. Thread list.
4. Thread detail.
5. Login/Web login.
6. Settings/bookmarks.
7. RSS manager/import.
8. Browser/image viewer.
9. AI summaries.

Exit criteria:

- Compose UI tests and screenshots match approved artifacts within an explicit tolerance.
- Page state/error/loading/empty behavior matches specs.

### Phase 6: source completion and interactive features

- 4D4Y replies/new threads/delete own thread.
- Linux.do replies/new topics.
- V2EX replies.
- Zhihu recommendations/search/not-interested filtering.
- Gemini summaries.
- TTS/accessibility.
- Background prefetch.

Exit criteria:

- XCTest parity manifest is fully mapped.
- Default CI is green.
- Live opt-in smoke tests are documented and separated from deterministic tests.

## Current first implementation slice

Start with Phase 1 foundation:

- Rename domain types to avoid collision while preserving iOS semantics.
- Add typed `ForumService` result/error models.
- Add service capability registry matching v2 spec.
- Add iOS-compatible schema constants.
- Add cache-key helpers for topic/detail/summary keys.
- Add tests for source order/capabilities/defaults/schema/cache keys.

No new page UI should be considered final until Phase 5.
