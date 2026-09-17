package com.kolehoenicke.apkextractor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.kolehoenicke.apkextractor.ui.ApkExtractorApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val inAppReviewCoordinator by lazy { InAppReviewCoordinator(this) }
    private val focusSearchRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var packageReceiverRegistered = false
    private var hasStartedOnce = false
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) {
                return
            }
            viewModel.refreshSilently()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ enforces edge-to-edge for our target SDK. Avoid the bar-color
            // setters in Activity's compatibility helper on these versions.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val darkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        } else {
            // Older Android versions still need AndroidX's bar-color/cutout compatibility.
            enableEdgeToEdge()
        }

        setContent {
            ApkExtractorTheme {
                ApkExtractorApp(
                    viewModel = viewModel,
                    focusSearchRequests = focusSearchRequests,
                    onSuccessfulExtractionSession =
                        inAppReviewCoordinator::onSuccessfulExtractionSession,
                )
            }
        }
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F &&
            (event.isCtrlPressed || event.isMetaPressed) &&
            focusSearchRequests.tryEmit(Unit)
        ) {
            return true
        }
        return super.onKeyShortcut(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.repeatCount == 0) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data.add(
            KeyboardShortcutGroup(
                getString(R.string.keyboard_shortcuts),
                listOf(
                    KeyboardShortcutInfo(
                        getString(R.string.keyboard_shortcut_search),
                        KeyEvent.KEYCODE_F,
                        KeyEvent.META_CTRL_ON,
                    ),
                    KeyboardShortcutInfo(
                        getString(R.string.keyboard_shortcut_dismiss),
                        KeyEvent.KEYCODE_ESCAPE,
                        0,
                    ),
                ),
            ),
        )
    }

    override fun onStart() {
        super.onStart()
        if (!packageReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                this,
                packageChangeReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            packageReceiverRegistered = true
        }
        if (hasStartedOnce) {
            viewModel.refreshSilently()
        } else {
            hasStartedOnce = true
        }
    }

    override fun onStop() {
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChangeReceiver)
            packageReceiverRegistered = false
        }
        super.onStop()
    }
}
