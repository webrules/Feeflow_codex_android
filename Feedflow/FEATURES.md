# Feedflow Feature List
Generated from source analysis — use for test case creation.

---

## 1. Multi-Site Forum Aggregation

| # | Feature | Source | Notes |
|---|---------|--------|-------|
| 1.1 | Browse 7 forum sites in one app | `ForumSite` enum in `SiteListView.swift` | RSS, HackerNews, 4D4Y, V2EX, Linux.do, Zhihu |
| 1.2 | Toggle community visibility on/off (except RSS) | `CommunitySettingsManager` in `SiteListView.swift` | RSS always enabled |
| 1.3 | Navigate between communities via `NavigationStack` | `NavigationManager` | Deep linking support |
| 1.4 | Each forum has unique `ForumService` implementation | `ForumService` protocol | Polymorphic service layer |

## 2. Authentication & Session Management

| # | Feature | Source |
|---|---------|--------|
| 2.1 | Per-site login via WKWebView | `WebLoginView.swift`, `LoginView.swift` |
| 2.2 | OAuth provider support (Google, GitHub, Apple, etc.) | `SiteLoginConfig.OAuthOption` |
| 2.3 | Login status display per site (✓ / ○) | `LoginView.siteSelector` |
| 2.4 | Logout per site (clear cookies, credentials) | `LoginView.logout()` |
| 2.5 | Cookie persistence to SQLite DB (encrypted) | `DatabaseManager.replaceCookies()` |
| 2.6 | Session cookie → 30-day persistent upgrade | `LoginView.handleLoginSuccess()` |
| 2.7 | Session restoration on app launch | `ForumService.restoreSession()` |
| 2.8 | Session validation (HTML-based: check for logout link) | `FourD4YService.validateSession()` |
| 2.9 | Auto-login with encrypted saved credentials | `FourD4YService.performAutoLogin()` |
| 2.10 | Credential encryption (AES-GCM + Keychain) | `EncryptionHelper` |
| 2.11 | Stale cookie cleanup on validation failure | `FourD4YService.restoreSession()` |
| 2.12 | SID extraction from HTML, guarded against guest SID poisoning | `FourD4YService.extractSID()` |

## 3. Content Browsing

| # | Feature | Source |
|---|---------|--------|
| 3.1 | Fetch community/category list per site | `ForumService.fetchCategories()` |
| 3.2 | Paginated thread list per category | `ThreadListViewModel`, `fetchCategoryThreads` |
| 3.3 | Pull-to-refresh thread lists | `ThreadListView.refreshable` |
| 3.4 | Thread detail with full content + comments | `ThreadDetailView` |
| 3.5 | Paginated comment loading ("load more") | `ThreadDetailViewModel.loadComments()` |
| 3.6 | Navigate previous/next thread in list | `ThreadDetailView` prev/next buttons |
| 3.7 | Thread bookmarking (toggle, persist to DB) | `DatabaseManager.bookmarkThread/unbookmark` |
| 3.8 | URL bookmarking from InAppBrowser | `DatabaseManager.addURLBookmark/removeURLBookmark` |
| 3.9 | View all bookmarks (threads + URLs) | `BookmarksView` |
| 3.10 | Delete bookmarks | `BookmarksViewModel.removeURLBookmark()` |
| 3.11 | In-app web browser for external links | `InAppBrowserView` |
| 3.12 | Full-screen image viewer (zoom, rotate) | `FullScreenImageView` |
| 3.13 | Avatar display (async image, fallback SF Symbol) | `AvatarView` |
| 3.14 | Community list caching to SQLite | `DatabaseManager.saveCommunities/getCommunities` |
| 3.15 | Cache-preservation fallback when fetch returns fewer items | `ForumViewModel.resolveCommunitiesAfterFetch()` |
| 3.16 | Background prefetch of thread details for reading offline | `ThreadListViewModel` prefetch queue |
| 3.17 | Network-aware prefetch (WiFi only, user toggle) | `NetworkMonitor.isWiFi`, Settings toggle |

## 4. Reading & Accessibility

| # | Feature | Source |
|---|---------|--------|
| 4.1 | Text-to-speech for thread content | `SpeechService` (AVSpeechSynthesizer) |
| 4.2 | Speech supports zh-CN and en-US voices | `SpeechService.speak()` language mapping |
| 4.3 | Tap-to-stop speaking | `SpeechService.speak()` toggle behavior |
| 4.4 | Dark mode support | `ThemeManager.isDarkMode` |
| 4.5 | EN / 中文 language switcher | `LocalizationManager` |
| 4.6 | All UI strings localized (~100+ keys) | `LocalizationManager.localizedString()` |
| 4.7 | HTML entity decoding in titles/content | `String.decodingHTMLEntities()` |

## 5. Content Interaction

| # | Feature | Source |
|---|---------|--------|
| 5.1 | Post comment/reply to threads | `ForumService.postComment()` |
| 5.2 | Reply to specific comment (threaded UI) | `ThreadDetailView` reply toolbar |
| 5.3 | Reply error handling with alert | `ThreadDetailView` alert binding |
| 5.4 | Create new thread in a category | `NewThreadView`, `ForumService.createThread()` |
| 5.5 | Like/unlike thread | `Thread.isLiked` toggle (service-dependent) |
| 5.6 | Share thread URL | `ThreadDetailView` share button |
| 5.7 | Open thread in external browser | `ThreadDetailView` Safari button |

## 6. AI Features

| # | Feature | Source |
|---|---------|--------|
| 6.1 | AI summary of single thread (Gemini) | `AISummaryView` |
| 6.2 | Cross-site AI summary (all sites, daily digest) | `CrossSiteAISummaryView` |
| 6.3 | Daily RSS summary with AI-generated digest | `DailyRSSSummaryView` |
| 6.4 | Summary caching (7-day TTL) | `DailyRSSSummaryViewModel.cacheKey/cacheMaxAge` |
| 6.5 | Gemini API key configurable in Settings | `SettingsView` |
| 6.6 | AI summaries persisted to SQLite | `DatabaseManager` `ai_summaries` table |

## 7. RSS Feed Management

| # | Feature | Source |
|---|---------|--------|
| 7.1 | Add custom RSS feeds (name + URL) | `RSSFeedManagerView` (DataImportView.swift) |
| 7.2 | Import OPML files | `OPMLParser`, file picker |
| 7.3 | Edit feed list (delete feeds) | `RSSFeedManagerView.editMode` |
| 7.4 | Parse RSS 2.0 + Atom feeds | `RSSParser` |
| 7.5 | RSS feed content with HTML cleanup | `RSSService` HTML processing |
| 7.6 | Feed list persisted | RSS service manages subscriptions |

## 8. Settings & Preferences

| # | Feature | Source |
|---|---------|--------|
| 8.1 | Gemini API key management (save/load/clear) | `SettingsView` |
| 8.2 | Background prefetch toggle | `SettingsView` `background_prefetch` |
| 8.3 | Dark mode toggle | `SiteListView` theme button |
| 8.4 | Language toggle (EN/中文) | `SiteListView` language button |
| 8.5 | Community enable/disable | `CommunityConfigView` |
| 8.6 | Language preference persists to UserDefaults | `LocalizationManager` |
| 8.7 | Theme preference persists | `ThemeManager` |

## 9. Data Persistence

| # | Feature | Source |
|---|---------|--------|
| 9.1 | SQLite database for all persistent data | `DatabaseManager` |
| 9.2 | Encrypted cookie storage | `DatabaseManager.persistCookies()` + `EncryptionHelper` |
| 9.3 | Encrypted credential storage | `DatabaseManager.saveSetting()` login keys |
| 9.4 | Community list cache | `communities` table |
| 9.5 | Bookmarked threads + URLs | `bookmarks` / `url_bookmarks` tables |
| 9.6 | AI summaries cache | `ai_summaries` table |
| 9.7 | Settings key-value store | `settings` table |
| 9.8 | Thread-safe DB access (serial queue) | `DatabaseManager.dbQueue` |
| 9.9 | Schema migration support | `DatabaseSchemaMigration` |

## 10. Per-Site Service Specifics

| # | Site | Features |
|---|------|----------|
| 10.1 | **4D4Y** | Discuz 7.2 forum scraping, GBK encoding, SID/formHash extraction, Cloudflare clearance, native login POST |
| 10.2 | **V2EX** | HTML scraping, named entity decoding, OAuth (Google, Solana) |
| 10.3 | **Linux.do** | Discourse JSON API, OAuth (Google, GitHub, X, Discord, Apple, Passkey) |
| 10.4 | **HackerNews** | Firebase REST API, no login required, karma/comment counts |
| 10.5 | **Zhihu** | JSON API with cookie auth, `z_c0` token, recommendations feed, post filtering, downvote tracking |
| 10.6 | **RSS** | Custom feed management, OPML import, XML parsing |
| 10.7 | **All sites** | HTML entity decoding (`&#12290;` → `。`), GBK/UTF-8 charset handling, time-ago formatting |

## 11. Error Handling & Edge Cases

| # | Scenario | Handling |
|---|----------|----------|
| 11.1 | Session expired mid-use | `validateSession` → `needsLogin` → Login sheet |
| 11.2 | Login required for content | `ForumViewModel.needsLogin` triggers `LoginView` |
| 11.3 | Empty thread list (retry with auto-login) | `fetchCategoryThreadsInternal` retry logic |
| 11.4 | Network unavailable | `NetworkMonitor.isConnected`, prefetch paused |
| 11.5 | TLS/DNS failures (e.g., img01.4d4y.com) | Avatar fallback to SF Symbol |
| 11.6 | Reply posting failure | Alert with error message |
| 11.7 | AI summary generation failure | `errorMessage` state + cached fallback |
| 11.8 | Cookie serialization failure | `HTTPCookie(properties:)` nil guard → skip + log |
| 11.9 | Concurrent refresh collision | `ThreadListViewModel` ignores empty refresh to preserve existing threads |
| 11.10 | Cloudflare challenge page | SID guarded extraction, stale cookie cleanup |
