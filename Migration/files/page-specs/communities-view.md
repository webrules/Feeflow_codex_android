# CommunitiesView spec

**iOS source:** `Feedflow/Views/CommunitiesView.swift`

## Purpose

Show categories/communities for the selected source.

## Android UI

- Hidden native top bar; custom bottom toolbar.
- Forum background.
- Initial loading with centered accent progress indicator when `isLoading && communities.isEmpty`.
- Loaded state is a vertical scroll list of `CommunityRow`.
- Bottom toolbar: back icon, service name, spacer, RSS-only daily summary and manage feeds icons, refresh icon, home icon.

## Behavior

- On open, view model loads communities.
- Pull-to-refresh and toolbar refresh call `ForumViewModel.refresh()`.
- Row tap navigates to `ThreadListView(community, service)`.
- RSS manage feeds opens `RSSFeedManagerView`; refresh communities on dismiss.
- RSS daily summary opens `DailyRSSSummaryView`.
- If `needsLogin` becomes true, open `LoginView(initialSite)` and refresh after dismiss.
- Left-edge horizontal swipe dismisses.

## Data

- Service injected from selected site.
- Community model: id, name, description, category, activeToday, onlineNow.

## Test cases

- Loading state only when communities empty.
- Non-empty communities render all rows.
- RSS-only buttons visible only for RSS service.
- Refresh button invokes refresh.
- Login-required state opens login and retries refresh.
- Back/home behavior matches navigation stack expectations.
