# Privacy

APK Extractor processes the installed-app inventory locally on the device. It has no internet permission, advertising SDK, analytics SDK, account system, or remote service. It does not sell installed-app information.

On Android 16 and later, authorized Android assistants can search installed-app metadata, prepare an export selection, and read export progress and the most recent result counts through Android MCP/AppFunctions. Responses can include app names, package identifiers, versions, APK sizes, split counts, update times, and progress. An assistant's processing of these responses is governed by its own privacy policy. These actions do not return APK files, export-folder access, or private application data, and do not start extraction or sharing.

Prepared selections are stored in app-private storage excluded from backups. They expire after 15 minutes, are single-use, and are limited to eight pending selections. Opening a prepared selection rechecks installed packages and uses the existing app UI. The user must tap Extract to begin copying files.

The selected export-folder URI is stored locally so later exports can return to that folder. Users can replace it at any time with the folder button. Exported APKs and ZIP archives are written only to the user-selected document provider.

