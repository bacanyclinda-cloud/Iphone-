# iPhone Native Rebuild — independent Android launcher build

This is a completely new launcher build track. The older Velocity Launcher remains in the repository only as a rollback; its UI and APK are **not** used in this build.

## Native foundation
We use the open-source [Folio Launcher](https://github.com/McCal-Codes/folio), tag **v0.6.7.3**, licensed under the MIT License. The GitHub workflow fetches that fixed release and builds a **native Kotlin + Jetpack Compose** Android APK, including its source and license. Original authors retain attribution. This is not Apple's proprietary iOS software, and Apple does not endorse it.

Why Folio: four-column phone home screen; installed Android app icons and supported icon packs; translucent dock; real app launching; App Library and search; folders; widgets; icon themes; Control Center; Notification Center; Dynamic Island; screen-sized adaptive layouts and optional camera-cutout alignment. Some system controls open Android-provided panels because apps cannot toggle them silently.

The installed launcher is labeled **iPhone Native Rebuild** (original project remains clearly credited in About/source). Android 12+ is required. On first launch choose the new Home application.

## Build / source
- GitHub Actions workflow: `.github/workflows/iphone-rebuild.yml`.
- Build target: `:app:assembleFast` (optimized but debug signed). The package is independent of Velocity Launcher, so both can coexist until you change the default Home app.
- Download APK from the successful Actions run's `iphone-native-rebuild-apk` artifact.
- Download original open-source snapshot from the same run's source artifact, including Folio's MIT `LICENSE` and `THIRD_PARTY_NOTICES.md`.
- The upstream source remains available via the tag linked above. Keep copyright and license notices in redistributed copies.

## Limits / honesty
A launcher cannot actually install iOS or its closed-source apps, replace Android's secure keyguard, or own the OS-wide notification shade. Folio's own Control Center/Notification Center and Dynamic Island use Android-authorized integration where possible. Optional accessibility/overlay permissions may be required, and are enabled by the phone owner. This build has **not** been tested on your particular phone until you install it.
