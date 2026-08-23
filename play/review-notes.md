# Google Play review notes

## Core functionality

APK Extractor's sole user-facing purpose is to display the applications installed on the device and export the APK files for applications explicitly selected by the user. Without visibility of all installed packages, the app cannot present the complete inventory it exists to export and becomes unusable for its core purpose.

The installed-app inventory is processed only on the device. The app declares no Internet permission and contains no advertising, analytics, account, or remote-service SDK. Inventory information is never transmitted, sold, or shared.

## QUERY_ALL_PACKAGES review instructions

1. Launch APK Extractor.
2. Observe the complete installed-app list. Use User, System, and All to filter it.
3. Tap an app to extract it, or long-press an app and select several apps.
4. Choose an export folder using Android's system document picker.
5. Observe per-app progress and the completed output in the selected folder.

A finite `<queries>` manifest declaration cannot identify the arbitrary set of apps each user may have installed, so it cannot support the app's core function.

## Foreground service: dataSync

Exports are initiated directly by the user. The `dataSync` foreground service copies the selected installed APK files to the user-selected Storage Access Framework folder. Immediate execution prevents large exports from being interrupted when the user turns off the screen or switches apps. The ongoing notification shows progress and provides a Cancel action. The service stops as soon as all requested exports finish or the user cancels.

## Reviewer access

No account, sign-in, payment, subscription, or other restricted access is required.

## Permission demonstration video

https://apk.kole.work/review/apk-extractor-permissions.mp4
