<overview>
The user wants a native Android port of the iOS Feedflow app inside the existing `webrules/Feedflow` repo, with eventual 100% UI, feature, resource, and test parity. The work shifted from an initial rough scaffold to a spec-first migration plan, then into iterative implementation slices with continuous validation on Gradle tests and emulator connected tests.
</overview>

<history>
1. Initial Android port request
   - Created a native Android project under `android/` using Kotlin, Jetpack Compose, Material 3, modular `core:*` packages, and Gradle.
   - Initial prototype was deployed/tested but the user rejected it as poor quality and requested a restart from specs.

2. Spec-first reset
   - Drafted migration/master plans, page specs, non-page technical specs, and iOS-derived screenshot/design artifacts in session artifacts.
   - User later approved continuing implementation without waiting for every page-spec approval.

3. Resource and foundation work
   - Migrated iOS-like app icons, colors, strings, theme, semantic symbol mappings, and launcher assets.
   - Implemented Kotlin domain models, service interfaces, source capabilities, persistence contracts, cache keys, cookie/auth helpers, RSS/OPML/HN/4D4Y/V2EX/Zhihu/Linux.do parser foundations, and tests.

4. Persistence, auth, and UI implementation
   - Added SQLite-backed Android store matching iOS schema concepts, encrypted cookie/settings semantics, RSS feed persistence, bookmarks, cached topics/threads, summaries, and filtered posts.
   - Added WebView cookie capture/auth session foundations and Compose screens for home, communities, threads, detail, login, settings, bookmarks, RSS manager, community config, AI summary, search, web login, browser, image viewer, and new thread.
   - Connected UI smoke tests were added and made to pass on emulator.

5. User demanded continuation to 100% parity and called out icon mismatch
   - Replaced generic Android letter site icons with iOS SF-symbol-like Material mappings for RSS/HN/4D4Y/V2EX/Linux.do/Zhihu.
   - Changed toolbar icon treatment to better match iOS: home toolbar uses plain symbols in a rounded card, screen toolbars use circular symbol buttons.
   - Converted Android home search from chip row to compact iOS-like source selector menu with default Zhihu and searchable sites 4D4Y/V2EX/Linux.do/Zhihu.
   - Captured updated Android home screenshots in session artifacts.

6. Continued parity slices
   - Fixed Android string resource locale reversal: default `values/strings.xml` is now English and `values-zh/strings.xml` Chinese.
   - Replaced language globe icon with iOS-style `EN`/`中` text toggle.
   - Made posting/reply flows service-backed instead of always faking success; unsupported services now surface errors.
   - Added real OPML file picker, real image viewer surface, HN search via Algolia, RSS service using stored subscriptions, Cross-Site AI Top 10 route, Daily RSS Summary route, Linux.do detail parsing, V2EX detail parsing, and basic Zhihu hot/search URL extraction.
   - Full Gradle validation was repeatedly run and was green after the latest complete validation.
</history>

<work_done>
Files updated:
- `.gitignore`
  - Added Android ignores such as `.gradle/`, `local.properties`, build/capture/APK artifacts.
- `android/`
  - New Android project remains untracked in git.
  - Modules include `:app`, `:core:model`, `:core:data`, `:core:database`, `:core:network`, `:core:security`, `:core:ui`.

Major completed work:
- Android scaffold with Kotlin/Compose/Material 3 and modular Gradle setup.
- iOS resources migrated to Android resource folders.
- Corrected English/Chinese Android string resource folder swap.
- iOS-like Feedflow icon mapping and toolbar/site icon parity improvements.
- Cache-first repository/app state for communities, thread lists, thread details, and search.
- Real URLConnection HTTP client wired into repository/service factory.
- SQLite-backed store for settings, cookies, bookmarks, summaries, cached data, filtered posts, and RSS feeds.
- Store-backed settings, theme, language, login cookie status, bookmarks, RSS subscriptions.
- OPML picker and RSS import preview/import state.
- HN live search via Algolia parser.
- Linux.do categories/topics/detail parsing.
- V2EX categories/thread list/detail/reply parsing.
- Basic Zhihu hot/search URL extraction.
- Cross-Site AI Top 10 screen modeled after iOS.
- Daily RSS Summary screen modeled after iOS states.
- Service-backed new-thread and reply actions.
- Real WebView-backed image viewer surface with zoom/pan/double-tap/rotate state.
- Connected Compose smoke tests updated for stable content descriptions and new routes.

Most recent validation:
- Full suite passed after major parity slices:
  - `:core:network:test`
  - `:core:database:test`
  - `:core:data:test`
  - `:app:assembleDebug`
  - `:app:connectedDebugAndroidTest`

Most recent active work:
- Continuing source-feature parity after adding V2EX and Zhihu improvements.
- Was about to wire 4D4Y `fetchThreadDetail` through its existing parser; parser exists but service still does not override detail fetch.

Current state:
- Android app is usable and substantially improved from the rejected prototype.
- It is still not genuinely 100% UI/feature parity.
- Remaining gaps include deeper source-specific authenticated posting/reply/delete flows, full Gemini API calls, real TTS playback, screenshot diff assertions, full localization coverage, complete XCTest mapping, and source-by-source live validation.
</work_done>

<technical_details>
- Workspace path: `/Users/zoujoe/Projects/copilot-worktrees/Feedflow/joey-zou-3stripes-fuzzy-chainsaw`
- Repo: `webrules/Feedflow`
- Android project path: `android/`
- Branch rename to `feedflow-android-parity-port` was already done/skipped by tool.
- Do not operate in the main checkout.

Android stack:
- Kotlin 1.9.25, AGP 8.7.3, Compose BOM 2024.12.01, compile/target SDK 35.
- Modules: `app`, `core:model`, `core:data`, `core:database`, `core:network`, `core:security`, `core:ui`.

Important iOS parity facts:
- `ForumSite` order must be RSS, Hacker News, 4D4Y, V2EX, Linux.do, Zhihu.
- iOS source logos:
  - RSS: `dot.radiowaves.left.and.right`
  - HN: `flame.fill`
  - V2EX: `point.3.connected.trianglepath.dotted`
  - Linux.do: `terminal.fill`
  - 4D4Y: `4.circle.fill`
  - Zhihu: `questionmark.bubble.fill`
- iOS home search searchable sites are only 4D4Y, V2EX, Linux.do, Zhihu; default is Zhihu.
- iOS home toolbar uses plain symbols/text inside a rounded card; screen toolbars use circular symbol buttons.
- iOS language toggle displays `EN` or `中`, not a globe.

Persistence/schema details:
- SQLite-compatible tables include:
  - `communities(id,name,description,category,activeToday,onlineNow,serviceId, PRIMARY KEY(id, serviceId))`
  - `settings(key PRIMARY KEY,value)`
  - `filtered_posts(postId PRIMARY KEY,serviceId,filteredAt)`
  - `ai_summaries(thread_id,service_id DEFAULT '',summary,created_at, PRIMARY KEY(thread_id,service_id))`
  - `cached_topics(cache_key PRIMARY KEY,data,timestamp)`
  - `cached_threads(thread_id,service_id DEFAULT '',data,timestamp, PRIMARY KEY(thread_id,service_id))`
  - `bookmarks(thread_id,service_id,data,timestamp, PRIMARY KEY(thread_id,service_id))`
  - `url_bookmarks(url PRIMARY KEY,title,timestamp)`
  - Android also adds `rss_feeds`.
- Cookie key format: `login_<siteId>_cookies`.
- Gemini API key setting: `gemini_api_key`.
- Background prefetch setting key currently `background_prefetch`.
- Theme/language setting keys currently `dark_theme`, `language`.

Auth details:
- 4D4Y domain `4d4y.com`, auth fragments `auth`, `login`, `member`.
- HN required cookie `user`.
- V2EX auth fragment `a2`.
- Linux.do fragments `_t`, `remember_user_token`.
- Zhihu required cookie `z_c0`.
- Web login uses Android `CookieManager`, JavaScript, DOM storage, popup settings, and `AuthSessionCoordinator`.

Validation quirks:
- Espresso 3.6.1 failed on API 36 due `InputManager.getInstance`; upgraded AndroidX test ext to 1.3.0 and Espresso to 3.7.0.
- Connected UI tests should use stable content descriptions like `Site Hacker News`, `Community row`, `Thread row` instead of dynamic/duplicated text.
- Adding HN to search selector caused duplicate “Hacker News”; fixed by reverting search selector to iOS source list and using site-card hooks.
- String resource files were accidentally reversed earlier; now default is English and `values-zh` is Chinese.

Open assumptions/uncertainties:
- Cross-Site AI and Daily RSS Summary currently generate local placeholder summaries when API key exists/doesn’t exist; real Gemini HTTP integration still missing.
- Zhihu hot/search implementation extracts URLs and creates generic titles, not full Zhihu API/feed parity.
- Source-specific authenticated create/reply/delete implementations are still mostly unsupported or incomplete.
</technical_details>

<important_files>
- `android/app/src/main/kotlin/com/webrules/feedflow/ui/FeedflowApp.kt`
  - Main Compose UI shell and route graph.
  - Contains home/site list, communities, thread list, detail, login, settings, bookmarks, RSS manager, Daily RSS Summary, Cross-Site AI, community config, AI summary, search, WebLogin, browser, image viewer, new thread.
  - Recently changed for iOS-like icons, compact search selector, language text toggle, Cross-Site AI, Daily RSS Summary, service-backed posting/reply, real image viewer.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/ForumServices.kt`
  - Service interface and per-source implementations.
  - Recently changed to wire `UrlConnectionFeedflowHttpClient`, stored RSS feeds, HN search, Linux.do detail, V2EX detail, and Zhihu hot/search URL extraction.
  - Next likely edit: add `FourD4YService.fetchThreadDetail` using existing `parseThreadDetailHtml`.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowHelpers.kt`
  - Large parser/helper file.
  - Contains RSS/OPML/HN/V2EX/Zhihu/Discourse/4D4Y helper logic, content rendering helpers, HTML/JSON unescape helpers.
  - Recently added `jsonUnescape`, V2EX topic title/content/author parsing.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowRepository.kt`
  - Cache-first repository and service access layer.
  - Recently added `searchThreads`, `createThread`, and `postComment`.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowAppState.kt`
  - App state controller for cache-first loading and persisted UI state integration.
  - Exposes communities/threads/detail/search/createThread/postComment.

- `android/core/database/src/main/kotlin/com/webrules/feedflow/core/database/FeedflowStore.kt`
  - Store contract, in-memory implementation, schema constants, codecs.
  - Defines RSS feeds, bookmarks, summaries, settings, cookies, cache behavior.

- `android/app/src/main/kotlin/com/webrules/feedflow/persistence/AndroidSqliteFeedflowStore.kt`
  - Android SQLite implementation of `FeedflowStore`.
  - Handles encrypted settings/cookies, bookmarks, RSS feeds, cached topics/threads, summaries, filtered posts.

- `android/core/network/src/main/kotlin/com/webrules/feedflow/core/network/NetworkFoundation.kt`
  - `FeedflowHttpClient`, `UrlConnectionFeedflowHttpClient`, cookie header matching.
  - Real HTTP client has tests and is wired by default.

- `android/app/src/androidTest/kotlin/com/webrules/feedflow/FeedflowUiParitySmokeTest.kt`
  - Connected Compose smoke tests.
  - Updated for stable site/community/thread semantics, home AI Cross-Site route, iOS language text.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/SourceServiceWiringTest.kt`
  - Deterministic parser/service wiring tests for V2EX, Linux.do, 4D4Y, Zhihu.
  - Recently extended for Linux.do detail, V2EX detail, Zhihu hot/search.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/FeedflowAppStateControllerTest.kt`
  - Tests app state, cache-first fallback, search, posting delegation, stored RSS subscriptions.

- `android/app/src/main/res/values/strings.xml`
  - Default English strings after correction.
- `android/app/src/main/res/values-zh/strings.xml`
  - Chinese strings after correction.

- iOS reference files viewed:
  - `Feedflow/Theme/FeedflowIcons.swift`
  - `Feedflow/Views/SiteListView.swift`
  - `Feedflow/Views/ThreadListView.swift`
  - `Feedflow/Views/NewThreadView.swift`
  - `Feedflow/ViewModels/NewThreadViewModel.swift`
  - `Feedflow/Views/CrossSiteAISummaryView.swift`
  - `Feedflow/Views/DailyRSSSummaryView.swift`
  - `Feedflow/Services/GeminiService.swift`
</important_files>

<next_steps>
Immediate next step:
- Wire `FourD4YService.fetchThreadDetail(threadId, page)` to call:
  - `https://www.4d4y.com/forum/viewthread.php?tid=$threadId&page=$page`
  - `parseThreadDetailHtml(html, threadId, page)`
  - return `ThreadDetailResult(thread, comments, totalPages)`
  - throw `FeedflowError.Parsing` if parsing fails.
- Add/extend a `SourceServiceWiringTest` fixture for 4D4Y detail parser through service, then run `:core:data:test`.

Continue parity work:
- Implement real Gemini HTTP summary service using stored `gemini_api_key` and Google Generative Language REST endpoint, or at least a properly isolated interface for real calls.
- Integrate Android TextToSpeech for summary speech instead of in-memory `SpeechPlaybackState`.
- Implement authenticated source actions:
  - 4D4Y create/reply/delete with cookies/form tokens.
  - Linux.do create/reply/delete via Discourse API/cookies.
  - V2EX reply where supported.
- Improve Zhihu parity beyond URL extraction: real feed item parsing, not-interested/downvote semantics, titles/content/auth handling.
- Improve 4D4Y/V2EX/Linux.do detail content rendering and pagination parity.
- Add screenshot comparison assertions against session artifacts/page screenshots.
- Expand localization usage in Compose screens rather than hard-coded English strings.
- Split oversized `FeedflowHelpers.kt` by source for maintainability.
- Add live opt-in source smoke tests separate from deterministic CI.
- Keep running:
  - `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:network:test :core:database:test :core:data:test :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --quiet`
</next_steps>