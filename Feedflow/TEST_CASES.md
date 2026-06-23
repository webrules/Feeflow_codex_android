# Feedflow Test Case Recommendations
> Suggested test cases mapped to each feature. **No code changes until confirmed.**

---

## 1. Multi-Site Forum Aggregation

### 1.1 Browse 7 forum sites in one app
- **[UI]** Verify all 7 sites (RSS, HackerNews, 4D4Y, V2EX, Linux.do, Zhihu) appear as cards on home screen
- **[UI]** Tap each card → verify correct `CommunitiesView` loads with matching title
- **[UI]** Verify site cards re-render correctly when `CommunitySettingsManager.enabledSites` changes
- **[Unit]** `ForumSite.allCases.count == 6` (RSS is separate)
- **[Unit]** `ForumSite.from(serviceId:)` returns correct enum for all valid IDs
- **[Unit]** `ForumSite.from(serviceId:)` returns nil for invalid ID

### 1.2 Toggle community visibility on/off
- **[UI]** Open CommunityConfigView → all sites show toggles ON
- **[UI]** Toggle 4D4Y OFF → 4D4Y card disappears from home screen
- **[UI]** Toggle 4D4Y ON → 4D4Y card reappears
- **[UI]** RSS toggle is locked (shows seal icon, cannot toggle)
- **[Unit]** `CommunitySettingsManager.toggle(.rss)` does nothing (RSS always enabled)
- **[Unit]** `CommunitySettingsManager.isEnabled()` returns true for RSS when all others off
- **[Unit]** Toggle state persists after `UserDefaults` reset simulation

### 1.3 Navigate between communities via NavigationStack
- **[UI]** Tap site → push to CommunitiesView → tap community → push to ThreadListView → tap thread → push to ThreadDetailView
- **[UI]** Use system back swipe → verify each level pops correctly
- **[UI]** Tap home icon → verify `popToRoot()` returns to SiteListView
- **[Unit]** `NavigationManager.popToRoot()` clears path

### 1.4 Polymorphic ForumService
- **[Unit]** `ForumSite.fourD4Y.makeService()` returns `FourD4YService`
- **[Unit]** `ForumSite.linuxDo.makeService()` returns `DiscourseService`
- **[Unit]** All 6 services conform to `ForumService` protocol
- **[Unit]** Default protocol extensions (`requiresLogin`, `supportsCommenting`, `restoreSession`) return correct defaults

---

## 2. Authentication & Session Management

### 2.1 Per-site login via WKWebView
- **[UI]** Tap Login on home → select 4D4Y → WebLoginSheetView opens with `logging.php?action=login`
- **[UI]** Enter credentials in WebView → success → checkmark animation → auto-dismiss
- **[UI]** Tap Cancel during login → sheet dismisses without saving
- **[UI]** "Save Session" button captures cookies even before auto-detection triggers
- **[Integration]** Login → verify cookies stored in DatabaseManager for correct siteId

### 2.2 OAuth provider support
- **[UI]** V2EX login shows Google + Solana OAuth buttons
- **[UI]** Linux.do login shows Google, GitHub, X, Discord, Apple, Passkey buttons
- **[UI]** Tap OAuth button → `oauthOverrideURL` set → WebView opens provider URL
- **[UI]** Successful OAuth login → cookies captured and validated
- **[Unit]** `SiteLoginConfig.config(for: .linuxDo)` returns config with 6 oauthOptions
- **[Unit]** `SiteLoginConfig.config(for: .rss)` returns nil

### 2.3 Login status display per site
- **[UI]** Logged-in site shows ✓ (green checkmark.circle.fill) on LoginView selector
- **[UI]** Logged-out site shows ○ (gray circle)
- **[UI]** Logout → icon changes to ○ immediately
- **[Integration]** App restart with valid cookies → status shows ✓

### 2.4 Logout per site
- **[UI]** Tap logout on signed-in card → status changes to ○
- **[Integration]** Logout → verify DB cookies cleared, HTTPCookieStorage cleared, WKWebView cookies cleared
- **[Integration]** Logout → verify `login_*_username` and `login_*_password` settings removed
- **[Integration]** Logout from one site does not affect other sites' login status

### 2.5 Cookie persistence to SQLite DB
- **[Unit]** `DatabaseManager.replaceCookies(siteId: "4d4y", cookies: [...])` → stored encrypted
- **[Unit]** `DatabaseManager.getCookies(siteId: "4d4y")` returns matching cookies
- **[Unit]** Cookie with nil expiresDate → stored without `expires` key → deserialized without crash
- **[Unit]** Cookie serialization round-trip preserves: name, value, domain, path, secure, httpOnly
- **[Unit]** `DatabaseManager.clearCookies()` removes all cookies for site

### 2.6 Session cookie → 30-day persistent upgrade
- **[Unit]** Cookie with `expiresDate == nil` → `persistentCookies` map adds 30-day expiration
- **[Unit]** Cookie with existing `expiresDate` → unchanged
- **[Unit]** New expiration date is within ±5 seconds of `Date() + 30 * 24 * 3600`

### 2.7 Session restoration on app launch
- **[Integration]** App launch with valid DB cookies → `restoreSession()` returns true → no login prompt
- **[Integration]** App launch with invalid/stale cookies → `restoreSession()` returns false → login prompt shown
- **[Integration]** `restoreSession()` tries: DB cookies → WKWebView cookies → auto-login (in order)
- **[Integration]** WKWebView has valid cookies but DB empty → WKWebView cookies saved to DB

### 2.8 Session validation (logout link check)
- **[Integration]** `validateSession` with valid auth cookies → index.php has "退出" → returns true
- **[Integration]** `validateSession` with expired auth → index.php has "登录" → returns false
- **[Integration]** `validateSession` with valid auth but Cloudflare challenge page → returns false (no forum links)
- **[Unit]** Empty HTML → `matches.isEmpty` → returns false
- **[Unit]** HTML with forum links but only guest indicators → returns false

### 2.9 Auto-login with encrypted credentials
- **[Integration]** `performAutoLogin()` with valid encrypted credentials → successful login → cookies stored
- **[Integration]** `performAutoLogin()` with invalid/expired credentials → returns false, no crash
- **[Integration]** `performAutoLogin()` with no saved credentials → returns false immediately

### 2.10 Credential encryption
- **[Unit]** Encrypt "username" → decrypt → equals "username"
- **[Unit]** Encrypt "password!@#$%" → decrypt → equals original
- **[Unit]** Encrypt empty string → decrypt → equals empty string
- **[Unit]** Encryption key persists across `EncryptionHelper` re-instantiation (Keychain)
- **[Unit]** Legacy key migration: data encrypted with old hardcoded key → can be decrypted

### 2.11 Stale cookie cleanup
- **[Integration]** `validateSession` returns false → `DatabaseManager.clearCookies()` called
- **[Integration]** `clearSystemCookies` called for domain "4d4y.com"
- **[Integration]** Next `restoreSession()` call does NOT retry with same stale cookies

### 2.12 SID extraction guarded against guest poisoning
- **[Unit]** Guest page HTML (has "登录" not "退出") → `extractSID` NOT called
- **[Unit]** Logged-in page HTML (has "退出") → `extractSID` called, `currentSID` updated
- **[Integration]** Transient Cloudflare page → guest SID not extracted → real SID preserved
- **[Unit]** `extractSID` regex matches `sid=abc123` pattern in HTML

---

## 3. Content Browsing

### 3.1 Fetch community/category list per site
- **[Integration]** `fetchCategories()` for 4D4Y → returns [Community] with non-empty array
- **[Integration]** `fetchCategories()` for HackerNews → returns HackerNews categories
- **[Integration]** `fetchCategories()` fails with network error → throws, caught by ViewModel
- **[Integration]** Empty HTML response → returns empty array → triggers auto-login retry

### 3.2 Paginated thread list per category
- **[Integration]** `fetchCategoryThreads(categoryId: "2", page: 1)` → returns page 1 threads
- **[Integration]** `fetchCategoryThreads(categoryId: "2", page: 2)` → returns different threads or empty
- **[UI]** Scroll to bottom → load more → `canLoadMore` updates
- **[Edge]** Category has 0 threads → empty state shown
- **[Edge]** Single-page category → `canLoadMore` = false after first load

### 3.3 Pull-to-refresh thread lists
- **[UI]** Pull down on ThreadListView → spinner appears → threads refresh
- **[UI]** Concurrent pull-to-refresh → only one refresh active
- **[UI]** Refresh during loading → gracefully handles

### 3.4 Thread detail with full content + comments
- **[Integration]** Tap thread → `ThreadDetailView` shows title, author, content, comments
- **[Integration]** Thread content with HTML → rendered with `LinkedTextView` (links preserved)
- **[Integration]** Thread content with images → `[IMAGE:url]` placeholders shown
- **[Integration]** Thread with 0 comments → no comment section, no crash
- **[UI]** Thread detail shows author avatar, username, time-ago, like count, comment count

### 3.5 Paginated comment loading
- **[UI]** Tap "Load more" → next page of comments appended
- **[UI]** Last page loaded → "Load more" button hidden (`canLoadMore = false`)
- **[Integration]** Comments with nested replies → `Comment.replies` rendered recursively
- **[Edge]** Single comment, no replies → shown without reply indentation

### 3.6 Navigate previous/next thread in list
- **[UI]** Browse thread #2 of 10 → tap "Previous" → navigates to thread #1
- **[UI]** Browse thread #1 → "Previous" button disabled/hidden
- **[UI]** Browse last thread → "Next" button disabled/hidden
- **[Edge]** Context thread list is empty → prev/next buttons hidden

### 3.7 Thread bookmarking
- **[UI]** Tap bookmark icon → toggles to filled state (`bookmark.fill`)
- **[UI]** Tap again → toggles to outline (`bookmark`)
- **[Integration]** Bookmarked thread appears in BookmarksView
- **[Integration]** Bookmarked thread persists after app restart
- **[Unit]** `DatabaseManager.bookmarkThread(threadId:serviceId:)` → `isBookmarked` returns true
- **[Unit]** `DatabaseManager.unbookmarkThread()` → `isBookmarked` returns false
- **[Unit]** Bookmark same thread twice → no duplicate row

### 3.8 URL bookmarking
- **[UI]** Open InAppBrowser → tap bookmark → URL appears in BookmarksView
- **[Integration]** URL bookmark includes title and timestamp
- **[Unit]** `DatabaseManager.addURLBookmark(url:title:)` → stored with current date
- **[Unit]** `DatabaseManager.removeURLBookmark(url:)` → removed from bookmarks

### 3.9 View all bookmarks
- **[UI]** BookmarksView shows: thread bookmarks (with site icon + title) + URL bookmarks
- **[UI]** Empty state when no bookmarks: shows "no_bookmarks" message
- **[UI]** Tap thread bookmark → navigates to ThreadDetailView
- **[UI]** Tap URL bookmark → opens InAppBrowserView

### 3.10 Delete bookmarks
- **[UI]** Swipe-to-delete or delete button → bookmark removed from list
- **[Integration]** Delete URL bookmark → removed from DB → list updates

### 3.11 In-app web browser
- **[UI]** InAppBrowserView loads URL → shows progress bar
- **[UI]** Navigation controls: back, forward, refresh, share
- **[UI]** Bookmark button in toolbar → saves current URL
- **[Integration]** URLs with custom schemes handled gracefully (no crash)

### 3.12 Full-screen image viewer
- **[UI]** Tap image link → FullScreenImageView opens with image
- **[UI]** Pinch-to-zoom → image scales
- **[UI]** Rotation gesture → image rotates
- **[UI]** Tap background → dismiss viewer
- **[Edge]** Image URL fails to load → shows placeholder or error state

### 3.13 Avatar display
- **[UI]** Valid image URL → async loads and displays circular avatar
- **[UI]** Invalid/404 image URL → falls back to SF Symbol (person.circle)
- **[UI]** Empty avatar string → shows fallback with username initial
- **[Edge]** Very large avatar → renders at constrained size, no memory issues
- **[Unit]** `isGenericAvatar()` correctly identifies "person.circle" variants

### 3.14 Community list caching
- **[Unit]** `DatabaseManager.saveCommunities()` stores communities for service
- **[Unit]** `DatabaseManager.getCommunities(forService:)` returns cached communities
- **[Integration]** App offline → shows cached communities instead of error
- **[Integration]** Fresh fetch → cache updated with new data

### 3.15 Cache-preservation fallback
- **[Integration]** Fresh fetch returns 8 forums (was 14) → `resolveCommunitiesAfterFetch` preserves 6 cached
- **[Integration]** Fresh fetch returns MORE forums → all used, no cache preserved
- **[Unit]** For non-4D4Y services → always returns fresh fetch result

### 3.16 Background prefetch
- **[Integration]** With WiFi + prefetch enabled → threads prefetched silently
- **[Integration]** No WiFi or prefetch disabled → `[Prefetch] Paused: Not on WiFi` logged
- **[Unit]** `maxPrefetchQueueSize = 5` → at most 5 threads queued

### 3.17 Network-aware prefetch
- **[Unit]** Simulator → `NetworkMonitor.isWiFi` returns true (simulator fallback)
- **[Unit]** Device on cellular → `NetworkMonitor.isWiFi` returns false
- **[Integration]** Switch from WiFi to cellular → prefetch pauses

---

## 4. Reading & Accessibility

### 4.1 Text-to-speech
- **[UI]** Tap speak button → thread content read aloud via AVSpeechSynthesizer
- **[UI]** Tap again while speaking → stops
- **[Unit]** `SpeechService.isSpeaking` toggles correctly on speak/stop/finish

### 4.2 Speech language support
- **[Unit]** App language "zh" → `AVSpeechSynthesisVoice(language: "zh-CN")`
- **[Unit]** App language "en" → `AVSpeechSynthesisVoice(language: "en-US")`
- **[Integration]** Chinese thread in zh mode → Mandarin voice used
- **[Integration]** English thread in en mode → English voice used

### 4.3 Tap-to-stop speaking
- **[UI]** Speaking in progress → `isSpeaking = true` → tap → stops immediately (`.immediate`)
- **[UI]** `speechSynthesizer(didFinish:)` → `isSpeaking = false`
- **[UI]** `speechSynthesizer(didCancel:)` → `isSpeaking = false`

### 4.4 Dark mode
- **[UI]** Toggle theme → all views switch to dark color scheme
- **[UI]** Toggle back → all views switch to light color scheme
- **[Integration]** Dark mode preference persists after app restart
- **[UI]** `.preferredColorScheme()` applied to WindowGroup

### 4.5 EN / 中文 language switcher
- **[UI]** Home toolbar shows "EN" or "中" → tap toggles
- **[UI]** All visible text strings change language immediately
- **[Integration]** Language preference persists to UserDefaults

### 4.6 Localized strings (~100+ keys)
- **[Unit]** Every key in `LocalizationManager.localizedString()` has both "en" and "zh" translations
- **[Unit]** Missing key → returns key itself (no crash)
- **[UI]** Login, Settings, Bookmarks, ThreadDetail → all text matches selected language

### 4.7 HTML entity decoding
- **[Unit]** `"&#12290;".decodingHTMLEntities()` → `"。"`
- **[Unit]** `"&amp;".decodingHTMLEntities()` → `"&"`
- **[Unit]** `"&#x4E2D;".decodingHTMLEntities()` → `"中"`
- **[Unit]** `"&mdash;".decodingHTMLEntities()` → `"—"`
- **[Unit]** String without entities → returns unchanged
- **[Unit]** Mixed named + numeric entities → all decoded correctly

---

## 5. Content Interaction

### 5.1 Post comment/reply
- **[Integration]** `postComment(topicId:categoryId:content:)` with valid SID → comment posted
- **[Integration]** `postComment` with expired SID → server rejects, error thrown
- **[Integration]** Empty content → service may reject or post empty (site-dependent)
- **[UI]** After successful post → `shouldScrollAfterReply = true` → auto-scrolls

### 5.2 Reply to specific comment
- **[UI]** Tap reply on comment → reply toolbar shows "Replying to @username"
- **[UI]** Type reply text → tap send → comment posted
- **[UI]** Tap cancel → reply toolbar dismisses, `replyingTo` set to nil

### 5.3 Reply error handling
- **[UI]** Network error during post → alert shows "reply_failed" with error message
- **[UI]** Dismiss alert → reply text preserved, user can retry
- **[UI]** Server returns error → alert shows server message

### 5.4 Create new thread
- **[UI]** Tap compose → NewThreadView opens with category name
- **[UI]** Title and content fields → both required, validation prevents empty post
- **[UI]** Successful post → view dismisses
- **[UI]** Post failure → error message shown, fields preserved
- **[Integration]** Thread appears in category list after posting

### 5.5 Like/unlike thread
- **[UI]** Tap like → `Thread.isLiked` toggles, count updates
- **[Edge]** Like on service without like API → handled gracefully

### 5.6 Share thread URL
- **[UI]** Tap share → iOS share sheet opens with thread URL
- **[Unit]** `service.getWebURL(for: thread)` returns valid URL string

### 5.7 Open in external browser
- **[UI]** Tap Safari button → opens thread in system browser (SFSafariViewController / openURL)

---

## 6. AI Features

### 6.1 AI thread summary (Gemini)
- **[UI]** Open thread → tap AI summary → loading indicator → summary text appears
- **[UI]** Cached summary → shown immediately (no loading)
- **[Integration]** Gemini API call → response parsed → summary displayed
- **[Edge]** Gemini API key not configured → error message shown

### 6.2 Cross-site AI summary
- **[UI]** Tap sparkles on home → CrossSiteAISummaryView opens
- **[UI]** Sections loaded per site → each section shows AI-generated digest
- **[Edge]** All sites fail → empty state or error message

### 6.3 Daily RSS summary
- **[UI]** DailyRSSSummaryView shows article count + AI-generated digest
- **[Integration]** RSS articles fetched → sent to Gemini → summary generated
- **[Integration]** Fewer than N articles → summary still generated

### 6.4 Summary caching (7-day TTL)
- **[Unit]** Summary < 7 days old → `isCached = true` → returned from cache
- **[Unit]** Summary > 7 days old → `isCached = false` → fresh generation triggered
- **[Unit]** No cached summary → fresh generation

### 6.5 Gemini API key configuration
- **[UI]** Settings → enter API key → Save → key stored encrypted
- **[UI]** Clear API key → key removed from storage
- **[UI]** Invalid API key → AI features show appropriate error

### 6.6 AI summaries persisted to SQLite
- **[Unit]** `ai_summaries` table: `thread_id + service_id` as composite primary key
- **[Unit]** INSERT or REPLACE → no duplicate rows
- **[Unit]** Query with `maxAgeSeconds` → returns nil for expired summaries

---

## 7. RSS Feed Management

### 7.1 Add custom RSS feeds
- **[UI]** RSSFeedManagerView → tap Add → enter name + URL → feed added to list
- **[UI]** Invalid URL → validation error shown
- **[UI]** Empty name → validation error
- **[Integration]** New feed content fetched and displayed in thread list

### 7.2 Import OPML files
- **[UI]** Tap Import → file picker opens → select .opml file
- **[Integration]** OPMLParser extracts feeds → list updated
- **[Edge]** Invalid OPML file → error shown, no crash
- **[Edge]** Empty OPML file → empty list, no crash

### 7.3 Edit feed list
- **[UI]** Edit mode → delete button appears per feed
- **[UI]** Delete feed → removed from list
- **[Integration]** Deleted feed no longer appears in thread list

### 7.4 Parse RSS 2.0 + Atom feeds
- **[Unit]** RSSParser with valid RSS 2.0 XML → returns items with title, link, pubDate
- **[Unit]** RSSParser with valid Atom XML → returns entries with title, link, updated
- **[Unit]** Malformed XML → parser handles gracefully (no crash)
- **[Unit]** XML without items/entries → returns empty array

### 7.5 RSS feed content with HTML cleanup
- **[Unit]** RSS content with `<br/>` tags → converted to newlines
- **[Unit]** RSS content with images → `[IMAGE:url]` placeholders
- **[Unit]** RSS content with links → `[LINK:url|title]` format preserved

### 7.6 Feed list persisted
- **[Integration]** Add feed → kill app → restart → feed still in list

---

## 8. Settings & Preferences

### 8.1 Gemini API key management
- **[Integration]** Save key → stored encrypted → read back decrypted
- **[UI]** Save button enables only when key field is non-empty
- **[UI]** Clear → key field emptied, storage cleared

### 8.2 Background prefetch toggle
- **[UI]** Toggle ON → prefetch activates (when on WiFi)
- **[UI]** Toggle OFF → prefetch stops immediately
- **[Integration]** Toggle state persists via UserDefaults

### 8.3 Dark mode toggle
- **[UI]** Tap theme button → dark mode toggles → all views update
- **[UI]** Rapidly toggle → no flickering or crash

### 8.4 Language toggle
- **[UI]** Tap "EN" / "中" → language toggles → all strings update
- **[UI]** Rapidly toggle → no flickering or crash

### 8.5 Community enable/disable
- **[Unit]** `visibleSites` reflects enabled status immediately
- **[Unit]** All disabled + RSS → `visibleSites` contains only RSS

### 8.6 Language preference persists
- **[Integration]** Set language to "zh" → kill app → restart → language is "zh"

### 8.7 Theme preference persists
- **[Integration]** Set dark mode → kill app → restart → dark mode active

---

## 9. Data Persistence

### 9.1 SQLite database
- **[Integration]** App first launch → `openDatabase()` creates DB at expected path
- **[Integration]** DB schema created with all tables (settings, communities, bookmarks, etc.)
- **[Integration]** Corrupted DB → error logged, app doesn't crash
- **[Integration]** Concurrent reads/writes → serial queue prevents race conditions

### 9.2 Encrypted cookie storage
- **[Unit]** `persistCookies` → cookies serialized to JSON → encrypted → stored
- **[Unit]** `getCookies` → decrypted → JSON parsed → HTTPCookie array
- **[Unit]** Empty cookie array → `getCookies` returns nil, no crash

### 9.3 Encrypted credential storage
- **[Unit]** `saveSetting(key: "login_4d4y_username", value: encryptedUsername)` → stored
- **[Unit]** `getSetting(key: "login_4d4y_username")` → returns encrypted value
- **[Unit]** Nonexistent key → `getSetting` returns nil

### 9.4 Community list cache
- **[Unit]** Save 14 communities → `getCommunities` returns 14
- **[Unit]** Save 0 communities → `getCommunities` returns empty array

### 9.5 Bookmarked threads + URLs
- **[Unit]** Bookmark thread A, B, C → `getBookmarkedThreads` returns [A, B, C]
- **[Unit]** Unbookmark B → `getBookmarkedThreads` returns [A, C]
- **[Unit]** `getURLBookmarks` returns entries sorted by date

### 9.6 AI summaries cache
- **[Unit]** Save summary → retrieve within TTL → returns summary
- **[Unit]** Save summary → query with maxAge 0 → returns nil (expired)

### 9.7 Settings key-value store
- **[Unit]** `saveSetting(key:value:)` → `getSetting(key:)` returns value
- **[Unit]** Update existing key → overwrites old value
- **[Unit]** `removeSetting(key:)` → `getSetting(key:)` returns nil

### 9.8 Thread-safe DB access
- **[Unit]** 100 concurrent reads → all succeed, no crash
- **[Unit]** Concurrent read + write → write completes before read returns stale data

### 9.9 Schema migration
- **[Unit]** `needsCompositePrimaryKeyMigration` with different keys → returns true
- **[Unit]** `needsCompositePrimaryKeyMigration` with same keys → returns false
- **[Integration]** Old schema → app auto-migrates → data preserved

---

## 10. Per-Site Service Specifics

### 10.1 4D4Y (Discuz 7.2)
- **[Unit]** GBK-encoded HTML → correctly decoded to String
- **[Unit]** UTF-8 HTML → falls back to UTF-8 decoding
- **[Unit]** `extractSID` from HTML with `sid=abc123` → `currentSID = "abc123"`
- **[Unit]** `extractFormHash` from HTML → `currentFormHash` set
- **[Integration]** `login()` POST → returns Set-Cookie headers
- **[Integration]** `fetchContent` sends Cookie header manually (not via URLSession auto)
- **[Integration]** Thread detail with multiple pages → parses page count from pagination
- **[Integration]** Post author extraction from `ParsedPostAuthor` struct

### 10.2 V2EX
- **[Unit]** Title with `&amp;` → decoded to `&`
- **[Unit]** "Improved parsing" extracts author and reply count from cell items
- **[Integration]** V2EX login via OAuth → Google/Solana redirect URLs configured
- **[Integration]** Thread detail title from `<h1>` tag

### 10.3 Linux.do (Discourse)
- **[Unit]** `DiscourseTopicListItem` decodes JSON → validates required fields
- **[Unit]** `DiscourseTopicDetail` decodes full JSON with posts
- **[Integration]** `restoreSession()` validates via `/session/current.json`
- **[Integration]** OAuth providers all have valid login paths

### 10.4 HackerNews
- **[Unit]** Firebase API → items parsed correctly (type, by, title, kids)
- **[Unit]** Top stories API → returns up to 500 story IDs
- **[Integration]** No login required → `requiresLogin = false`, `restoreSession` always true

### 10.5 Zhihu
- **[Unit]** `z_c0` token detection → `isAuthenticatedCookieSet` returns true
- **[Integration]** Recommendations feed → parsed from JSON API
- **[Integration]** Post filtering → downvoted/filtered posts excluded
- **[Integration]** `markCurrentZhihuRecommendationAsRead()` called on detail view

### 10.6 RSS
- **[Unit]** RSS 2.0 `<item>` → parsed to Thread
- **[Unit]** Atom `<entry>` → parsed to Thread
- **[Unit]** Feed without dates → time-ago defaults to "now"

### 10.7 All sites (shared)
- **[Unit]** `&#12290;` in 4D4Y title → decoded to `。`
- **[Unit]** `&#x4E2D;` in RSS title → decoded to `中`
- **[Unit]** `calculateTimeAgo(from: Date())` → "just now"
- **[Unit]** `calculateTimeAgo(from: Date() - 3600)` → "1h"
- **[Unit]** `calculateTimeAgo(from: Date() - 86400)` → "1d"

---

## 11. Error Handling & Edge Cases

### 11.1 Session expired mid-use
- **[Integration]** Load thread with expired session → validateSession fails → `needsLogin = true` → LoginView shown
- **[Integration]** After re-login → content loads normally

### 11.2 Login required for content
- **[UI]** First visit to 4D4Y (not logged in) → Login sheet appears automatically
- **[UI]** Dismiss login → empty community page shown

### 11.3 Empty thread list with retry
- **[Integration]** Empty thread list on page 1 → auto-login attempted → retry fetch
- **[Integration]** Auto-login fails (no saved credentials) → returns empty list

### 11.4 Network unavailable
- **[UI]** Airplane mode → all fetches fail gracefully → cached data shown if available
- **[UI]** Network restored → refresh works again

### 11.5 TLS/DNS failures
- **[Integration]** `img01.4d4y.com` DNS failure → avatar falls back to SF Symbol
- **[Integration]** Main domain DNS failure → error state, not crash

### 11.6 Reply posting failure
- **[UI]** Server error during reply → alert with localized "reply_failed" + server error
- **[UI]** Dismiss alert → reply text preserved in text field

### 11.7 AI summary failure
- **[UI]** Gemini API returns error → `errorMessage` set → error shown in UI
- **[UI]** Cached summary available despite API failure → cached version shown

### 11.8 Cookie serialization failure
- **[Unit]** `HTTPCookie(properties:)` returns nil for malformed dict → skipped, logged
- **[Integration]** All cookies fail to deserialize → `getCookies` returns empty array → login required

### 11.9 Concurrent refresh collision
- **[Integration]** Pull-to-refresh while loading → old threads preserved until new load completes
- **[Integration]** Empty result from refresh → `[ThreadListViewModel] Ignored empty refresh to preserve N visible threads`

### 11.10 Cloudflare challenge
- **[Integration]** 4D4Y returns Cloudflare challenge page → SID NOT extracted → validateSession returns false
- **[Integration]** Challenge page does not crash HTML parsing
