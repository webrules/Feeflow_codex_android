<overview>
The user wants a native Android port of the iOS Feedflow app inside the existing `webrules/Feedflow` repository, with an explicit expectation of 100% UI, feature, resource, and test parity. The current approach is continuous parity-driven implementation: inspect iOS source as source of truth, fix concrete gaps, validate with Gradle/emulator tests, deploy to Pixel 7a, and capture screenshots for visual comparison.
</overview>

<history>
1. Initial Android port request
   - Created a native Android project under `android/` using Kotlin, Jetpack Compose, Material 3, modular Gradle modules, source models/services, persistence/security/network placeholders, and initial UI.
   - User rejected the initial outcome as not usable and requested a spec-first restart.

2. Spec-first parity reset
   - Drafted Android migration plan, page specs, non-page technical specs, and screenshot/design artifacts based on iOS source.
   - User later approved continuing implementation without page-by-page approval and demanded continuous progress toward 100% parity.

3. Broad Android parity implementation
   - Implemented major source-service and UI foundations: RSS, Hacker News, Linux.do, V2EX, 4D4Y, Zhihu, bookmarks, settings, login/WebView cookie capture, AI summaries, TTS, OPML, new-thread/reply/delete flows, localization, and tests.
   - Migrated/added Android resources including strings and icon mappings.

4. Pixel deployment and repeated visual validation
   - Deployed multiple debug builds to Pixel 7a serial `34171JE0N10908`.
   - Pixel screenshots were captured into the session artifacts directory.
   - Found and fixed visible parity/functionality issues from screenshots: login fake sign-in, hard-coded strings, home icon mismatches, theme tokens, status bar readability, and RSS feed loading.

5. Most recent debugging cycle
   - Live RSS list initially showed “Failed to parse fetchCategoryThreads for rss” and then “No topics yet.”
   - Diagnosed that repository calls were using blocking `HttpURLConnection` from Compose-side coroutines, causing app runtime failures even though tests passed.
   - Moved repository network/service calls to `Dispatchers.IO`, promoted coroutines to production dependency, added RSS parser fallback for Android DOM edge case, validated RSS on Pixel, and confirmed real Hacker Podcast items now load.
</history>

<work_done>
Files updated:
- `.gitignore`
  - Added Android build/runtime ignores.
- `android/`
  - Native Android project exists with modules:
    - `:app`
    - `:core:model`
    - `:core:data`
    - `:core:database`
    - `:core:network`
    - `:core:security`
    - `:core:ui`

Recent concrete changes:
- Fixed login parity:
  - OAuth buttons no longer fake sign-in.
  - Login opens WebView flow and only marks signed-in after source auth cookies are captured.
  - Startup signed-in state now requires source-specific auth cookies via `SiteLoginConfig`.
- Fixed localization:
  - Added many Android string resources in English and Chinese.
  - Wrapped Compose tree in a localized `LocalContext` driven by Feedflow’s in-app language setting, not device locale.
  - Corrected home title to iOS text: “Select a Community.”
- Improved UI parity:
  - Custom source icons for 4D4Y, V2EX, Zhihu and iOS-like site icon ring.
  - Home grid geometry aligned with iOS `SiteListView`: 24dp vertical spacing, 16dp grid spacing, 16dp card radius, 52dp icon target, 32dp vertical card padding.
  - Theme tokens aligned with iOS `Theme.swift`: background/card/accent/text/secondary/input/separator colors.
  - Removed redundant outer theme wrapper in `MainActivity`.
  - Explicitly set Android system bar colors and light/dark icon appearance so Pixel status bar is readable.
- Fixed source/network runtime:
  - Moved repository calls for communities, threads, detail, search, create/reply/delete, and Gemini summaries to `Dispatchers.IO`.
  - Added production dependency on `kotlinx-coroutines-core` in `core:data`.
  - Improved repository error mapping for `HttpStatusException` and `IOException`.
  - Added RSS regex fallback parser for RSS/Atom item extraction when Android DOM parsing returns no items.
  - Added deterministic regression test for RSS fallback parser.
- Tests:
  - UI smoke tests now use deterministic fake services instead of live HN network.
  - Updated smoke assertions for exact iOS title and localized strings.
  - Full validation was green after recent changes:
    - `:core:network:test`
    - `:core:database:test`
    - `:core:data:test`
    - `:app:assembleDebug`
    - `:app:connectedDebugAndroidTest`
- Pixel:
  - Latest validated build deployed to Pixel 7a.
  - Latest relevant screenshots:
    - `pixel7a-rss-fallback-fixed.png`: RSS Hacker Podcast loads 9 visible real items.
    - `pixel7a-home-title-parity.png`: home title/spacing/status bar updated.
    - `pixel7a-home-systembars-fixed.png`: readable status bar.
</work_done>

<technical_details>
- Workspace:
  - Repo root: `/Users/zoujoe/Projects/copilot-worktrees/Feedflow/joey-zou-3stripes-fuzzy-chainsaw`
  - Android project: `/Users/zoujoe/Projects/copilot-worktrees/Feedflow/joey-zou-3stripes-fuzzy-chainsaw/android`
  - Pixel 7a serial: `34171JE0N10908`
  - Emulator serial: `emulator-5554`
  - Android SDK: `$HOME/Library/Android/sdk`
- Android stack:
  - Kotlin 1.9.25, AGP 8.7.3, Compose BOM 2024.12.01, compile/target SDK 35.
- iOS source of truth:
  - Home source order: RSS, Hacker News, 4D4Y, V2EX, Linux.do, Zhihu.
  - Searchable sources: 4D4Y, V2EX, Linux.do, Zhihu; default Zhihu.
  - Home title key in iOS localization is “Select a Community.”
  - Toolbar icons in iOS home use `forumTextPrimary` (black in light mode), not accent blue.
- Important parity facts:
  - 4D4Y logo: `4.circle.fill`
  - HN logo: `flame.fill`
  - RSS logo: `dot.radiowaves.left.and.right`
  - V2EX logo: `point.3.connected.trianglepath.dotted`
  - Linux.do logo: `terminal.fill`
  - Zhihu logo: `questionmark.bubble.fill`
- Auth/session:
  - Cookie key prefix: `login_<siteId>_cookies`.
  - Gemini key: `gemini_api_key`.
  - Settings keys: `dark_theme`, `language`, `background_prefetch`.
  - Auth cookie detection:
    - HN: `user`
    - V2EX: fragment `a2`
    - Linux.do: fragments `_t`, `remember_user_token`
    - 4D4Y: fragments `auth`, `login`, `member`
    - Zhihu: `z_c0`
- RSS issue/root cause:
  - Live RSS XML from `https://hacker-podcast.agi.li/rss.xml` was valid and reachable.
  - App initially failed because blocking network was being invoked on the main thread from Compose `LaunchedEffect`.
  - After IO dispatch fix, Pixel still parsed zero items in one path because Android DOM returned no `<item>` nodes for that feed; regex fallback fixed it.
  - Confirmed Pixel now loads real RSS thread rows.
- Validation quirks:
  - Use emulator-scoped tests with `ANDROID_SERIAL=emulator-5554`.
  - Running connected tests on Pixel can install/replace/clear app state; deploy Pixel separately afterward.
  - Pixel screenshots can show lock/black screen if device is locked/dozing; use direct launch and ensure `MainActivity` is resumed.
- Open uncertainty:
  - 100% parity is not complete.
  - Full authenticated live validation for 4D4Y/V2EX/Linux.do/Zhihu/HN still needed.
  - Screenshot diff automation against iOS artifacts is not complete.
  - Many screens beyond home/RSS still need detailed visual parity review.
</technical_details>

<important_files>
- `android/app/src/main/kotlin/com/webrules/feedflow/ui/FeedflowApp.kt`
  - Main Compose UI/navigation shell.
  - Contains home screen, thread list/detail, login, settings, bookmarks, RSS manager, AI screens, browser, image viewer, composer, toolbar/icon helpers.
  - Recent changes: localized context, system bar styling, home geometry, custom source icons, localized strings, login flow fix, thread-list warnings.
  - Key sections:
    - `FeedflowApp` setup around language/theme/store/repository.
    - `SiteListScreen` for home UI parity.
    - `ThreadListScreen` and `ThreadDetailScreen` for list/detail parity.
    - `FeedflowIconMap`, `SiteIcon`, `FeedflowSiteSymbol`.
    - `FeedflowSystemBars`.
- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowRepository.kt`
  - Repository/service coordination and cache-first behavior.
  - Recent changes: moved blocking service/network calls to `Dispatchers.IO`, improved network error mapping, summary calls also dispatched to IO.
- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowHelpers.kt`
  - Helpers including `RssParser`, `OpmlParser`, localization helper, cleaners.
  - Recent changes: RSS regex fallback parser for RSS/Atom items.
- `android/core/data/build.gradle.kts`
  - Recent change: `kotlinx-coroutines-core:1.8.1` promoted from test dependency to production implementation.
- `android/core/ui/src/main/kotlin/com/webrules/feedflow/core/ui/FeedflowTheme.kt`
  - Android theme tokens.
  - Recent changes: added iOS-matching text/secondary/input/separator colors to Material color schemes.
- `android/app/src/main/kotlin/com/webrules/feedflow/MainActivity.kt`
  - Android entry point.
  - Recent change: removed redundant outer `FeedflowTheme`; `FeedflowApp` owns theme.
- `android/app/src/main/res/values/strings.xml`
  - English strings.
  - Recent changes: expanded localization and fixed `select_community` to “Select a Community.”
- `android/app/src/main/res/values-zh/strings.xml`
  - Chinese strings.
  - Recent changes: matching localization additions.
- `android/app/src/androidTest/kotlin/com/webrules/feedflow/FeedflowUiParitySmokeTest.kt`
  - Connected Compose UI smoke tests.
  - Recent changes: deterministic fixture services; updated title assertion to “Select a Community.”
- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/FeedflowParityTest.kt`
  - Core parity/unit tests.
  - Recent change: RSS fallback parser regression.
- iOS references:
  - `Feedflow/Views/SiteListView.swift`: home layout, search bar, toolbar.
  - `Feedflow/Views/ThreadListView.swift`: thread list behavior/toolbars.
  - `Feedflow/Views/ThreadDetailView.swift`: detail content/reply/bottom toolbar behavior.
  - `Feedflow/Theme/Theme.swift`: color tokens.
  - `Feedflow/Theme/FeedflowIcons.swift`: SF Symbol mapping.
  - `Feedflow/Services/LocalizationManager.swift`: exact localized strings and language persistence behavior.
</important_files>

<next_steps>
Immediate next steps:
- Continue from the active task: verify RSS thread detail parity after list loading.
  - Use Pixel to tap a loaded RSS item and capture the detail screen.
  - Compare Android `ThreadDetailScreen` against iOS `ThreadDetailView`, especially:
    - RSS hides author/avatar header.
    - title/content spacing and divider behavior.
    - bottom toolbar icons/actions.
    - browser/AI/bookmark actions.
- Continue replacing remaining hard-coded labels/content descriptions in `FeedflowApp.kt` where they affect visible UI or accessibility.
- Continue high-risk source parity:
  - Hacker News list/detail live validation.
  - V2EX/Linux.do/4D4Y/Zhihu login/session and authenticated behavior.
  - Posting/reply/delete flows per source.
- Improve visual parity:
  - Home is much closer now; remaining icon approximations may still not perfectly match SF Symbols.
  - Thread list/detail screens need iOS-style status header/empty/loading states.
  - Bottom toolbars should be compared screen by screen.
- Keep validation loop:
  - Run emulator tests before Pixel deploy:
    - `ANDROID_SERIAL=emulator-5554 ./gradlew :core:network:test :core:database:test :core:data:test :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --quiet`
  - Deploy to Pixel:
    - `ANDROID_SERIAL=34171JE0N10908 ./gradlew :app:installDebug --no-daemon --quiet`
    - Launch with SDK adb path and capture screenshots.
</next_steps>