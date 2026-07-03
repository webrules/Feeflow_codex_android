# SettingsView spec

**iOS source:** `Feedflow/Views/SettingsView.swift`

## Purpose

Configure Gemini API key and reading prefetch preference.

## Android UI

- Modal form titled `settings`.
- Section `gemini_api_key`: secure text field `enter_api_key`, caption `api_key_note`.
- Section `reading`: switch `background_prefetch`, caption `background_prefetch_note`.
- Bottom toolbar Cancel and Save.

## Behavior

- On appear, load encrypted `gemini_api_key`.
- Save stores key encrypted and writes background prefetch boolean.
- Cancel dismisses without saving pending edits.

## Test cases

- Save/load/update/remove plain setting.
- Encrypted setting does not equal plaintext and round-trips.
- Plaintext migration encrypts legacy value.
- Prefetch toggle persists.
- Cancel leaves previous value unchanged.
