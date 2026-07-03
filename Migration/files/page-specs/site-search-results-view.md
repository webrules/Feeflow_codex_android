# SiteSearchResultsView spec

**iOS source:** `Feedflow/Views/SiteSearchResultsView.swift`

## Purpose

Display source-specific search results launched from Home.

## Android UI

- Forum background and hidden top app bar.
- Initial centered progress when loading and no results.
- Header: “Search results” and quoted query.
- Results use `ThreadRow`.
- Empty/error state: magnifying glass icon and message.
- Bottom toolbar: back, service name, refresh, home.

## Behavior

- On open, restore session.
- If login required and restore fails, show “Login is required to search {service}.”
- Load page 1 via `searchThreads(query, page: 1)`.
- Empty result shows “No results found.”
- Pagination loads when last result appears; append only unique ids.
- Pull-to-refresh reloads page 1.
- Horizontal swipe pops root.

## Test cases

- Login-required error.
- Successful load populates results and canLoadMore.
- Empty result error.
- Pagination appends unique rows.
- Refresh resets page/results.
- Row opens Thread Detail with context results.
