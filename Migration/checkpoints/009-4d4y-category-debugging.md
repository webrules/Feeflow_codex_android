<overview>
The user wants the native Android Feedflow port to reach 100% iOS UI and feature parity, with special emphasis on Pixel 7a validation and the category/thread-list/thread-detail pages. The work proceeds in small parity slices: compare iOS SwiftUI/source behavior, patch Android Compose/data/service code, run JVM + Pixel connected validation, install/launch on Pixel, commit, and push to `webrules/Feeflow_codex_android`.
</overview>

<history>
1. The user repeatedly emphasized “do not stop until 100% UI/feature parity,” especially on category, thread-list, thread-detail, icons, login/browser, and 4D4Y protected categories.
   - Continued from prior Android port work and kept one main todo (`phase5-ui`) in progress.
   - Used iOS files as source of truth and pushed each completed slice to the GitHub handoff repo.

2. The user asked to check status and continue parity.
   - Confirmed worktree was clean at `41a4587`.
   - Added and pushed several UI/navigation parity fixes:
     - Protected-page login prompts on category/thread-list pages.
     - iOS-style category/thread loading UI.
     - iOS-style flat detail action toolbar.
     - Automatic detail comment loading.
     - Detail author/avatar metadata preservation.
     - Detail scroll reset when switching previous/next thread.
     - AI summary, image viewer, and in-app browser return-to-source navigation.
     - Site icon depth/shadow closer to iOS.
   - Ran `:core:data:test`, `:app:assembleDebug`, Pixel 7a connected Android tests, and installed/launched the APK after each slice.

3. The user then reported two 4D4Y bugs:
   - Visiting a 4D4Y category shows nothing.
   - After successful login, member-only categories such as “Category” are missing.
   - Started focused 4D4Y investigation.
   - Checked worktree state at `e844e75`.
   - Added a note to the active `phase5-ui` todo.
   - Began reading Android `FourD4YService`, `FourD4YParser`, tests, and iOS `FourD4YService.swift`.
   - Compaction interrupted during analysis before implementing the fix.
</history>

<work_done>
Files modified and pushed in this segment:

- `android/app/src/main/kotlin/com/webrules/feedflow/ui/FeedflowApp.kt`
  - Added login-required cards on protected category/thread pages.
  - Removed extra category header so category rows better match iOS.
  - Added iOS-style thread-list loading card.
  - Reworked detail action toolbar into a flat iOS-like bottom bar.
  - Auto-loads more comments when the pagination footer appears.
  - Resets detail list scroll to top when switching threads.
  - Changed AI summary/image/browser routes to return to the originating detail/source page instead of home.
  - Added site icon shadow/depth closer to iOS.
  - Several temporary scoping mistakes occurred while patching route return handlers (`current.returnTo` was accidentally placed in Login branch twice); both were fixed and validated.

- `android/app/src/main/res/values/strings.xml`
  - Added strings for protected-page login prompt and thread-list loading card.

- `android/app/src/main/res/values-zh/strings.xml`
  - Added Chinese translations for those new strings.

- `android/app/src/androidTest/kotlin/com/webrules/feedflow/FeedflowUiParitySmokeTest.kt`
  - Updated detail toolbar assertions to match the iOS-like reduced visible action set.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowAppState.kt`
  - Added detail thread merge logic that preserves richer list-row author/avatar/metadata when fresh detail returns placeholder author data, matching iOS `ThreadDetailViewModel.resolvedAuthor`.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/FeedflowAppStateControllerTest.kt`
  - Added regression coverage for preserving richer detail author/avatar/community/last-post metadata.

Recent pushed commits:
- `e844e75 Match iOS site icon depth`
- `27c00e7 Return browser links to source page`
- `8f6f6c8 Return image viewer to thread detail`
- `5e5048d Return AI summary to thread detail`
- `65f0244 Reset detail scroll on thread navigation`
- `26dd1d4 Preserve Android detail author metadata`
- `78d423c Autoload Android detail comments`
- `702abcd Align Android detail action toolbar`
- `0b21370 Align category and thread loading UI`
- `25e1fdf Surface login prompts on protected pages`

Current state:
- Latest known pushed HEAD: `e844e75` on branch `joey-zou-3stripes-feedflow-android-port`, remote `webrules-android/main`.
- Latest known worktree was clean before starting the 4D4Y investigation.
- 4D4Y bugs reported by user are not fixed yet.
</work_done>

<technical_details>
- Handoff repo: `webrules/Feeflow_codex_android`, private, push target `webrules-android HEAD:main`.
- Use `env -u GH_TOKEN git push webrules-android HEAD:main`; active `GH_TOKEN` identity can be wrong and cause 403.
- Pixel 7a serial: `34171JE0N10908`.
- Preferred validation:
  - `cd android && ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :core:data:test :app:assembleDebug --no-daemon --quiet`
  - `cd android && ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SERIAL=34171JE0N10908 ./gradlew :app:connectedDebugAndroidTest --no-daemon --quiet`
  - Install/launch with `adb install -r app/build/outputs/apk/debug/app-debug.apk` and `adb shell monkey -p com.webrules.feedflow 1`.

4D4Y investigation details:
- Android `FourD4YService` is in `ForumServices.kt`.
  - `restoreSession()` currently:
    - Reads cookies from store.
    - Requires a cookie matching `4d4y.com` and `SiteLoginConfig.hasAuthenticatedSession`.
    - Fetches `https://www.4d4y.com/forum/index.php` via `withSid(...)`.
    - Calls `rememberAuthenticatedPageArtifacts(html)`.
    - Returns `validateSessionHtml(html)`.
  - `fetchCategories()`:
    - Calls `authCookies()`.
    - Fetches `index.php` with SID.
    - Calls `rememberAuthenticatedPageArtifacts`.
    - Returns `FourD4YParser.parseCategories(html)`.
  - `fetchCategoryThreads(categoryId, communities, page)`:
    - Calls `authCookies()`.
    - Fetches `https://www.4d4y.com/forum/forumdisplay.php?fid=$categoryId&page=$page` with SID.
    - Calls `rememberAuthenticatedPageArtifacts`.
    - Returns `FourD4YParser.parseThreadRows(html, community)`.
  - `withSid(...)` appends `sid` from a `sid` cookie or store setting `4d4y_sid`.
  - `validateSessionHtml(...)` currently considers session valid when categories are non-empty, no login/challenge, and logout or `Discovery` exists.
  - `rememberAuthenticatedPageArtifacts(...)` only saves SID/username if `validateSessionHtml(html)` passes.
- Android `FourD4YParser` is in `FeedflowHelpers.kt`.
  - `parseCategories` currently uses a regex for `forumdisplay.php?fid=...` anchor links and handles nested links from prior work.
  - `parseThreadRows` likely needs review/fix for real 4D4Y category HTML, especially if the live page uses WAP/mobile/list formats or link-only rows not matching current row regex.
- iOS `FourD4YService.swift` has useful behavior to port:
  - `fetchCategoryThreadsInternal` ensures current SID by fetching categories if `currentSID == nil`.
  - Builds category URL as `forumdisplay.php?fid=\(categoryId)\(sidParam)\(pageParam)\(_t=timestamp)`.
    - Note order differs: iOS uses `fid=ID&sid=SID&page=N&_t=...`; Android currently uses `fid=ID&page=N&sid=SID`.
    - Discuz may be sensitive or cached; add cache buster and consider order.
  - Parses `<tbody id="normalthread_...">` rows first.
  - If no rows, falls back to `extractThreadLinksFallback(from: html, community:)`, scanning all `viewthread.php?tid=` links and extracting surrounding context.
  - If empty on first page, tries auto-login and retries.
  - iOS category parsing uses a simpler anchor regex but may miss nested span names; Android had improved nested parsing earlier.
- User specifically says member-only category such as “Category” is missing after successful login. This implies Android category parsing/session validation likely doesn’t preserve or parse all authenticated forum links, or cookies/SID are not being applied to the index request after login.
- Potential root causes to verify next:
  - `SiteLoginConfig.hasAuthenticatedSession` may reject actual 4D4Y cookies if names differ from `auth/login/member`.
  - Cookies may be stored for a path not matching `/forum/` or not sent by the HTTP client.
  - `FourD4YParser.parseCategories` may not parse forum links where text is nested deeper or encoded differently.
  - `rememberAuthenticatedPageArtifacts` may not save SID if `validateSessionHtml` fails because protected category is not `Discovery`.
  - Category thread pages may return WAP/mobile HTML or a login page; current parser may return empty without throwing/login prompt.
  - Android category URL lacks iOS cache buster and has page before SID.
</technical_details>

<important_files>
- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/ForumServices.kt`
  - Central source-service implementations.
  - `FourD4YService` starts around line ~441.
  - Key methods: `restoreSession`, `fetchCategories`, `fetchCategoryThreads`, `withSid`, `validateSessionHtml`, `rememberAuthenticatedPageArtifacts`, `parseThreadDetailHtml`.
  - This is the first file to patch for 4D4Y category/thread bugs.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/FeedflowHelpers.kt`
  - Parser helpers, including `FourD4YParser`.
  - Key methods: `parseCategories`, `parseThreadRows`, `extractSid`.
  - Likely needs 4D4Y thread-list fallback parsing and broader authenticated category parsing.

- `android/core/data/src/test/kotlin/com/webrules/feedflow/core/data/SourceServiceWiringTest.kt`
  - Contains existing 4D4Y tests for SID, protected categories, nested forum links, authenticated categories, category thread requests, WAP detail parsing, reply/new-thread/delete request parity.
  - Extend here with regressions for:
    - Authenticated index includes `Category` or other protected forum, not only `Discovery`.
    - Category page with link-only/fallback thread HTML returns threads.
    - Category fetch URL includes SID and possibly cache buster.

- `Feedflow/Services/FourD4YService.swift`
  - iOS source of truth for 4D4Y behavior.
  - Relevant sections:
    - `restoreSession` / `validateSession`: lines ~94-290.
    - `fetchContent`: lines ~297-365.
    - `fetchCategoriesInternal`: lines ~444-499.
    - `fetchCategoryThreadsInternal`: lines ~551-680.
    - `extractThreadLinksFallback`: lines ~683-735.
  - Port exact behavior carefully.

- `android/app/src/main/kotlin/com/webrules/feedflow/ui/FeedflowApp.kt`
  - Main Compose shell and route state.
  - Recently changed heavily for category/thread/detail parity.
  - Important if 4D4Y login success or page warning/login-prompt state needs UI adjustment.

- `android/app/src/main/kotlin/com/webrules/feedflow/auth/AndroidWebLoginCookieBridge.kt`
  - WebView cookie capture/injection.
  - Recent prior work added 4D4Y multi-URL cookie collection, persisted cookie injection, Chrome-like UA, WebView settings, and cookie clearing.
  - Revisit if successful login still doesn’t yield member-only categories.

- `android/core/data/src/main/kotlin/com/webrules/feedflow/core/data/AuthSessionFoundation.kt`
  - Auth session persistence/restore/logout.
  - Prior work clears 4D4Y SID/detected username on logout and clears cached communities after login.
</important_files>

<next_steps>
Immediate next steps for the reported 4D4Y bugs:
1. Reproduce/triage with tests first.
   - Add failing tests in `SourceServiceWiringTest` for:
     - `FourD4YParser.parseCategories` parsing an authenticated index containing a member-only forum named `Category` (or a sample protected forum link with nested `<span>`/extra tags).
     - `FourD4YService.fetchCategories()` preserving/returning protected authenticated categories and saving SID from authenticated index.
     - `FourD4YParser.parseThreadRows` fallback parsing category pages where rows are not `<tbody id="normalthread_...">` but contain `viewthread.php?tid=` links.
2. Patch `FourD4YParser.parseThreadRows` with an iOS-equivalent fallback:
   - First parse normal `tbody id=(normalthread_|thread_)`.
   - If empty, scan all `viewthread.php?tid=` links.
   - Use surrounding `<tr>`, `<li>`, or `<tbody>` context.
   - Clean link titles and extract author/reply/last post when possible.
3. Patch `FourD4YService.fetchCategoryThreads` URL behavior to match iOS:
   - Ensure SID is initialized by fetching categories if missing.
   - Prefer URL order `fid=ID&sid=SID&page=N&_t=timestamp` or at least add `_t`.
   - If page 1 returns empty and auth exists, retry once after refreshing categories/SID.
4. Patch authenticated category behavior:
   - Make `validateSessionHtml` not depend only on `Discovery`; accept any protected/member forum when logout exists and no login/challenge.
   - Save SID even if forum list includes member-only categories other than `Discovery`.
   - Broaden `parseCategories` to handle nested tags/absolute URLs/entities/classes and dedupe by fid.
5. Run validation:
   - `:core:data:test :app:assembleDebug`
   - Pixel connected tests
   - Install/launch on Pixel
6. Commit and push to `webrules-android main`.
7. If possible, do a live Pixel flow: login to 4D4Y, confirm member-only `Category` appears, open it, confirm thread list shows non-empty rows.
</next_steps>