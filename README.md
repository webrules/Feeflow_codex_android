# Feedflow

Feedflow is a native iOS reader for forums, social news, knowledge feeds, and RSS sources. It gives each source a consistent reading experience while preserving site-specific capabilities such as login, replies, recommendations, bookmarks, AI summaries, and offline cache.

## Features

- **Multi-source reading**: Browse 4D4Y, Linux.do, Hacker News, V2EX, Zhihu, and RSS feeds from one SwiftUI app.
- **Source-aware posting**: Reply and create topics where the source supports it natively. 4D4Y supports replies and new threads; Linux.do supports replies and new topics through the Discourse posts API; V2EX supports replies; read-only sources hide unsupported composers.
- **RSS management**: Read the bundled feeds, add custom feeds manually, and import OPML files.
- **Daily RSS briefing**: Generate a Gemini-powered daily summary of recent feed updates.
- **AI summaries**: Summarize threads with Google Gemini and cache results locally by source and thread.
- **Offline cache**: Cache communities, thread lists, and thread details in SQLite for fast return visits and offline reading.
- **Bookmarks**: Save forum threads and in-app browser pages.
- **Browser login**: Capture site sessions through an embedded web login flow with support for captcha, 2FA, and OAuth-based sign-in where the site offers it.
- **Dark mode and bilingual UI**: Switch between dark/light presentation and English/Chinese labels.

## Supported Sources

| Source | Content | Login | Replies | New Threads | Notes |
| --- | --- | --- | --- | --- | --- |
| 4D4Y | Discuz forum categories, threads, posts | Required | Yes | Yes | Handles GBK/GB18030 pages, formhash, sid, and Discuz cookies. |
| Linux.do | Discourse latest/category topics and posts | Required | Yes | Yes | Uses Discourse JSON APIs and browser-session cookies. |
| Hacker News | Top, new, best, show, ask, and jobs stories | Optional browser session | No | No | Official Firebase API is read-only. |
| V2EX | Public tab feeds and thread details | Optional for reading, required for replies | Yes | No | Uses web session cookies and the page `once` token for replies. |
| Zhihu | Recommendations, hot list, answers, articles, questions | Required | No | No | Supports content detail, comments, and local/server "not interested" feedback. |
| RSS | RSS 2.0 and Atom feeds | Not required | No | No | Supports manual feeds, OPML import, article reading, and daily briefing. |

## Requirements

- Xcode 26 or newer
- iOS 26 simulator/runtime or device
- Swift 5.9+

The current project settings target iOS 26.x. Older iOS support would require a separate compatibility pass.

## Getting Started

```bash
git clone https://github.com/webrules/Feedflow.git
cd Feedflow
open Feedflow.xcodeproj
```

In Xcode, select the `Feedflow` scheme and run it on an iOS simulator or device.

## Configuration

### Gemini API Key

AI summaries are optional. To enable them:

1. Open Feedflow.
2. Tap the key icon on the home screen.
3. Enter a Gemini API key from Google AI Studio.

The key is stored locally in the app database.

### Site Login

Some sources require a browser session before content or posting works.

1. Tap the person icon on the home screen.
2. Select the target site.
3. Sign in through the embedded browser.

Feedflow stores relevant session cookies locally and encrypts them before saving.

## Architecture

- **SwiftUI + MVVM** for views and state management.
- **ForumService protocol** for source-specific implementations behind a shared content model.
- **SQLite** for settings, encrypted cookies, bookmarks, summaries, and content cache.
- **URLSession + async/await** for networking.
- **Native parsed content rendering** for cleaned HTML, links, and images.

## Notes

Feedflow integrates with sites that may change their HTML or private APIs. Service implementations prefer official JSON APIs where available and fall back to source-specific parsing only when needed.

## License

MIT License.
