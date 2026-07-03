# SiteListView spec

**iOS source:** `Feedflow/Views/SiteListView.swift`

## Purpose

Home screen and app entry point. It lets the user select a content source, search source-specific sites, and open global utilities.

## Android UI

- Full-screen forum background.
- Heading text from `select_community`.
- Search card near top:
  - Source selector menu with only 4D4Y, V2EX, Linux.do, Zhihu.
  - Text input with `Search` placeholder.
  - Search action disabled when trimmed query is empty.
- Adaptive grid with minimum card width equivalent to iOS 150 pt.
- Site cards show only circular site icon and service name, centered, with forum card background, 16 dp rounded corners, subtle border.
- Bottom safe-area toolbar card:
  - Left group: login, settings, bookmarks, cross-site AI.
  - Right group: community config, theme toggle, language toggle.

## Behavior

- Site tap navigates to `CommunitiesView` for that service.
- Search submit appends `SiteSearchRoute(site, trimmedQuery)` to navigation.
- Toolbar buttons open sheets: Login, Settings, Bookmarks, CommunityConfig, CrossSiteAISummary.
- Theme and language changes are immediate and persisted.
- RSS always appears because `CommunitySettingsManager` cannot disable it.

## Data

- Uses `ForumSite` order: RSS, Hacker News, 4D4Y, V2EX, Linux.do, Zhihu.
- Uses `ForumSite.makeService()` for name/logo.
- Uses `CommunitySettingsManager.visibleSites`.

## Test cases

- All six sites render in default order.
- RSS remains visible after attempted disable.
- Search ignores whitespace and disables button.
- Search route uses trimmed query and selected site.
- Each toolbar action opens the correct modal.
- Theme/language toggle updates visible strings/colors.
- Light/dark screenshot parity.
