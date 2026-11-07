package com.kolehoenicke.apkextractor.data;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Grants only the fixture tree from its owning UID, as a document picker would. */
public class ExportTestGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        grantUriPermission("com.kolehoenicke.apkextractor",
            DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
