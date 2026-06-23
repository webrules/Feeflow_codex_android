# 4D4Y Login — Root Cause Analysis & Test Cases

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│ LOGIN FLOW                                                            │
│                                                                       │
│  LoginView ──► WebLoginView (WKWebView)                               │
│                   │                                                   │
│                   │ checkCookiesWithRetry (8 retries, 1s interval)    │
│                   │   ↓ hasAuthenticatedSession?                      │
│                   │ siteCookies(from:) filters by domain              │
│                   ▼                                                   │
│              onLoginSuccess(cookies)                                  │
│                   │                                                   │
│         handleLoginSuccess(site, cookies)                             │
│           1. domain filter (4d4y.com)                                 │
│           2. isAuthenticatedCookieSet → !cookies.isEmpty (for 4d4y)   │
│           3. persistentCookies: nil expires → 30-day upgrade           │
│           4. DB: replaceCookies(siteId, persistentCookies)             │
│           5. Runtime: replaceRuntimeCookies → HTTPCookieStorage        │
│           6. verify: site.makeService().restoreSession()               │
│              └─ validateSession() → HTTP GET index.php                │
│                 └─ check: forumdisplay links + "退出" + no "登录"      │
│           7. dismiss → nav to site                                    │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│ CONTENT FLOW                                                          │
│                                                                       │
│  ThreadListViewModel.loadTopics(community)                             │
│      │                                                                │
│      ├─1. restoreSession() → returns true/false                       │
│      │     └─ if false + requiresLogin → needsLogin=true, STOP        │
│      │                                                                │
│      ├─2. Check cache: DB.getCachedTopics(cacheKey)                   │
│      │                                                                │
│      ├─3a. forceRefresh → refreshCategoryThreads → update UI          │
│      │       └─ shouldAcceptFreshThreads: if empty for 4d4y → KEEP OLD│
│      ├─3b. isReturning || cacheExists → load cache, background fetch  │
│      │       └─ fetchFreshData: only updates if isAtTop && hasChanges │
│      └─3c. First load → show spinner, fetchFresh                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Bug 1: Login WebView shows logged in but session never saves

### Symptom
User logs in via WKWebView, sees the "欢迎回来" message on 4d4y. `handleLoginSuccess` is called but `loginStatus` stays false and the user sees "未登录" on subsequent app launches.

### Root Cause Chain

#### R1: `isAuthenticatedCookieSet` is too permissive for 4d4y
```swift
// LoginView.swift:414
private func isAuthenticatedCookieSet(site: ForumSite, cookies: [HTTPCookie]) -> Bool {
    case .fourD4Y:
        return !cookies.isEmpty  // ← ANY cookie passes
}
```
**Bug**: WKWebView may have stale `cdb_sid` (guest session cookie) from a previous anonymous visit. `siteCookies` filters by `"4d4y.com"` domain, so `cdb_sid` passes. But `cdb_sid` alone is NOT an auth cookie — you need `cdb_auth`.

**Counter-measure in place**: `handleLoginSuccess` calls `restoreSession()` → `validateSession()` after saving, which does a real HTML check. But if `validateSession` fails, it logs:
```
[Login] WARNING: Session verification FAILED after login for 4d4y
```
…and returns false. But by this point, `replaceCookies` has ALREADY written (possibly incomplete) cookies to DB, polluting the stored session.

**Mitigation already present**: When `validateSession` fails, the cookies are already in DB but `loginStatus` stays false. Next `restoreSession()` call will find them in DB but they'll fail `validateSession` and be left alone while checking WKWebView.

#### R2: Cookie persistence race — `validateSession` uses fresh DB cookies but they may be incomplete
When `handleLoginSuccess` calls `replaceCookies`, it writes persistent cookies. Then `replaceRuntimeCookies` syncs to `HTTPCookieStorage`. Then `restoreSession()` is called which:
1. Loads from DB → finds the just-saved cookies
2. Calls `validateSession(cookies)` which fetches index.php with a manual Cookie header
3. If the server returns a page with `登录` (guest view), validation fails

**Key issue**: The manual Cookie header in `cookieHeader` may not send all cookies if:
- Cookie domain mismatch: `www.4d4y.com` vs `.4d4y.com`
- Cookie path mismatch: stored with `/forum` but index.php is at `/forum/index.php`
- Cookie is session-only and `expiresDate` upgrade didn't work

#### R3: `WebLoginView.checkCookiesWithRetry` dedup logic blocks retry
```swift
// WebLoginView.swift Coordinator
let cookieSignature = self.cookieSignature(for: siteCookies)
guard self.lastRejectedCookieSignature != cookieSignature else {
    AppLogger.debug("[WebLogin] Skipping previously rejected cookie set")
    return
}
```
If `onLoginSuccess` returns false (because `restoreSession` failed), the same cookie set is cached as rejected. Even if new auth cookies arrive on a subsequent navigation, if the signature matches (same cookie names), they're silently skipped.

#### R4: SID cookie overwrites auth cookies in HTTPCookieStorage
```swift
// FourD4YService.swift extractSID:
if let sidCookie = HTTPCookie(properties: [
    .name: "cdb_sid",
    .value: self.currentSID!,
    .domain: "www.4d4y.com",
    .path: "/forum",
    .expires: Date().addingTimeInterval(86400)
]) {
    HTTPCookieStorage.shared.setCookie(sidCookie)
}
```
`extractSID` writes `cdb_sid` (guest SID) to `HTTPCookieStorage.shared`. If later `replaceRuntimeCookies` is called, it first clears system cookies then sets auth cookies. But if `extractSID` runs AFTER `replaceRuntimeCookies`, the guest SID overwrites the auth session.

### Test Cases for Bug 1

| # | Test | What It Catches |
|---|------|----------------|
| 1.1 | `testHandleLoginSuccess_OnlySIDCookie_ReturnsFalse` | R1: `cdb_sid` alone passes `isAuthenticatedCookieSet` but `validateSession` fails |
| 1.2 | `testHandleLoginSuccess_ValidAuthCookies_ReturnsTrue` | Happy path: `cdb_auth` + `cdb_sid` + `cf_clearance` |
| 1.3 | `testHandleLoginSuccess_ExpiredAuthCookie_Fails` | Cookie with past `expiresDate` rejected by `hasDiscuzAuthenticationCookie` |
| 1.4 | `testCookieSignatureDedup_RejectedCookiesBlocked` | R3: Same cookie signature skipped on retry |
| 1.5 | `testCookieSignatureDedup_NewCookiesAfterRejection` | R3: Different cookie signature after rejection IS accepted |
| 1.6 | `testExtractSID_DoesNotOverwriteAuthCookies` | R4: Guest `cdb_sid` doesn't clobber `cdb_auth` in storage |
| 1.7 | `testValidateSession_ReturnsFalseForGuestPage` | R2: HTML with `登录` but no `退出` → validation fails |
| 1.8 | `testValidateSession_ReturnsTrueForLoggedInPage` | HTML with `退出` and forum links → validation succeeds |
| 1.9 | `testValidateSession_ReturnsFalseForCloudflareChallenge` | Cloudflare challenge page → no forum links → fail |
| 1.10 | `testRestoreSession_EmptyDBCookies_TriesWKWebView` | DB empty → checks WKWebView cookies |
| 1.11 | `testRestoreSession_InvalidDBCookies_FallsToWKWebView` | DB cookies fail validation → checks WKWebView |
| 1.12 | `testRestoreSession_WKWebViewHasValidCookies_SavesToDB` | WKWebView has valid → saves to DB, returns true |
| 1.13 | `testRestoreSession_NoCookiesAtAll_TriesAutoLogin` | Nothing in DB or WKWebView → attempts auto-login |
| 1.14 | `testCookieHeader_DomainMatching` | `www.4d4y.com` host matches `.4d4y.com` domain cookie |
| 1.15 | `testCookieHeader_PathMatching` | `/forum/index.php` matches `/forum` path cookie |
| 1.16 | `testCookieHeader_ExpiredCookieOmitted` | Expired cookie NOT included in header |
| 1.17 | `testCookieHeader_SessionCookieIncluded` | Session cookie (nil expires) IS included |
| 1.18 | `testPersistentCookieUpgrade_OnlySessionCookies` | Only nil-expires cookies get 30-day upgrade |
| 1.19 | `testPersistentCookieUpgrade_PreservesExistingExpiry` | Cookies with existing expiresDate unchanged |
| 1.20 | `testPersistentCookieUpgrade_FailedPropertiesRoundTrip` | Cookie with nil properties returns original |

---

## Bug 2: After login, navigating to community shows cached content on refresh

### Symptom
User logs in successfully, navigates to Discovery (fid=2). Sees thread list. Pulls to refresh → same old content. Or: closes app, reopens, navigates to Discovery → sees stale cache, refresh doesn't update.

### Root Cause Chain

#### R5: `shouldAcceptFreshThreads` preserves stale cache when fetch returns empty
```swift
// ThreadListViewModel.swift:198
private func shouldAcceptFreshThreads(_ newThreads: [Thread]) -> Bool {
    let shouldPreserveOnEmpty = (service.id == "4d4y") || (...)
    guard shouldPreserveOnEmpty, newThreads.isEmpty, !threads.isEmpty else {
        return true
    }
    // KEEPS OLD THREADS — even on explicit refresh!
    return false
}
```

**Scenario**: User is on Discovery with 75 cached threads. Session cookie expired. User pulls to refresh:
1. `restoreSession()` → false (cookie expired) → `needsLogin = true`
2. Function returns early — old threads stay on screen, "Login required" message shown
3. OR: `restoreSession()` → true (cookie still partially valid), fetch returns empty (server silently denies)
4. `shouldAcceptFreshThreads` sees empty → keeps old 75 threads
5. User sees NO change, thinks "refresh didn't work"

#### R6: `fetchFreshData` only updates UI when `isAtTop && hasChanges`
```swift
// ThreadListViewModel.swift:157-163
private func fetchFreshData(for community: Community, cacheKey: String) async {
    // ... fetch ...
    if isAtTop && hasChanges(old: self.threads, new: newThreads) {
        self.threads = newThreads
    }
    // If NOT at top: silently discards fresh data!
    // If NO changes: silently discards fresh data!
}
```

**Scenario**: User scrolls down a bit, background fetch completes with new data → not at top → silently discards. User scrolls back up → sees stale data. Pulls to refresh → still sees stale.

#### R7: Cache key collision between logged-in and logged-out states
```swift
let cacheKey = "\(service.id)_\(community.id)_page1"
```
The cache key is `"4d4y_2_page1"` regardless of login state. When user logs in and visits Discovery, `loadTopics` is called:
- `isReturning = false`, `cachedThreads = nil` (first visit after login) → goes to else branch
- Fetches fresh data → saves to cache → shows new content

But if user logged out, browsed Discovery (got guest version with 8 forums), logged back in, returned to Discovery:
- `isReturning = true` or `cachedThreads != nil` → loads stale cache from guest session
- Background `fetchFreshData` runs → if at top & has changes → updates
- But if server hiccup and fetch returns empty → `shouldAcceptFreshThreads` keeps guest cache

#### R8: `restoreSession` may return true with stale/guest cookies
```swift
// FourD4YService.swift:78
func restoreSession() async -> Bool {
    if !savedCookies.isEmpty {
        if await validateSession(cookies: savedCookies) || hasDiscuzAuthenticationCookie(savedCookies) {
            syncCookies(savedCookies)
            return true  // ← returns true even if validateSession returned false!
        }
    }
}
```

**Critical**: Uses `||` — if `validateSession` returns false BUT `hasDiscuzAuthenticationCookie` returns true (because `cdb_auth` contains "auth"), it STILL returns true. Then content fetch runs with a "validated" session that the server doesn't actually accept.

`hasDiscuzAuthenticationCookie` just checks name contains "auth" or "member" and value is non-empty. `cdb_auth` always matches this. So even if the `cdb_auth` value is expired/invalid, `restoreSession` returns true!

### Test Cases for Bug 2

| # | Test | What It Catches |
|---|------|----------------|
| 2.1 | `testForceRefresh_ClearsCache_ShowsFreshData` | forceRefresh bypasses cache, shows new threads |
| 2.2 | `testForceRefresh_EmptyResponse_KeepsOldWithMessage` | R5: Empty refresh → old threads preserved + message |
| 2.3 | `testForceRefresh_SessionExpired_SetsNeedsLogin` | R5: Session expired → needsLogin, old threads preserved |
| 2.4 | `testFetchFreshData_NotAtTop_KeepsOldThreads` | R6: User scrolled → fresh data silently discarded |
| 2.5 | `testFetchFreshData_AtTopWithChanges_UpdatesThreads` | R6: At top + new data → threads update |
| 2.6 | `testFetchFreshData_AtTopNoChanges_KeepsOld` | R6: Same data → no unnecessary update |
| 2.7 | `testCacheKey_IsSameRegardlessOfLoginState` | R7: Cache key doesn't differentiate auth state |
| 2.8 | `testLoadTopics_IsReturning_LoadsStaleCache` | R7: isReturning=true loads cache, may be from different session |
| 2.9 | `testRestoreSession_AuthCookieNameCheck_ButInvalidSession` | R8: `cdb_auth` exists but server rejects → still returns true |
| 2.10 | `testRestoreSession_ValidateFails_AuthCookieCheckPasses` | R8: validateSession=false, hasDiscuzAuth=true → returns true |
| 2.11 | `testRestoreSession_BothChecksFail_ReturnsFalse` | Both checks fail → correctly returns false |
| 2.12 | `testShouldAcceptFreshThreads_4d4yEmptyPreserves` | R5: 4d4y empty result preserves old threads |
| 2.13 | `testShouldAcceptFreshThreads_Non4d4yEmptyAccepts` | Non-4d4y empty result → accepts (clears old) |
| 2.14 | `testHasChanges_DifferentCounts_ReturnsTrue` | Different thread counts → changes detected |
| 2.15 | `testHasChanges_SameCountDifferentIDs_ReturnsTrue` | Same count, different IDs → changes detected |
| 2.16 | `testHasChanges_IdenticalLists_ReturnsFalse` | Same thread list → no changes |
| 2.17 | `testValidateSession_And_HasDiscuzAuth_ShortCircuit` | validateSession passes → hasDiscuzAuth not evaluated |
| 2.18 | `testLoadTopics_FirstVisitAfterLogin_FetchesFresh` | R7: No cache → fresh fetch → shows correct data |

---

## Integration Test Cases (End-to-End)

| # | Test | Scenario |
|---|------|----------|
| 3.1 | `testFullLoginToDiscoveryFlow` | Login via WKWebView → save cookies → navigate to Discovery → see threads |
| 3.2 | `testLoginThenAppRestart` | Login → save cookies → simulate app restart → restoreSession → threads load |
| 3.3 | `testLoginThenRefresh_SeesNewContent` | Login → cache old threads → new thread posted on server → refresh → new thread visible |
| 3.4 | `testSessionExpiryMidUse` | Login → browse → cookies expire → refresh → login prompt (not stale cache) |
| 3.5 | `testCloudflareChallenge_DoesNotCacheGuestPage` | Cloudflare challenge page → not cached as valid community list |
| 3.6 | `testAutoLogin_WithSavedCredentials` | Saved encrypted credentials → auto-login on session expiry → new cookies obtained |
| 3.7 | `testLogout_ClearsCookiesFromAllSources` | Logout → DB cleared → HTTPCookieStorage cleared → WKWebView cleared |

---

## Priority Fixes

### Critical (likely causing both bugs)
1. **R8**: `restoreSession` uses `||` between `validateSession` and `hasDiscuzAuthenticationCookie`.
   `hasDiscuzAuthenticationCookie` is a weak check (just name contains "auth") and returns true for expired/bad cookies.
   **Fix**: Only return true if `validateSession` passes. Use `hasDiscuzAuthenticationCookie` as a pre-filter, not a bypass.

2. **R7**: Cache key doesn't include auth state. Stale cache from guest session poisons logged-in view.
   **Fix**: Include a hash of auth state in cache key or clear community caches on login/logout.

### High
3. **R1**: `isAuthenticatedCookieSet` for 4d4y returns true for any cookie. 
   **Fix**: Check for `cdb_auth` specifically (matching the `authCookieNameFragments: ["auth", "login", "member"]` in config).

4. **R5/R6**: `shouldAcceptFreshThreads` preserves stale data without informing user.
   **Fix**: When fresh threads are empty but old threads exist on explicit refresh, show a distinct "session may have expired" message and offer re-login.

### Medium
5. **R3**: Cookie signature dedup can block valid retries.
   **Fix**: Clear rejected signature on new navigation or after a timeout.

6. **R4**: SID extraction sets `cdb_sid` in shared cookie storage which can race with auth cookies.
   **Fix**: Use a separate in-memory store for SID, not `HTTPCookieStorage.shared`.
