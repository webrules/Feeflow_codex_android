# Feedflow Android

Native Android scaffold for duplicating the existing SwiftUI Feedflow app with Kotlin, Jetpack Compose, Material 3, Navigation Compose, and modular foundations for source services, persistence, network, and encrypted local secrets.

## Structure

- `app`: Compose UI shell mirroring the iOS home grid, search entry, bottom toolbar, community list, and thread-list flow.
- `core:model`: Kotlin data models matching Feedflow's Swift models.
- `core:data`: source service contracts, mock repository, localization, parsers, and source-specific helper logic.
- `core:database`: persistence interface plus an in-memory parity implementation; the app provides a SQLite-backed store (`AndroidSqliteFeedflowStore`) for on-device persistence.
- `core:network`: HTTP client and cookie matching foundations, including per-request cookies, custom headers, and forced-charset (GB18030) decoding.
- `core:security`: AES-GCM secret store foundation; the app provides an Android Keystore-backed implementation (`KeystoreSecretStore`) so the encryption key is persisted securely across launches.
- `core:ui`: Material 3 theme colors aligned with the iOS Feedflow theme.

## Build and test

From this directory:

```sh
./gradlew :app:assembleDebug :core:data:test
```

If the wrapper is not yet installed in this checkout, run with any compatible Gradle 8.x installation or generate the wrapper from the `android/` directory:

```sh
gradle wrapper --gradle-version 8.10.2
```

The parity tests in `core:data/src/test` are translated from the existing XCTest inventory and intentionally cover the same model, service capability, login/cookie, parser, settings/cache, localization, image zoom, and 4D4Y/Zhihu regression surfaces.
