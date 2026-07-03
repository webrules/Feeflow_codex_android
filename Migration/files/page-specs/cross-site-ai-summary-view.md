# CrossSiteAISummaryView spec

**iOS source:** `Feedflow/Views/CrossSiteAISummaryView.swift`

## Purpose

Show AI summaries and top links for major sources.

## Android UI

- Modal navigation title `AI Cross-Site Top 10`.
- Scroll list of section cards for Hacker News, V2EX, Linux.do, 4D4Y.
- Section card: site title, Top 10 badge, loading row or error text or summary plus post links.
- Bottom toolbar: Close and Refresh; refresh disabled while loading.

## Behavior

- Load sections concurrently.
- For each section: restore session, fetch categories, choose preferred category if present, fetch first page, take 10 posts, generate language-aware summary.
- Section errors do not block other sections.
- Post link opens Thread Detail with section context threads.

## Test cases

- Initial section order/count.
- Preferred category fallback.
- Empty categories/posts error.
- Current language changes prompt language.
- Refresh guarded by `isRefreshing`.
- Post link opens correct service/thread.
