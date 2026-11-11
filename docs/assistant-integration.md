# Android assistant integration

APK Extractor exposes three Android MCP tools using Jetpack AppFunctions 1.0.0-rc01. The platform service is enabled on Android 16/API 36 and later and requires Android's `BIND_APP_FUNCTION_SERVICE` permission. Android authorizes callers. Older devices keep the ordinary extraction UI without loading this service.

There is no new search field, chat screen, model download, account, or network permission. Gemini or another authorized assistant interprets natural-language requests and supplies typed parameters. APK Extractor evaluates those parameters against the current installed-app catalog locally. It does not run a second Gemini Nano agent internally.

## Actions

| Action | Behavior |
| --- | --- |
| `searchInstalledApps` | Returns a page of installed-app metadata, sorted by exact package identifier, and the complete match count. Default page size 50, maximum 100. |
| `prepareExport` | Prepares the complete matching selection, up to 500 apps, and returns an immutable, single-use `PendingIntent` opening the existing selection UI. No file copying or sharing occurs. |
| `getExportStatus` | Returns active progress and the latest completed result counts. Does not change the journal, recover files, or grant file access. |

All filters in `criteria` are combined with AND:

- `query`: a case-insensitive substring of the label or package identifier, not an unparsed command.
- `packageNames`: exact identifiers from a previous search. For named-app requests, search first and resolve ambiguous names before preparing an export.
- `appType`: `user`, `system`, or `all`. Omitted means `user`.
- `updatedSinceMillis`: inclusive Unix epoch milliseconds. The assistant resolves phrases such as "this week" using the user's timezone.
- `minimumBytes` and `maximumBytes`: inclusive combined size of the base APK and all installed splits.
- `splitApks`: `true` for split installations, `false` for single APKs, omitted for either.

For example, "select non-system apps updated this week" maps to `appType=user` and a computed `updatedSinceMillis`. "Show split APK apps larger than 500 MB" maps to `splitApks=true` and `minimumBytes=500000001` for strictly greater than 500 decimal MB. The app checks metadata; it does not infer app categories from names.

Preparation never silently truncates a selection. Missing explicit packages, invalid ranges, more than 500 matches, and an already-running export return typed errors. The user opens the review action and taps the existing Extract button. Normal folder selection and notification handling still apply. Prepared selections expire after 15 minutes, are stored privately outside backups, and cannot be replayed to overwrite another selection. Packages are rechecked on opening. Opening a review while an export is already running leaves that export alone; prepare a fresh selection afterward.

## Platform testing

The generated IDs use this prefix:

```text
com.kolehoenicke.apkextractor.assistant.BaseApkAppFunctionService#
```

After installing on an Android 16+ test device, allow indexing to complete and inspect the registry:

```bash
adb shell cmd app_function list-app-functions --package com.kolehoenicke.apkextractor
```

Execute a query using the actual registered metadata:

```bash
adb shell cmd app_function execute-app-function \
  --package com.kolehoenicke.apkextractor \
  --function 'com.kolehoenicke.apkextractor.assistant.BaseApkAppFunctionService#searchInstalledApps' \
  --parameters '{"criteria":{"query":"APK Extractor","appType":"all"},"limit":10}'
```

`ApkAppFunctionsTest` exercises the registry, real service, request/response serialization, optional filters, pagination, errors, review `PendingIntent`, and progress. `AssistantExportHandoffTest` covers lifecycle, private tokens, expiry, replay protection, and an active export. Run those classes on an isolated device, along with existing XR and desktop regressions.

Shell execution and instrumentation prove Android tool integration, not Gemini discovery. Follow Google's [Android MCP connected-app guide](https://developer.android.com/ai/appfunctions/create-manage-connected-apps) to register the integration and test it before public release. As of November 10, 2026, the developer account has applied for Connected Apps private-preview access, but the live console has not enabled connected-app creation. Direct Gemini invocation remains unverified. Do not advertise end-to-end Gemini availability until verified.

## XR dependencies

XR Runtime, SceneCore, and transitive ARCore resolve to stable 1.0.0. Compose for XR is 1.0.0-beta02; XR Material remains 1.0.0-alpha17 because no stable replacement exists. Existing layout, panel controls, and Material compatibility wrappers are retained.

## References

- [AppFunctions releases](https://developer.android.com/jetpack/androidx/releases/appfunctions)
- [AppFunctions platform integration](https://developer.android.com/ai/appfunctions)
- [Connected Apps and Android MCP](https://developers.google.com/connected-apps)
- [Android Developer Summit intelligence announcements](https://developer.android.com/blog/posts/android-intelligence-system-in-app-agents-ads)
- [XR Runtime releases](https://developer.android.com/jetpack/androidx/releases/xr-runtime)
- [SceneCore releases](https://developer.android.com/jetpack/androidx/releases/xr-scenecore)
- [Compose for XR releases](https://developer.android.com/jetpack/androidx/releases/xr-compose)
