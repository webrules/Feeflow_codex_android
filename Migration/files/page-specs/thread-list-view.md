# ThreadListView spec

**iOS source:** `Feedflow/Views/ThreadListView.swift`, `Feedflow/ViewModels/ThreadListViewModel.swift`

## Purpose

Show paginated topics for a community with cache, refresh, login, source-specific actions, and prefetch behavior.

## Android UI

- Forum background.
- Loading card: progress + “Loading {community}” + “Checking {service} and preparing threads”.
- List content:
  - Status header card with site icon, community name, subtitle, visible count chip, source/session chips.
  - Empty card with tray icon, message, Refresh button.
  - `ThreadRow` for each topic.
  - Bottom progress when loading more.
- Top refresh warning card when `refreshMessage` is non-null.
- Bottom toolbar: back, community name, compose when `service.canCreateThread`, refresh, theme, home.

## Behavior

- On first load, restore session, then load cache/fresh topics.
- If service requires login and session restore fails, set login request and show refresh message.
- Returning from detail loads stale cache first and background refreshes unless skipped for Zhihu recommend.
- Force refresh fetches fresh data, preserves existing posts for 4D4Y/Zhihu recommend on empty result, and warns on session/network issues.
- Infinite scroll loads next page when last row appears.
- Prefetch detail only when global setting enabled, service allowlisted, on Wi-Fi, queue not full, detail uncached, and row survives debounce.
- Zhihu rows expose context menu action “不感兴趣” to remove/downvote.

## Data

- Cache key: `{service.id}_{community.id}_page1`.
- Uses `ThreadListViewModel.threads`, `isLoading`, `canLoadMore`, `needsLogin`, `refreshMessage`.

## Test cases

- Initial state and loading state.
- Force refresh calls `refreshCategoryThreads`.
- Returning flow loads cached topics first.
- Background refresh does not replace list when not at top.
- 4D4Y/Zhihu empty refresh preserves old posts.
- Non-4D4Y empty refresh can clear posts.
- Login-required path opens Login and preserves old posts.
- Prefetch gating by preference, Wi-Fi, service, cache, queue.
- Compose icon visibility follows service capability.
