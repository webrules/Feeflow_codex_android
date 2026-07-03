<overview>
The user wants a native Android port of the iOS Feedflow app with eventual 100% UI, feature, resource, and test parity. The work pivoted from an initially rejected prototype to a spec-first parity process, then resumed implementation slice-by-slice without waiting for further approvals.
</overview>

<history>
1. Initial Android scaffold and deployment
   - Created an Android project under `android/` using Kotlin, Compose, Material 3, Navigation, modular `core:*` packages, Gradle wrapper/config, mock UI, and tests.
   - Deployed to Pixel 7a, but the user rejected the result as a poor prototype and asked to restart with planning/specs.

2. Spec-first reset
   - Created migration plan, page specs, non-page technical specs, screenshot/design artifacts, and a master plan in session artifacts under `files/`.
   - Active spec set includes `android-parity-master-plan.md`, `page-specs/`, `page-screenshots/`, `non-page-specs-v2/`, and `feedflow-android-active-specs.zip`.

3. Resource migration
   - Migrated iOS app icon assets, 95 English and 95 Chinese strings, Feedflow theme colors, dark colors, and semantic SF Symbol mappings into Android resources.
   - Updated manifest/styles to use migrated resources.

4. Foundation and parser implementation
   - Implemented domain models, service contracts, service capabilities, typed errors/results, SQLite schema constants, cache keys, cookie/auth helpers, RSS/OPML parsing, Hacker News parsing, and parser helpers for 4D4Y, V2EX, Zhihu, and Linux.do/Discourse.
   - Fixed parser-scope/bracing issues and added tests until `:core:data:test` passed.

5. Persistence/auth/UI implementation
   - Added iOS-compatible persistence foundations, shared codecs, Android SQLite-backed store, JSON/encrypted cookie semantics, cookie merge/replace behavior, cache clearing, RSS feeds persistence, and tests.
   - Added auth/session coordinator, WebView cookie capture foundations, logout/session restore behavior, and deterministic tests.
   - Rebuilt the UI shell from specs instead of the rejected prototype.

6. User asked why work stopped and demanded no stopping before 100% parity
   - Admitted stopping at progress-report boundaries was wrong for the user’s instruction.
   - Broke remaining parity work into concrete todos and continued implementation.

7. Continued parity slices
   - Added spec-shaped screens/routes for Login, Settings, Bookmarks, RSS feed manager, Community config, AI summary, Search results, Web Login sheet, In-app browser, Full-screen image viewer, New Thread, and main reading flow.
   - Added reusable UI rendering policies/components: Avatar, ThreadRow rules, CommentRow reply behavior, Feedflow tags, section headers, parsed content, linked text, image/quote markers, progress line.
   - Added posting/reply state, RSS manager state, AI/TTS/prefetch state, connected Compose UI smoke tests, class-qualified XCTest manifest, cache-first repository APIs, and started real HTTP client parity.

8. Validation and emulator work
   - Regularly ran Gradle validation: `:core:database:test`, `:core:data:test`, `:app:assembleDebug`.
   - Ran connected Compose tests on `Medium_Phone_API_36.1`; initially failed due AndroidX test/Espresso API 36 issue, then upgraded test deps and fixed a duplicate text assertion.
   - Connected UI tests passed.
   - Captured screenshots in session artifacts, including `android-feedflow-ui-tested-home.png`.
</history>

<work_done>
Repository changes:
- `.gitignore`
  - Added Android ignores: `.gradle/`, `local.properties`, captures/APK artifacts, etc.
- `android/`
  - New Android project remains untracked in git.
  - Modules: `:app`, `:core:model`, `:core:data`, `:core:database`, `:core:network`, `:core:security`, `:core:ui`.
  - Uses Kotlin 1.9.25, AGP 8.7.3, Compose BOM 2024.12.01, compile/target SDK 35.

Completed work:
- Android scaffold and Gradle setup.
- iOS resource migration to Android.
- Foundation model/service/database/network/security scaffolding.
- RSS/OPML/HN/4D4Y/V2EX/Zhihu/Linux.do parser helpers and tests.
- iOS-compatible DB schema constants and persistence contract.
- `InMemoryFeedflowStore` and `AndroidSqliteFeedflowStore`.
- JSON cookie serialization format matching iOS shape.
- Cookie merge/replace/expiry filtering and encrypted storage.
- Auth session coordinator and Android WebView cookie bridge.
- Parser-backed service wiring for V2EX, Linux.do, 4D4Y, Zhihu category defaults.
- Spec-driven Compose screens for many major pages.
- Reusable rendering policies and tests for avatars, thread rows, linked text, parsed content, URL bookmark time, etc.
- Posting/reply composer state and UI wiring.
- RSS feed manager state and UI wiring with manual add and OPML preview/import.
- AI summary cache/regenerate state, speech state, prefetch gate logic.
- Compose connected UI smoke tests.
- Class-qualified iOS XCTest manifest resource and test.
- Cache-first repository methods and tests.
- Began real URLConnection-based HTTP client.

Most recent work:
- Implemented `UrlConnectionFeedflowHttpClient` in `core:network`.
- Added `UrlConnectionFeedflowHttpClientTest`.
- First compile failed because `orEmpty()` was used on a nullable `ByteArray`; fixed it with `?: ByteArray(0)`.
- Compaction occurred before rerunning network tests after that fix.

Current state:
- Before the HTTP-client change, full validation was green:
  - `:core:database:test`
  - `:core:data:test`
  - `:app:assembleDebug`
  - `:app:connectedDebugAndroidTest`
- After the ByteArray fix, network tests still need to be rerun.
- App is substantially more complete than the rejected prototype but still not truly 100% feature/UI parity.
</work_done>

<technical_details>
- Worktree path: `/Users/zoujoe/Projects/copilot-worktrees/Feedflow/joey-zou-3stripes-fuzzy-chainsaw`.
- Repo: `webrules/Feedflow`.
- Android project path: `android/`.
- Branch rename to `feedflow-android-parity-port` was attempted; tool said it was already renamed/skipped.
- Do not operate in the main checkout; use this worktree path.

Important implementation facts:
- `ForumSite` order must match iOS: RSS, Hacker News, 4D4Y, V2EX, Linux.do, Zhihu.
- iOS DB-compatible tables:
  - `communities(id,name,description,category,activeToday,onlineNow,serviceId, PRIMARY KEY(id, serviceId))`
  - `settings(key PRIMARY KEY,value)`
  - `filtered_posts(postId PRIMARY KEY,serviceId,filteredAt)`
  - `ai_summaries(thread_id,service_id DEFAULT '',summary,created_at, PRIMARY KEY(thread_id,service_id))`
  - `cached_topics(cache_key PRIMARY KEY,data,timestamp)`
  - `cached_threads(thread_id,service_id DEFAULT '',data,timestamp, PRIMARY KEY(thread_id,service_id))`
  - `bookmarks(thread_id,service_id,data,timestamp, PRIMARY KEY(thread_id,service_id))`
  - `url_bookmarks(url PRIMARY KEY,title,timestamp)`
  - Android adds `rss_feeds`.
- Cookie key format: `login_<siteId>_cookies`.
- Gemini key setting: `gemini_api_key`.
- Cookie JSON format is encrypted setting value after plaintext JSON:
  - array of objects with `name`, `value`, `domain`, `path`, `secure`, `httpOnly`, optional `expires` in seconds.
- Auth config:
  - 4D4Y domain `4d4y.com`, auth fragments `auth`, `login`, `member`.
  - HN required cookie `user`.
  - V2EX auth fragment `a2`.
  - Linux.do fragments `_t`, `remember_user_token`.
  - Zhihu required cookie `z_c0`.
- Web login uses Android `CookieManager`, JavaScript, DOM storage, popup support settings, domain cookie extraction, and `AuthSessionCoordinator`.
- AndroidX test issue:
  - Espresso 3.6.1 failed on API 36 due `InputManager.getInstance` reflection.
  - Upgraded `androidxTestExt` to `1.3.0`, Espresso to `3.7.0`.
- Connected UI test duplicate text issue:
  - `4D4Y` appeared in both search selector and site grid; fixed test to query `onAllNodesWithText(label)[0]`.
- Emulator:
  - AVD available: `Medium_Phone_API_36.1`.
  - Connected tests passed on it.
- Device:
  - Earlier Pixel 7a serial was `34171JE0N10908`, but most recent work used emulator.
- Current todo table had many completed subtasks; the umbrella `phase5-ui` remains in progress because true 100% parity still requires more.
</technical_details>

<important_files>
- `android/app/src/main/kotlin/com/webrules/feedflow/ui/FeedflowApp.kt`
  - Main Compose app shell and route graph.
  - Contains screens for home/site list, communities, threads, detail, login, settings, bookmarks, RSS manager, community config, AI summary, search, WebLogin, browser, image viewer, new thread.
  - Recently wired parsed content, reply composer, RSS state, AI state, WebView cookie bridge.

- `android/app/src/main/kotlin/com/webrules/feedflow/auth/AndroidWebLoginCookieBridge.kt`
  - Android CookieManager bridge.
  - Configures WebView and extracts/captures cookies through `AuthSessionCoordinator`.

- `android/app/src/main/kotlin/com/webrules/feedflow/persistence/AndroidSqliteFeedflowStore.kt`
  - Android SQLite implementation of `FeedflowStore`.
  - Uses iOS-compatible schema statements and shared codecs.

- `android/app/src/androidTest/kotlin/com/webrules/feedflow/FeedflowUiParitySmokeTest.kt`
  - Connected Compose UI smoke tests.
  - Verifies home site rendering, toolbar utilities, login/settings/bookmarks routes, thread navigation, AI summary entry point.

- `android/core/model/src/main/kotlin/com/webrules/feedflow/core/model/FeedflowModels.kt`
  - Domain models and `ForumSite`.
  - `FeedThread` is data class for Swift-like value equality.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/ForumServices.kt`
  - Service interface and source implementations.
  - Parser-backed service wiring for RSS, HN, V2EX, Linux.do, 4D4Y, Zhihu basics.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowHelpers.kt`
  - Large helper/parsing file.
  - Contains RSS/OPML/HN/FourD4Y/V2EX/Zhihu/Discourse parsers.
  - Has grown large; should eventually be split by source.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/ContentRendering.kt`
  - Testable rendering policies for Avatar, ThreadRow, parsed content, linked text, image dedupe, relative time.
  - Drives Compose UI parity components.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/PostingState.kt`
  - New thread and reply composer state, formatting toolbar helpers.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/RssFeedManagerState.kt`
  - Manual add, edit/delete, OPML preview/import state.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/AiTtsPrefetchState.kt`
  - AI summary coordinator, speech playback state, prefetch gate logic.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowRepository.kt`
  - Mock helpers plus newly added cache-first APIs:
    - `loadCommunities`
    - `loadThreads`
    - `loadThreadDetail`
  - Uses `FeedflowStore` and `ForumService`.

- `android/core/database/src/main/kotlin/com/webrules/feedflow/core/database/FeedflowStore.kt`
  - Store interface, in-memory implementation, schema contract, cache keys, persistence codecs.
  - Includes cookie JSON encode/decode and RSS feed subscription model.

- `android/core/network/src/main/kotlin/com/webrules/feedflow/core/network/NetworkFoundation.kt`
  - Network model, cookie matcher, and most recent HTTP client implementation.
  - Important recent fix: replaced nullable ByteArray `.orEmpty()` with `?: ByteArray(0)`.

- `android/core/network/src/test/kotlin/com/webrules/feedflow/core/network/UrlConnectionFeedflowHttpClientTest.kt`
  - Newly added tests for HTTP client; needs rerun after the ByteArray fix.
  - Uses `com.sun.net.httpserver.HttpServer`.

- `android/core/data/src/test/resources/ios-xctest-manifest.txt`
  - Class-qualified XCTest manifest with 262 iOS tests.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/IosParityInventoryTest.kt`
  - Validates class-qualified XCTest manifest count, uniqueness, and required anchors.

- `android/gradle/libs.versions.toml`
  - Dependency versions.
  - Test deps currently include `androidxTestExt = 1.3.0`, `espresso = 3.7.0`.

- Session artifacts:
  - `files/android-parity-master-plan.md`
  - `files/page-specs/`
  - `files/non-page-specs-v2/`
  - `files/page-screenshots/`
  - screenshots such as `android-feedflow-ui-tested-home.png`.
</important_files>

<next_steps>
Immediate next action:
1. Rerun network tests after the ByteArray fix:
   - `cd android`
   - `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:network:test --no-daemon --quiet`
2. If network tests fail, fix `UrlConnectionFeedflowHttpClient` or the test fixtures.
3. Run full validation:
   - `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:network:test :core:database:test :core:data:test :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --quiet`
4. If full validation passes, mark `http-client-parity` done in SQL.
5. Continue toward actual 100% parity, likely next slices:
   - Wire `UrlConnectionFeedflowHttpClient` into app/service factory instead of default unimplemented clients where safe.
   - Add real ViewModel/state layers to move UI off `mockCommunities/mockThreads`.
   - Implement live opt-in source smoke tests separately from deterministic CI.
   - Complete WebView popup handling and real cookie retry counts.
   - Implement full image gesture behavior (zoom/pan/double-tap/rotate) beyond placeholder.
   - Add delete-thread UI and source action flows.
   - Add real OPML file picker.
   - Add screenshot comparisons against design artifacts, not just smoke tests.
   - Split `FeedflowHelpers.kt` by source for maintainability.
   - Continue closing `phase5-ui` only when actual feature/UI/test parity is achieved.
</next_steps>