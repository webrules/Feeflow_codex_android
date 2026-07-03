<overview>
The user wants a high-fidelity native Android port of the existing iOS Feedflow app, with eventual 100% feature, test, and UI parity. After an initial prototype was rejected, the work pivoted to a spec-first parity process, then resumed implementation in strict spec order: foundations, resources, read-only source parsers, then persistence/auth/UI.
</overview>

<history>
1. The user initially requested a native Android project inside the Feedflow repo.
   - Created an Android Gradle project under `android/` with Kotlin, Compose, Material 3, Navigation Compose, modular `core:*` foundations, mock UI, Gradle wrapper, README, and tests.
   - Built, deployed to Pixel 7a, but the user rejected the outcome as poor quality/prototype-only.

2. The user requested restarting with migration plan and detailed specs before coding.
   - Created session artifact specs rather than repo docs.
   - Split specs into one markdown file per page/view under `files/page-specs/`.
   - Generated screenshot/design-spec artifacts under `files/page-screenshots/` with dark/light PNGs and contact sheets.

3. The user asked what other specs were needed.
   - Identified missing non-page specs: models, service contracts, per-source services, SQLite/cache, auth/security, networking/parsing, settings, navigation, theme, localization, AI, TTS/accessibility, offline/prefetch, and test matrix.
   - Drafted initial `files/non-page-specs/`, then replaced them with deeper reverse-engineered specs under `files/non-page-specs-v2/`.

4. The user asked to streamline specs and start implementation.
   - Removed obsolete high-level non-page specs from active artifacts.
   - Created `files/android-parity-master-plan.md`.
   - Active artifacts became: `android-parity-master-plan.md`, `page-specs/`, `page-screenshots/`, `non-page-specs-v2/`, and `feedflow-android-active-specs.zip`.
   - Started Phase 1 implementation: model/service contracts, source capability metadata, typed errors/results, schema constants/cache keys, and parity tests.

5. The user asked if the Android app was usable / 100% parity.
   - Answered plainly: no. It builds and has a scaffold/foundation tests, but real networking, persistence, auth, UI parity, source implementations, AI/TTS, and instrumentation/screenshot tests are still missing.

6. The user requested migrating all iOS resources to Android.
   - Inventoried iOS assets: only app icon catalog and an empty AccentColor colorset existed as asset catalog resources.
   - Migrated app icon PNGs/adaptive launcher resources from iOS 1024 icon.
   - Migrated 95 English and 95 Chinese strings from `LocalizationManager.swift`.
   - Migrated Feedflow theme color resources and dark-mode colors from `Theme.swift`.
   - Migrated 27 `FeedflowIcon` semantic SF Symbol names to Android string resources.
   - Updated manifest and styles to use migrated resources.

7. The user requested continuing implementation without waiting for approval, aiming for 100% parity and allowing simulator/device validation.
   - Started Phase 2 parser/read-only source work.
   - Completed RSS/OPML and Hacker News deterministic foundations and tests.
   - Began remaining parser slice for Linux.do, V2EX, Zhihu, and 4D4Y.
   - At compaction, parser helper code and tests for those sources had just been added but validation had not yet been run after the latest patch.
</history>

<work_done>
Repository changes made:
- `.gitignore`
  - Added Android ignores: `.gradle/`, `local.properties`, `captures/`, `*.apk`, `*.ap_`.

- `android/`
  - Created full Android Gradle project, currently untracked in git.
  - Includes app module and `core:model`, `core:data`, `core:database`, `core:network`, `core:security`, `core:ui`.
  - Uses Kotlin 1.9.25, AGP 8.7.3, Compose BOM 2024.12.01, compile/target SDK 35.
  - Build command used successfully: `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:data:test :app:assembleDebug --no-daemon --quiet`.

Session artifacts:
- `files/android-parity-master-plan.md`
  - Active master plan and implementation order.
- `files/page-specs/`
  - 30 page/view specs plus index.
- `files/page-screenshots/`
  - Dark/light screenshot-design artifacts and contact sheets.
- `files/non-page-specs-v2/`
  - Detailed reverse-engineered technical specs.
- `files/feedflow-android-active-specs.zip`
  - Current bundled active specs.

Implementation completed:
- Phase 1 foundation:
  - Domain models and service capabilities aligned closer to iOS.
  - Typed `FeedflowError`, `ThreadDetailResult`, `SearchResult`.
  - Source registry/capabilities and site order.
  - SQLite schema constants and cache key helpers.
  - Cookie/auth helper refinements.
  - Parity tests for source order/capabilities/defaults/schema/cache keys.
- Resource migration:
  - Android launcher icons from iOS icon catalog.
  - English/Chinese string XML resources.
  - Feedflow light/dark color XML resources.
  - Android styles/night styles.
  - Feedflow icon semantic mapping resources.
- Read-only source slice:
  - RSS/OPML parser improvements and service behavior.
  - Hacker News JSON parser/mapping, categories, detail/comment mapping, cleaner.
  - Fixture-style tests.
- Remaining parser slice in progress:
  - Added parser helpers for 4D4Y, V2EX, Zhihu, and Linux.do/Discourse.
  - Added tests for those parser helpers.
  - Validation after this latest patch still pending.

Current state:
- Android project builds as of the last completed validation before remaining parser slice.
- After the latest parser patch, tests/build need to be run.
- The Android app is still not 100% usable or parity-complete.
- UI remains prototype/scaffold and should be rebuilt later from page specs/screenshots.
</work_done>

<technical_details>
- Worktree/repo: `/Users/zoujoe/Projects/copilot-worktrees/Feedflow/joey-zou-3stripes-fuzzy-chainsaw`.
- Repo: `webrules/Feedflow`.
- Branch rename was attempted to `feedflow-android-parity-port`, but tool reported branch was already renamed previously; current exact branch may still be `joey-zou-3stripes-feedflow-android-port` or original session branch depending workspace metadata.
- Do not treat the existing Compose UI as final. The user explicitly rejected the prototype quality.
- Active plan order:
  1. Foundation.
  2. Parser/read-only source slices.
  3. Persistence/offline.
  4. Auth/WebView login.
  5. UI parity shell.
  6. Source completion/interactions.
- iOS source order for `ForumSite`: RSS, Hacker News, 4D4Y, V2EX, Linux.do, Zhihu.
- iOS service metadata:
  - RSS: `id=rss`, name `RSS Feeds`, logo `FeedflowIcon.feed`.
  - HN: `id=hackernews`, name `Hacker News`, logo `flame.fill`.
  - 4D4Y: `id=4d4y`, logo `4.circle.fill`, login/comment/thread creation true.
  - V2EX: `id=v2ex`, logo `point.3.connected.trianglepath.dotted`, commenting true, creation false.
  - Linux.do: `id=linux_do`, name `Linux.do`, logo `terminal.fill`, login/comment/thread creation true.
  - Zhihu: `id=zhihu`, name `知乎`, logo `questionmark.bubble.fill`, requires login.
- iOS DB schema reverse-engineered:
  - `communities(id,name,description,category,activeToday,onlineNow,serviceId, PRIMARY KEY(id, serviceId))`
  - `settings(key PRIMARY KEY,value)`
  - `filtered_posts(postId PRIMARY KEY,serviceId,filteredAt)`
  - `ai_summaries(thread_id,service_id DEFAULT '',summary,created_at, PRIMARY KEY(thread_id,service_id))`
  - `cached_topics(cache_key PRIMARY KEY,data,timestamp)`
  - `cached_threads(thread_id,service_id DEFAULT '',data,timestamp, PRIMARY KEY(thread_id,service_id))`
  - `bookmarks(thread_id,service_id,data,timestamp, PRIMARY KEY(thread_id,service_id))`
  - `url_bookmarks(url PRIMARY KEY,title,timestamp)`
  - Android also adds planned `rss_feeds`.
- Cookie setting key format: `login_<siteId>_cookies`.
- Gemini key setting: `gemini_api_key`.
- RSS default feeds:
  - Hacker Podcast `https://hacker-podcast.agi.li/rss.xml`
  - Ruanyifeng Blog `https://www.ruanyifeng.com/blog/atom.xml`
  - O’Reilly Radar `https://www.oreilly.com/radar/feed/`
- HN behavior from iOS:
  - Categories: `topstories`, `newstories`, `beststories`, `showstories`, `askstories`, `jobstories`.
  - Fetch first 20 IDs only.
  - Page > 1 returns empty/dummy.
  - Detail comments fetch first 20 kids.
  - Deleted/dead/empty comments skipped.
- Resource migration:
  - Android default strings are English in `values/strings.xml`; Chinese strings in `values-zh/strings.xml`.
  - 95 strings in each locale.
  - 27 icon semantic resources in `values/feedflow_icons.xml`.
  - 10 launcher PNGs generated across densities.
- A previous Python generation script initially failed due to f-string backslash escaping, then succeeded with a safer script.
- Build/test validation has been run many times with `ANDROID_HOME="$HOME/Library/Android/sdk"`.
- Pixel 7a deployment from earlier:
  - Device serial `34171JE0N10908`.
  - Package `com.webrules.feedflow`.
  - Deployment succeeded, but that app was rejected as prototype-only.
- Most recent incomplete state:
  - Added parser helper objects: `FourD4YParser`, `V2exParser`, `ZhihuParser`, `DiscourseParser`.
  - Added tests in `RemainingSourceParserParityTest`.
  - Need to import `java.nio.charset.Charset` in test file or fully qualify it, because test uses `Charset.forName(...)`.
  - Need to run `:core:data:test :app:assembleDebug` and fix any compile/test failures.
</technical_details>

<important_files>
- `.gitignore`
  - Added Android build artifact ignores.

- `android/settings.gradle.kts`
  - Defines project modules: `:app`, `:core:model`, `:core:data`, `:core:database`, `:core:network`, `:core:security`, `:core:ui`.

- `android/app/src/main/AndroidManifest.xml`
  - Uses migrated launcher icons: `@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`.
  - Internet permission present.

- `android/app/src/main/res/`
  - Migrated resources:
    - `values/strings.xml`
    - `values-zh/strings.xml`
    - `values/colors.xml`
    - `values-night/colors.xml`
    - `values/feedflow_icons.xml`
    - `values/styles.xml`
    - `values-night/styles.xml`
    - `mipmap-* / ic_launcher*.png`
    - `mipmap-anydpi-v26/ic_launcher*.xml`
    - `drawable/ic_launcher_foreground.png`

- `android/core/model/src/main/kotlin/com/webrules/feedflow/core/model/FeedflowModels.kt`
  - Domain models: `User`, `Community`, `FeedThread`, `Comment`, `ForumSite`, `SourceCapability`.
  - `FeedThread` was changed to a `data class` to match Swift struct value equality.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/ForumServices.kt`
  - Service contract and concrete service stubs/foundations.
  - Added `FeedflowError`, `ThreadDetailResult`, `SearchResult`.
  - Implemented RSS and HN service foundations.
  - Still has static/mock service base for some non-implemented sources.
  - `FourD4YService` has some pre-existing parser helpers and should eventually be reconciled with new `FourD4YParser`.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowHelpers.kt`
  - Contains helper/parsing logic:
    - `CommunitySettingsManager`
    - `SiteLoginConfig`
    - localization/test helpers
    - `RssParser`, `OpmlParser`, `RssContentCleaner`
    - `HackerNewsJson`, `HackerNewsContentCleaner`
    - newly added `FourD4YParser`, `V2exParser`, `ZhihuParser`, `DiscourseParser`
  - This file has grown large and should later be split by source.

- `android/core/database/src/main/kotlin/com/webrules/feedflow/core/database/FeedflowStore.kt`
  - In-memory store, DB contract constants, schema statements, cache key helpers.
  - `FeedflowDatabaseContract` includes iOS-compatible schema constants.
  - `FeedflowCacheKeys` added.

- `android/core/network/src/main/kotlin/com/webrules/feedflow/core/network/NetworkFoundation.kt`
  - Cookie model, HTTP client interface, cookie matcher/header helpers.

- `android/core/security/src/main/kotlin/com/webrules/feedflow/core/security/SecurityFoundation.kt`
  - AES-GCM in-memory secret store for JVM tests; not yet Android Keystore-backed production implementation.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/FeedflowParityTest.kt`
  - Main JVM parity test file.
  - Tests: site/capabilities, auth/cookies, store/model, parsing, RSS/OPML, HN, service metadata, schema/cache keys.
  - Newly appended `RemainingSourceParserParityTest` for 4D4Y/V2EX/Zhihu/Discourse parsers; not yet validated after latest patch.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/IosParityInventoryTest.kt`
  - Tracks iOS XCTest names. Current inventory tracks distinct names; v2 specs require class-qualified manifest later.

- Session artifacts:
  - `/Users/zoujoe/.copilot/session-state/853fc27e-d884-43f9-b8f9-fd26c651802c/files/android-parity-master-plan.md`
  - `/Users/zoujoe/.copilot/session-state/853fc27e-d884-43f9-b8f9-fd26c651802c/files/non-page-specs-v2/`
  - `/Users/zoujoe/.copilot/session-state/853fc27e-d884-43f9-b8f9-fd26c651802c/files/page-specs/`
  - `/Users/zoujoe/.copilot/session-state/853fc27e-d884-43f9-b8f9-fd26c651802c/files/page-screenshots/`
</important_files>

<next_steps>
Immediate next action after compaction:
1. Run focused validation:
   - `cd android`
   - `ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :core:data:test :app:assembleDebug --no-daemon --quiet`
2. Fix compile/test failures from latest remaining parser patch.
   - Likely first issue: missing `Charset` import in `FeedflowParityTest.kt` for `Charset.forName("GB18030")`.
   - Also watch for regex/parser test expectations around `stripTags()` removing token brackets or whitespace.
3. Once tests pass, mark `remaining-parser-slice` done in todo tracking.
4. Continue master plan:
   - Split parser helpers into per-source files if needed.
   - Move into Phase 3 persistence/offline: real Room/SQLDelight schema, DAOs, migrations, repository cache-first behavior.
   - Then Phase 4 WebView/auth/session.
   - Then rebuild UI from page specs/screenshots.
5. Do not claim whole-app 100% parity until:
   - All page specs implemented.
   - Screenshot/UI tests pass.
   - All iOS XCTest inventory is mapped class-qualified and covered.
   - Real source/network/auth flows are validated, with live opt-in tests separated from deterministic CI.
</next_steps>