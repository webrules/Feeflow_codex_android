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

## Requirements

- Android Studio or another JDK 17+ installation.
- Android SDK Platform 35 and matching build tools.
- An SDK path configured through `ANDROID_HOME` or `local.properties`.

For a local SDK configuration:

```sh
cp local.properties.example local.properties
```

Then edit `sdk.dir` in `local.properties` for your machine. The file is intentionally ignored by Git.

## Build and test

From this directory:

```sh
./gradlew :core:data:test :core:database:test :core:network:test :app:assembleDebug
```

Run device or emulator UI tests with:

```sh
./gradlew :app:connectedDebugAndroidTest
```

If the device already has Feedflow installed with a different signing key, use an isolated debug application ID so its data is left untouched:

```sh
./gradlew :app:connectedDebugAndroidTest -Pfeedflow.debugApplicationIdSuffix=.local
```

Run the opt-in, read-only live source checks with:

```sh
./gradlew :core:data:test --tests '*LiveSourceSmokeTest' -Pfeedflow.liveTests=true
```

These checks access only public RSS, Hacker News, V2EX, and 4D4Y guest pages. They do not use saved sessions or perform posting operations.

Authenticated 4D4Y, Linux.do, and Zhihu checks remain manual and should use dedicated test accounts. V2EX search is also excluded because iOS obtains Google results through an embedded browser; the Android WebView search adapter is not implemented yet.

`ios-xctest-manifest.txt` inventories all 262 class-qualified iOS XCTest declarations. `ios-parity-rules-v1.tsv` maps every source test class to an executable Android contract test or an explicit instrumentation replacement; `IosParityInventoryTest` fails when a class is missing, a status is invalid, or a JVM target is not a real JUnit test method.

The parity tests in `core:data/src/test` are translated from the existing XCTest inventory and intentionally cover the same model, service capability, login/cookie, parser, settings/cache, localization, image zoom, and 4D4Y/Zhihu regression surfaces.
