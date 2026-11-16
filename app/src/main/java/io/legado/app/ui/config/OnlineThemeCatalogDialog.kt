package io.legado.app.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.resource.RemoteThemeItem
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.theme.NgTheme

@Composable
fun OnlineThemeCatalogDialog(
    items: List<RemoteThemeItem>,
    loading: Boolean,
    error: String?,
    installingId: String?,
    onDismiss: () -> Unit,
    onInstall: (RemoteThemeItem) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.ng_theme_online_title),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .heightIn(max = 480.dp),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                    )
                }
            },
        ) {
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.ng_theme_online_loading),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                }

                error != null -> Text(
                    text = stringResource(R.string.ng_theme_online_error, error),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )

                items.isEmpty() -> Text(
                    text = stringResource(R.string.ng_theme_online_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items) { item ->
                        OnlineThemeRow(
                            item = item,
                            installing = installingId == item.id,
                            busy = installingId != null,
                            onInstall = { onInstall(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineThemeRow(
    item: RemoteThemeItem,
    installing: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.ifBlank { item.id },
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOf(item.author, item.displayVersion)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onInstall,
            enabled = !busy,
        ) {
            Text(
                text = stringResource(
                    if (installing) R.string.ng_theme_online_installing
                    else R.string.ng_theme_online_install,
                ),
                color = Color(NgTheme.colors.primary),
            )
        }
    }
}
