# Networking, parsing, and normalization technical design

## iOS anchors

- `Feedflow/Services/*Service.swift`
- `Feedflow/Services/RSSParser.swift`
- `Feedflow/Services/OPMLParser.swift`
- `Feedflow/Utilities/HTMLEntityDecoder.swift`
- `Feedflow/Views/AvatarView.swift`

## HTTP client architecture

Android must provide:

1. Shared OkHttp base client.
2. Source-specific clients/interceptors for:
   - user agent,
   - referer/origin,
   - manual cookie header,
   - CSRF,
   - cache-control,
   - charset decoding.
3. Source-scoped cookie jar backed by encrypted DB cookies.
4. Explicit redirect handling for login/posting flows where `Set-Cookie` preservation matters.
5. Cancellation through coroutines.

## Charset contract

| Source | Required decoding |
|---|---|
| 4D4Y | GBK/GB18030 first; tested byte fixtures required. |
| Linux.do | UTF-8 JSON/HTML. |
| HN | UTF-8 JSON. |
| V2EX | UTF-8 HTML. |
| Zhihu | UTF-8 JSON/HTML. |
| RSS/OPML | XML-declared charset or HTTP charset; UTF-8 fallback. |

Fallback decoding must be explicit and test-covered. Do not silently replace undecodable bytes without a parser error or logged context.

## HTML entity decoder requirements

Must support:

- Common named entities from iOS tests.
- Decimal numeric entities like `&#12290;`.
- Hex numeric entities.
- Mixed text/entity strings.
- Invalid entities without crash.
- Empty strings unchanged.

## URL normalization rules

- Protocol-relative URLs beginning `//` become `https:`.
- Root-relative paths prefix the service base URL.
- Relative RSS links should resolve against the feed URL where possible.
- `javascript:`, fragment-only anchors, data URLs, equation images, smilies, avatars, and UI sprites must be filtered according to source cleaner rules.

## Rich content token format

Android parsers must initially preserve iOS token format because page specs and tests can then map tokens to Compose rich content:

- Image: `[IMAGE:https://example.com/image.jpg]`
- Link: `[LINK:https://example.com|Visible title]`
- Quote: `[QUOTE]quoted content[/QUOTE]`

Rendering can later transform these tokens into inline Compose/WebView interactions, but parser tests must assert the token output.

## Parser strategy by source

| Source | Parser strategy |
|---|---|
| 4D4Y | GB18030 decode, JSoup where safe, regex fallbacks for Discuz/WAP patterns. |
| Linux.do | Kotlin serialization/Moshi for JSON; JSoup-like cleanup for cooked HTML. |
| HN | JSON decode plus simple HTML cleaner for `text`. |
| V2EX | JSoup plus regex fallbacks for topic cells and replies. |
| Zhihu | JSON decode, WebView/DOM search fallback fixtures, HTML cleaner. |
| RSS | XML pull parser/SAX equivalent with item/entry state machine. |
| OPML | XML parser over nested outlines. |

## Avatar/image cache normalization

iOS `ImageCache` behavior to reproduce:

- Memory cache max count: 200.
- Memory total cost: 30 MB.
- Disk directory: app cache dir `/FeedflowImageCache`.
- Disk key: SHA-256 prefix 32 of absolute URL.
- Lookup order: memory, disk, then network.
- Disk hit promotes to memory.
- Store writes memory immediately and disk asynchronously.
- Purge files older than 7 days.

AvatarView-specific behavior:

- `noavatar` or invalid URL -> initials fallback.
- Protocol-relative avatar -> HTTPS.
- Check image cache before network.
- Fetch with image accept headers and source referer.
- 4D4Y avatar should try size/host/path alternatives before fallback.

## Error reporting

Parser/network errors must carry:

- `serviceId`
- operation/stage (`categories`, `threadList`, `detail`, `comments`, `login`, `reply`, etc.)
- URL without secrets
- HTTP status if present
- non-secret body preview capped to a small size

Do not log:

- cookie values,
- authorization tokens,
- Gemini key,
- form password,
- full private response bodies from authenticated sources.

## Required tests

1. Cookie domain/path/secure/expiry matcher.
2. Source-specific headers per service.
3. 4D4Y GB18030 byte fixture.
4. Entity decoder complete XCTest inventory.
5. URL normalization for protocol-relative, root-relative, relative, JavaScript, anchors, data images.
6. Token output for image/link/quote cleaners.
7. RSS unsafe HTML sanitizer removes script/style/iframe but keeps normal links/images.
8. Avatar cache memory hit, disk hit, promotion, 7-day purge.
9. Network/parser error object never includes secrets.
