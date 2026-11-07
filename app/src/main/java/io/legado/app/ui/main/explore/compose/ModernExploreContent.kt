package io.legado.app.ui.main.explore.compose

import io.legado.app.lib.theme.eInkGrayscale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.FlexChildStyle
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.book.explore.compose.EXPLORE_LAYOUT_GRID
import io.legado.app.ui.book.explore.compose.EXPLORE_LAYOUT_LIST
import io.legado.app.ui.book.explore.compose.EXPLORE_LAYOUT_WATERFALL
import io.legado.app.ui.book.explore.compose.ExploreShowActions
import io.legado.app.ui.book.explore.compose.ExploreShowController
import io.legado.app.ui.book.explore.compose.ExploreShowListContent
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.ui.widget.components.ModernTagBar
import io.legado.app.ui.widget.components.dialog.AppSearchableChoiceDialog
import io.legado.app.ui.widget.components.dialog.AppTagGridDialog
import io.legado.app.ui.widget.components.BlockProgressChip
import io.legado.app.ui.widget.components.BookBottomSheet
import io.legado.app.ui.widget.components.FlexWrapItemSpec
import io.legado.app.ui.widget.components.FlexWrapLayout

/** 标签条里的一个分类项（参考分支 DiscoverTagItem 的精简版） */
private data class ModernTagItem(
    val text: String,
    val url: String?,
    val group: String?,
    val kind: ExploreKind,
)

/**
 * 该分类项能不能直接进标签条加载。
 *
 * select/button/text/toggle 的 url 是模板/脚本（多数书源干脆留空），只进「发现页管理」表单，
 * 所以分组条是否显示某个分组，也要按这个口径判断——只有它下面存在这种可加载项才值得列出来。
 */
private fun ModernTagItem.isLoadableTag(): Boolean = url != null && kind.type == ExploreKind.Type.url

/** 分类项解析结果：标签项 + 大分组表头（表头的 style 供分组展开弹窗按书源声明排布宽度） */
private class ModernTagModel(
    val items: List<ModernTagItem>,
    /** 大分组表头项：`text` / `group` 为分组名、`kind` 为原表头项；隐式「其它」分组没有表头 */
    val groupHeaders: List<ModernTagItem>,
)

/** 标签/分组展开弹窗的数量阈值（对齐参考分支 ExpandableTagSelector.EXPAND_THRESHOLD） */
private const val TAG_EXPAND_THRESHOLD = 12

/**
 * 新版发现：在发现主 Tab 内直接显示"源切换 + 分类标签 + 内容列表"，
 * 免去"书源列表 → 分类列表"的两级跳转（对齐 Legado_R 新版发现的结构）。
 *
 * 结构对齐参考分支：
 * - 头部行在 TitleBar 正下方左上角：源名（20sp 粗体）+ ▾ 下拉切换源；
 *   右侧是"发现页管理"（齿轮）与功能菜单（三点）两个圆钮；
 * - 分类按整行项（`flexBasisPercent>=0.95`，或不带 url 的 `flexGrow>=1`）拆成大分组：
 *   分组条（可切换分组）+ 当前分组的 url 类标签条（每分组自动带「全部」项），
 *   标签条末尾 ▾ 展开全部标签；
 *   第一个分组标题之前的分类（如番茄小说的「猜你喜欢…热搜榜单」）归「其它」分组，不丢进表单
 *   （该分组下没有可加载分类时不会出现在分组条上）；
 *   两个展开弹窗与分类区共用 FlexWrapLayout，同样吃书源 style 的 flex 声明；
 * - select/text/button 类不进标签条，收进「发现页管理」表单弹窗：
 *   select 值写入书源 infoMap 并触发分类重建，与旧版展开分类区同一套机制。
 *
 * @param controller 内容区状态（与独立发现列表页同一套控制器）
 * @param kindsController 分类控制器（JS 求值 / infoMap / 内联 WebView，宿主 Fragment 持有）
 * @param onSwitchLegacy 切换回旧版发现
 */
@Composable
fun ModernExploreContent(
    controller: ExploreShowController,
    actions: ExploreShowActions,
    kindsController: ExploreKindsController,
    sources: List<BookSourcePart>,
    selectedSourceUrl: String?,
    onSelectSource: (BookSourcePart) -> Unit,
    onSwitchLegacy: () -> Unit,
    showBlockProgress: Boolean,
    modifier: Modifier = Modifier,
    /** 主界面底栏占用的高度（px），补进列表底部内边距，避免滑到底的内容被底栏遮挡 */
    bottomPaddingPx: Int = 0,
    /** 三点菜单「刷新」：清掉分类缓存后重建分类区并重拉当前分类（宿主负责顺序） */
    onRefreshSource: () -> Unit = {},
    /** 三点菜单「登录」：仅当前书源声明了登录地址时显示 */
    onLogin: () -> Unit = {},
    /** 三点菜单「编辑书源」：与旧版长按书源菜单的「编辑」同一动作 */
    onEditSource: () -> Unit = {},
    /** 三点菜单「搜索书籍」：与旧版长按书源菜单的「搜索」同一动作 */
    onSearchSource: () -> Unit = {},
) {
    var showSourcePicker by remember { mutableStateOf(false) }
    var showBookSheet by remember { mutableStateOf(false) }
    var sheetBook by remember { mutableStateOf<SearchBook?>(null) }
    var sheetShelfState by remember { mutableStateOf(BookShelfState.NOT_IN_SHELF) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }

    // 分类区状态：随选中源重建；书源规则刷新信号（长按刷新/登录/筛选回调）变化时重建
    val kindsState = remember(selectedSourceUrl) { ExploreKindsState() }
    val refreshTick = selectedSourceUrl?.let { kindsController.refreshTick(it) } ?: 0
    var handledRefreshTick by remember(selectedSourceUrl) { mutableStateOf(0) }
    // 当前选中的标签 URL（筛选重建后据此恢复）
    var selectedTagUrl by remember(selectedSourceUrl) { mutableStateOf<String?>(null) }
    // 当前大分组（随选中源重置）
    var currentGroup by remember(selectedSourceUrl) { mutableStateOf<String?>(null) }

    val allLabel = stringResource(R.string.all)
    val otherLabel = stringResource(R.string.other)

    LaunchedEffect(selectedSourceUrl, refreshTick) {
        val sourceUrl = selectedSourceUrl ?: return@LaunchedEffect
        val force = refreshTick != handledRefreshTick
        handledRefreshTick = refreshTick
        kindsState.load(kindsController, sourceUrl, refreshTick)
        if (force) {
            // 筛选变化后分类已被书源重建：恢复原选中分类，失效则落到第一个 url 类
            // （只认 type==url：select/button/text/toggle 的 url 是模板/脚本，不能当分类加载）
            val items = buildModernTagItems(kindsState.kinds, allLabel, otherLabel).items
            val target = items.firstOrNull { it.isLoadableTag() && it.url == selectedTagUrl }
                ?: items.firstOrNull { it.isLoadableTag() }
            target?.let {
                selectedTagUrl = it.url
                controller.loadExploreUrl(it.url.orEmpty(), it.text)
            }
        }
    }

    // 首次进入时控制器已自动选中第一个 url 类分类：
    // 把控制器当前分类同步到标签条选中态，避免"加载了分类但标签无高亮"
    LaunchedEffect(controller.currentExploreUrl) {
        val url = controller.currentExploreUrl ?: return@LaunchedEffect
        if (selectedTagUrl == null) {
            selectedTagUrl = url
        }
    }

    // 标签条只放可直接加载的 url 类分类（见 isLoadableTag）：
    // 对齐参考分支 tagItems = filter { type != select && !isButton }
    val tagModel = remember(kindsState.kinds, allLabel, otherLabel) {
        buildModernTagItems(kindsState.kinds, allLabel, otherLabel)
    }
    val allItems = tagModel.items
    // 分组条同样只列有可加载分类的分组：select/text/button/toggle 只进「发现页管理」表单，
    // 它们在第一个分组标题之前也会带上「其它」，但那个「其它」切进去什么都没有（哔哩哔哩源
    // 前面 4 项就是搜索类型/搜索关键词/搜索按钮），不该因此凭空多出一个空分组
    val groups = remember(allItems) {
        allItems.filter { it.isLoadableTag() }.mapNotNull { it.group }.distinct()
    }
    val currentGroupValue = groups.firstOrNull { it == currentGroup } ?: groups.firstOrNull()
    val tagItems = remember(allItems, currentGroupValue) {
        allItems.filter {
            it.isLoadableTag() && (currentGroupValue == null || it.group == currentGroupValue)
        }
    }
    val settingItems = remember(allItems) { buildModernSettingItems(allItems) }
    // 展开弹窗按书源 style 的 flex 声明排布（与分类区、发现页管理表单同一套口径）
    val groupSpecs = remember(groups, tagModel) {
        buildModernGroupSpecs(groups, tagModel.groupHeaders)
    }
    val tagSpecs = remember(tagItems) {
        tagItems.map { FlexWrapItemSpec(style = it.kind.style()) }
    }

    // 切换大分组（对齐参考分支 rvDiscoverSelects 点击 → applyDiscoverTagFilterAndSelect）：
    // 当前选中标签不属于新分组时，自动选中并加载新分组的第一个 url 类标签，
    // 否则只切分组条、内容区还停在旧分组（起点按钮筛选这类多分组源上必现）
    fun selectGroup(group: String?) {
        currentGroup = group
        val groupTags = allItems.filter { it.group == group && it.isLoadableTag() }
        if (groupTags.none { it.url == selectedTagUrl }) {
            val target = groupTags.firstOrNull()
            selectedTagUrl = target?.url
            if (target != null) {
                controller.loadExploreUrl(target.url.orEmpty(), target.text)
            } else {
                // 分组下没有可选分类（如按钮筛选组）：清空内容区显示空态，
                // 不保留上一个分组的内容（对齐参考分支 clearDiscoverBooksToEmpty）
                controller.clearBooksToEmpty()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        ModernExploreHeader(
            controller = controller,
            actions = actions,
            onSwitchLegacy = onSwitchLegacy,
            sources = sources,
            selectedSourceUrl = selectedSourceUrl,
            hasSettings = settingItems.isNotEmpty(),
            onPickSource = { showSourcePicker = true },
            onOpenSettings = { showSettingsSheet = true },
            onRefreshSource = onRefreshSource,
            onLogin = onLogin,
            onEditSource = onEditSource,
            onSearchSource = onSearchSource,
            modifier = Modifier.fillMaxWidth(),
        )
        // 大分组条：仅当书源声明了整行分组项时显示
        if (currentGroupValue != null) {
            ModernTagBar(
                items = groups,
                selectedIndex = groups.indexOf(currentGroupValue),
                onSelect = { index -> selectGroup(groups.getOrNull(index)) },
                onExpand = { showGroupPicker = true },
                showExpand = groups.size >= TAG_EXPAND_THRESHOLD,
            )
        }
        // 当前分组的 url 类标签条
        ModernTagBar(
            items = tagItems.map { it.text },
            selectedIndex = tagItems.indexOfFirst { it.url == selectedTagUrl },
            onSelect = { index ->
                val item = tagItems.getOrNull(index) ?: return@ModernTagBar
                item.url?.let {
                    selectedTagUrl = it
                    controller.loadExploreUrl(it, item.text)
                }
            },
            onExpand = { showTagPicker = true },
            showExpand = tagItems.size >= TAG_EXPAND_THRESHOLD,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            ExploreShowListContent(
                controller = controller,
                onShowBookInfo = actions.onShowBookInfo,
                onBookLongClick = { book ->
                    sheetBook = book
                    sheetShelfState = controller.getBookShelfState(book)
                    showBookSheet = true
                },
                bottomPaddingPx = bottomPaddingPx,
            )
            if (showBlockProgress && controller.blockedCount > 0) {
                BlockProgressChip(
                    blockedCount = controller.blockedCount,
                    onClick = actions.onShowBlockRuleClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            start = AppDimens.exploreShowBlockChipOuterHorizontal,
                            top = AppDimens.exploreShowBlockChipOuterTop,
                            end = AppDimens.exploreShowBlockChipOuterHorizontal,
                        ),
                )
            }
        }
    }

    if (showSourcePicker) {
        AppSearchableChoiceDialog(
            title = stringResource(R.string.discovery),
            options = sources.map { it.bookSourceName },
            selectedIndex = sources.indexOfFirst { it.bookSourceUrl == selectedSourceUrl },
            onSelect = { index ->
                showSourcePicker = false
                sources.getOrNull(index)?.let(onSelectSource)
            },
            onDismissRequest = { showSourcePicker = false },
        )
    }

    if (showTagPicker) {
        AppTagGridDialog(
            title = stringResource(R.string.select),
            options = tagItems.map { it.text },
            selectedIndex = tagItems.indexOfFirst { it.url == selectedTagUrl },
            onSelect = { index ->
                showTagPicker = false
                tagItems.getOrNull(index)?.let { item ->
                    item.url?.let {
                        selectedTagUrl = it
                        controller.loadExploreUrl(it, item.text)
                    }
                }
            },
            onDismissRequest = { showTagPicker = false },
            itemSpecs = tagSpecs,
        )
    }

    if (showGroupPicker) {
        AppTagGridDialog(
            title = stringResource(R.string.select),
            options = groups,
            selectedIndex = groups.indexOf(currentGroupValue),
            onSelect = { index ->
                showGroupPicker = false
                selectGroup(groups.getOrNull(index))
            },
            onDismissRequest = { showGroupPicker = false },
            itemSpecs = groupSpecs,
        )
    }

    if (showSettingsSheet && selectedSourceUrl != null) {
        ModernExploreSettingsSheet(
            sourceUrl = selectedSourceUrl,
            items = settingItems,
            kindsController = kindsController,
            onOpenExplore = { url, title ->
                showSettingsSheet = false
                selectedTagUrl = url
                controller.loadExploreUrl(url, title)
            },
            onDismiss = { showSettingsSheet = false },
        )
    }

    if (showBookSheet) {
        BookBottomSheet(
            show = true,
            book = sheetBook,
            shelfState = sheetShelfState,
            onDismiss = { showBookSheet = false },
            onAddToShelf = { actions.addToShelf(it) },
            onShowInfo = { actions.onShowBookInfo(it) },
        )
    }
}

/**
 * 头部行：左上角"源名 ▾"（20sp 粗体，点击切换源），右侧"发现页管理"（齿轮）
 * 与功能菜单（三点，Compose DropdownMenu 锚定按钮）圆钮——对齐参考分支
 * ll_discover_source_row。
 */
@Composable
private fun ModernExploreHeader(
    controller: ExploreShowController,
    actions: ExploreShowActions,
    onSwitchLegacy: () -> Unit,
    sources: List<BookSourcePart>,
    selectedSourceUrl: String?,
    hasSettings: Boolean,
    onPickSource: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshSource: () -> Unit,
    onLogin: () -> Unit,
    onEditSource: () -> Unit,
    onSearchSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSource = sources.firstOrNull { it.bookSourceUrl == selectedSourceUrl }
    val currentName = currentSource?.bookSourceName
        ?: sources.firstOrNull()?.bookSourceName
        ?: stringResource(R.string.discovery)
    // 对齐旧版书源条目的长按菜单：没有登录地址的书源不显示「登录」
    val hasLoginUrl = currentSource?.hasLoginUrl == true
    var showMoreMenu by remember { mutableStateOf(false) }
    // 头部行顶替旧版 TitleBar，配色必须继续走 TopBarConfig 统一体系
    val topBarColors = pageTopBarColors()

    Row(
        modifier = modifier
            // 背景先于 statusBarsPadding：覆盖状态栏 + 顶栏区域，与旧版 TitleBar 一致
            .pageTopBarBackground(topBarColors)
            .statusBarsPadding()
            // 自占旧版 TitleBar 的高度（状态栏 inset + 56dp toolbar），内容垂直居中
            .height(AppDimens.topBarHeight)
            .padding(horizontal = AppDimens.exploreShowTabsHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPickSource),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentName,
                color = topBarColors.contentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = stringResource(R.string.discovery),
                tint = topBarColors.contentColor,
                modifier = Modifier.size(28.dp),
            )
        }
        // 搜索书籍：图标入口，放在「发现页管理」左侧（此前收在三点菜单里）
        if (currentSource != null) {
            IconButton(onClick = onSearchSource) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.search),
                    tint = topBarColors.contentColor,
                )
            }
        }
        if (hasSettings) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    // 发现页管理=筛选/调节参数表单：用调节滑杆，比通用齿轮更贴语义
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.setting),
                    tint = topBarColors.contentColor,
                )
            }
        }
        Box {
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.more),
                    tint = topBarColors.contentColor,
                )
            }
            DropdownMenu(
                modifier = Modifier.eInkGrayscale(),
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.menu_page, controller.currentPage)) },
                    onClick = {
                        showMoreMenu = false
                        actions.onPagePick(controller.currentPage) { controller.skipPageTo(it) }
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.refresh)) },
                    onClick = {
                        showMoreMenu = false
                        onRefreshSource()
                    },
                )
                if (hasLoginUrl) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.login)) },
                        onClick = {
                            showMoreMenu = false
                            onLogin()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.add_all_to_shelf)) },
                    onClick = {
                        showMoreMenu = false
                        actions.onAddAllToShelfClick()
                    },
                )
                DropdownMenuItem(
                    text = {
                        val modeName = when (controller.layoutMode) {
                            EXPLORE_LAYOUT_GRID -> stringResource(R.string.switch_layout_grid)
                            EXPLORE_LAYOUT_WATERFALL -> stringResource(R.string.switch_layout_waterfall)
                            else -> stringResource(R.string.switch_layout_list)
                        }
                        Text(text = stringResource(R.string.switch_layout_current, modeName))
                    },
                    onClick = {
                        showMoreMenu = false
                        controller.switchLayout()
                    },
                )
                // 列表布局没有列数概念，不显示选列入口
                if (controller.layoutMode != EXPLORE_LAYOUT_LIST) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.select_column_count)) },
                        onClick = {
                            showMoreMenu = false
                            actions.onColumnPick(controller.effectiveColumnCount()) {
                                controller.selectColumnCount(it)
                            }
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.explore_block_rule)) },
                    onClick = {
                        showMoreMenu = false
                        actions.onShowBlockRuleClick()
                    },
                )
                // 与旧版长按书源菜单里的「编辑」同一动作，作用于当前选中的书源
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.edit)) },
                    onClick = {
                        showMoreMenu = false
                        onEditSource()
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.switch_to_old_explore)) },
                    onClick = {
                        showMoreMenu = false
                        onSwitchLegacy()
                    },
                )
            }
        }
    }
}

/**
 * 「发现页管理」表单弹窗（对齐参考分支 RowUiDialog）：
 * select/text/button 类分类项以流式表单呈现，select 选中即关闭并重建分类。
 *
 * 每项宽度对齐参考分支 RowUiForm.createRowLayoutParams：
 * 声明 flexBasisPercent 的按整行宽百分比、声明 flexGrow 的按内容宽再分剩余空间、
 * 两者都没声明的独占整行（View 版 MATCH_PARENT）——与分类区共用 FlexWrapLayout。
 */
@Composable
private fun ModernExploreSettingsSheet(
    sourceUrl: String,
    items: List<ModernTagItem>,
    kindsController: ExploreKindsController,
    onOpenExplore: (url: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val kindsActions = remember(sourceUrl) {
        ExploreSourceActions(
            onToggleExpand = {},
            onMenuAction = { _, _ -> },
            onOpenExplore = { _, title, url -> onOpenExplore(url, title) },
            onShowError = {},
            onShowPhoto = { _, _ -> },
        )
    }
    // 表单任一项（select/toggle/text）改值后分类可能已被书源重建：
    // 统一在弹窗关闭路径上触发刷新信号，不能靠各组件自行回调（会漏）
    var formChanged by remember { mutableStateOf(false) }
    fun dismissWithRefresh() {
        if (formChanged) {
            kindsController.requestRefresh(sourceUrl)
        }
        onDismiss()
    }

    AlertDialog(
        modifier = Modifier.eInkGrayscale(),
        onDismissRequest = { dismissWithRefresh() },
        title = { Text(text = stringResource(R.string.setting)) },
        text = {
            FlexWrapLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                items = items.map { item ->
                    val style = item.kind.style()
                    FlexWrapItemSpec(
                        style = style,
                        fillLine = style.layout_flexBasisPercent < 0f && style.layout_flexGrow <= 0f,
                    )
                },
                // 表单读的是字，不是分类区的胶囊流：项间距按参考分支 RowUiForm 的 margin
                // （4dp/6dp → 8dp/12dp）收紧，而不是分类区那套 14dp
                horizontalSpacing = AppDimens.exploreFormItemSpacingHorizontal,
                verticalSpacing = AppDimens.exploreFormItemSpacingVertical,
            ) { index ->
                val item = items[index]
                ExploreKindItem(
                    kind = item.kind,
                    sourceUrl = sourceUrl,
                    controller = kindsController,
                    actions = kindsActions,
                    onSelected = if (item.kind.type == ExploreKind.Type.select) {
                        {
                            // select 选中即关闭（对齐参考分支 dismissOnSelect），
                            // 关闭前必须标记变更，否则刷新信号在关闭路径上丢失
                            formChanged = true
                            dismissWithRefresh()
                        }
                    } else {
                        null
                    },
                    onFormChanged = { formChanged = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { dismissWithRefresh() }) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {},
    )
}

/**
 * 分类项分组（对齐参考分支 buildDiscoverTagItems）：
 * - 整行类（flexBasisPercent>=0.95 或 flexGrow>=1）且无 action 的项作为分组标题，
 *   其后的项归入该分组；分组标题自带 url 时补一个「全部」项；
 * - url 类（非 button/select）进标签条；select/button/text 进设置表单；
 * - 第一个分组标题之前（整源都没有分组标题时则是全部）的项归「其它」分组。
 */
private fun buildModernTagItems(
    kinds: List<ExploreKind>,
    allLabel: String,
    otherLabel: String,
): ModernTagModel {
    var currentGroup: String? = null
    val groupHeaders = mutableListOf<ModernTagItem>()
    val result = mutableListOf<ModernTagItem>()
    kinds.forEach { kind ->
        val action = kind.action?.takeIf { it.isNotBlank() }
        val url = kind.url?.takeIf { it.isNotBlank() }
        val isSelect = kind.type == ExploreKind.Type.select
        val isButton = kind.type == ExploreKind.Type.button && !action.isNullOrBlank()

        if (isModernMajorGroupKind(kind, currentGroup != null)) {
            val group = kind.title.trim().ifBlank { null }
            currentGroup = group
            if (group != null) {
                groupHeaders += ModernTagItem(
                    text = group,
                    url = null,
                    group = group,
                    kind = kind,
                )
            }
            if (!url.isNullOrBlank()) {
                result += ModernTagItem(
                    text = allLabel,
                    url = url,
                    group = group,
                    kind = kind,
                )
            }
            return@forEach
        }

        if (!url.isNullOrBlank() && !isButton && !isSelect) {
            result += ModernTagItem(
                text = kind.title,
                url = url,
                group = currentGroup,
                kind = kind,
            )
            return@forEach
        }

        if (isSelect ||
            isButton ||
            kind.type == ExploreKind.Type.text ||
            kind.type == ExploreKind.Type.toggle ||
            !action.isNullOrBlank()
        ) {
            result += ModernTagItem(
                text = kind.title,
                url = url,
                group = currentGroup,
                kind = kind,
            )
        }
    }
    // 落在第一个分组标题之前的项（番茄小说这类"前导分类"源必现）原本 group 为 null：
    // 有分组时它们既进不了任何分组的标签条，又会被「发现页管理」表单捞走（那表单是给
    // 筛选/开关用的，摆不下可加载的分类）。统一并进「其它」分组后，其中可加载的分类
    // 能像正常分组一样切出来；若它们全是表单项（哔哩哔哩源），该分组不会出现在分组条上
    return ModernTagModel(
        items = result
            .map { if (it.group == null) it.copy(group = otherLabel) else it }
            .distinctBy { "${it.group}|${it.kind.type}|${it.kind.title}|${it.kind.url}|${it.kind.action}" },
        groupHeaders = groupHeaders,
    )
}

/**
 * 分组展开弹窗的项宽：分组表头本身就是整行项（书源声明 flexBasisPercent>=0.95，
 * 或是带标题性质的 flexGrow>=1 无 url 项），按声明排布即各占一行；
 * 隐式「其它」分组没有表头，与同级表头保持一致的整行口径。
 */
private fun buildModernGroupSpecs(
    groups: List<String>,
    groupHeaders: List<ModernTagItem>,
): List<FlexWrapItemSpec> {
    val headerStyles = groupHeaders.associate { it.text to it.kind.style() }
    return groups.map { group ->
        val style = headerStyles[group]
        if (style == null) {
            FlexWrapItemSpec(style = FlexChildStyle.defaultStyle, fillLine = true)
        } else {
            FlexWrapItemSpec(style = style)
        }
    }
}

/**
 * 整行项判定（对齐参考分支 isDiscoverMajorGroupKind / isDiscoverFullLineKind）。
 *
 * 只声明 `layout_flexGrow` 的项**不算整行**：flexbox 里它是"内容宽做基准、再分走本行剩余宽度"，
 * 同行有别的项时并不独占整行——番茄小说「热门标签」下的 纯爱/悬疑/… 就是这么排一行多个的。
 * 这类项里只有**不带 url** 的才是书源当标题用的装饰项。若把带 url 的也算整行，第一个分组标题
 * 之后每个成员都会被提升成分组标题，真正的标题（热门标签/主题/角色/情节）反而名下无项、从分组条上消失。
 */
private fun isModernMajorGroupKind(kind: ExploreKind, hasStartedGroup: Boolean): Boolean {
    if (!kind.action.isNullOrBlank()) return false
    if (kind.type == ExploreKind.Type.button || kind.type == ExploreKind.Type.select) return false
    if (!kind.url.isNullOrBlank() && !hasStartedGroup) return false
    val style = kind.style()
    if (style.layout_flexBasisPercent >= 0.95f) return true
    if (style.layout_flexGrow >= 1f &&
        style.layout_flexBasisPercent < 0f &&
        kind.url.isNullOrBlank()
    ) {
        return true
    }
    return false
}

/** 设置表单内容：select / text / toggle / button（含 action）类 */
private fun buildModernSettingItems(items: List<ModernTagItem>): List<ModernTagItem> = items.filter {
    it.kind.type == ExploreKind.Type.select ||
        it.kind.type == ExploreKind.Type.text ||
        it.kind.type == ExploreKind.Type.toggle ||
        (it.kind.type == ExploreKind.Type.button && !it.kind.action.isNullOrBlank())
}
