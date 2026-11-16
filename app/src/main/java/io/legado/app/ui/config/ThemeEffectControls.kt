package io.legado.app.ui.config

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgPreparedThemeImport
import io.legado.app.help.config.NgThemeImportAction
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme

private fun NgManagedTheme.packageVersionLabel(): String = publication?.versionName
    ?.takeIf(String::isNotBlank) ?: publication?.versionCode?.toString() ?: "—"

@Composable
internal fun ThemePackageImportDialog(
    candidate: NgPreparedThemeImport,
    installing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var keepCustomizations by remember(candidate) {
        mutableStateOf(candidate.incompatibleParameters.isEmpty())
    }
    val action = stringResource(when (candidate.action) {
        NgThemeImportAction.INSTALL -> R.string.ng_theme_import_package
        NgThemeImportAction.UPDATE -> R.string.effect_theme_update
        NgThemeImportAction.REPLACE -> R.string.effect_theme_replace
        NgThemeImportAction.DOWNGRADE -> R.string.effect_theme_downgrade
        NgThemeImportAction.NO_OP -> R.string.effect_theme_installed
    })
    ThemePackageImportWindow(
        onDismissRequest = { if (!installing) onDismiss() },
    ) { dialogModifier ->
        NgDialog(
            title = action,
            modifier = dialogModifier,
            titleFontSize = 18.sp,
            titleFontWeight = FontWeight.Normal,
            actions = {
                EffectThemeButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = NgButtonVariant.TONAL,
                    enabled = !installing,
                    buttonHeight = 42.dp,
                )
                EffectThemeButton(
                    text = action,
                    onClick = { onConfirm(keepCustomizations) },
                    modifier = Modifier.weight(1f),
                    enabled = !installing,
                    buttonHeight = 42.dp,
                )
            },
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val theme = candidate.theme
                val author = theme.publication?.author?.takeIf(String::isNotBlank)
                val size = Formatter.formatFileSize(LocalContext.current, candidate.sizeBytes)
                AppThemePackageImportSummary(theme, author, size)
                if (candidate.action == NgThemeImportAction.UPDATE ||
                    candidate.action == NgThemeImportAction.DOWNGRADE
                ) candidate.previous?.let { previous ->
                    Text("${previous.packageVersionLabel()} → ${theme.packageVersionLabel()}", color = Color(NgTheme.colors.onSurface))
                }
                if (candidate.hasCustomizations) {
                    NgFormSwitchRow(title = stringResource(R.string.effect_theme_keep_customizations),
                        checked = keepCustomizations, enabled = !installing && candidate.incompatibleParameters.isEmpty(),
                        onCheckedChange = { keepCustomizations = it })
                    if (candidate.incompatibleParameters.isNotEmpty()) {
                        val names = candidate.incompatibleParameters.joinToString("、") { key ->
                            candidate.previous?.effect?.parameterSchema?.firstOrNull { it.key == key }?.label ?: key
                        }
                        Text(stringResource(R.string.effect_theme_reset_incompatible, names),
                            color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp)
                    }
                }
                if (candidate.previous?.publication?.source == "resource") {
                    Text(
                        stringResource(R.string.ng_theme_import_replace_resource),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                    )
                }
                if (candidate.previous != null) {
                    Text(
                        stringResource(R.string.effect_theme_update_current),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePackageImportWindow(
    onDismissRequest: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val sideMargin = dimensionResource(R.dimen.ng_dialog_margin)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val dialogWidth = (maxWidth - sideMargin * 2).coerceIn(1.dp, 520.dp)
            val dismissArea = Modifier.fillMaxHeight().pointerInput(onDismissRequest) {
                detectTapGestures { onDismissRequest() }
            }
            // The wide window includes the side margins, so handle taps there explicitly.
            Row(Modifier.matchParentSize()) {
                Spacer(dismissArea.weight(1f))
                Spacer(Modifier.width(dialogWidth))
                Spacer(dismissArea.weight(1f))
            }
            content(Modifier.width(dialogWidth))
        }
    }
}

@Composable
private fun AppThemePackageImportSummary(theme: NgManagedTheme, author: String?, size: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val primary = Color(NgTheme.colors.primary)
            Box(
                modifier = Modifier.size(44.dp).background(
                    primary.copy(alpha = 0.12f),
                    RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (theme.effect == null) Icons.Rounded.Palette else Icons.Rounded.AutoAwesome,
                    null, Modifier.size(26.dp), tint = primary,
                )
            }
            Text(
                text = theme.name,
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
            )
        }
        val fields = listOf(
            stringResource(R.string.ng_theme_import_type) to stringResource(
                if (theme.effect == null) R.string.ng_theme_import_normal_type else R.string.ng_theme_import_effect_type,
            ),
            stringResource(R.string.version) to theme.packageVersionLabel(),
            stringResource(R.string.author) to (author ?: "—"),
            stringResource(R.string.ng_theme_import_size) to size,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 230.dp * LocalDensity.current.fontScale) 2 else 1
            Column {
                fields.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.weight(1f).heightIn(min = 22.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.widthIn(min = 32.dp),
                                    color = Color(NgTheme.colors.onSurfaceVariant),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                                Text(
                                    text = value,
                                    modifier = Modifier.weight(1f),
                                    color = Color(NgTheme.colors.onSurface),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EffectThemeDeleteDialog(themeName: String, inUse: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val title = stringResource(R.string.delete)
        NgDialog(title = title, actions = {
            EffectThemeButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f), variant = NgButtonVariant.OUTLINE)
            EffectThemeButton(title, onConfirm, Modifier.weight(1f))
        }) {
            Text(
                stringResource(
                    R.string.ng_theme_delete_message,
                    themeName,
                ),
                color = Color(NgTheme.colors.onSurface),
            )
            if (inUse) Text(stringResource(R.string.effect_theme_uninstall_current),
                Modifier.padding(top = 8.dp), color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EffectThemeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NgButtonVariant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
    enabled: Boolean = true,
    buttonHeight: Dp = 44.dp,
) {
    NgFormActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        variant = variant,
        appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
        buttonHeight = buttonHeight,
        minimumWidth = 76.dp,
        textSize = 14.sp,
    )
}
