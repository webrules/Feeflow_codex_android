# Source service technical design: Hacker News, V2EX, Zhihu, RSS/OPML

## Hacker News

### Identity

| Property | Value |
|---|---|
| `id` | `hackernews` |
| `name` | `Hacker News` |
| `logo` | `flame.fill` |
| Base | `https://hacker-news.firebaseio.com/v0` |
| Login/comment/create | No / no / no |

### Categories

Static communities:

1. `topstories`
2. `newstories`
3. `beststories`
4. `showstories`
5. `askstories`
6. `jobstories`

### Routes and mapping

- List IDs: GET `/{category}.json`
- Fetch item: GET `/item/{id}.json`
- Detail: GET `/item/{threadId}.json`
- Web URL: `https://news.ycombinator.com/item?id={id}`

`HNItem` fields:

- `id`
- `type`
- `by`
- `time`
- `text`
- `url`
- `title`
- `score`
- `descendants`
- `kids`

Rules:

- List fetch takes first 20 IDs.
- Page > 1 returns empty/dummy as iOS does.
- Detail comments fetch first 20 kids.
- `score` -> like count.
- `descendants` -> comment count.
- `by` -> author username/id.
- `text ?? url` -> content.
- Deleted/empty comments are skipped.

Cleaner:

- `<p>` and code/pre tags become readable newlines/code text.
- Links become URL text.
- Strip remaining tags.
- Decode common and decimal numeric entities.
- Collapse excessive newlines.

Tests: category order, first-20 limit, item decode, page > 1 empty, deleted/dead items skipped, nested comment fixture, cleaner fixtures.

## V2EX

### Identity

| Property | Value |
|---|---|
| `id` | `v2ex` |
| `name` | `V2EX` |
| `logo` | `point.3.connected.trianglepath.dotted` |
| Base | `https://v2ex.com` |
| Requires login for reading | No |
| Commenting | Yes |
| Thread creation | No |
| Search | Google WebKit fallback |

### Categories

Static tabs in order:

`tech`, `creative`, `play`, `apple`, `jobs`, `deals`, `city`, `qna`, `hot`, `all`, `r2`, `xna`, `planet`

### Routes

- Tab threads: GET `https://v2ex.com/?tab={categoryId}`
- Detail: GET `/t/{threadId}?p={page}`; page > 1 currently returns dummy/empty in iOS.
- Reply token: GET `/t/{topicId}`
- Reply POST: POST same topic URL, body `content={urlQueryAllowed(content)}&once={once}`
- Search: GET `https://www.google.com/search?q=site:v2ex.com/t {query}&num=20&hl=zh-CN`
- Web URL: `/t/{id}`

Reply headers:

- Desktop browser UA.
- `Content-Type: application/x-www-form-urlencoded`
- `Referer: https://v2ex.com/t/{topicId}`
- Cookie header from encrypted DB cookies.

### Parser contracts

Once token regex variants:

```regex
name="once"[^>]*value="(\d+)"
value="(\d+)"[^>]*name="once"
var\s+once\s*=\s*"?(\d+)"?
'once'\s*:\s*'?(\d+)'?
once=(\d+)
```

Thread list:

- Split around `class="cell item"`.
- ID/title: `<a href="/t/(\d+)[^"]*" class="topic-link"[^>]*>(.*?)</a>`
- Author: `href="/member/([^"]+)"`
- Replies: `class="count_livid">(\d+)</a>`
- Last poster from `class="topic_info"` block.

Detail:

- Title: `<h1[^>]*>([^<]+)</h1>`
- Content: `<div class="topic_content"...>`
- Comments split by `id="r_"`
- Comment author from `class="dark">`
- Comment content from `class="reply_content">`
- Time from `class="ago"`

Cleaner:

- Images -> `[IMAGE:src]`
- `<br>`/`<p>` -> newlines
- Strip tags
- Decode basic entities
- Avatar URLs: `//` -> `https:`, `/path` -> base URL.

Tests: tab order, once extraction variants, list cell fixture, detail comments fixture, post body encoding, avatar normalization, Google result de-dupe and URL normalization.

## Zhihu

### Identity

| Property | Value |
|---|---|
| `id` | `zhihu` |
| `name` | `知乎` |
| `logo` | `questionmark.bubble.fill` |
| Requires login | Yes |
| Commenting/create | No / no |
| Required cookie | `z_c0` |

### Request headers

- `x-api-version: 3.1.8`
- `x-app-version: 10.61.0`
- `x-requested-with: fetch`
- `Referer: https://www.zhihu.com/`
- iPhone Safari UA
- Manual cookie header

### Session

- Restore encrypted DB cookies for `siteId=zhihu`.
- Filter domain containing `zhihu.com`.
- Sync to runtime cookie store.
- `restoreSession()` returns false if no cookies.
- `verifyLogin()` GET `https://www.zhihu.com/api/v4/me`; success on 200 decode of user profile.

### Categories/routes

Communities:

- `recommend`
- `hot`

Routes:

- Recommend: `https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=10`
- Recommend next: `paging.next`, fallback `after_id={page}`
- Refresh adds `_={epochMs}` and no-cache headers.
- Hot: `https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=10&desktop=true`
- Answer detail: `/api/v4/answers/{id}?include=content,html_content,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,author`
- Article detail: `/api/v4/articles/{id}?include=...`
- Question detail: `/api/v4/questions/{id}?include=detail,excerpt,answer_count,visit_count,comment_count,follower_count,topics`
- Question answers: `/api/v4/questions/{id}/answers?include=content,voteup_count,comment_count&limit=10&offset={(page-1)*10}&sort_by=default`
- Comments: `/api/v4/{answers|articles}/{id}/root_comments?limit=20&offset={(page-1)*20&order=normal&status=open`
- Search WebKit: `https://www.zhihu.com/search?type=content&q=...`
- Search API fallback: `/api/v4/search_v3?q={q}&t=content&correction=1&offset={(page-1)*20&limit=20`
- Downvote/not interested: POST `/api/v3/feed/topstory/uninterest`, JSON `{target_type,target_id,reason:"not_interested"}`

### Data and filtering

- Thread ID format: `{type}_{id}`, e.g. `answer_123`.
- Dispatch detail by `answer`, `article`, `question`.
- In-memory state in iOS:
  - `nextPageURL`
  - `currentFeedItems`
  - `questionDataCache[questionId]`
- Persistent filtering:
  - Setting `zhihu_downvoted_ids`
  - DB `filtered_posts` for service `zhihu`
  - Cleanup old filtered posts.

Quality filter:

- Skip `feed_advert`.
- Skip unsupported types.
- Skip downvoted/read posts.
- Answer: skip votes `< 10` if not following.
- Article/zvideo: skip low follower/vote combinations per iOS filter.

### Cleaner and normalization

- Avatar template `{size}` -> `80`.
- Protocol-relative URLs -> HTTPS.
- Remove `<noscript>`.
- Links -> `[LINK:url|title]`.
- Images from `src`, `data-original`, or `data-actualsrc` -> `[IMAGE:url]`.
- Skip duplicate normalized Zhihu image keys.
- Skip data/equation images.
- Strip figures/tags/entities/newlines.
- Search text strips tags, decodes entities, collapses whitespace.

Tests: missing/expired `z_c0`, profile verification, recommend paging/filtering, hot card mapping, detail retries empty content, comments including child comments, duplicate image cleanup, WebKit search DOM/API fallback, downvote DB keys, avatar/zoom URL normalization.

## RSS and OPML

### Identity

| Property | Value |
|---|---|
| `id` | `rss` |
| `name` | `RSS Feeds` |
| `logo` | Feedflow feed icon |
| Login/comment/create | No / no / no |

### Default feeds

- Hacker Podcast: `https://hacker-podcast.agi.li/rss.xml`
- Ruanyifeng Blog: `https://www.ruanyifeng.com/blog/atom.xml`
- O'Reilly Radar: `https://www.oreilly.com/radar/feed/`

### Service behavior

- iOS stores custom feeds in UserDefaults key `custom_rss_feeds`; Android must persist in SQLite `rss_feeds`.
- Categories are feeds.
- Fetch feed URL with normal HTTP GET.
- `fetchCategoryThreads` ignores page, parses all items, and caches each by `thread.id`.
- Detail returns cached thread or placeholder `"Content Unavailable"`.
- No comments.
- Web URL is `thread.id` because parser uses link as ID.
- `fetchDailyUpdates()` concurrently fetches all feeds and keeps recent `just now|m|h` items.

### RSS/Atom parser

Supported containers:

- RSS `<item>`
- Atom `<entry>`

Fields:

- `title`
- `link` text or Atom `link href` where `rel=alternate` or rel absent
- `description`
- `content`
- `content:encoded`
- `summary`
- `pubDate`
- `updated`
- `published`
- `dc:date`
- `author`
- `name`
- `dc:creator`
- `guid`
- `id`

Date formats:

- RFC822: `EEE, dd MMM yyyy HH:mm:ss Z`
- ISO8601
- fallback `"Recent"`

### RSS content cleaner

- Remove `<script>` and `<style>` blocks.
- Images -> `[IMAGE:url]`.
- Links -> `[LINK:href|title]`; skip anchors/javascript.
- Strip remaining tags.
- Decode common and decimal numeric entities.
- Collapse newlines.

### OPML parser

- Parse `<outline xmlUrl="...">`.
- Title fallback order: `text`, then `title`, then URL.
- Ignore outlines without `xmlUrl`.
- Support nested outlines.
- Deduplicate by URL during import.

Tests: default feeds, add/remove feeds, RSS 2.0 fixture, Atom fixture, content:encoded, dc creator/date, parser chunking, parse error returns empty, OPML nested outlines, title fallback, daily update deterministic aggregation.
