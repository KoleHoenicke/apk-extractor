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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kolehoenicke.apkextractor.ui.ApkExtractorApp
import com.kolehoenicke.apkextractor.ui.theme.ApkExtractorTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val inAppReviewCoordinator by lazy { InAppReviewCoordinator(this) }
    private val focusSearchRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var packageReceiverRegistered = false
    private var hasStartedOnce = false
    private var canOpenNewWindow by mutableStateOf(false)
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
        updateWindowCapabilities()
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
                    onNewWindow = if (canOpenNewWindow) ::openNewWindow else null,
                )
            }
        }
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (canOpenNewWindow && keyCode == KeyEvent.KEYCODE_N && event.isShiftPressed &&
            (event.isCtrlPressed || event.isMetaPressed) && event.repeatCount == 0
        ) {
            openNewWindow()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_F &&
            (event.isCtrlPressed || event.isMetaPressed) &&
            focusSearchRequests.tryEmit(Unit)
        ) {
            return true
        }
        return super.onKeyShortcut(keyCode, event)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        updateWindowCapabilities()
    }

    private fun updateWindowCapabilities() {
        canOpenNewWindow = Build.VERSION.SDK_INT >= 35 && isInMultiWindowMode
    }

    private fun openNewWindow() = startActivity(createNewWindowIntent(this))

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
                    KeyboardShortcutInfo(
                        getString(R.string.keyboard_shortcut_select_all),
                        KeyEvent.KEYCODE_A,
                        KeyEvent.META_CTRL_ON,
                    ),
                    KeyboardShortcutInfo(
                        getString(R.string.extract_selected),
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.META_CTRL_ON,
                    ),
                    KeyboardShortcutInfo(
                        getString(R.string.keyboard_shortcut_toggle),
                        KeyEvent.KEYCODE_SPACE,
                        0,
                    ),
                    KeyboardShortcutInfo(
                        getString(R.string.keyboard_shortcut_menu),
                        KeyEvent.KEYCODE_F10,
                        KeyEvent.META_SHIFT_ON,
                    ),
                ) + if (canOpenNewWindow) listOf(
                    KeyboardShortcutInfo(getString(R.string.new_window), KeyEvent.KEYCODE_N,
                        KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),
                ) else emptyList(),
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
