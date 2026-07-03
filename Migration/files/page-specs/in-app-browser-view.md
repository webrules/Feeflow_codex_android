# InAppBrowserView spec

**iOS source:** `Feedflow/Views/InAppBrowserView.swift`

## Purpose

Open web links inside the app with navigation, share, and URL bookmarking.

## Android UI

- Modal navigation.
- Title is WebView title, fallback pageTitle, fallback `browser`.
- WebView body.
- Web content toolbar: back, forward, reload/stop, open externally.
- Navigation bottom toolbar: Done, bookmark toggle, share.

## Behavior

- Back/forward buttons are disabled when unavailable.
- Reload changes to stop while loading.
- Bookmark state loads from DB for initial URL.
- Toggle bookmark uses current WebView URL and title.
- Share uses current URL fallback initial URL.
- Open external launches browser intent.

## Test cases

- Initial URL loads.
- WebView model updates title/currentURL/loading/nav booleans.
- Bookmark add/remove round trip.
- Back/forward disabled/enabled states.
- Reload/stop action switches by loading state.
