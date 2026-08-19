package io.legado.app.ui.main.homepage.manage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageManageActions
import io.legado.app.ui.main.homepage.HomepageManageUiState
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

/**
 * 首页模块管理底部弹窗。
 *
 * 该文件是首页模块管理功能的核心入口，以底部弹窗形式承载多级页面导航，
 * 包括：集列表、集详情、书源浏览、书源模块详情、自定义集添加模块等页面。
 * 同时内嵌了创建集、重命名集、删除集/模块、添加/编辑模块等对话框。
 *
 * 主要功能：
 * - 管理首页显示的"集"（书源集或自定义集）及其包含的模块
 * - 支持集的启用/禁用、重命名、删除、排序
 * - 支持模块的添加、编辑、删除、排序、可见性切换
 * - 支持从书源浏览并添加模块到自定义集
 *
 * @param show 是否显示弹窗
 * @param onDismiss 关闭弹窗的回调
 * @param state 首页管理界面的 UI 状态数据
 * @param actions 首页管理相关的用户操作回调集合
 */
@Composable
fun HomepageModuleManageSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    state: HomepageManageUiState,
    actions: HomepageManageActions,
) {
    // ==================== 导航状态 ====================
    // 当前选中的集 URL（用于显示集详情页）
    var selectingSetUrl by remember { mutableStateOf<String?>(null) }
    // 当前正在浏览的书源 URL（用于显示书源模块详情页）
    // 注意：browsingSourceUrl 仅用于书源浏览场景，订阅源浏览通过独立的
    // showRssSourceBrowser 状态驱动，直接启动外部 Activity，避免书源/订阅源
    // 因 sourceUrl 可能重复而产生导航状态污染。
    var browsingSourceUrl by remember { mutableStateOf<String?>(null) }
    // 是否显示书源浏览页
    var showSourceBrowser by remember { mutableStateOf(false) }
    // 是否显示订阅源浏览页（独立状态，不与书源 URL 状态共享）
    var showRssSourceBrowser by remember { mutableStateOf(false) }
    // 当前正在浏览的订阅源 URL（用于显示订阅源模块详情页）
    var browsingRssSourceUrl by remember { mutableStateOf<String?>(null) }
    // 当前正在为哪个自定义集添加模块（值为自定义集 ID）
    var showCustomSetAddModules by remember { mutableStateOf<String?>(null) }

    // ==================== 对话框状态 ====================
    // 是否显示创建集对话框
    var showCreateSetDialog by remember { mutableStateOf(false) }
    // 正在重命名的集 ID
    var renameSetId by remember { mutableStateOf<String?>(null) }
    // 待确认删除的集 ID
    var deleteSetConfirmId by remember { mutableStateOf<String?>(null) }
    // 待确认删除的模块 ID
    var deleteModuleConfirmId by remember { mutableStateOf<String?>(null) }
    // 添加模块对话框的预填充数据
    var addDialogPrefill by remember { mutableStateOf<ModuleDef?>(null) }
    // 正在编辑的模块（Pair<模块ID, 模块定义>）
    var editingModule by remember { mutableStateOf<Pair<String, ModuleDef>?>(null) }
    // 添加按钮组对话框的上下文（Pair<集ID, 模块标识>）
    var showAddButtonGroupDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 自定义标题编辑对话框的目标集 ID
    var customSetTitleEdit by remember { mutableStateOf<String?>(null) }

    // 根据导航状态确定当前应显示的页面（优先级从高到低）
    val currentPage: ManageScreen = when {
        showCustomSetAddModules != null -> ManageScreen.CustomSetAddModules(showCustomSetAddModules!!)
        browsingSourceUrl != null -> ManageScreen.SourceBrowseDetail(browsingSourceUrl!!, selectingSetUrl)
        browsingRssSourceUrl != null -> ManageScreen.RssSourceBrowseDetail(browsingRssSourceUrl!!)
        showSourceBrowser -> ManageScreen.BrowseSources
        showRssSourceBrowser -> ManageScreen.BrowseRssSources
        selectingSetUrl != null -> ManageScreen.SetDetail(selectingSetUrl!!)
        else -> ManageScreen.SetList
    }

    // 根据当前页面计算弹窗标题
    val title = when (currentPage) {
        is ManageScreen.SetList -> stringResource(R.string.homepage_module_manage_title)
        is ManageScreen.SetDetail -> state.sets.find { it.sourceUrl == currentPage.setUrl }?.sourceName ?: stringResource(R.string.homepage_set_detail)
        is ManageScreen.BrowseSources -> stringResource(R.string.homepage_browse_source)
        is ManageScreen.BrowseRssSources -> stringResource(R.string.homepage_browse_rss_source_modules)
        is ManageScreen.SourceBrowseDetail -> state.sourceNames[currentPage.sourceUrl] ?: stringResource(R.string.homepage_source_modules)
        is ManageScreen.RssSourceBrowseDetail -> state.sourceNames[currentPage.sourceUrl] ?: stringResource(R.string.homepage_browse_rss_source_modules)
        is ManageScreen.CustomSetAddModules -> stringResource(R.string.homepage_add_module)
    }

    // 只有非集列表页面才支持返回操作
    val canGoBack = currentPage !is ManageScreen.SetList

    // 返回上一级页面的处理逻辑
    val handleBack: () -> Unit = {
        when (currentPage) {
            is ManageScreen.SourceBrowseDetail -> browsingSourceUrl = null
            is ManageScreen.RssSourceBrowseDetail -> browsingRssSourceUrl = null
            is ManageScreen.CustomSetAddModules -> showCustomSetAddModules = null
            is ManageScreen.SetDetail -> selectingSetUrl = null
            is ManageScreen.BrowseSources -> showSourceBrowser = false
            is ManageScreen.BrowseRssSources -> showRssSourceBrowser = false
            is ManageScreen.SetList -> {}
        }
    }

    // 重置所有状态并关闭弹窗
    val handleDismiss: () -> Unit = {
        selectingSetUrl = null
        browsingSourceUrl = null
        browsingRssSourceUrl = null
        showSourceBrowser = false
        showRssSourceBrowser = false
        showCustomSetAddModules = null
        showCreateSetDialog = false
        renameSetId = null
        deleteSetConfirmId = null
        deleteModuleConfirmId = null
        addDialogPrefill = null
        editingModule = null
        showAddButtonGroupDialog = null
        customSetTitleEdit = null
        onDismiss()
    }

    // 拦截系统返回键，仅在可返回时生效
    BackHandler(enabled = show && canGoBack) {
        handleBack()
    }

    // 计算当前选中集包含的模块列表
    val currentSetModules = remember(selectingSetUrl, state.allJoinedModules) {
        selectingSetUrl?.let { url ->
            state.allJoinedModules.filter { belongsToSet(it, url) }
        } ?: emptyList()
    }

    // 检查当前集是否已存在无限流模块（用于限制只能添加一个）
    val hasInfiniteModule = remember(currentSetModules) {
        currentSetModules.any { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }

    // 判断当前集是否为自定义集
    val isCurrentSetCustom = selectingSetUrl?.let { HomepageViewModel.isCustomSetUrl(it) } ?: false
    // 若为自定义集，提取其 ID
    val currentSetId = selectingSetUrl?.takeIf { HomepageViewModel.isCustomSetUrl(it) }
        ?.let { HomepageViewModel.customSetIdFromUrl(it) }

    // AnimatedContent 内的 LazyColumn 需要有限高度约束才能正常渲染。
    // 使用屏幕高度的 80% 作为容器高度（与 ExploreKindSelectSheet 策略一致）。
    val contentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f

    AppModalBottomSheet(
        show = show,
        onDismissRequest = handleDismiss,
        title = title,
        skipPartiallyExpanded = true,
        innerScrollEnabled = false,
        startAction = if (canGoBack) {
            {
                IconButton(onClick = { handleBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.homepage_back)
                    )
                }
            }
        } else null
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                // 根据页面深度确定滑动方向：进入时向左滑入，返回时向右滑入
                val direction = if (targetState.depth > initialState.depth) 1 else -1
                slideInHorizontally { fullWidth -> fullWidth * direction } togetherWith
                        slideOutHorizontally { fullWidth -> -fullWidth * direction }
            },
            modifier = Modifier.fillMaxWidth().height(contentHeight)
        ) { page ->
            when (page) {
                // 集列表页：展示所有集，支持创建自定义集和浏览书源
                is ManageScreen.SetList -> SetListPage(
                    sets = state.sets,
                    onToggleSet = actions.onToggleSet,
                    onSetClick = { setUrl -> selectingSetUrl = setUrl },
                    onRenameSet = { setUrl ->
                        // 自定义集按 customSetId 重命名，书源集/订阅源集按 setUrl 重命名
                        renameSetId = if (HomepageViewModel.isCustomSetUrl(setUrl)) {
                            HomepageViewModel.customSetIdFromUrl(setUrl)
                        } else {
                            setUrl
                        }
                    },
                    onDeleteSet = { setUrl ->
                        // 自定义集和书源集均可删除
                        deleteSetConfirmId = if (HomepageViewModel.isCustomSetUrl(setUrl)) {
                            HomepageViewModel.customSetIdFromUrl(setUrl)
                        } else {
                            // 书源集：setUrl 即为集 ID（如 src_xxx）
                            setUrl
                        }
                    },
                    onReorderSets = actions.onReorderSets,
                    onCreateCustomSet = { showCreateSetDialog = true },
                    onBrowseSources = { showSourceBrowser = true },
                    onBrowseRssSources = { showRssSourceBrowser = true },
                )

                // 集详情页：展示集内模块，支持模块的增删改查与排序
                is ManageScreen.SetDetail -> SetDetailPage(
                    setTitle = state.sets.find { it.sourceUrl == page.setUrl }?.sourceName
                        ?: stringResource(R.string.homepage_set_detail),
                    modules = currentSetModules,
                    isCustomSet = HomepageViewModel.isCustomSetUrl(page.setUrl),
                    onToggleModule = actions.onToggleModule,
                    onEditModule = { moduleId, moduleDef ->
                        editingModule = Pair(moduleId, moduleDef)
                    },
                    onDeleteModule = { moduleId -> deleteModuleConfirmId = moduleId },
                    onReorderModules = actions.onReorderModules,
                    onAddModules = {
                        if (isCurrentSetCustom && currentSetId != null) {
                            // 自定义集：跳转到从其他集添加模块页面
                            showCustomSetAddModules = currentSetId
                        } else if (HomepageViewModel.isRssSourceSetId(page.setUrl)) {
                            // 订阅源集：跳转到订阅源模块详情页
                            val sourceUrl = HomepageViewModel.sourceUrlFromSetId(page.setUrl)
                            browsingRssSourceUrl = sourceUrl
                        } else {
                            // 书源集：跳转到书源模块详情页，选择分类添加模块
                            val sourceUrl = HomepageViewModel.sourceUrlFromSetId(page.setUrl)
                            browsingSourceUrl = sourceUrl
                        }
                    },
                    onBack = { handleBack() },
                )

                // 书源浏览页：展示所有可用书源，点击进入书源模块详情
                is ManageScreen.BrowseSources -> BrowseSourcesPage(
                    sources = state.browseSources,
                    onSourceClick = { sourceUrl -> browsingSourceUrl = sourceUrl },
                    onBack = { handleBack() },
                )

                // 订阅源浏览页：展示所有订阅源，点击进入订阅源模块详情
                is ManageScreen.BrowseRssSources -> BrowseRssSourcesPage(
                    onSourceClick = { sourceUrl -> browsingRssSourceUrl = sourceUrl },
                    onBack = { handleBack() },
                )

                // 订阅源模块详情页：展示某订阅源下已加入的模块与可发现的分类
                is ManageScreen.RssSourceBrowseDetail -> RssSourceBrowseDetailPage(
                    sourceUrl = page.sourceUrl,
                    sourceName = state.sourceNames[page.sourceUrl] ?: page.sourceUrl,
                    targetSetId = null,
                    allModules = state.allJoinedModules,
                    actions = actions,
                    onEditModule = { moduleId, moduleDef ->
                        editingModule = Pair(moduleId, moduleDef)
                    },
                    onBack = { handleBack() },
                )

                // 书源模块详情页：展示某书源的所有模块，可添加到指定集
                is ManageScreen.SourceBrowseDetail -> SourceBrowseDetailPage(
                    sourceUrl = page.sourceUrl,
                    sourceName = state.sourceNames[page.sourceUrl] ?: page.sourceUrl,
                    targetSetId = page.setUrl?.let {
                        // 自定义集提取 ID，书源集直接使用集 URL 作为 ID
                        if (HomepageViewModel.isCustomSetUrl(it)) HomepageViewModel.customSetIdFromUrl(it) else it
                    },
                    allModules = state.allJoinedModules,
                    actions = actions,
                    onEditModule = { moduleId, moduleDef ->
                        editingModule = Pair(moduleId, moduleDef)
                    },
                    onBack = { handleBack() },
                )

                // 自定义集添加模块页：从其他集选择模块添加到当前自定义集
                is ManageScreen.CustomSetAddModules -> CustomSetAddModulesPage(
                    targetSetId = page.targetSetId,
                    allModules = state.allJoinedModules,
                    onAssignModule = actions.onAssignModuleToCustomSet,
                    onBack = { handleBack() },
                )
            }
        }

        // ==================== 内嵌对话框 ====================

        // 创建自定义集对话框
        if (showCreateSetDialog) {
            var newSetName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {
                    showCreateSetDialog = false
                    newSetName = ""
                },
                title = { Text(stringResource(R.string.homepage_new_custom_set)) },
                text = {
                    OutlinedTextField(
                        value = newSetName,
                        onValueChange = { newSetName = it },
                        label = { Text(stringResource(R.string.homepage_set_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newSetName.isNotBlank()) {
                                actions.onCreateCustomSet(newSetName)
                            }
                            showCreateSetDialog = false
                            newSetName = ""
                        }
                    ) {
                        Text(stringResource(R.string.homepage_create))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateSetDialog = false
                            newSetName = ""
                        }
                    ) {
                        Text(stringResource(R.string.homepage_cancel))
                    }
                }
            )
        }

        // 重命名集对话框
        if (renameSetId != null) {
            var newName by remember(renameSetId) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {
                    renameSetId = null
                },
                title = { Text(stringResource(R.string.homepage_rename_set)) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.homepage_new_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                actions.onRenameCustomSet(renameSetId!!, newName)
                            }
                            renameSetId = null
                        }
                    ) {
                        Text(stringResource(R.string.homepage_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameSetId = null }) {
                        Text(stringResource(R.string.homepage_cancel))
                    }
                }
            )
        }

        // 删除集确认对话框
        if (deleteSetConfirmId != null) {
            AlertDialog(
                onDismissRequest = { deleteSetConfirmId = null },
                title = { Text(stringResource(R.string.homepage_delete_set)) },
                text = { Text(stringResource(R.string.homepage_delete_set_msg)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actions.onDeleteCustomSet(deleteSetConfirmId!!)
                            deleteSetConfirmId = null
                        }
                    ) {
                        Text(stringResource(R.string.homepage_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteSetConfirmId = null }) {
                        Text(stringResource(R.string.homepage_cancel))
                    }
                }
            )
        }

        // 删除模块确认对话框
        if (deleteModuleConfirmId != null) {
            AlertDialog(
                onDismissRequest = { deleteModuleConfirmId = null },
                title = { Text(stringResource(R.string.homepage_delete_module)) },
                text = { Text(stringResource(R.string.homepage_delete_module_msg)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actions.onDeleteModule(deleteModuleConfirmId!!)
                            deleteModuleConfirmId = null
                        }
                    ) {
                        Text(stringResource(R.string.homepage_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteModuleConfirmId = null }) {
                        Text(stringResource(R.string.homepage_cancel))
                    }
                }
            )
        }

        // 添加模块对话框（新建模式）
        AddCustomModuleDialog(
            show = addDialogPrefill != null,
            prefill = addDialogPrefill,
            isEditMode = false,
            canSelectInfinite = !hasInfiniteModule,
            onConfirm = { moduleDef ->
                val sourceUrl = selectingSetUrl ?: ""
                val setId = currentSetId
                actions.onAddCustomModule(sourceUrl, setId, moduleDef)
                addDialogPrefill = null
            },
            onDismiss = { addDialogPrefill = null }
        )

        // 编辑模块对话框（编辑模式）
        AddCustomModuleDialog(
            show = editingModule != null,
            prefill = editingModule?.second,
            isEditMode = true,
            canSelectInfinite = !hasInfiniteModule,
            onConfirm = { moduleDef ->
                editingModule?.let { (moduleId, _) ->
                    actions.onUpdateModule(moduleId, moduleDef)
                }
                editingModule = null
            },
            onDismiss = { editingModule = null }
        )

        // 自定义标题编辑对话框
        if (customSetTitleEdit != null) {
            var customTitle by remember(customSetTitleEdit) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {
                    customSetTitleEdit = null
                },
                title = { Text(stringResource(R.string.homepage_custom_title)) },
                text = {
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text(stringResource(R.string.homepage_custom_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actions.onSetCustomSetTitle(
                                customSetTitleEdit!!,
                                customTitle.ifBlank { null }
                            )
                            customSetTitleEdit = null
                        }
                    ) {
                        Text(stringResource(R.string.homepage_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { customSetTitleEdit = null }) {
                        Text(stringResource(R.string.homepage_cancel))
                    }
                }
            )
        }
    }
}

/**
 * 管理页面导航层级定义。
 *
 * 用于描述底部弹窗内部的多级页面导航结构，
 * 通过 [depth] 属性控制页面切换时的滑动动画方向。
 */
private sealed interface ManageScreen {
    /** 页面深度，用于动画方向判断 */
    val depth: Int

    /** 集列表页（根页面，深度 0） */
    data object SetList : ManageScreen {
        override val depth: Int = 0
    }

    /** 集详情页（深度 1） */
    data class SetDetail(val setUrl: String) : ManageScreen {
        override val depth: Int = 1
    }

    /** 书源浏览页（深度 1） */
    data object BrowseSources : ManageScreen {
        override val depth: Int = 1
    }

    /** 订阅源浏览页（深度 1） */
    data object BrowseRssSources : ManageScreen {
        override val depth: Int = 1
    }

    /** 订阅源模块详情页（深度 2） */
    data class RssSourceBrowseDetail(val sourceUrl: String) : ManageScreen {
        override val depth: Int = 2
    }

    /** 书源模块详情页（深度 2） */
    data class SourceBrowseDetail(val sourceUrl: String, val setUrl: String?) : ManageScreen {
        override val depth: Int = 2
    }

    /** 自定义集添加模块页（深度 2） */
    data class CustomSetAddModules(val targetSetId: String) : ManageScreen {
        override val depth: Int = 2
    }
}

/**
 * 判断模块是否属于指定的集。
 *
 * 对于自定义集，通过 [HomepageViewModel.customSetIdFromUrl] 提取集 ID 后与模块的 customSetId 比较；
 * 对于书源集，setUrl 即为集 ID（格式 src_<书源URL>），直接与模块的 customSetId 比较。
 *
 * @param module 待判断的模块 UI 数据
 * @param setUrl 目标集的 URL
 * @return true 表示模块属于该集，false 表示不属于
 */
private fun belongsToSet(module: HomepageModuleManageUi, setUrl: String): Boolean {
    return if (HomepageViewModel.isCustomSetUrl(setUrl)) {
        // 自定义集：通过集 ID 匹配
        val setId = HomepageViewModel.customSetIdFromUrl(setUrl)
        module.customSetId == setId
    } else if (HomepageViewModel.isBookSourceSetId(setUrl)) {
        // 书源集：customSetId 为 null（在书源集中）或等于 setUrl
        module.customSetId == null || module.customSetId == setUrl
    } else if (HomepageViewModel.isRssSourceSetId(setUrl)) {
        // 订阅源集：customSetId 等于 setUrl
        module.customSetId == setUrl
    } else {
        false
    }
}
