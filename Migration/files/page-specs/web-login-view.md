# WebLoginView spec

**iOS source:** `Feedflow/Views/WebLoginView.swift`

## Purpose

Embedded WebView login that supports captcha, OAuth, popups, and session cookie capture.

## Android UI

- Android WebView fills parent.
- Use default persistent cookie/data store.
- Popup web views must render over current WebView with visible close affordance.

## Behavior

- Load configured login URL.
- Use Safari-like/user-agent-compatible string when needed to reduce OAuth blocks.
- Allow JavaScript popups.
- On page finish:
  - If success URL or post-login URL, check cookies with 8 retries.
  - If same-domain page, check cookies with 3 retries.
- Authenticated cookie set is site-domain filtered and validated via `SiteLoginConfig`.
- Prevent duplicate reports of same rejected cookie signature.
- Popup close triggers cookie check.

## Data

- Input: `SiteLoginConfig`, async `onLoginSuccess(cookies) -> Bool`.

## Test cases

- Site domain filtering.
- Success URL pattern detection.
- Login URL and post-login URL detection.
- Cookie retry count behavior.
- Rejected signature suppression.
- Popup create/close invokes cookie checks.
