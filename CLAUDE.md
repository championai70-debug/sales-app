# Sales App: notes for Claude Code

Android app (Kotlin, Jetpack Compose) for salespeople: ranks products per shop with an
on-device model, suggests amounts and shares limited stock. No server, no AI service at runtime.

- `core/` is plain Kotlin (no Android) and holds all logic; test it with `./gradlew :core:test`.
  In a sandbox without Android SDK access, build `core` alone from a settings file that
  includes only `:core` with `kotlin("jvm") version "2.1.0" apply false` at the root.
- `app/` is the UI. The APK is built by `.github/workflows/build-apk.yml` and published at
  the `latest-apk` release.
- Add a test in `core/src/test` for every logic change or bug fix.
- User-facing text: short, plain English. Prices in euros (`€1.49`).
