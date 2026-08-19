/**
 * 文件：RssSourceBrowseDetailPage.kt
 *
 * 作用：订阅源模块浏览详情页，用于管理指定订阅源下的首页模块。
 *
 * 主要功能：
 * 1. 通过两 Tab 结构（已加入 / 发现）分类展示和管理模块
 * 2. Tab 0（已加入）：展示当前订阅源已加入的模块，支持长按拖拽排序、编辑、删除、显隐切换
 * 3. Tab 1（发现）：从订阅源分类创建模块，支持选择分类类型后添加
 *
 * 参照书源 SourceBrowseDetailPage 实现，关键差异：
 * - 订阅源使用 rss_ 前缀集 ID 以避免与书源集（src_）冲突
 * - 发现分类通过 RssSource.sortUrls() 获取
 * - 模块添加通过 onAddRssCustomModule 操作
 */
package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.data.entities.rule.ExploreKind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageManageActions
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.utils.GSON
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 订阅源模块浏览详情页，两 Tab 结构
 *
 * 参照书源的 SourceBrowseDetailPage 实现，通过两个 Tab 分类管理
 * 指定订阅源下的首页模块：
 * - Tab 0：已加入当前集的模块列表
 * - Tab 1：从订阅源分类创建新模块
 *
 * @param sourceUrl 订阅源 URL
 * @param sourceName 订阅源名称，用于界面展示
 * @param targetSetId 目标集 ID（rss_ 前缀），为 null 表示默认
 * @param allModules 所有模块的 UI 数据列表
 * @param actions 首页管理操作回调集合（含 onGetRssKinds / onAddRssCustomModule）
 * @param onEditModule 编辑模块的回调
 * @param onBack 返回上一页的回调
 */
@Composable
fun RssSourceBrowseDetailPage(
    sourceUrl: String,
    sourceName: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
    onEditModule: (String, ModuleDef) -> Unit,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }

    // 计算当前 RSS 源的集 ID（rss_<sourceUrl>），用于「已加入」Tab 筛选
    val rssSetId = remember(sourceUrl) { HomepageViewModel.rssSourceSetId(sourceUrl) }
    val effectiveTargetSetId = targetSetId ?: rssSetId

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.homepage_joined)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.homepage_discover)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (selectedTab) {
            0 -> JoinedModulesTab(
                sourceUrl = sourceUrl,
                targetSetId = effectiveTargetSetId,
                allModules = allModules,
                actions = actions,
                onEditModule = onEditModule,
            )
            // 「发现」Tab 传入原始 targetSetId（null 时由 addRssCustomModule 负责创建集）
            1 -> RssDiscoverTab(
                sourceUrl = sourceUrl,
                targetSetId = targetSetId,
                sourceName = sourceName,
                actions = actions,
            )
        }
    }
}

/**
 * Tab 0: 已加入的模块（复用书源的 JoinedModulesTab 逻辑）
 */
@Composable
private fun JoinedModulesTab(
    sourceUrl: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
    onEditModule: (String, ModuleDef) -> Unit,
) {
    // 获取当前集已加入的模块
    // 统一按集归属过滤；未指定集时默认使用订阅源集（rss_ 前缀）
    val joinedModules = remember(sourceUrl, targetSetId, allModules) {
        if (targetSetId != null) {
            // 自定义集：精确匹配 customSetId
            allModules.filter { module -> module.customSetId == targetSetId }
        } else {
            // 订阅源集：customSetId 为 null 或等于 rss_ 前缀集 ID
            val setId = HomepageViewModel.rssSourceSetId(sourceUrl)
            allModules.filter { module ->
                module.sourceUrl == sourceUrl && (module.customSetId == null || module.customSetId == setId)
            }
        }
    }

    var localModules by remember(joinedModules) { mutableStateOf(joinedModules) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        localModules = localModules.toMutableList().apply {
            val fromIndex = indexOfFirst { it.id == from.key }
            val toIndex = indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val orderedIds = localModules.map { it.id }
            if (orderedIds != joinedModules.map { it.id }) {
                actions.onReorderModules(orderedIds)
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(localModules, key = { it.id }) { module ->
                ReorderableItem(reorderableState, key = module.id) { isDragging ->
                    RssModuleItem(
                        module = module,
                        isDragging = isDragging,
                        onToggle = { actions.onToggleModule(module.id, it) },
                        onEdit = {
                            onEditModule(
                                module.id,
                                ModuleDef(
                                    key = module.moduleKey,
                                    type = module.type,
                                    title = module.title,
                                    args = module.args,
                                    layoutConfig = module.layoutConfig,
                                    url = module.url,
                                    sourceUrl = module.sourceUrl
                                )
                            )
                        },
                        onDelete = { actions.onDeleteModule(module.id) },
                        dragModifier = Modifier
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                }
                            )
                    )
                }
            }
        }
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

/**
 * Tab 1: 从订阅源分类创建模块
 *
 * 对标 MD3-main 分支的视觉风格与交互模式。
 * 当模块类型为按钮组时，支持多选分类，每个分类生成一个按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssDiscoverTab(
    sourceUrl: String,
    targetSetId: String?,
    sourceName: String,
    actions: HomepageManageActions,
) {
    // 异步获取订阅源的分类列表
    // RSS 分类列表（Pair 格式），需转换为 ExploreKind 以传入选择弹窗
    val rssKinds by produceState<List<Pair<String, String>>>(emptyList(), sourceUrl) {
        value = actions.onGetRssKinds(sourceUrl)
    }
    val isLoadingKinds = rssKinds.isEmpty()
    // 转换为 ExploreKind（RSS 无样式，使用默认值）
    val rssExploreKinds = remember(rssKinds) {
        rssKinds.map { (title, url) -> ExploreKind(title = title, url = url) }
    }

    var selectedModuleType by remember { mutableStateOf(HomepageModuleType.Grid.key) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    // 已选中的分类 URL 集合（以 URL 区分同名分类）
    var selectedKindUrls by remember(sourceUrl, selectedModuleType) { mutableStateOf(setOf<String>()) }
    var showKindSheet by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var manualAddPrefill by remember { mutableStateOf<ModuleDef?>(null) }

    // 是否多选模式
    val isMultiSelectMode = selectedModuleType == HomepageModuleType.ButtonGroup.key
            || selectedModuleType == HomepageModuleType.Ranking.key
            || selectedModuleType == HomepageModuleType.GridRanking.key
    val isRankingMode = selectedModuleType == HomepageModuleType.Ranking.key
            || selectedModuleType == HomepageModuleType.GridRanking.key

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---- 模块类型选择 ----
        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded,
            onExpandedChange = { typeMenuExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            OutlinedTextField(
                value = stringResource(HomepageModuleType.fromKey(selectedModuleType).titleRes),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.homepage_module_type)) },
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false }
            ) {
                HomepageModuleType.entries.forEach { moduleType ->
                    if (moduleType == HomepageModuleType.Unknown) return@forEach
                    DropdownMenuItem(
                        text = { Text(stringResource(moduleType.titleRes)) },
                        onClick = {
                            selectedModuleType = moduleType.key
                            typeMenuExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ---- 分类选择触发区 ----
        if (isLoadingKinds) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        text = stringResource(R.string.homepage_loading_categories),
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor(),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            val selectedDisplayTitles = remember(selectedKindUrls, rssExploreKinds) {
                selectedKindUrls.mapNotNull { url -> rssExploreKinds.find { k -> (k.url ?: k.title) == url }?.title }
            }
            ExposedDropdownMenuBox(
                expanded = false,
                onExpandedChange = { if (it) showKindSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                OutlinedTextField(
                    value = if (isMultiSelectMode) {
                        when {
                            selectedDisplayTitles.isEmpty() -> ""
                            selectedDisplayTitles.size <= 3 -> selectedDisplayTitles.filter { it.isNotBlank() }.joinToString("、")
                            else -> stringResource(R.string.homepage_selected_categories_count, selectedDisplayTitles.size)
                        }
                    } else {
                        selectedDisplayTitles.firstOrNull()?.ifBlank { sourceName } ?: ""
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.homepage_select_category)) },
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
            }
            if (isMultiSelectMode) {
                Text(
                    text = stringResource(R.string.homepage_multi_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // ---- 手动添加按钮 ----
        OutlinedButton(
            onClick = {
                manualAddPrefill = ModuleDef(
                    type = selectedModuleType,
                    title = sourceName,
                    sourceUrl = sourceUrl
                )
                showManualAddDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.homepage_manual_add_module))
        }
    }

    // ---- 分类选择底部弹窗（对标 MD3-main ExploreKindSelectSheet） ----
    ExploreKindSelectSheet(
        show = showKindSheet,
        onDismissRequest = { showKindSheet = false },
        kinds = rssExploreKinds,
        multiple = isMultiSelectMode,
        initialSelectedUrls = selectedKindUrls,
        onSelected = { kinds ->
            if (isMultiSelectMode) {
                // 多选模式：更新选中 URL 集合，打开预填充的编辑对话框
                selectedKindUrls = kinds.map { it.url ?: it.title }.toSet()
                showKindSheet = false
                if (kinds.isNotEmpty()) {
                    val title = kinds.joinToString("、") { it.title.ifBlank { sourceName } }
                    val argsJson = GSON.toJson(
                        kinds.map { mapOf("t" to it.title.ifBlank { sourceName }, "u" to (it.url ?: "")) }
                    )
                    // 多选模式下也填入第一个分类的url，确保URL输入框有内容
                    val firstKindUrl = kinds.firstOrNull()?.url ?: ""
                    manualAddPrefill = ModuleDef(
                        type = selectedModuleType,
                        title = title,
                        sourceUrl = sourceUrl,
                        args = argsJson,
                        url = firstKindUrl
                    )
                    showManualAddDialog = true
                }
            } else {
                val kind = kinds.firstOrNull() ?: return@ExploreKindSelectSheet
                selectedKindUrls = setOf(kind.url ?: kind.title)
                showKindSheet = false
                manualAddPrefill = ModuleDef(
                    key = "rss_${kind.title}_${kind.url}",
                    type = selectedModuleType,
                    title = kind.title.ifBlank { sourceName },
                    url = kind.url ?: "",
                    sourceUrl = sourceUrl
                )
                showManualAddDialog = true
            }
        }
    )

    // ---- 手动添加模块对话框 ----
    if (showManualAddDialog) {
        AddCustomModuleDialog(
            show = true,
            prefill = manualAddPrefill ?: ModuleDef(type = selectedModuleType, sourceUrl = sourceUrl),
            isEditMode = false,
            onConfirm = { moduleDef ->
                actions.onAddRssCustomModule(sourceUrl, targetSetId, moduleDef)
                showManualAddDialog = false
                manualAddPrefill = null
                selectedKindUrls = emptySet()
            },
            onDismiss = {
                showManualAddDialog = false
                manualAddPrefill = null
            }
        )
    }
}

/**
 * 单个模块项的 UI 组件（订阅源版本）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssModuleItem(
    module: HomepageModuleManageUi,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
) {
    val moduleType = HomepageModuleType.fromKey(module.type)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.homepage_drag_sort),
                tint = pageSecondaryTextColor(),
                modifier = Modifier
                    .size(24.dp)
                    .then(dragModifier)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title.ifBlank {
                        module.originalTitle.ifBlank { stringResource(R.string.homepage_unnamed_module) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextCard(
                        text = stringResource(moduleType.titleRes),
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    TextCard(
                        text = if (module.sourceType == "rss") "订阅源" else "书源",
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.homepage_edit))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.homepage_delete))
            }
            Switch(checked = module.isVisible, onCheckedChange = onToggle)
        }
    }
}
