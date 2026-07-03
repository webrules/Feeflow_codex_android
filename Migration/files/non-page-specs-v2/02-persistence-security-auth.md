# Persistence, security, and authentication technical design

## iOS anchors

- `Feedflow/DatabaseManager.swift`
- `Feedflow/Services/EncryptionHelper.swift`
- `Feedflow/Views/LoginView.swift`
- `Feedflow/Views/WebLoginView.swift`
- `Feedflow/Views/SettingsView.swift`

## SQLite configuration

iOS opens `Feedflow.sqlite` in the app Documents directory, enables WAL, sets a 5-second busy timeout, and serializes all DB access through `com.feedflow.database`.

Android must:

- Use a single app database named `Feedflow.sqlite` unless Room naming conventions require extension handling.
- Enable WAL.
- Use a single-writer strategy through Room/SQLDelight transaction boundaries and coroutine dispatcher control.
- Include migration tests for every schema version.

## Required schema

Use these exact logical table names and columns. Room can add internal metadata only if tests verify user tables remain compatible.

```sql
CREATE TABLE IF NOT EXISTS communities(
  id TEXT,
  name TEXT,
  description TEXT,
  category TEXT,
  activeToday INTEGER,
  onlineNow INTEGER,
  serviceId TEXT,
  PRIMARY KEY (id, serviceId)
);

CREATE TABLE IF NOT EXISTS settings(
  key TEXT PRIMARY KEY,
  value TEXT
);

CREATE TABLE IF NOT EXISTS filtered_posts(
  postId TEXT PRIMARY KEY,
  serviceId TEXT,
  filteredAt INTEGER
);

CREATE TABLE IF NOT EXISTS ai_summaries(
  thread_id TEXT,
  service_id TEXT DEFAULT '',
  summary TEXT,
  created_at INTEGER,
  PRIMARY KEY (thread_id, service_id)
);

CREATE TABLE IF NOT EXISTS cached_topics(
  cache_key TEXT PRIMARY KEY,
  data TEXT,
  timestamp INTEGER
);

CREATE TABLE IF NOT EXISTS cached_threads(
  thread_id TEXT,
  service_id TEXT DEFAULT '',
  data TEXT,
  timestamp INTEGER,
  PRIMARY KEY (thread_id, service_id)
);

CREATE TABLE IF NOT EXISTS bookmarks(
  thread_id TEXT,
  service_id TEXT,
  data TEXT,
  timestamp INTEGER,
  PRIMARY KEY (thread_id, service_id)
);

CREATE TABLE IF NOT EXISTS url_bookmarks(
  url TEXT PRIMARY KEY,
  title TEXT,
  timestamp INTEGER
);
```

Android must add an RSS subscription table because the iOS RSS service uses UserDefaults rather than SQLite:

```sql
CREATE TABLE IF NOT EXISTS rss_feeds(
  url TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  isDefault INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
```

## DAO/function parity

| iOS function family | Android repository/DAO requirement |
|---|---|
| `saveCommunities`, `getCommunities` | Insert/replace communities scoped by service; read by `serviceId`. |
| `saveSetting`, `getSetting`, `removeSetting` | Plain key-value CRUD. |
| `saveEncryptedSetting`, `getEncryptedSetting` | AES-GCM encrypted values, plaintext migration behavior. |
| `addFilteredPost`, `isPostFiltered`, `getFilteredPostIds`, cleanup | Persist Zhihu not-interested/read filtering by service. Cleanup cutoff: 90 days. |
| `saveCookies`, `replaceCookies`, `getCookies`, `clearCookies`, `hasCookies` | Encrypted cookie JSON stored at `settings.key = login_<siteId>_cookies`. |
| `saveSummary`, `getSummary`, `getSummaryIfFresh` | Summary cache by `(thread_id, service_id)` with `created_at`. |
| `saveCachedTopics`, `getCachedTopics`, `clearCachedTopicsForService` | Thread list JSON by cache key. Clear prefix `siteId_`. |
| `saveCachedThread`, `getCachedThread` | Thread detail + comments JSON by `(thread_id, service_id)`. |
| Bookmark functions | Toggle thread bookmarks by `(thread_id, service_id)` and URL bookmarks by URL. |

## Migration parity

iOS has no `user_version`; it performs structural checks and rebuilds:

- `cached_threads` must migrate to `PRIMARY KEY(thread_id, service_id)`.
- `ai_summaries` must migrate to `PRIMARY KEY(thread_id, service_id)`.
- Missing/null `service_id` becomes `''`.
- Deprecated table `zhihu_read_recommendations` is dropped.

Android implementation must express this as formal Room/SQLDelight migrations:

1. Create backup table.
2. Copy rows with `COALESCE(service_id, '')`.
3. Recreate target schema with composite key.
4. Insert or replace old data.
5. Drop backup.

## AES-GCM design

iOS `EncryptionHelper`:

- Creates random 256-bit AES-GCM key.
- Stores key in Keychain account `com.feedflow.encryption-key`, service `Feedflow`, accessible after first unlock.
- Stores encrypted value as Base64 of CryptoKit `sealedBox.combined`.
- Decrypt attempts current key first, then legacy key `SHA256("FeedflowLocalEncryption2024")`.
- If encrypted setting cannot decrypt and plaintext migration is allowed, iOS returns plaintext once and rewrites encrypted.

Android equivalent:

- Keystore alias: `com.feedflow.encryption-key`.
- Algorithm: `AES/GCM/NoPadding`.
- Key size: 256-bit.
- Random IV/nonce per encryption.
- Stored format: Base64 of `iv || ciphertext || tag`.
- If a value is not decryptable, try legacy SHA-256 seed key; then plaintext migration only for settings explicitly allowed by migration code.
- Never log plaintext, ciphertext, cookies, or API keys.

## Cookie JSON format

Plain JSON before encryption:

```json
[
  {
    "name": "cdb_auth",
    "value": "...",
    "domain": ".4d4y.com",
    "path": "/",
    "secure": true,
    "httpOnly": true,
    "expires": 1770000000.0
  }
]
```

Rules:

- Stored key: `login_<siteId>_cookies`.
- `expires` omitted for session cookies.
- Expired cookies are skipped on read.
- `replaceCookies` deletes all previous cookies for that site.
- `saveCookies` merges by `(name, domain, path)`.
- Login success upgrades session cookies to 30-day persistent cookies before storing.

## Login site configuration

| Site | Login URL | Domain filter | Required/auth cookie fragments |
|---|---|---|---|
| 4D4Y | `https://www.4d4y.com/forum/logging.php?action=login` | `4d4y.com` | `auth`, `login`, `member` |
| Hacker News | `https://news.ycombinator.com/login` | `ycombinator.com` | `user` |
| V2EX | `https://v2ex.com/signin` | `v2ex.com` | `a2` |
| Linux.do | `https://linux.do/login` | `linux.do` | `_t`, `remember_user_token` |
| Zhihu | `https://www.zhihu.com/signin` | `zhihu.com` | required exact cookie `z_c0` |

OAuth buttons:

- V2EX: Google, Solana.
- Linux.do: Google, GitHub, X, Discord, Apple, Passkey.

## WebView login state machine

1. Open WebView with source login URL or selected OAuth override URL.
2. Enable JavaScript and popup windows.
3. Use persistent Android `CookieManager`.
4. On page finished:
   - If URL is success/post-login navigation: poll cookies up to 8 times, 1 second apart.
   - If URL is same-domain navigation: poll cookies up to 3 times.
5. Filter cookies by configured domain suffix.
6. Reject empty cookie set.
7. Reject cookie set missing required auth cookie.
8. Reject repeated identical failed cookie signature until next navigation.
9. On accepted cookies:
   - Upgrade session cookies to 30-day expiry.
   - `replaceCookies(siteId)`.
   - Clear cached topics for service.
   - Inject cookies into HTTP client cookie jar and WebView `CookieManager`.
   - Call service `restoreSession()` to validate.
   - If valid, close login and navigate to selected site.
   - If invalid, keep login status false and show error.

Manual “Save Session” runs the same validation path against current WebView cookies.

## Logout state machine

1. Remove `login_<siteId>_cookies`.
2. Remove legacy `login_<siteId>_username` and `login_<siteId>_password`.
3. Clear matching cookies from HTTP cookie jar.
4. Clear matching cookies from Android `CookieManager`.
5. Update login status false.
6. Do not clear other sites.

## Required tests

1. Exact schema creation and composite keys.
2. Migration preserves old cached thread/summary rows with empty service ID.
3. `settings` CRUD and encrypted setting migration.
4. AES-GCM encryption round trips special chars, Chinese, empty string; same plaintext produces different ciphertext.
5. Invalid ciphertext decrypt returns null without crash.
6. Cookie serialization preserves name/value/domain/path/secure/httpOnly/expires.
7. Expired cookies are skipped.
8. `replaceCookies` overwrites; `saveCookies` merges.
9. Web login rejects no cookies, wrong-domain cookies, and missing auth cookie.
10. Zhihu requires `z_c0`.
11. Logout clears only one source.
12. Session-cookie 30-day upgrade uses a fixed-clock test.
