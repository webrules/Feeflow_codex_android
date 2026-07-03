# Settings, theme, localization, AI, and TTS technical design

## Settings

### iOS anchors

- `Feedflow/Views/SettingsView.swift`
- `Feedflow/Services/ThemeManager.swift`
- `Feedflow/Services/LocalizationManager.swift`
- `Feedflow/ViewModels/ThreadListViewModel.swift`

### Required keys/defaults

| Setting | iOS storage | Default | Android storage |
|---|---|---|---|
| Dark mode | UserDefaults `isDarkMode` | `true` | DataStore/SharedPreferences, mirrored into Compose theme state. |
| Language | UserDefaults `app_language` | `en` | DataStore/SharedPreferences. |
| Background prefetch | UserDefaults `background_prefetch_enabled` | `false` | DataStore/SharedPreferences. |
| Gemini API key | SQLite encrypted setting `gemini_api_key` | empty | Encrypted SQLite setting. |
| Cookies | SQLite encrypted setting `login_<siteId>_cookies` | none | Encrypted SQLite setting. |

Settings Save behavior:

- Gemini key is saved encrypted.
- Background prefetch is saved on Settings Save.
- Cancel must not write new values.

## Theme/design tokens

### iOS anchors

- `Feedflow/Theme/Theme.swift`
- `Feedflow/Theme/FeedflowIcons.swift`
- screenshots in `page-screenshots/`

Required tokens:

- Light/dark background.
- Card/surface.
- Primary/secondary text.
- Divider.
- Accent.
- Success/warning/destructive.
- Rounded card radius.
- Chip radius.
- Toolbar/action icon sizes.
- List row spacing.
- Source icon mapping.

Known anchors:

- Light background `#F2F2F7`
- Light card `#FFFFFF`
- Light accent `#007AFF`
- Dark background `#0B101B`
- Dark card `#151C2C`
- Dark accent `#2D62ED`

Android must not use raw Material colors in page code; all pages must consume shared Feedflow tokens.

## Localization

### iOS anchor

- `Feedflow/Services/LocalizationManager.swift`

Language contract:

- Supported: `en`, `zh`.
- Default: `en`.
- Missing translation returns the key.
- Runtime toggle updates active UI.
- User-generated/source content is not translated.

Android string-resource rules:

- Every key in iOS `LocalizationManager` must exist in Android English and Chinese resources.
- Visible literals from SwiftUI views must be inventoried and moved into Android string resources.
- Formatted strings require placeholder tests in both languages.

## AI summary

### iOS anchors

- `Feedflow/Services/GeminiService.swift`
- `Feedflow/Views/AISummaryView.swift`
- `Feedflow/Views/DailyRSSSummaryView.swift`
- `Feedflow/Views/CrossSiteAISummaryView.swift`
- `Feedflow/DatabaseManager.swift`

Gemini key:

- Loaded from encrypted setting `gemini_api_key` when service initializes.
- Missing key returns user-facing setup text rather than throwing.

Prompt:

- Includes language instruction:
  - Chinese app language -> Chinese response.
  - Otherwise English response.
- Wraps user/content text.
- Limits input to first 10,000 characters.
- Must not include cookies, credentials, or hidden script content.

Thread summary:

- Detail view passes composed content:
  - title,
  - body,
  - first 25 comments,
  - target language instruction.
- Cache key used by view: `{threadId}_{serviceId}_{language}`.
- DB stores this key as `thread_id` and service/language scope according to implementation; Android should preserve language in the cache key to avoid cross-language reuse.
- Non-force generation checks cache first.
- Force refresh bypasses cache.
- Successful result saved.

Daily RSS summary:

- Uses recent RSS articles.
- Cache freshness from feature inventory: 7 days.
- Aggregate cache key must change when input article set changes.

Error states:

- Missing key -> setup message/action.
- Empty content -> no API call.
- API/network failure -> visible error and retry.
- Cached fallback shown when available and parity says so.

## TTS/accessibility

### iOS anchor

- `Feedflow/Services/SpeechService.swift`

TTS contract:

- Singleton state `isSpeaking`.
- `speak(text, language)` is toggle behavior:
  - if already speaking, stop and return.
  - otherwise speak cleaned text.
- Language mapping:
  - `zh` -> `zh-CN`
  - everything else -> `en-US`
- Rate `0.5`, pitch `1.0`, volume `1.0` equivalent where Android allows.
- `stop()` cancels and sets not speaking.
- Closing AI sheet stops speech.

Accessibility contract:

- Every icon button needs content description.
- Thread rows announce author, title, time, comment count.
- Image viewer supports dismiss/back and zoom without trapping TalkBack.
- TTS reads cleaned text, not raw HTML or `[IMAGE]` tokens.
- Large font scale keeps primary actions reachable.

## Required tests

1. Settings defaults: dark true, language en, prefetch false, Gemini key empty.
2. Save/Cancel behavior for settings.
3. Theme toggle persists and updates Compose colors.
4. Every iOS localization key exists in Android resources.
5. Language toggle updates active UI.
6. Gemini missing key returns setup message and makes no API call.
7. Prompt includes language instruction and truncates content to 10,000 chars.
8. Summary cache key includes language.
9. Daily RSS TTL is 7 days.
10. TTS language mapping and toggle-stop behavior.
11. Accessibility content descriptions for all toolbar icons.
