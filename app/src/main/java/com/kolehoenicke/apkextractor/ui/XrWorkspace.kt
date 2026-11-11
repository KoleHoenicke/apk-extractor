@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.kolehoenicke.apkextractor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.xr.compose.material3.SpaceToggleButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.xr.scenecore.scene
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterPosition
import androidx.xr.compose.spatial.SpatialDialog
import androidx.xr.compose.unit.DpVolumeOffset
import androidx.xr.compose.subspace.SpatialCurvedRow
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialSpacer
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.heightIn
import androidx.xr.compose.subspace.layout.SpatialAlignment
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.unit.DpVolumeSize
import com.kolehoenicke.apkextractor.AppUiState
import com.kolehoenicke.apkextractor.R

internal data class XrUi(val isXr: Boolean = false, val isSpatial: Boolean = false)
internal val LocalXrUi = staticCompositionLocalOf { XrUi() }

@Composable
internal fun Modifier.xrActionSize(): Modifier =
    if (LocalXrUi.current.isXr) heightIn(min = 56.dp).widthIn(min = 56.dp) else this

/** Keep the Activity content in place so mode changes retain search, scroll, and dialog state. */
@Composable
internal fun XrWorkspace(
    supportingPane: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val isXr = LocalSession.current != null
    val isSpatial = isXr && LocalSpatialCapabilities.current.isSpatialUiEnabled
    CompositionLocalProvider(
        LocalXrUi provides XrUi(isXr, isSpatial),
        LocalMinimumInteractiveComponentSize provides if (isXr) 56.dp else 48.dp,
    ) {
        val typography = MaterialTheme.typography
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            motionScheme = MaterialTheme.motionScheme,
            typography = if (isXr) typography.copy(
                bodySmall = typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                labelMedium = typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                labelSmall = typography.labelSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                bodySmallEmphasized = typography.bodySmallEmphasized.copy(fontSize = 14.sp, lineHeight = 20.sp),
                labelMediumEmphasized = typography.labelMediumEmphasized.copy(fontSize = 14.sp, lineHeight = 20.sp),
                labelSmallEmphasized = typography.labelSmallEmphasized.copy(fontSize = 14.sp, lineHeight = 20.sp),
            ) else typography,
        ) {
            content()
            if (isXr) {
                // Keep the spatial scene composed in Home Space. The SDK hides its panels
                // there while retaining user movement, resize and supporting-pane state.
                Subspace {
                    // The system supplies the launch pose and distance. Keep camera/environment
                    // control with the user and offer native panel movement/resize affordances.
                    SpatialCurvedRow(curveRadius = 825.dp, verticalAlignment = SpatialAlignment.Top) {
                        SpatialMainPanel(
                            SubspaceModifier.width(840.dp).height(720.dp)
                                .movable()
                                .resizable(
                                    minimumSize = DpVolumeSize(600.dp, 480.dp, 0.dp),
                                    maximumSize = DpVolumeSize(2560.dp, 1800.dp, 0.dp),
                                ),
                        )
                        SpatialSpacer(SubspaceModifier.width(24.dp))
                        SpatialPanel(
                            SubspaceModifier.width(400.dp).heightIn(max = 720.dp)
                                .movable()
                                .resizable(
                                    minimumSize = DpVolumeSize(360.dp, 240.dp, 0.dp),
                                    maximumSize = DpVolumeSize(2560.dp, 720.dp, 0.dp),
                                ),
                        ) {
                            // The content can shrink before the spatial panel's bounds update.
                            // Round its own measured edges as well as the native panel clip.
                            Surface(
                                Modifier.fillMaxWidth().heightIn(max = 720.dp),
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                supportingPane()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun XrSpaceModeButton() {
    val session = LocalSession.current ?: return
    val isSpatial = LocalSpatialCapabilities.current.isSpatialUiEnabled
    // Material XR alpha17's SpaceToggleButton calls a method removed in Compose XR beta01.
    // Use its official icons with Material's toggle and the current SceneCore transition API.
    IconToggleButton(
        checked = isSpatial,
        onCheckedChange = {
            if (isSpatial) session.scene.requestHomeSpace() else session.scene.requestFullSpace()
        },
        modifier = Modifier.size(56.dp),
    ) {
        if (isSpatial) SpaceToggleButtonDefaults.CollapseIcon()
        else SpaceToggleButtonDefaults.ExpandIcon()
    }
}

@Composable
internal fun XrExportPane(
    state: AppUiState,
    onChooseFolder: () -> Unit,
    onOpenFolder: () -> Unit,
    onShowLastResult: () -> Unit,
    onShareLastResult: () -> Unit,
    modifier: Modifier = Modifier,
    showResult: Boolean = false,
    canRetry: Boolean = false,
    onRetry: () -> Unit = {},
    onOpenResultFolder: () -> Unit = onOpenFolder,
    onCloseResult: () -> Unit = {},
) {
    // Bound measurement for scrolling, but let shorter content choose the panel's height.
    Column(modifier.fillMaxWidth().heightIn(max = 720.dp).padding(24.dp)) {
        val result = state.lastResult
        if (showResult && result != null && needsDetailedExportReport(result)) {
            ExportResultPane(result, canRetry, onRetry, onOpenResultFolder, onShareLastResult, onCloseResult,
                Modifier.weight(1f, fill = false))
        } else {
            Text(stringResource(R.string.xr_export_workspace), style = MaterialTheme.typography.headlineSmall)
            ExportSupportingPane(
                state, onChooseFolder, onOpenFolder, onShowLastResult, onShareLastResult,
                Modifier.weight(1f, fill = false).padding(top = 16.dp),
            )
        }
    }
}

/** Compose XR beta and Material's standard controls, compiled together against current APIs.
 * XR Material alpha17's automatic overrides use removed Material 1.5 JVM entry points.
 */
@Composable
internal fun XrAppBar(content: @Composable () -> Unit) {
    Orbiter(
        shape = SpatialRoundedCornerShape(CornerSize(percent = 50)),
        position = OrbiterPosition.TopCenter(
            verticalEdgeAlignment = OrbiterPosition.EdgeAlignment.Outside,
            offset = DpVolumeOffset(y = 20.dp, z = 16.dp),
        ),
    ) { content() }
}

/** Material alert dialog slots and tokens on a native spatial dialog, with the usual 2D fallback. */
@Composable
internal fun XrAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    if (!LocalXrUi.current.isSpatial) {
        AlertDialog(onDismissRequest = onDismissRequest, title = title, text = text,
            confirmButton = confirmButton, dismissButton = dismissButton)
        return
    }
    SpatialDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(24.dp)) {
                CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                }
                // SpatialDialog measures wrap-content panels; weight can assign its body zero
                // height. Result/privacy content already owns its bounded scrolling region.
                Box(Modifier.padding(top = 16.dp, bottom = 24.dp).heightIn(max = 420.dp)) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
