package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

sealed interface NgListState<out T> {
    data object Loading : NgListState<Nothing>
    data class Empty(val message: String) : NgListState<Nothing>
    data class Error(val message: String) : NgListState<Nothing>
    data class Content<T>(val items: List<T>) : NgListState<T>
}

/** 统一承载短加载、空结果和错误状态；真实列表布局仍由页面提供。 */
@Composable
fun <T> NgListStateContent(
    state: NgListState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (List<T>) -> Unit
) {
    when (state) {
        is NgListState.Content -> Box(modifier = modifier) {
            content(state.items)
        }
        NgListState.Loading -> NgListMessageHost(modifier) {
            CircularProgressIndicator(
                color = Color(NgTheme.colors.primary),
                strokeWidth = 2.dp
            )
        }

        is NgListState.Empty -> NgListMessageHost(modifier) {
            NgListMessage(state.message)
        }

        is NgListState.Error -> NgListMessageHost(modifier) {
            NgListMessage(state.message)
            if (onRetry != null) {
                NgButton(
                    onClick = onRetry,
                    variant = NgButtonVariant.OUTLINE
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun NgListMessageHost(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = { content() }
        )
    }
}

@Composable
private fun NgListMessage(message: String) {
    Text(
        text = message,
        color = Color(NgTheme.colors.onSurfaceVariant),
        fontSize = 14.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center
    )
}
