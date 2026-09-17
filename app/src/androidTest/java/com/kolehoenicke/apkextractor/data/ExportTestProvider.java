package com.kolehoenicke.apkextractor.data;

import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

// Java keeps the standalone test-provider process independent of the target APK's Kotlin runtime.
public class ExportTestProvider extends DocumentsProvider {
    private File root() {
        File root = new File(getContext().getCacheDir(), "exports");
        root.mkdirs();
        return root;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (method.equals("grantTestTree")) {
            getContext().grantUriPermission("com.kolehoenicke.apkextractor",
                DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root"),
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            return new Bundle();
        }
        return super.call(method, arg, extras);
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID});
        c.addRow(new Object[]{"root", "root"});
        return c;
    }
    private MatrixCursor cursor(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE});
    }
    private void row(MatrixCursor c, String id) {
        File f = id.equals("root") ? root() : new File(root(), id);
        if (!f.exists()) return;
        Object[] values = new Object[c.getColumnCount()];
        for (int i = 0; i < values.length; i++) {
            switch (c.getColumnNames()[i]) {
                case Document.COLUMN_DOCUMENT_ID: values[i] = id; break;
                case Document.COLUMN_DISPLAY_NAME: values[i] = f.getName(); break;
                case Document.COLUMN_MIME_TYPE: values[i] = id.equals("root") ? Document.MIME_TYPE_DIR : "application/octet-stream"; break;
                case Document.COLUMN_FLAGS: values[i] = id.equals("root") ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE; break;
                case Document.COLUMN_SIZE: values[i] = f.length(); break;
            }
        }
        c.addRow(values);
    }
    @Override public Cursor queryDocument(String id, String[] projection) {
        MatrixCursor c = cursor(projection); row(c, id); return c;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) {
        MatrixCursor c = cursor(projection);
        File[] files = root().listFiles();
        if (files != null) for (File f : files) row(c, f.getName());
        return c;
    }
    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        try { if (!new File(root(), name).createNewFile()) throw new IOException("Already exists"); }
        catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
        return name;
    }
    @Override public void deleteDocument(String id) throws FileNotFoundException {
        if (!new File(root(), id).delete()) throw new FileNotFoundException(id);
    }
    @Override public boolean isChildDocument(String parent, String id) { return parent.equals("root"); }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(new File(root(), id), ParcelFileDescriptor.parseMode(mode));
    }
}
