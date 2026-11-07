package com.kolehoenicke.apkextractor.data

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals

fun grantExportTestTree() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val tree = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
    context.startActivity(Intent().apply {
        component = ComponentName(instrumentation.context.packageName, ExportTestGrantActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val deadline = SystemClock.uptimeMillis() + 5_000
    while (context.checkUriPermission(tree, Process.myPid(), Process.myUid(), flags) != PackageManager.PERMISSION_GRANTED &&
        SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
    assertEquals(PackageManager.PERMISSION_GRANTED,
        context.checkUriPermission(tree, Process.myPid(), Process.myUid(), flags))
    instrumentation.waitForIdleSync()
}
