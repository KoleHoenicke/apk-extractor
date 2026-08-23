# APK Extractor

[![Android CI](https://github.com/KoleHoenicke/apk-extractor/actions/workflows/android.yml/badge.svg)](https://github.com/KoleHoenicke/apk-extractor/actions/workflows/android.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A free, ad-free, open-source APK extraction utility for Android.

APK Extractor lists the applications installed on the device and copies their original APK files to a folder selected through Android's system document picker. A conventional installation is exported as a single `.apk`. An app installed as a base APK plus configuration or feature splits is exported as a ZIP containing every installed APK and a `manifest.json` with filenames, split names, sizes, and SHA-256 hashes.

Long-pressing an app enters contextual selection mode. Multiple selected apps are exported concurrently with independent per-app progress and one final result message. Exports run in a data-sync foreground service so they can finish while the screen is off or another app is open. Android 16.1 and newer can surface the native progress notification as a promoted Live Update and status-bar chip; older versions receive a standard ongoing progress notification. Finished notifications can share the exported files directly through Android's share sheet.

## Principles

- Native Jetpack Compose interface using Material 3 Expressive and Google Sans Flex
- Edge-to-edge, dynamic color, light and dark appearance
- Adaptive phone, tablet, foldable, split-screen, and desktop-window layouts
- Live list updates when apps are installed, updated, or removed
- Physical-keyboard support with `Ctrl+F` for search and `Esc` for dismissal
- No advertisements, subscriptions, accounts, analytics, or network permission
- No root access or broad storage permission
- Byte-for-byte extraction that preserves the installed APK signatures
- Honest handling of split APKs instead of exporting an incomplete `base.apk`

## Build

The project uses Android SDK 37, Android Gradle Plugin 9.3.1, Gradle 9.7.1, Kotlin 2.4.10, Compose BOM 2026.08.00, and Material 3 1.5.0-alpha26.

Install JDK 17 and Android SDK 37, then run:

```bash
./gradlew build
```

The debug APK is written beneath `app/build/outputs/apk/debug/`. Release signing is configured only when the four `APK_EXTRACTOR_UPLOAD_*` environment variables are supplied; signing credentials and keystores are never stored in this repository.

## Contributing

Bug reports, accessibility improvements, translations, and focused compatibility fixes are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Security-sensitive reports should follow [SECURITY.md](SECURITY.md).

## Privacy and package visibility

The app does not request internet access and does not transmit or collect data. It requests `QUERY_ALL_PACKAGES` because discovering every installed app is its sole user-facing purpose and a finite `<queries>` declaration cannot describe that set. Google Play classifies the installed-app inventory as sensitive; Play distribution is subject to approval and requires an accurate Permissions Declaration Form. Approval is not guaranteed, so this permission must be reviewed again before publishing.

## Export limitations

An extraction contains the APK files installed for the current device, not the publisher's original Android App Bundle and not private application data. Split APK archives may be specific to the current device's ABI, density, language, and installed feature set. Third-party APKs remain the property of their respective publishers and should not be redistributed without permission.

## License

Apache License 2.0. Material Symbols are provided by Google under the Apache License 2.0. Google Sans Flex is provided by Google under the SIL Open Font License 1.1; see [NOTICE](NOTICE) and [licenses/OFL-1.1.txt](licenses/OFL-1.1.txt).

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License. Android is a trademark of Google LLC.
