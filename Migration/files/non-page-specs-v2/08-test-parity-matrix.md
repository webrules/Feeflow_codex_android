# Test parity matrix technical design

## iOS anchors

- `FeedflowTests/FeedflowCoreTests.swift`
- `FeedflowTests/FeedflowExtendedTests.swift`
- `FeedflowTests/FeedflowFourD4YLoginTests.swift`
- `FeedflowTests/ZhihuSearchAndZoomTests.swift`
- Existing Android prototype tests under `android/core/data/src/test/...`

## Inventory finding

- Swift declarations found: 262 XCTest declarations.
- Distinct names: 257 because 5 names are duplicated across files.
- Existing Android `IosParityInventoryTest` tracks distinct names only; this must be replaced with class-qualified mapping so duplicates are not collapsed.
- No Android `src/androidTest` coverage currently exists.

## Required manifest format

Create a versioned parity manifest in Android test resources or source:

```yaml
- swiftFile: FeedflowTests/FeedflowFourD4YLoginTests.swift
  swiftClass: FeedflowFourD4YLoginTests
  swiftTest: test_validateSession_cloudflareChallenge_fails
  featureArea: auth-session
  androidTest: com.webrules.feedflow.auth.FourD4YSessionTest.cloudflareChallengeFails
  testType: jvm
  status: mapped
  notes: Uses static HTML fixture; no network.
```

Valid statuses:

- `mapped`
- `covered-by-contract`
- `android-specific-replacement`
- `live-opt-in`
- `obsolete-approved`

No XCTest can be removed without an entry and rationale.

## CI policy

1. Extract class-qualified XCTest declarations from `FeedflowTests/**/*.swift`.
2. Load Android parity manifest.
3. Fail if any XCTest is missing.
4. Fail if mapped Android test is ignored/disabled.
5. Allow `live-opt-in` tests only outside default CI.
6. Fail if fixture files referenced by manifest are missing.

## Android test layers

| Layer | Use |
|---|---|
| JVM unit | Models, parsers, mappers, cache keys, ViewModel state machines, fake DB/repositories. |
| Robolectric | Android resources, DataStore/SharedPreferences, Room integration when instrumentation is unnecessary. |
| Instrumentation | WebView cookies, Android `CookieManager`, Keystore/encrypted storage, app restart, Compose navigation on device. |
| Compose UI | Page flows, semantics, content descriptions, login-required routing. |
| Screenshot | Compare approved page-screenshot artifacts/design tokens. |
| Live opt-in | Gemini smoke test and optional real-source smoke tests. |

## Feature-area mapping

| Feature area | XCTest groups | Android test type |
|---|---|---|
| Forum site registry/order | `testAllCasesCount`, `testFromServiceIdMapsCorrectly`, `testMakeServiceReturnsCorrectType`, `testForumSiteOrder` | JVM |
| Community visibility | RSS always enabled, toggle visibility, visible sites | JVM |
| Service metadata/capabilities | service IDs, names, logos, default capabilities, web URLs | JVM |
| Auth config/cookies | OAuth option counts, auth cookie detection, domain filters | JVM + instrumentation for platform cookie store |
| Cookie serialization/expiry | header matching, 30-day upgrade, secure/httpOnly, clear/replace/isolation | JVM + instrumentation |
| Login/session validation | 4D4Y guest/logged-in/Cloudflare/logout HTML, restore decision tree | JVM fixtures |
| 4D4Y posting/parsing | SID/formhash/type ID/post ID/username/WAP detail/delete ownership | JVM fixtures |
| Logout persistence | DB + system cookie clearing across restart | instrumentation |
| Bookmarks | thread/URL bookmark toggle/list/idempotence | JVM |
| Settings/encryption/schema | settings CRUD, encrypted settings, plaintext migration, schema migration | JVM/Robolectric |
| Cache/summaries/filtered posts | cached topics/thread, summary TTL, filtered post lifecycle | JVM fixed clock |
| Thread/Forum ViewModels | refresh/cache/login/scroll/prefetch edge cases | JVM coroutine tests |
| RSS/Atom/OPML | parser existence, RSS2/Atom/empty/OPML fixtures | JVM |
| HTML/entity decoding | named/decimal/hex/mixed invalid entities, blockquote/link cleanup | JVM |
| Zhihu parsing | avatar URLs, author fallback, comments/search/zoom | JVM |
| 4D4Y avatar | UID extraction and avatar URL generation | JVM |
| Models | constructors/equality/optionals/toggle like | JVM |
| Encryption | AES roundtrip, invalid data, nonce variance | JVM |
| AI | Gemini no-key/no-crash; real call opt-in | JVM + live opt-in |
| Theme/localization/speech/network/navigation/image zoom | prefs, resources, TTS wrapper, network monitor, nav reset, clamp | Robolectric/JVM |
| Time formatting | threshold and invalid ISO behavior | JVM fixed clock |

## Fixture requirements

Add deterministic fixtures before implementation tests:

- `fixtures/4d4y/logged_in.html`
- `fixtures/4d4y/guest.html`
- `fixtures/4d4y/cloudflare.html`
- `fixtures/4d4y/logout_without_forum_links.html`
- `fixtures/4d4y/thread_list_gbk.html` as bytes.
- `fixtures/4d4y/thread_detail_desktop.html`
- `fixtures/4d4y/thread_detail_wap.html`
- `fixtures/linux/categories.json`
- `fixtures/linux/topic_list.json`
- `fixtures/linux/topic_detail.json`
- `fixtures/v2ex/topic_list.html`
- `fixtures/v2ex/topic_detail.html`
- `fixtures/v2ex/google_search.html`
- `fixtures/zhihu/recommend.json`
- `fixtures/zhihu/hot.json`
- `fixtures/zhihu/answer_detail.json`
- `fixtures/zhihu/comments.json`
- `fixtures/zhihu/search_dom.html`
- `fixtures/rss/rss2.xml`
- `fixtures/rss/atom.xml`
- `fixtures/rss/opml.xml`

## Minimum green bar before any UI implementation

1. Domain model tests pass.
2. DB schema/migration tests pass.
3. Encryption/cookie tests pass.
4. Parser fixtures pass for all read-only sources.
5. ViewModel state-machine tests pass with fake services.
6. Parity manifest covers all current iOS XCTest declarations.
