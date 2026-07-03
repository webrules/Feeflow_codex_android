<overview>
The user wants a native Android port of the existing iOS Feedflow app inside the `webrules/Feedflow` repo, with the explicit expectation to continue until 100% UI, feature, resource, and test parity. The approach evolved from rough implementation to spec-first parity work, then continuous implementation/validation/deployment, using the iOS source and tests as the source of truth.
</overview>

<history>
1. Initial Android project request
   - Created a native Kotlin/Jetpack Compose Android project under `android/`.
   - Added modular Gradle setup, Compose UI shell, models, services, persistence/security/network placeholders, resources, and tests.
   - User rejected the initial result as poor and requested a restart with migration plan/specs before coding.

2. Spec-first reset and implementation restart
   - Drafted master migration plan, page specs, non-page technical specs, and iOS-derived screenshot/design artifacts.
   - Later user approved continuing implementation without waiting for page-by-page approval.
   - Implemented many foundational Android parity layers: models, source services, SQLite store, encrypted settings/cookies, WebView login, RSS/OPML/HN/4D4Y/V2EX/Linux.do/Zhihu foundations, bookmarks, summaries, settings, and Compose screens.

3. User demanded continuous 100% parity
   - User repeatedly instructed not to stop until 100% UI/feature parity.
   - Improved iOS icon parity, toolbar treatment, home search selector, language toggle, locale resources, and multiple page flows.
   - Added real source/service behavior: HN search, RSS subscriptions, Linux.do/V2EX/4D4Y detail parsing, basic Zhihu hot/search/detail, service-backed create/reply/delete actions.

4. Latest work segment before compaction
   - Deployed debug app to Pixel 7a (`34171JE0N10908`) and launched it.
   - Pixel was locked/dozing; app package was installed and `MainActivity` was resumed behind the lock screen, but screenshots showed the lock screen/black display until device was forced awake.
   - Continued parity work using emulator for validation while keeping Pixel build installed.
</history>

<work_done>
Files updated/created:
- `.gitignore`
  - Added Android build/runtime ignores.
- `android/`
  - New untracked native Android project with modules:
    - `:app`
    - `:core:model`
    - `:core:data`
    - `:core:database`
    - `:core:network`
    - `:core:security`
    - `:core:ui`

Recent completed work:
- Real Gemini REST summary client and cache-aware summary flows for:
  - Thread AI summary
  - Daily RSS Summary
  - Cross-Site AI Top 10
- Android TextToSpeech integration for AI summaries.
- Real source actions:
  - V2EX reply via `once` token.
  - Linux.do Discourse create/reply via `/posts.json`.
  - 4D4Y create/reply/delete via Discuz `formhash`, first post ID, and cookie auth.
- 4D4Y `restoreSession()` now parses/stores logged-in username for owner-only delete gating.
- 4D4Y thread detail service wired.
- Zhihu detail fetching added.
- Browser screen now has functional WebView Back/Forward/Reload/Open controls.
- New thread composer UI improved:
  - iOS-like attachment strip.
  - Android image picker wiring.
  - Remove selected image.
  - Formatting toolbar actions append/apply markdown/link/list text.
- Home screen UI parity improved:
  - iOS-like centered title.
  - Compact source selector/search bar.
  - Removed clipped fixed-height grid; rows now scroll naturally.
- Thread detail content now uses parsed content rendering instead of raw text.
- Remote avatar loading added.
- Removed visible scaffold/mock fallback topics and comments from runtime paths.
- Corrected `values/strings.xml` to English and `values-zh/strings.xml` to Chinese after discovering they were reversed again.
- Added/updated deterministic tests for AI, source actions, source detail, restore-session, and UI smoke behavior.
- Installed/latest build deployed to Pixel 7a; package confirmed installed.

Validation state:
- Emulator full validation was green before the latest login/localization investigation:
  - `:core:network:test`
  - `:core:database:test`
  - `:core:data:test`
  - `:app:assembleDebug`
  - `:app:connectedDebugAndroidTest`
- After later UI changes, emulator UI tests passed again.
- Pixel connected tests should not be used casually because the connected test task removed/replaced the Pixel app once; deploy to Pixel separately at the end.
- Pixel visual screenshot was blocked by secure lock screen; package installed and `MainActivity` resumed, but screenshot showed lock/black state until awake/unlocked.

Most recent active work:
- Investigating login/session parity.
- Opened Android `LoginScreen` and iOS `LoginView.swift`.
- Noted Android OAuth buttons currently call `onSignIn(selectedSite)` directly, which falsely marks a site signed in; iOS opens WebLogin with OAuth override and only marks signed-in after cookie capture.
</work_done>

<technical_details>
Environment:
- Workspace: `/Users/zoujoe/Projects/copilot-worktrees/Feedflow/joey-zou-3stripes-fuzzy-chainsaw`
- Repo: `webrules/Feedflow`
- Android project: `android/`
- Pixel 7a serial: `34171JE0N10908`
- Emulator: `emulator-5554`
- Android SDK path: `$HOME/Library/Android/sdk`
- Android stack: Kotlin 1.9.25, AGP 8.7.3, Compose BOM 2024.12.01, compile/target SDK 35.

Important iOS parity facts:
- `ForumSite` order: RSS, Hacker News, 4D4Y, V2EX, Linux.do, Zhihu.
- iOS source icons:
  - RSS: `dot.radiowaves.left.and.right`
  - HN: `flame.fill`
  - V2EX: `point.3.connected.trianglepath.dotted`
  - Linux.do: `terminal.fill`
  - 4D4Y: `4.circle.fill`
  - Zhihu: `questionmark.bubble.fill`
- iOS home searchable sources: 4D4Y, V2EX, Linux.do, Zhihu; default Zhihu.
- iOS language toggle displays `EN` / `中`, not globe.
- Home toolbar uses plain symbols in rounded card; screen toolbars use circular symbol buttons.

Persistence/schema:
- Key tables include communities, settings, filtered_posts, ai_summaries, cached_topics, cached_threads, bookmarks, url_bookmarks, rss_feeds.
- Cookie key format: `login_<siteId>_cookies`.
- Gemini key: `gemini_api_key`.
- Settings keys: `dark_theme`, `language`, `background_prefetch`.
- 4D4Y delete gating setting: `detected_4d4y_username`.

Auth/session details:
- 4D4Y domain `4d4y.com`; auth fragments `auth`, `login`, `member`.
- HN required cookie `user`.
- V2EX auth fragment `a2`.
- Linux.do fragments `_t`, `remember_user_token`.
- Zhihu required cookie `z_c0`.
- Android WebView login uses `CookieManager`, JavaScript, DOM storage, popup settings, and `AuthSessionCoordinator`.

Validation/deployment quirks:
- Running connected tests against both emulator and Pixel can uninstall/replace the app on Pixel; prefer setting `ANDROID_SERIAL=emulator-5554` for validation and deploy Pixel separately.
- Pixel screenshots were black/lockscreen because `mWakefulness=Dozing` or secure keyguard; app can be foreground behind lock screen.
- To wake Pixel:
  - `adb shell svc power stayon true`
  - `adb shell input keyevent 224`
  - `adb shell wm dismiss-keyguard`
  - Secure unlock may still require user biometric/PIN.
- Pixel package was confirmed installed with `pm list packages | grep com.webrules.feedflow`.
- Pixel `MainActivity` was confirmed resumed behind lock screen.

Open/uncertain:
- Not genuinely 100% parity yet.
- Full live authenticated validation per source still needed.
- Screenshot diff parity not automated.
- Android localization still has many hard-coded strings in Compose.
- Login OAuth flow has a known parity bug: Android OAuth buttons fake sign-in instead of opening WebLogin and waiting for cookie capture.
</technical_details>

<important_files>
- `android/app/src/main/kotlin/com/webrules/feedflow/ui/FeedflowApp.kt`
  - Main Compose app shell and all major screens/routes.
  - Recently changed for home UI parity, detail parsed rendering, browser controls, composer attachments/formatting, delete confirmation, TTS, Pixel-deployable app behavior.
  - Important sections:
    - App route handling near top.
    - `SiteListScreen`
    - `ThreadDetailScreen`
    - `LoginScreen`
    - `InAppBrowserScreen`
    - `NewThreadScreen`
    - `ParsedContent`, `AvatarView`, toolbar/icon helpers.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/ForumServices.kt`
  - Source service implementations.
  - Recently changed:
    - V2EX reply with `once`.
    - Linux.do create/reply.
    - 4D4Y create/reply/delete/restore username.
    - 4D4Y detail.
    - Zhihu detail.
  - Also contains `FeedflowError`, `ForumService`, parsers/helpers.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowRepository.kt`
  - Cache-first repository/service access layer.
  - Recently changed:
    - Gemini summaries.
    - source web URL/canDelete/delete support.
    - runtime mock fallback removal; now `defaultCommunities` and empty thread fallback.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowAppState.kt`
  - UI-facing controller.
  - Recently changed:
    - summary methods.
    - web URL/delete methods.
    - removed sample comments/mock fallback.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/AiTtsPrefetchState.kt`
  - AI summary coordinator and Gemini REST client.
  - Includes JSON request/response handling and cache-aware summary state.

- `android/core/network/src/main/kotlin/com/webrules/feedflow/core/network/NetworkFoundation.kt`
  - HTTP abstraction.
  - `post()` widened with content type for Gemini JSON while preserving form posts.

- `android/app/src/androidTest/kotlin/com/webrules/feedflow/FeedflowUiParitySmokeTest.kt`
  - Connected Compose smoke tests.
  - Updated so home scroll assertions work on Pixel-sized devices.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/SourceServiceWiringTest.kt`
  - Deterministic parser/service/action coverage for V2EX, Linux.do, 4D4Y, Zhihu.
  - Includes latest tests for reply/create/delete/session username.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/AiTtsPrefetchParityTest.kt`
  - Tests Gemini REST client and repository summary cache behavior.

- `android/app/src/main/res/values/strings.xml`
  - Default English strings; was corrected after accidental reversal.

- `android/app/src/main/res/values-zh/strings.xml`
  - Chinese strings; was corrected after accidental reversal.

- iOS references:
  - `Feedflow/Views/SiteListView.swift`
  - `Feedflow/Theme/FeedflowIcons.swift`
  - `Feedflow/Views/LoginView.swift`
  - `Feedflow/Views/NewThreadView.swift`
  - `Feedflow/Views/ThreadDetailView.swift`
  - `Feedflow/Services/FourD4YService.swift`
  - `Feedflow/Services/V2EXService.swift`
  - `Feedflow/Services/DiscourseService.swift`
  - `Feedflow/Services/GeminiService.swift`
</important_files>

<next_steps>
Immediate next steps:
1. Fix Android login/OAuth parity:
   - In `LoginScreen`, OAuth option buttons must not call `onSignIn(selectedSite)`.
   - They should open the WebLogin route with an OAuth override URL or equivalent behavior matching iOS `LoginView`.
   - Only mark signed-in after cookie capture succeeds.
2. Continue replacing hard-coded Compose strings with localized resources or language-aware strings, prioritizing:
   - Login screen
   - Settings screen
   - Bookmarks/RSS manager
   - Detail/reply/composer/browser screens
3. Run validation on emulator only:
   - `export ANDROID_SERIAL=emulator-5554`
   - `./gradlew :core:network:test :core:database:test :core:data:test :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --quiet`
4. Deploy latest build to Pixel 7a separately after each stable pass:
   - `ANDROID_SERIAL=34171JE0N10908 ./gradlew :app:installDebug --no-daemon --quiet`
   - Launch with `adb -s 34171JE0N10908 shell am start -n com.webrules.feedflow/.MainActivity`
   - If screenshot is black/lockscreen, user must unlock device or use wake/dismiss commands; secure lock may still block visual verification.
5. Continue parity gap closure:
   - Full authenticated live tests for 4D4Y/V2EX/Linux.do/Zhihu/HN.
   - Screenshot diff assertions against iOS artifacts.
   - Complete localization cleanup.
   - Expand Android tests from iOS XCTest inventory from “tracked” to “behaviorally mirrored.”
   - Improve Zhihu parsing beyond URL/title/meta extraction.
   - Verify image/avatar rendering across real feeds.
</next_steps>