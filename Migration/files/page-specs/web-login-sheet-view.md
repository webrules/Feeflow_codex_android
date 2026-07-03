# WebLoginSheetView spec

**iOS source:** `Feedflow/Views/WebLoginView.swift`

## Purpose

Modal wrapper around `WebLoginView` with explicit Save Session and login-success state.

## Android UI

- Modal navigation titled with service name.
- WebLogin content until accepted login.
- Success state: large green check, `login_success`.
- Bottom toolbar: Cancel/Done and Save Session.
- Bottom overlay when saving or when save error exists.

## Behavior

- Save Session manually reads current WebView cookies, filters site cookies, validates auth session, then calls completion.
- `isSavingSession` prevents duplicate saves.
- Accepted completion switches to success state.
- Manual failure shows `signed_out`.
- Cancel/Done dismisses.

## Test cases

- Save disabled while saving or after login.
- Missing auth cookie shows signed-out error.
- Accepted session sets success state.
- Duplicate save taps call completion once.
- Dismiss button label changes after success.
