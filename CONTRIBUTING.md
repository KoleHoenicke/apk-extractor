# Contributing to APK Extractor

Thanks for helping make APK extraction better on Android.

## Good contributions

- Reproducible bug fixes across Android versions and device types
- Accessibility, keyboard, tablet, foldable, and localization improvements
- Focused performance or reliability improvements
- Tests that cover extraction, sharing, filtering, and adaptive layouts

Please open an issue before beginning a large feature or redesign. APK Extractor intentionally stays small, native, free, and ad-free.

## Development setup

1. Install JDK 17 and Android SDK 37.
2. Clone the repository.
3. Run `./gradlew build`.
4. Run connected tests with `./gradlew connectedDebugAndroidTest` when an emulator or device is available.

Keep signing credentials outside the repository. A debug build requires no signing setup.

## Pull requests

- Keep each pull request focused on one change.
- Explain the user-visible behavior and the Android versions or form factors tested.
- Add or update tests where practical.
- Run `./gradlew lint testDebugUnitTest assembleDebug` before submitting.
- Include screenshots or a short recording for visible UI or motion changes.
- Preserve the existing Material 3 Expressive, edge-to-edge, and adaptive behavior.

By contributing, you agree that your contribution is licensed under the Apache License 2.0.

## Translations

English strings live in `app/src/main/res/values/strings.xml`. Translations use Android resource directories: `values-es`, `values-pt` (Brazilian wording), `values-fr`, `values-ar`, and `values-fa`. The app follows Android language preferences; Android 13 and later also offer an app-specific language in Settings.

Translate complete messages in screen context. Keep APK, package identifiers, app names, and filenames intact. Resources marked `translatable="false"` include the product name and verbatim legal attributions. Preserve placeholders (`%1$s`, `%d`, `%%`) and the direction-isolation escapes around names in RTL messages. Provide the plural categories required by the language; do not assume English singular/plural rules apply.

Run `python3 scripts/check-localizations.py` and Android lint after editing. Check the actual screens with large text and RTL layout. The initial translations were AI-assisted; wording corrections from speakers are welcome. Include the screen, current wording, and suggested replacement in an issue or pull request.
