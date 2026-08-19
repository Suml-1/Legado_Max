/**
 * 文件：SourceBrowseDetailPage.kt
 *
 * 作用：书源模块浏览详情页，用于管理指定书源下的首页模块。
 *
 * 主要功能：
 * 1. 通过两 Tab 结构（已加入 / 发现）分类展示和管理模块
 * 2. Tab 0（已加入）：展示当前集已加入的模块，支持长按拖拽排序、编辑、删除、显隐切换
 * 3. Tab 1（发现）：从书源发现分类创建模块，支持单选添加、多选创建按钮组、手动添加自定义模块
 *
 * 该页面是首页模块管理功能的核心交互界面之一。
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
 * 书源模块浏览详情页，两 Tab 结构
 *
 * 该页面通过两个 Tab 分类管理指定书源下的首页模块：
 * - Tab 0：已加入当前集的模块列表
 * - Tab 1：从书源发现分类创建新模块
 *
 * @param sourceUrl 书源 URL，用于定位具体书源
 * @param sourceName 书源名称，用于界面展示
 * @param targetSetId 目标集 ID，为 null 表示默认集
 * @param allModules 所有模块的 UI 数据列表
 * @param actions 首页管理操作回调集合
 * @param onEditModule 编辑模块的回调
 * @param onBack 返回上一页的回调
 */
@Composable
fun SourceBrowseDetailPage(
    sourceUrl: String,
    sourceName: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
    onEditModule: (String, ModuleDef) -> Unit,
    onBack: () -> Unit,
) {
    // 当前选中的 Tab 索引，默认显示"已加入"Tab
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 顶部 Tab 栏，提供两个分类入口
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
        // 根据选中的 Tab 显示对应内容
        when (selectedTab) {
            0 -> JoinedModulesTab(
                sourceUrl = sourceUrl,
                targetSetId = targetSetId,
                allModules = allModules,
                actions = actions,
                onEditModule = onEditModule,
            )
            1 -> DiscoverTab(
                sourceUrl = sourceUrl,
                targetSetId = targetSetId,
                actions = actions,
            )
        }
    }
}

/**
 * Tab 0: 已加入的模块
 *
 * 展示当前集已加入的模块列表，每个模块支持以下操作：
 * - 长按拖拽排序：调整模块显示顺序
 * - 编辑：打开编辑对话框修改模块配置
 * - 删除：从当前集移除模块
 * - 显隐切换：控制模块在首页是否可见
 *
 * @param sourceUrl 书源 URL
 * @param targetSetId 目标集 ID，为 null 表示默认集
 * @param allModules 所有模块的 UI 数据列表
 * @param actions 首页管理操作回调集合
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
    val joinedModules = remember(sourceUrl, targetSetId, allModules) {
        if (targetSetId != null) {
            // 自定义集：精确匹配 customSetId
            allModules.filter { module -> module.customSetId == targetSetId }
        } else {
            // 书源集：匹配 sourceUrl 且 customSetId 为 null 或等于书源集 ID
            val setId = HomepageViewModel.bookSourceSetId(sourceUrl)
            allModules.filter { module -> module.sourceUrl == sourceUrl && (module.customSetId == null || module.customSetId == setId) }
        }
    }

    // 本地排序列表，拖拽时即时更新
    var localModules by remember(joinedModules) { mutableStateOf(joinedModules) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    // 拖拽排序状态
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        localModules = localModules.toMutableList().apply {
            val fromIndex = indexOfFirst { it.id == from.key }
            val toIndex = indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    // 拖拽结束后持久化排序
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
                    ModuleItem(
                        module = module,
                        isDragging = isDragging,
                        onToggle = { actions.onToggleModule(module.id, it) },
                        onEdit = {
                            // 构造模块定义对象，传递给编辑回调以打开编辑对话框
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
 * Tab 1: 从书源发现分类创建模块
 *
 * 对标 MD3-main 分支的视觉风格与交互模式：
 * - 模块类型选择使用 GlassCard 包裹的 CompactDropdownSettingItem 风格
 * - 分类选择通过 ExploreKindSelectSheet 底部弹窗完成
 * - 支持单选（直接打开添加对话框）和多选（按钮组/排行榜模式）
 * - 选中状态通过背景色动效反馈（primaryContainer），无 Checkbox
 *
 * @param sourceUrl 书源 URL
 * @param targetSetId 目标集 ID，为 null 表示默认集
 * @param actions 首页管理操作回调集合
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverTab(
    sourceUrl: String,
    targetSetId: String?,
    actions: HomepageManageActions,
) {
    // 异步获取书源的发现分类列表（含样式信息，用于 flexBasisPercent 整行支持）
    val exploreKinds by produceState<List<ExploreKind>>(emptyList(), sourceUrl) {
        value = actions.onGetExploreKinds(sourceUrl)
    }
    val isLoadingKinds = exploreKinds.isEmpty()

    // 选中的模块类型
    var selectedModuleType by remember { mutableStateOf(HomepageModuleType.Grid.key) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    // 已选中的分类 URL 集合（以 URL 区分同名分类）
    var selectedKindUrls by remember(sourceUrl, selectedModuleType) { mutableStateOf(setOf<String>()) }
    // 分类选择弹窗
    var showKindSheet by remember { mutableStateOf(false) }
    // 手动添加对话框
    var showManualAddDialog by remember { mutableStateOf(false) }
    var manualAddPrefill by remember { mutableStateOf<ModuleDef?>(null) }

    // 是否多选模式（按钮组 / 排行榜）
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

        // ---- 分类选择触发区（对标 MD3-main SelectionItemCard 风格） ----
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
            // 点击触发 ExploreKindSelectSheet
            // 从 URL 还原显示标题
            val selectedDisplayTitles = remember(selectedKindUrls, exploreKinds) {
                selectedKindUrls.mapNotNull { url -> exploreKinds.find { k -> (k.url ?: k.title) == url }?.title }
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
                            selectedDisplayTitles.size <= 3 -> selectedDisplayTitles.joinToString("、")
                            else -> stringResource(R.string.homepage_selected_categories_count, selectedDisplayTitles.size)
                        }
                    } else {
                        selectedDisplayTitles.firstOrNull() ?: ""
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
            // 多选模式提示
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
        kinds = exploreKinds,
        multiple = isMultiSelectMode,
        initialSelectedUrls = selectedKindUrls,
        onSelected = { kinds ->
            if (isMultiSelectMode) {
                // 多选模式：更新选中 URL 集合，打开预填充的编辑对话框
                selectedKindUrls = kinds.map { it.url ?: it.title }.toSet()
                showKindSheet = false
                if (kinds.isNotEmpty()) {
                    val title = kinds.joinToString("、") { it.title }
                    val argsJson = GSON.toJson(
                        kinds.map { mapOf("t" to it.title, "u" to (it.url ?: "")) }
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
                // 单选模式：打开添加模块对话框预填充
                val kind = kinds.firstOrNull() ?: return@ExploreKindSelectSheet
                selectedKindUrls = setOf(kind.url ?: kind.title)
                showKindSheet = false
                manualAddPrefill = ModuleDef(
                    key = "explore_${kind.title}_${kind.url}",
                    type = selectedModuleType,
                    title = kind.title,
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
                actions.onAddCustomModule(sourceUrl, targetSetId, moduleDef)
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
 * 单个模块项的 UI 组件。
 *
 * 以卡片形式展示模块的标题和类型，并提供拖拽排序、编辑、删除和可见性切换等操作按钮。
 *
 * @param module 模块的 UI 数据
 * @param isDragging 当前项是否正在被拖拽
 * @param onToggle 切换可见性的回调
 * @param onEdit 编辑模块的回调
 * @param onDelete 删除模块的回调
 * @param dragModifier 拖拽手柄的 Modifier，用于长按拖拽排序
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleItem(
    module: HomepageModuleManageUi,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
) {
    // 根据模块类型 key 获取对应的枚举值
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
            // 拖拽手柄图标（仅此区域可触发长按拖拽）
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.homepage_drag_sort),
                tint = pageSecondaryTextColor(),
                modifier = Modifier
                    .size(24.dp)
                    .then(dragModifier)
            )
            Spacer(modifier = Modifier.size(8.dp))
            // 模块标题和类型标签
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 优先显示自定义标题，其次原始标题，最后显示默认名称
                    text = module.title.ifBlank { module.originalTitle.ifBlank { stringResource(R.string.homepage_unnamed_module) } },
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
            // 编辑按钮：打开编辑对话框，传入当前模块的完整配置
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.homepage_edit))
            }
            // 删除按钮：从当前集移除该模块
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.homepage_delete))
            }
            // 显隐开关：控制模块在首页是否可见
            Switch(
                checked = module.isVisible,
                onCheckedChange = onToggle
            )
        }
    }
}
