# Source service technical design: 4D4Y and Linux.do

## 4D4Y service

### iOS anchors

- `Feedflow/Services/FourD4YService.swift`
- `FeedflowTests/FeedflowFourD4YLoginTests.swift`

### Identity and capabilities

| Property | Value |
|---|---|
| `id` | `4d4y` |
| `name` | `4D4Y` |
| `logo` | `4.circle.fill` |
| `baseURL` | `https://www.4d4y.com/forum` |
| Requires login | Yes |
| Commenting | Yes |
| Thread creation | Yes |
| Delete thread | Own threads only |
| Encoding | GBK/GB18030 |

### Request/cookie strategy

- Use manual cookie header construction; disable automatic cookie handling for scraper requests.
- Filter cookies whose domain contains `4d4y.com`.
- Cookie match rules must honor domain, path, secure flag, and expiry.
- Deduplicate by cookie name, preferring longer path/domain specificity.
- Use iPhone Safari-style user agent from iOS service.
- GBK/GB18030 must be used for decoding HTML and percent-encoding login/post forms.

### Login and session restore

Restore sequence:

1. Load encrypted DB cookies for `siteId=4d4y`.
2. Validate with GET `/index.php`.
3. If DB cookies fail, try WebView cookie store cookies for `4d4y.com`.
4. If WebView cookies validate, save them encrypted.
5. If both fail, try encrypted legacy settings `login_4d4y_username/password` auto-login.
6. Return false if all fail.

Native login:

- GET `/logging.php?action=login` first.
- POST `/logging.php?action=login&loginsubmit=yes&inajax=1`.
- Body fields:
  - `username`
  - `password`
  - `loginsubmit=yes`
  - `inajax=1`
  - `cookietime=2592000`
- Header: `Content-Type: application/x-www-form-urlencoded`.

Session validation:

- GET `/index.php`.
- Logged-in success requires forum/authenticated links and absence of login-link markers.
- Accept logout/member markers like `action=logout` / `退出`.
- Challenge/guest/login pages must fail validation.
- SID alone must not prove login.

### Routes

| Operation | Request |
|---|---|
| Categories | GET `/index.php` |
| Thread list | GET `/forumdisplay.php?fid={fid}&sid={sid}&page={page}&_t={epoch}`; omit page for page 1 if iOS does. |
| Search | GET `/search.php?searchsubmit=yes&srchtxt={GBK(query)}&searchfield=all&page={page}&sid={sid}` |
| Detail | GET `/viewthread.php?tid={tid}&sid={sid}&page={page}&extra=page%3D1` for page > 1 |
| Reply | POST `/post.php?action=reply&fid={fid}&tid={tid}&extra=&replysubmit=yes&inajax=1&sid={sid}` |
| New thread | GET `/post.php?action=newthread&fid={fid}&sid={sid}`, then POST `/post.php?action=newthread&fid={fid}&extra=&topicsubmit=yes&inajax=1&sid={sid}` |
| Delete | GET edit form, then POST edit form with `delete=1&editsubmit=yes&inajax=1` |

Reply POST body:

```text
formhash={formhash}
posttime={unix}
wysiwyg=1
noticeauthor=
noticetrimstr=
noticeauthormsg=
subject=
message={GBK(content)}
replysubmit=yes
inajax=1
```

Required reply headers:

- `Accept: text/xml,*/*`
- `X-Requested-With: XMLHttpRequest`
- `Origin: https://www.4d4y.com`
- `Referer: viewthread URL`

New-thread POST body includes `formhash`, `posttime`, `wysiwyg`, `subject`, `message`, `topicsubmit`, `inajax`, and optional `typeid`.

### Parser contracts

Categories:

```regex
href="forumdisplay\.php\?fid=(\d+)[^"]*"[^>]*>([^<]+)</a>
```

SID:

```regex
sid=([a-zA-Z0-9]+)
```

Formhash must support all iOS patterns:

- URL query `formhash=...`
- hidden input `name="formhash" value="..."`
- reversed hidden-input attribute order
- JavaScript assignment variants

Thread rows:

```regex
<tbody[^>]*id="(?:normalthread_|thread_)(\d+)"[^>]*>(.*?)</tbody>
```

Within row:

- Title: `href="viewthread\.php\?tid=\d+[^"]*"[^>]*>([^<]+)</a>`
- Author UID/name: `space.php?uid=(\d+)`
- Replies: `<td class="nums"...<strong>(\d+)</strong>`
- Last poster/time from lastpost block.

Detail parsing:

- Desktop mode: `postmessage_{pid}` blocks.
- WAP fallback: `detailcon`, `replylist`, `replycon`, `pid` patterns.
- First post maps to thread body.
- Replies map to comments.

Avatar URL:

```text
uid padded to 9 digits:
https://img02.4d4y.com/forum/uc_server/data/avatar/{000}/{00}/{00}/{00}_avatar_middle.jpg
```

Content cleanup:

- Remove attachment blocks `<div class="t_attach">` and `<ignore_js_op>`.
- Convert images to `[IMAGE:absoluteUrl]`.
- Skip smilies/common UI images.
- Convert blockquotes to `[QUOTE]...[/QUOTE]`.
- Convert content links to `[LINK:href|title]`, excluding anchors/javascript/common image wrappers.
- Decode HTML entities, including numeric Chinese punctuation.
- Collapse excessive newlines.

### 4D4Y deterministic tests

1. GB18030 login form encoding produces expected bytes.
2. Auth-cookie detection rejects SID-only cookies.
3. Cloudflare/guest HTML validation fails.
4. Logged-in HTML validation succeeds.
5. Category fixture extracts IDs/names.
6. Thread-list fixture extracts ID/title/author/reply count/last poster.
7. Desktop detail fixture maps OP and comments.
8. WAP detail fixture maps OP and replies.
9. SID and formhash extraction supports every iOS pattern.
10. Reply/new-thread/delete POST payloads match required fields.
11. AJAX success/error XML/CDATA parsing is deterministic.
12. Avatar UID mapping handles short, padded, large, invalid, and empty UIDs.

## Linux.do service

### iOS anchors

- `Feedflow/Services/DiscourseService.swift`

### Identity and capabilities

| Property | Value |
|---|---|
| `id` | `linux_do` |
| `name` | `Linux.do` |
| `logo` | `terminal.fill` |
| `baseURL` | `https://linux.do` |
| Platform | Discourse |
| Requires login | Yes |
| Commenting | Yes |
| Thread creation | Yes |

### Auth/session

- Restore DB cookies first, then WebView cookies.
- Validate session by GET `/session/current.json`.
- Success requires HTTP 2xx and non-null `current_user`.
- CSRF token route: GET `/session/csrf.json`, decode `{ "csrf": "..." }`.
- 403 during CSRF or content requests maps to auth required.

### Request rules

- iPhone Safari UA.
- `Referer: https://linux.do`.
- Manual filtered `Cookie` header.
- Disable automatic cookies for authorized requests.
- For post/create:
  - `Origin: https://linux.do`
  - `Content-Type: application/json`
  - `Accept: application/json`
  - `X-CSRF-Token: {csrf}`

### Routes

| Operation | Request |
|---|---|
| Categories | GET `/categories.json` |
| Latest topics | GET `/latest.json?page={page-1}` |
| Category topics | GET `/c/{slug-or-id}/l/latest.json?page={page-1}` |
| Detail | GET `/t/{threadId}.json?page={page}` |
| Missing post | GET `/posts/{postId}.json` |
| Search | GET `/search/query?term={query}&page={page}` |
| Reply | POST `/posts.json` body `{ "topic_id": Int, "raw": content }` |
| New topic | POST `/posts.json` body `{ "title": title, "raw": content, "category": Int }` |

### JSON mappings

Categories:

- `category_list.categories[].id` -> `Community.id`
- `name` -> `Community.name`
- `description` -> `Community.description`
- `slug`/group -> category metadata
- `topic_count` -> `activeToday`
- Add synthetic `latest` category if iOS fallback does so.

Topics:

- `topic_list.topics[]`
- Fields: `id`, `title`, `fancy_title`, `slug`, `posts_count`, `reply_count`, `like_count`, `views`, `created_at`, `bumped_at`, `category_id`, `tags`, `posters`.
- Join author from `users[]` by poster/user ID.
- `bumped_at` maps to compact `timeAgo`.

Detail:

- `post_stream.posts` provides OP and comments.
- `post_stream.stream` controls pagination windows.
- Page 1 returns OP + first 20 replies.
- Later pages use stream window after OP.
- `totalPages = ceil((stream.count - 1) / 20)`.

Post fields:

- `id`
- `user_id`
- `username`
- `avatar_template`
- `cooked`
- `created_at`
- `post_number`
- `reply_count`
- `score`
- `primary_group_name`
- `admin`
- `moderator`

### HTML cleanup

- Avatar template `{size}` -> `64`.
- Relative avatar/content paths prefix `https://linux.do`.
- Remove avatar/site-icon/meta spans.
- Emoji images become alt text/Unicode where available.
- Content images become `[IMAGE:absoluteUrl]`.
- `<br>` and paragraph endings become newlines.
- Strip remaining tags and decode entities.
- Collapse whitespace.

### Linux.do deterministic tests

1. Cookie matching/dedupe respects domain/path/secure/expiry.
2. Session validation handles `current_user` present/absent.
3. CSRF route returns token and 403 maps auth required.
4. Category JSON maps communities and latest fallback.
5. Topic list JSON joins topic posters to users.
6. Detail JSON paginates stream windows.
7. Missing post fetch fills absent stream posts.
8. `/posts.json` reply and new-topic payloads match spec.
9. Cooked HTML cleaner preserves images/links/emoji text.
10. Search joins `topics[]` and `posts[]`; `more_results` maps `hasMore`.
