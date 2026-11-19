# Velocity Launcher — Native Android project

This is native Android application source code, **not an HTML prototype**. Version **0.2.0** includes a redesigned home screen, a native Control Centre and locally generated original wallpapers. It is an early functional launcher project, not an iOS clone or a finished production release.

## Implemented in this source

- Registered Android **HOME** launcher; can be chosen as the default Home app without Device Owner.
- Native display-sized UI retaining the system navigation bar; a custom status readout inside the launcher.
- Reads **actual installed launcher activities and their package-manager icons** (no placeholder application list).
- Launches installed apps, searches apps, and supports horizontal paging.
- User-selected photos from the system picker, current device wallpaper, multiple built-in gradient backgrounds, and optional online car photo wallpapers (downloaded and cached after choice, requiring Internet).
- First-run setup, notification access shortcut, and Android Home-role request.
- iPhone-inspired **Control Centre** with real media volume slider, brightness control, rear torch control, Focus / Do Not Disturb (with Android approval), and links to Wi-Fi/Bluetooth/mobile/flight mode system settings. Swipe down from the top-right of Home; top-left opens notifications.
- **24 original offline glass-style wallpapers** generated natively on the device, plus 10 optional online car photos and user photo import.
- Local **NotificationListenerService** displays real active notifications in a scrollable custom panel when permission is granted; notification taps attempt to open their corresponding content intents.
- Dynamic Island-style custom view uses `DisplayCutout` bounding rectangles where available, plus manual correction sliders. Exact camera-hole geometry varies by device, and calibration may be required.
- Real camera torch control via `CameraManager` when a flash is present and the user grants camera access.
- Settings page with display detection information, offline backgrounds, photo pick, system settings and align controls.

## Important Android limits

- A normal launcher cannot replace Android SystemUI everywhere. The Android notification shade remains outside the launcher, and on supported hardware may still be revealed by system gestures. Device Owner / enterprise restrictions are a separate later phase. Do **not** grant Device Owner until explicitly planned and tested.
- The custom display panel only covers the launcher's window; it is not a replacement for secure Android settings, lockscreen, notification policy, or system apps.
- Home role does not require Device Owner and can be changed by the user.
- Android 15/16 edge-to-edge and OEM camera cutouts need testing on a physical device.
- Image/photo URLs may not be available offline on first use; offline gradient wallpapers and gallery import always remain available.
- No client data, messages, or notification contents are uploaded by the app; photo downloads use HTTPS from Unsplash.

## Build APK on GitHub

1. Create a **new, empty GitHub repository** (do not reuse an existing app repository).
2. Upload this project's files, keeping the `.github/workflows/android-apk.yml` path.
3. Open the repository's **Actions** tab, select **Build real Android APK**, run it (or push to main).
4. When the build succeeds, download `velocity-launcher-debug-apk` from that run's Artifacts.
5. Unzip the artifact and install `app-debug.apk` on Android 8+ (subject to target device compatibility).

The debug APK is installable for testing but is **not a signed release distribution**. For release, use a permanent private signing keystore and update tests.

## Size

Size measures bytes only — it does not prove completeness. This project deliberately does **not** pad its APK with filler. Real features must be tested on the target device, not inferred from file size.
