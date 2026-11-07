package com.kolehoenicke.apkextractor.ui

import android.content.ClipData
import android.provider.DocumentsContract
import android.view.DragEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.os.SystemClock
import android.widget.TextView
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.test.platform.app.InstrumentationRegistry
import com.kolehoenicke.apkextractor.data.ExportedFile
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ExportDragTest {
    @get:Rule val rule = createComposeRule()

    @Test fun appIconLoadsByPackageAndMissingAppsUseTheFallback() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = loadExportAppIcon(context, context.packageName)
        assertNotNull(icon)
        assertNull(loadExportAppIcon(context, "no.such.extracted.app"))
        assertNull(loadExportAppIcon(context, null))
    }

    @Test fun mouseDragsAnIndividualFile() = dragFiles(1)
    @Test fun mouseDragsABatchOfFiles() = dragFiles(3)
    @Test fun touchLongPressDragsAnIndividualFile() = dragFiles(1, touch = true)

    private fun dragFiles(count: Int, touch: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val tree = DocumentsContract.buildTreeDocumentUri("com.kolehoenicke.apkextractor.test.exports", "root")
        com.kolehoenicke.apkextractor.data.grantExportTestTree()
        val documents = (1..count).map { index ->
            DocumentFile.fromTreeUri(context, tree)!!.createFile(
                "application/vnd.android.package-archive", "Calculator-$index.apk")!!
        }
        val contents = documents.mapIndexed { index, document ->
            "Actual drag fixture $index".toByteArray().also { bytes ->
                context.contentResolver.openOutputStream(document.uri)!!.use { it.write(bytes) }
            }
        }
        val files = documents.map { ExportedFile(it.uri, it.name!!, false, "APK Extractor", context.packageName) }
        val started = AtomicBoolean(false)
        val dropped = AtomicReference<ClipData?>()
        var shareClicks = 0
        try {
            rule.setContent { ApkExtractorTheme {
                Box(Modifier.fillMaxSize().padding(40.dp)) {
                    Column(Modifier.align(Alignment.TopCenter).width(360.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExportFileDragTile(files, Modifier.testTag("source"))
                        TextButton(onClick = { shareClicks++ }) { Text("Share") }
                    }
                    AndroidView(modifier = Modifier.align(Alignment.BottomCenter)
                        .width(400.dp).height(140.dp).testTag("drop"), factory = { viewContext ->
                        TextView(viewContext).apply {
                            text = "Drop target"
                            setOnDragListener { _, event ->
                                when (event.action) {
                                    DragEvent.ACTION_DRAG_STARTED -> { started.set(true); true }
                                    DragEvent.ACTION_DROP -> { dropped.set(event.clipData); true }
                                    else -> true
                                }
                            }
                        }
                    })
                }
            } }
            rule.onNodeWithText("Share").performClick()
            rule.runOnIdle { assertEquals(1, shareClicks) }
            SystemClock.sleep(700) // Let the system's desktop launch animation settle.
            fun center(tag: String): Offset {
                val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
                return node.positionOnScreen + Offset(node.size.width / 2f, node.size.height / 2f)
            }
            val source = center("source")
            val target = center("drop")
            val downTime = SystemClock.uptimeMillis()
            // Compose's local pointer injection bypasses the system input channel. A platform
            // drag needs a real mouse down there so Android can transfer pointer ownership.
            fun input(action: Int, position: Offset) {
                val properties = MotionEvent.PointerProperties().apply {
                    id = 0; toolType = if (touch) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE
                }
                val coordinates = MotionEvent.PointerCoords().apply {
                    x = position.x; y = position.y; pressure = 1f; size = 1f
                }
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    1, arrayOf(properties), arrayOf(coordinates), 0,
                    if (touch || action == MotionEvent.ACTION_UP) 0 else MotionEvent.BUTTON_PRIMARY,
                    1f, 1f, 0, 0, if (touch) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE, 0)
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            try {
                input(MotionEvent.ACTION_DOWN, source)
                if (touch) {
                    // Long-press detection runs on Compose's test clock; native input uses wall time.
                    rule.mainClock.advanceTimeBy(1_000)
                    SystemClock.sleep(600)
                } else SystemClock.sleep(50)
                input(MotionEvent.ACTION_MOVE, source + Offset(0f, 8f))
                SystemClock.sleep(50)
                input(MotionEvent.ACTION_MOVE, source + Offset(0f, 80f))
                rule.waitUntil(5_000) { started.get() }
                input(MotionEvent.ACTION_MOVE, source + Offset(100f, 180f))
                SystemClock.sleep(100)
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = java.io.File(context.filesDir, "drag-preview-${if (touch) "touch" else count}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                input(MotionEvent.ACTION_MOVE, target)
            } finally { input(MotionEvent.ACTION_UP, target) }
            rule.waitUntil(5_000) { dropped.get() != null }
            assertEquals(count, dropped.get()!!.itemCount)
            documents.forEachIndexed { index, document ->
                val uri = dropped.get()!!.getItemAt(index).uri
                assertEquals(document.uri, uri)
                assertArrayEquals(contents[index], context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            }
            rule.runOnIdle { assertEquals(1, shareClicks) }
        } finally { documents.forEach { it.delete() } }
    }
}
