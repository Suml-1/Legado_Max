package io.legado.app.ui.book.storage

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.book.storage.components.CacheItemCard
import io.legado.app.ui.book.storage.components.CacheSummaryCard
import io.legado.app.ui.book.storage.components.ClearAllConfirmDialog
import io.legado.app.ui.book.storage.components.ClearConfirmDialog
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.navigationBarBottomInset

// UI层
// 4. StorageManageScreen.kt

// - 作用 ：Compose 主界面，显示缓存列表
// - 主要功能 ：
//   - TopAppBar 显示标题、刷新按钮、一键清理按钮
//   - 根据 UI状态 显示不同内容（Loading、Clearing、Error、Idle）
//   - LazyColumn 渲染缓存汇总卡片和缓存项列表
//   - 管理清理确认对话框的显示（由 ViewModel 状态驱动，§4.5）

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManageScreen(
    viewModel: StorageManageViewModel,
    onBackClick: () -> Unit,
    onOpenPath: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cacheItems by viewModel.cacheItems.collectAsStateWithLifecycle()
    val totalSize by viewModel.totalSize.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()

    val containerColor = pageCardContainerColor()

    // 返回键拦截：有 Dialog 时先关闭 Dialog，无则正常返回（§4.5）
    val hasDialog = dialog != null
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(hasDialog, backDispatcher) {
        val callback = object : OnBackPressedCallback(hasDialog) {
            override fun handleOnBackPressed() = viewModel.dismissDialog()
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    // 清理确认对话框（ViewModel 状态条件渲染，§4.5）
    when (val state = dialog) {
        is StorageDialogState.ClearConfirm -> {
            val targetName = state.detailId ?: viewModel.getCacheName(state.cacheType)
            ClearConfirmDialog(
                targetName = targetName,
                onConfirm = {
                    viewModel.clearCache(state.cacheType, state.detailId)
                    viewModel.dismissDialog()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is StorageDialogState.ClearAll -> ClearAllConfirmDialog(
            onConfirm = {
                viewModel.clearAllCache()
                viewModel.dismissDialog()
            },
            onDismiss = { viewModel.dismissDialog() }
        )
        null -> Unit
    }
    
    AppScaffold(
        topBar = {
            // 统一顶栏（theme-styles.md §14.2）
            AppPageTopBar(
                title = stringResource(R.string.storage_manage_title),
                onBackClick = onBackClick
            ) {
                IconButton(onClick = { viewModel.loadCacheInfo() }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
                IconButton(onClick = { viewModel.requestClearAll() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.storage_clear_all))
                }
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is StorageUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is StorageUiState.Clearing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.storage_clearing, state.target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is StorageUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = { viewModel.loadCacheInfo() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
                        }
                    }
                }
            }
            is StorageUiState.Idle -> {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(
                        top = AppDimens.screenPadding,
                        bottom = AppDimens.screenPadding + navigationBarBottomInset,
                        start = AppDimens.screenPadding,
                        end = AppDimens.screenPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    item {
                        CacheSummaryCard(
                            totalSize = totalSize,
                            itemCount = cacheItems.size
                        )
                    }
                    
                    items(cacheItems, key = { it.id }) { item ->
                        CacheItemCard(
                            item = item,
                            onExpandClick = { 
                                viewModel.toggleExpand(CacheType.valueOf(item.id))
                            },
                            onClearClick = {
                                viewModel.requestClear(CacheType.valueOf(item.id))
                            },
                            onDetailClearClick = { detailId ->
                                viewModel.requestClear(CacheType.valueOf(item.id), detailId)
                            },
                            onOpenPathClick = onOpenPath
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
