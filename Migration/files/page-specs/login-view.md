# LoginView spec

**iOS source:** `Feedflow/Views/LoginView.swift`

## Purpose

Select site, inspect login status, launch WebLogin/OAuth, and logout.

## Android UI

- Modal screen titled `login`, bottom Cancel.
- Horizontal selector for HN, 4D4Y, V2EX, Linux.do, Zhihu; no RSS.
- Site selector item: site icon, check/circle login badge, short label, selected accent outline.
- Signed-out card with icon, `signed_out`, `login_to_site {service}`, arrow icon.
- Signed-in card with green seal, service name, web-login button, red logout pill.
- OAuth section: divider row, label, two-column provider grid.
- Note card with `login_web_note`.

## Behavior

- On appear, restore each login site session.
- Site selection animates and updates content.
- Browser login uses selected site config; OAuth overrides login URL.
- Successful login filters site-domain cookies, requires auth cookie, upgrades session cookies to 30 days, saves DB cookies, clears stale topic cache, validates session, updates status, dismisses and navigates to site.
- Logout clears DB cookies, login settings, system cookies, and WebView cookies for domain.

## Test cases

- Login sites exclude RSS.
- OAuth counts: Linux.do 6, V2EX 2.
- Required cookies for Zhihu/Linux.do/4D4Y.
- Domain filtering excludes Google/Cloudflare.
- Session-only cookie upgraded, existing expiry preserved.
- Logout clears DB/runtime cookies.
- Failed validation does not mark signed in.
