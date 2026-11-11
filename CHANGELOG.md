# Changelog

Notable changes to APK Extractor are documented here.

## 1.0.8

- Load the installed-app list faster with a bulk package query and load icons as their rows appear.
- Keep selection actions within reach in a floating toolbar showing the selected count and Extract action, with visible row checkboxes.
- Refresh filters and toolbar spacing with native Material 3 Expressive components.
- Keep filters full width when their labels fit, with native overflow for larger text and narrow windows.
- Shorten the privacy dialog and make full credits and license texts available offline through a separate Licenses action.
- Show export folder and result details in bottom sheets, with improved layouts for large text and short windows.
- Simplify extraction results with one extracted count, individual file rows, and a standard Material action button group that stacks when space is limited.
- Animate Done through the native bottom-sheet dismissal transition in result and folder sheets.
- Improve search navigation, sorting menus, and scrollbar behavior as the app bar expands and collapses.
- Add Russian, German, Korean, and Simplified Chinese translations with native app-language selection and language-specific count formats.
- Add Android MCP/AppFunctions for authorized assistants to search installed-app metadata, prepare selections for review, and read export status. Gemini availability depends on Google's Connected Apps rollout and approval.
- Update the XR libraries while retaining the existing spatial workspace and controls.

## 1.0.7

- Fix crashes while drawing scrollbars in very short windows, including during resizing and keyboard transitions.
- Finish foreground-service cleanup before accepting another export, avoiding a race during consecutive extractions.

## 1.0.6

- Add native Android XR support with a curved app panel and a separate export workspace.
- Allow switching between Home Space and Full Space while retaining app state, and let users move and resize spatial panels.
- Use larger controls, readable text, and spatial dialogs on XR.
- Keep export reports scrollable with their actions reachable, and round the export workspace's content corners.

## 1.0.5

- Add independent desktop windows with New window and Ctrl/Cmd+Shift+N, synchronized export settings and progress, and window-specific saved search and selection.
- Add native file drag and drop for individual exports or a completed batch, with read-only file access. Material file rows show the filename or batch count, the source app icon when available, hover feedback, and a matching native drag preview.
- Fix Sort by submenu placement in floating windows, including window edges and RTL layouts.
- Add an export-folder and last-result pane in wide desktop windows.
- Add mouse hover, visible keyboard focus, and tooltips for toolbar actions.
- Add right-click and Shift+F10 context menus, Ctrl/Cmd-click selection, and Shift-click range selection.
- Add Ctrl/Cmd+A to select visible apps, Ctrl/Cmd+Enter to extract the selection, and Space to toggle the focused app.
- Show native scroll indicators and allow installation on devices without a touchscreen.
- Avoid a sharing crash when document providers reject stream-type metadata queries.

## 1.0.4

- Explain that split APK exports are ZIP archives containing the base and required split APKs, not single files that Android can install by tapping.
- Make split exports explicit in the app list and show split-install guidance after export.

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
