# Velocity Launcher v0.4 — Real native Android launcher

An Android HOME app built in Java. **This is an independent Android launcher, not Apple iOS or a web app.**

## What's included

- Native Android HOME intent + default launcher selection, first run onboarding.
- App grid and dock populated using actual installed Android launcher applications/icons.
- A modern iPhone-like four-column home layout with adjustable icon size.
- Three icon presentations (Original, Dark monochrome, Clear background).
- Camera-cutout geometry detection (DisplayCutout API) with manual Island alignment.
- Live notification list from Android `NotificationListenerService`, permission-gated.
- Native overlay Control Centre with vector icons, real torch, volume and brightness, real Android settings redirects for protected Wi-Fi/Bluetooth/mobile controls.
- A wallpaper browser with 120 original high-resolution wallpapers built into the APK, plus photo import and original device wallpaper.
- Native Settings page, access to real Android system settings, and theme selection.

## Limits and disclosures

Device Owner is **not** enabled in this build; this app cannot globally replace Android SystemUI or silently toggle protected system settings. It hides the status bar *inside its own window only* and preserves the phone's existing navigation bar. The Island is an in-launcher visual feature, not a system-wide floating system overlay. The built-in photos are **original procedurally-rendered artwork** rather than Apple graphics. The installed app icons come from the user's phone rather than redistributed Apple assets.

## Build

Push to `main`: `.github/workflows/android-apk.yml` generates 120 offline JPEG wallpapers, installs JDK 17 + Android SDK platform 35, builds using Gradle 8.10.2 and uploads a debug APK.

The workflow includes a 100 MB file-size check. The artwork is real selectable wallpaper content—not dummy padding. In the event that original generated images compress below the threshold, the workflow fails and requires actual artwork improvement rather than fake padding.

## Install

Download the artifact from Actions, extract APK, allow Install Unknown Apps, install, open Velocity and choose it as the default Home app. If an old debug APK has a different signing key, uninstall the old test app first. This clears launcher settings.
