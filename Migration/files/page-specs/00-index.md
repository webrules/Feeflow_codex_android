# Feedflow Android page specs index

Status: draft for approval. These files are session artifacts only; no repository code should change until every page/view spec is approved.

## Global implementation contract

- Android target: native Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModels, coroutines/Flow, SQLite persistence, AndroidX Security Crypto, WebView, DataStore, English/Chinese localization.
- Visual parity: use iOS Feedflow colors, rounded card styling, bottom toolbars, icon semantics, spacing hierarchy, dark/light behavior, and source-aware UI differences.
- Testing parity: each page/view implementation must include executable Android tests and map back to existing XCTest coverage.
- Approval gate: implementation remains blocked until the user approves all files in this directory.

## Spec files

1. `site-list-view.md`
2. `community-config-view.md`
3. `communities-view.md`
4. `community-row.md`
5. `thread-list-view.md`
6. `thread-row.md`
7. `thread-detail-view.md`
8. `linked-text-view.md`
9. `parsed-content-view.md`
10. `tag-view.md`
11. `comment-row.md`
12. `new-thread-view.md`
13. `linear-progress-view.md`
14. `login-view.md`
15. `web-login-view.md`
16. `web-login-sheet-view.md`
17. `settings-view.md`
18. `bookmarks-view.md`
19. `section-header.md`
20. `bookmark-row.md`
21. `url-bookmark-row.md`
22. `in-app-browser-view.md`
23. `full-screen-image-view.md`
24. `ai-summary-view.md`
25. `cross-site-ai-summary-view.md`
26. `daily-rss-summary-view.md`
27. `rss-feed-manager-view.md`
28. `opml-import-sheet.md`
29. `site-search-results-view.md`
30. `avatar-view.md`
