# Changelog

Notable changes to APK Extractor are documented here.

## 1.0.3

- Avoid recursive supporting-text baseline measurement in app rows while retaining native Material list styling.
- Add Spanish, Portuguese, French, Arabic, and Persian with native Android app-language settings and RTL support.
- Manage the export folder in a compact dialog and choose sorting from a Material submenu.
- Open export folders from the app where supported by the device.
- Keep the last export result locally, including individual failures, sharing, and retry for installed apps.
- Limit simultaneous exports to three and check cancellation while copying.
- Recover interrupted export results and clean up recorded partial files when possible.
- Add remembered sorting by name or most recently updated.
- Preserve international app names and keep export filenames within common filesystem byte limits.

## 1.0.2

- Update Compose to 1.12.1 to fix recursive alignment measurement crashes when scrolling app lists.
- Bypass Android 8's adaptive-icon bitmap shader when rendering installed-app icons.
- Update the Fragment dependency pulled in by Google Play Review to 1.8.9.
- Use the modern edge-to-edge setup on Android 15 and later.
- Keep search above the keyboard in short landscape windows.
- Let full-screen search results scroll behind the gesture bar while keeping the final result reachable.

## 1.0.1

- Fix crashes while displaying and scrolling app lists.
- Show a helpful error when the system folder picker or sharing activity is unavailable.
- Continue extraction if the notification permission activity cannot open.

## 1.0.0 — 2026-08-23

Initial open-source release.

- Extract conventional APKs directly to a user-selected folder.
- Preserve split installations as ZIP archives with a manifest, split names, sizes, and SHA-256 hashes.
- Select and extract multiple apps concurrently with independent progress.
- Continue extraction in the background with native progress notifications and supported Live Updates.
- Share completed exports from the notification or Android share sheet.
- Search and filter user, system, or all installed apps.
- Refresh automatically when packages are installed, updated, or removed.
- Support phones, tablets, foldables, split-screen, desktop windows, and physical keyboards.
- Use a Material 3 Expressive, edge-to-edge interface with dynamic color, light mode, and dark mode.
- Operate without advertisements, subscriptions, accounts, analytics, or internet permission.
