package io.legado.app.ui.main.homepage

import android.app.Application
import android.text.Html
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourceHomepageLite
import io.legado.app.data.entities.BookSourceExploreLite
import io.legado.app.data.entities.HomepageModulePref
import io.legado.app.data.entities.HomepageUserModule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.HomepageModulesRepository
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.domain.model.ModuleItem
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.help.book.BookshelfMatcher
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.sortUrls
import io.legado.app.model.rss.Rss
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 首页 ViewModel（方案D重构版）
 *
 * 核心架构变更：
 * - 书源同步模块从 homepage_modules 表消除，改为从书源 homepageModules JSON 实时解析
 * - 用户偏好（显隐/排序/自定义标题/集归属）存储在 homepage_module_prefs 表（FK CASCADE）
 * - 用户手动创建模块存储在 homepage_user_modules 表（应用层级联清理）
 * - 自定义集成员关系存储在 homepage_custom_set_members 表（FK CASCADE）
 * - 书源集不再独立存储，由书源 Flow 自动生成
 *
 * 与发现页一致的关联机制：Room Flow 自动响应书源变更。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomepageViewModel(application: Application) : BaseViewModel(application) {

    private data class ModuleLoadParams(
        val modules: List<HomepageModuleUi>,
        val layout: Int,
        val preload: Int,
        val sets: List<HomepageSourceManageUi>,
        val tabIndex: Int
    )

    /**
     * 合成后的模块数据（从书源JSON解析 + 用户偏好 + 用户模块合并后）
     */
    private data class MergedModule(
        val globalId: String,
        val sourceUrl: String,
        val moduleKey: String,
        val type: String,
        val title: String,
        val args: String?,
        val layoutConfig: String?,
        val url: String?,
        val isEnabled: Boolean,
        val customSetId: String?,
        val sortOrder: Int,
        val isUserCreated: Boolean,
        val sourceType: String,
    )

    companion object {
        private const val CUSTOM_SET_URL_PREFIX = "custom://"

        /** 书源集 ID 前缀 */
        const val BOOK_SOURCE_SET_PREFIX = "src_"
        /** 订阅源集 ID 前缀 */
        const val RSS_SOURCE_SET_PREFIX = "rss_"

        fun customSetUrl(id: String) = "$CUSTOM_SET_URL_PREFIX$id"
        fun isCustomSetUrl(url: String) = url.startsWith(CUSTOM_SET_URL_PREFIX)
        fun customSetIdFromUrl(url: String): String = url.removePrefix(CUSTOM_SET_URL_PREFIX)

        /** 构造书源集 ID */
        fun bookSourceSetId(sourceUrl: String) = "$BOOK_SOURCE_SET_PREFIX$sourceUrl"
        /** 构造订阅源集 ID */
        fun rssSourceSetId(sourceUrl: String) = "$RSS_SOURCE_SET_PREFIX$sourceUrl"
        /** 判断是否为书源集 ID */
        fun isBookSourceSetId(setId: String) = setId.startsWith(BOOK_SOURCE_SET_PREFIX)
        /** 判断是否为订阅源集 ID */
        fun isRssSourceSetId(setId: String) = setId.startsWith(RSS_SOURCE_SET_PREFIX)
        /** 判断是否为源集 ID（书源集或订阅源集） */
        fun isSourceSetId(setId: String) = isBookSourceSetId(setId) || isRssSourceSetId(setId)
        /** 从源集 ID 中提取源 URL */
        fun sourceUrlFromSetId(setId: String): String =
            setId.removePrefix(BOOK_SOURCE_SET_PREFIX).removePrefix(RSS_SOURCE_SET_PREFIX)

        fun isInfinite(type: String?, layoutConfig: String?): Boolean {
            return type == HomepageModuleType.Waterfall.key
                    || type == HomepageModuleType.InfiniteGrid.key
        }

        private fun parseModuleDefs(sourceUrl: String, json: String): List<ModuleDef> =
            GSON.fromJsonArray<ModuleDef>(json).getOrDefault(emptyList())
                .map { it.copy(sourceUrl = sourceUrl) }
    }

    private val gateway: HomepageModulesGateway =
        HomepageModulesRepository(appDb.homepageModuleDao, appDb.homepageCustomSetDao)
    private val exploreBooksUseCase = ExploreBooksUseCase()
    private val saveSearchBooksUseCase = SaveSearchBooksUseCase()
    private val addToBookshelfUseCase = AddToBookshelfUseCase()

    private val _effects = MutableSharedFlow<HomepageEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private val loadJobs = ConcurrentHashMap<String, Job>()

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshingSetName = MutableStateFlow<String?>(null)
    private val _refreshingModuleIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isManageMode = MutableStateFlow(false)
    private val _configVersion = MutableStateFlow(0L)
    private val _moduleContentStates = MutableStateFlow<Map<String, ModuleLoadState>>(emptyMap())
    private val _bookSourcesCache = MutableStateFlow<Map<String, BookSourceExploreLite>>(emptyMap())
    private val _rssSourceNames = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _layoutConfigCache = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    private val _currentTabIndex = MutableStateFlow(0)
    private val _currentSets = MutableStateFlow<List<HomepageSourceManageUi>>(emptyList())

    // ==================== 新数据源：实时视图层 ====================

    private val homepageSourcesFlow = appDb.bookSourceDao.flowHomepageSourcesLite()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val modulePrefsFlow = appDb.homepageModulePrefDao.flowAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val userModulesFlow = appDb.homepageUserModuleDao.flowEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val customSetMembersFlow = appDb.homepageCustomSetMemberDao.flowAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customSetsFlow = gateway.flowCustomSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val customSetsSync = _configVersion.mapLatest {
        gateway.flowCustomSets().first()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 合成所有模块数据：书源JSON解析模块 + 用户偏好 + 用户创建模块
     */
    private val mergedModulesFlow = combine(
        homepageSourcesFlow,
        modulePrefsFlow,
        userModulesFlow,
        customSetMembersFlow,
        _configVersion
    ) { sources, prefs, userModules, members, _ ->
        val prefsMap = prefs.associateBy { "${it.sourceUrl}::${it.moduleKey}" }

        val result = mutableListOf<MergedModule>()

        // 1. 从书源 JSON 实时解析同步模块
        for (source in sources) {
            val json = source.homepageModules ?: continue
            val defs = parseModuleDefs(source.bookSourceUrl, json)
            for ((index, def) in defs.withIndex()) {
                val prefKey = "${source.bookSourceUrl}::${def.key}"
                val pref = prefsMap[prefKey]
                val customSetAssignment = members.firstOrNull {
                    it.sourceUrl == source.bookSourceUrl && it.moduleKey == def.key
                }

                result.add(MergedModule(
                    globalId = ModuleDef.globalIdOf(source.bookSourceUrl, def.key),
                    sourceUrl = source.bookSourceUrl,
                    moduleKey = def.key,
                    type = def.type,
                    title = pref?.customTitle ?: def.title,
                    args = def.args,
                    layoutConfig = def.layoutConfig,
                    url = def.url,
                    isEnabled = pref?.isEnabled ?: true,
                    customSetId = customSetAssignment?.customSetId ?: pref?.customSetId,
                    sortOrder = pref?.sortOrder ?: index,
                    isUserCreated = false,
                    sourceType = "book",
                ))
            }
        }

        // 2. 加入用户手动创建模块
        for (um in userModules) {
            result.add(MergedModule(
                globalId = um.id,
                sourceUrl = um.sourceUrl,
                moduleKey = um.moduleKey,
                type = um.type,
                title = um.title,
                args = um.args,
                layoutConfig = um.layoutConfig,
                url = um.url,
                isEnabled = um.isEnabled,
                customSetId = um.customSetId,
                sortOrder = um.sortOrder,
                isUserCreated = true,
                sourceType = um.sourceType,
            ))
        }

        result
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 替代旧的 gateway.flowAll()，供管理界面使用 */
    val allModulesCache = mergedModulesFlow.map { merged ->
        merged.map { mm ->
            ModuleItem(
                id = mm.globalId,
                sourceUrl = mm.sourceUrl,
                moduleKey = mm.moduleKey,
                type = mm.type,
                title = mm.title,
                args = mm.args,
                layoutConfig = mm.layoutConfig,
                url = mm.url,
                isEnabled = mm.isEnabled,
                customSetId = mm.customSetId,
                isUserCreated = mm.isUserCreated,
                sortOrder = mm.sortOrder,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 构建首页展示用的模块列表（含内容加载状态）
     */
    private val rawModulesFlow = combine(
        mergedModulesFlow,
        _moduleContentStates,
        homepageSourcesFlow,
        customSetsSync,
        combine(_rssSourceNames, combine(_layoutConfigCache, _configVersion) { cache, _ -> cache }) { rssNames, cache -> rssNames to cache }
    ) { merged, contentStates, sources, customSets, pair ->
        val rssNames = pair.first
        val configCache = pair.second
        val sourceMap = sources.associateBy { it.bookSourceUrl }
        val setNames = customSets.associate { it.id to it.name }
        val sortedSetIds = customSets.sortedBy { it.sortOrder }.map { it.id }
        val hidden = hiddenSetUrls

        val result = mutableListOf<HomepageModuleUi>()

        // 1. 按书源集分组（每个有 homepageModules 的书源自动成为一个集）
        for (source in sources) {
            val setUrl = bookSourceSetId(source.bookSourceUrl)
            if (setUrl in hidden) continue
            val mods = merged.filter { it.sourceUrl == source.bookSourceUrl && (it.customSetId == null || it.customSetId == bookSourceSetId(source.bookSourceUrl)) }
            for (mm in mods) {
                val configMap = configCache[mm.globalId] ?: emptyMap()
                result.add(
                    HomepageModuleUi(
                        sourceUrl = mm.sourceUrl,
                        setName = source.bookSourceName,
                        globalId = mm.globalId,
                        type = HomepageModuleType.fromKey(mm.type),
                        title = mm.title,
                        exploreUrl = mm.url ?: source.exploreUrl,
                        customSetId = mm.customSetId,
                        layoutConfig = mm.layoutConfig,
                        state = contentStates[mm.globalId] ?: ModuleLoadState.Loading,
                        config = configMap
                    )
                )
            }
        }

        // 2. 按订阅源集分组（用户创建的 RSS 模块，customSetId 为 rss_ 前缀或 null）
        val rssSetIds = merged
            .filter { it.sourceType == "rss" && (it.customSetId == null || it.customSetId?.startsWith(RSS_SOURCE_SET_PREFIX) == true) }
            .map { it.customSetId ?: rssSourceSetId(it.sourceUrl) }
            .distinct()
        for (setId in rssSetIds) {
            if (setId in hidden) continue
            val sourceUrl = sourceUrlFromSetId(setId)
            val setName = rssNames[sourceUrl] ?: sourceUrl
            val mods = merged.filter { it.customSetId == setId || (it.customSetId == null && it.sourceUrl == sourceUrl) }
            for (mm in mods) {
                val configMap = configCache[mm.globalId] ?: emptyMap()
                result.add(
                    HomepageModuleUi(
                        sourceUrl = mm.sourceUrl,
                        setName = setName,
                        globalId = mm.globalId,
                        type = HomepageModuleType.fromKey(mm.type),
                        title = mm.title,
                        exploreUrl = mm.url,
                        customSetId = mm.customSetId,
                        layoutConfig = mm.layoutConfig,
                        state = contentStates[mm.globalId] ?: ModuleLoadState.Loading,
                        config = configMap
                    )
                )
            }
        }

        // 3. 按自定义集分组
        for (setId in sortedSetIds) {
            val setUrl = customSetUrl(setId)
            if (setUrl in hidden) continue
            val setName = setNames[setId] ?: setId
            val members = customSetMembersFlow.value.filter { it.customSetId == setId }
            val memberModules = merged.filter { mm ->
                members.any { it.sourceUrl == mm.sourceUrl && it.moduleKey == mm.moduleKey && !mm.isUserCreated }
            }
            val userModulesInSet = merged.filter { mm ->
                mm.isUserCreated && mm.customSetId == setId
            }
            for (mm in memberModules + userModulesInSet) {
                val configMap = configCache[mm.globalId] ?: emptyMap()
                val source = sourceMap[mm.sourceUrl]
                result.add(
                    HomepageModuleUi(
                        sourceUrl = mm.sourceUrl,
                        setName = setName,
                        globalId = mm.globalId,
                        type = HomepageModuleType.fromKey(mm.type),
                        title = mm.title,
                        exploreUrl = mm.url ?: source?.exploreUrl,
                        customSetId = mm.customSetId,
                        layoutConfig = mm.layoutConfig,
                        state = contentStates[mm.globalId] ?: ModuleLoadState.Loading,
                        config = configMap
                    )
                )
            }
        }

        result
    }

    private val displayModulesFlow = combine(
        rawModulesFlow,
        BookshelfMatcher.version
    ) { modules, _ ->
        modules.map { module ->
            updateModuleShelfState(module) { item ->
                BookshelfMatcher.getState(item.book.name, item.book.author, item.book.bookUrl)
            }
        }
    }

    private fun updateModuleShelfState(
        module: HomepageModuleUi,
        resolveState: (HomepageBookItemUi) -> BookShelfState
    ): HomepageModuleUi {
        val state = module.state
        return when (state) {
            is ModuleLoadState.Loaded -> {
                module.copy(state = state.copy(
                    books = state.books.map { item ->
                        val newShelfState = resolveState(item)
                        if (item.shelfState == newShelfState) item
                        else item.copy(shelfState = newShelfState)
                    }
                ))
            }
            is ModuleLoadState.RankingTabs -> {
                module.copy(state = state.copy(
                    tabs = state.tabs.map { tab ->
                        val books = tab.books ?: return@map tab
                        tab.copy(books = books.map { item ->
                            val newShelfState = resolveState(item)
                            if (item.shelfState == newShelfState) item
                            else item.copy(shelfState = newShelfState)
                        })
                    }
                ))
            }
            else -> module
        }
    }

    // ==================== Management Flows ====================

    private val hiddenSetUrls: Set<String>
        get() {
            val json = HomepageConfig.homepageSourceHidden
            if (json.isBlank()) return emptySet()
            return GSON.fromJsonArray<String>(json).getOrDefault(emptySet()).toSet()
        }

    private fun saveHiddenSetUrls(urls: Set<String>) {
        HomepageConfig.homepageSourceHidden = GSON.toJson(urls)
    }

    val setsFlow = combine(
        homepageSourcesFlow,
        customSetsSync,
        mergedModulesFlow,
        _configVersion,
        _rssSourceNames
    ) { sources, customSets, modules, _, rssNames ->
        val hidden = hiddenSetUrls
        val result = mutableListOf<HomepageSourceManageUi>()

        // 书源集
        for (source in sources) {
            val setUrl = bookSourceSetId(source.bookSourceUrl)
            val count = modules.count { it.sourceUrl == source.bookSourceUrl && (it.customSetId == null || it.customSetId == bookSourceSetId(source.bookSourceUrl)) }
            result.add(HomepageSourceManageUi(
                sourceUrl = setUrl,
                sourceName = source.bookSourceName,
                isSelected = setUrl !in hidden,
                moduleCount = count,
                isCustomSet = false,
                sourceType = "book",
            ))
        }

        // 订阅源集（从用户创建的 RSS 模块中提取 rss_ 前缀集或 null）
        val rssSetIds = modules
            .filter { it.sourceType == "rss" && (it.customSetId == null || it.customSetId?.startsWith(RSS_SOURCE_SET_PREFIX) == true) }
            .map { it.customSetId ?: rssSourceSetId(it.sourceUrl) }
            .distinct()
        for (setId in rssSetIds) {
            val sourceUrl = sourceUrlFromSetId(setId)
            val count = modules.count { it.customSetId == setId || (it.customSetId == null && it.sourceUrl == sourceUrl) }
            result.add(HomepageSourceManageUi(
                sourceUrl = setId,
                sourceName = rssNames[sourceUrl] ?: sourceUrl,
                isSelected = setId !in hidden,
                moduleCount = count,
                isCustomSet = false,
                sourceType = "rss",
            ))
        }

        // 自定义集
        for (cs in customSets) {
            val setUrl = customSetUrl(cs.id)
            val count = modules.count { it.customSetId == cs.id }
            result.add(HomepageSourceManageUi(
                sourceUrl = setUrl,
                sourceName = cs.name,
                isSelected = setUrl !in hidden,
                moduleCount = count,
                isCustomSet = true,
                sourceType = null,
            ))
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browseSourcesFlow = combine(
        _bookSourcesCache,
        mergedModulesFlow,
        _configVersion
    ) { sources, modules, _ ->
        sources.values.map { source ->
            val count = modules.count { it.sourceUrl == source.bookSourceUrl }
            HomepageSourceManageUi(
                sourceUrl = source.bookSourceUrl,
                sourceName = source.bookSourceName,
                sourceGroup = source.bookSourceGroup,
                moduleCount = count,
                isCustomSet = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val layoutMode: StateFlow<Int> = _configVersion
        .map { HomepageConfig.homepageLayoutMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageConfig.homepageLayoutMode)

    val preloadMode: StateFlow<Int> = _configVersion
        .map { HomepageConfig.homepagePreload }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageConfig.homepagePreload)

    val manageStateFlow = combine(
        setsFlow,
        browseSourcesFlow,
        allModulesCache,
        _bookSourcesCache,
        _rssSourceNames
    ) { sets, browseSources, modules, sources, rssNames ->
        val sourceNames = sources.values.associate { it.bookSourceUrl to it.bookSourceName } + rssNames
        val allJoined = modules.map { mod ->
            HomepageModuleManageUi(
                id = mod.id,
                sourceUrl = mod.sourceUrl,
                sourceName = sourceNames[mod.sourceUrl] ?: mod.sourceUrl,
                moduleKey = mod.moduleKey,
                title = mod.displayTitle,
                customSetTitle = mod.customSetTitle,
                customSetId = mod.customSetId,
                isVisible = mod.isEnabled,
                type = mod.type,
                url = mod.url,
                args = mod.args,
                layoutConfig = mod.layoutConfig,
                originalTitle = mod.title,
                sourceType = if (rssNames.containsKey(mod.sourceUrl)) "rss" else "book",
            )
        }
        HomepageManageUiState(
            sets = sets,
            browseSources = browseSources,
            allJoinedModules = allJoined,
            sourceNames = sourceNames,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageManageUiState())

    val uiState: StateFlow<HomepageUiState> = combine(
        displayModulesFlow,
        _isRefreshing,
        _isManageMode,
        manageStateFlow
    ) { modules, isRefreshing, isManageMode, manageState ->
        HomepageUiState(
            modules = modules,
            isRefreshing = isRefreshing,
            isManageMode = isManageMode,
            manageState = manageState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageUiState())

    init {
        viewModelScope.launch {
            mergedModulesFlow.collect { modules ->
                val cache = mutableMapOf<String, Map<String, String>>()
                for (module in modules) {
                    val configStr = module.layoutConfig ?: continue
                    try {
                        val json = GSON.fromJson(configStr, Map::class.java)
                        if (json != null) {
                            val map = mutableMapOf<String, String>()
                            json.forEach { (k, v) -> map["layout_$k"] = v.toString() }
                            cache[module.globalId] = map
                        }
                    } catch (_: Exception) {
                    }
                }
                _layoutConfigCache.value = cache
            }
        }

        viewModelScope.launch {
            appDb.bookSourceDao.flowExploreSourcesLite().collect { sources ->
                _bookSourcesCache.value = sources.associateBy { it.bookSourceUrl }
            }
        }

        viewModelScope.launch {
            appDb.rssSourceDao.flowAllLite().collect { sources ->
                _rssSourceNames.value = sources.associate { it.sourceUrl to it.sourceName }
            }
        }

        viewModelScope.launch {
            combine(
                uiState.map { it.modules },
                layoutMode,
                preloadMode,
                _currentSets,
                _currentTabIndex
            ) { modules, layout, preload, sets, tabIndex ->
                ModuleLoadParams(modules, layout, preload, sets, tabIndex)
            }.collect { params ->
                val shouldLoadIds = computeShouldLoadModuleIds(
                    params.modules,
                    params.layout,
                    params.preload
                )

                params.modules.forEach { ui ->
                    if (ui.state is ModuleLoadState.Loading && loadJobs[ui.globalId]?.isActive != true) {
                        val shouldLoad = if (_isRefreshing.value) {
                            ui.globalId in _refreshingModuleIds.value
                        } else {
                            ui.globalId in shouldLoadIds
                        }
                        if (shouldLoad) {
                            val mm = mergedModulesFlow.value.find { it.globalId == ui.globalId }
                            if (mm != null) loadModule(mm)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            _moduleContentStates.collect { states ->
                if (_isRefreshing.value) {
                    val targetIds = _refreshingModuleIds.value
                    val allLoaded = if (targetIds.isNotEmpty()) {
                        targetIds.all { id ->
                            val state = states[id]
                            state != null && state !is ModuleLoadState.Loading
                        }
                    } else {
                        states.values.none { it is ModuleLoadState.Loading } && states.isNotEmpty()
                    }
                    if (allLoaded) {
                        kotlinx.coroutines.delay(400)
                        _isRefreshing.value = false
                        _refreshingSetName.value = null
                        _refreshingModuleIds.value = emptySet()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
    }

    private fun loadModule(mm: MergedModule) {
        loadJobs[mm.globalId]?.cancel()
        if (mm.type == HomepageModuleType.ButtonGroup.key) {
            loadJobs[mm.globalId] = viewModelScope.launch {
                kotlin.runCatching {
                    val selectedTitles = parseKindTitlesFromArgs(mm.args)
                    if (selectedTitles.isNullOrEmpty()) {
                        emptyList<ExploreKind>()
                    } else {
                        val rssSource = appDb.rssSourceDao.getByKey(mm.sourceUrl)
                        if (rssSource != null) {
                            val allKinds = rssSource.sortUrls().map { (title, url) ->
                                ExploreKind(title = title, url = url)
                            }
                            selectedTitles.mapNotNull { t -> allKinds.find { it.title == t } }
                        } else {
                            val source = appDb.bookSourceDao.getBookSource(mm.sourceUrl)
                                ?: throw Exception("Source not found")
                            val allKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
                            selectedTitles.mapNotNull { t -> allKinds.find { it.title == t } }
                        }
                    }
                }.onSuccess { kinds ->
                    _moduleContentStates.update { it + (mm.globalId to ModuleLoadState.Buttons(kinds)) }
                }.onFailure { e ->
                    _moduleContentStates.update { it + (mm.globalId to ModuleLoadState.Error(e.stackTraceStr)) }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(mm.globalId) } }
            return
        }
        val isRanking = mm.type == HomepageModuleType.Ranking.key || mm.type == HomepageModuleType.GridRanking.key
        val rankingCategoryPairs = if (isRanking) parseRankingCategories(mm.args) else null

        if (rankingCategoryPairs != null && rankingCategoryPairs.size >= 2) {
            val rssSource = appDb.rssSourceDao.getByKey(mm.sourceUrl)
            val initialTabs = rankingCategoryPairs.map { (title, url) ->
                RankingTabData(
                    title = title,
                    exploreUrl = url.ifBlank { null },
                    page = 1,
                    hasMore = true,
                    isLoadingMore = false
                )
            }
            _moduleContentStates.update { it + (mm.globalId to ModuleLoadState.RankingTabs(initialTabs)) }
            if (rankingCategoryPairs.isNotEmpty()) {
                val (title, url) = rankingCategoryPairs[0]
                loadRankingTab(mm.globalId, mm.sourceUrl, rssSource, 0, title, url, page = 1)
            }
            return
        }
        loadJobs[mm.globalId] = viewModelScope.launch {
            kotlin.runCatching {
                val rssSource = appDb.rssSourceDao.getByKey(mm.sourceUrl)
                if (rssSource != null) {
                    val sortUrl = mm.url ?: rssSource.sourceUrl
                    val sortName = mm.title.ifBlank { rssSource.sourceName }
                    val (articles, _) = withContext(Dispatchers.IO) {
                        Rss.getArticlesAwait(sortName, sortUrl, rssSource, page = 1)
                    }
                    val books = articles.map { article ->
                        val introText = article.description?.let {
                            Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                        }
                        SearchBook(
                            bookUrl = article.link,
                            origin = rssSource.sourceUrl,
                            originName = rssSource.sourceName,
                            name = article.title,
                            coverUrl = article.image,
                            intro = introText,
                            author = rssSource.sourceName,
                            latestChapterTitle = article.pubDate,
                        )
                    }
                    books to false
                } else {
                    val effectiveUrl = if (isRanking) {
                        parseRankingCategories(mm.args)?.firstOrNull()?.second?.ifBlank { null }
                            ?: mm.url
                    } else {
                        mm.url
                    }
                    val result = exploreBooksUseCase.execute(
                        sourceUrl = mm.sourceUrl,
                        moduleUrl = effectiveUrl,
                        args = mm.args,
                        page = 1
                    )
                    result.books to result.hasMore
                }
            }.onSuccess { (books, hasMore) ->
                _moduleContentStates.update {
                    it + (mm.globalId to ModuleLoadState.Loaded(
                        books = books.map { book ->
                            HomepageBookItemUi(
                                book = book,
                                shelfState = BookshelfMatcher.getState(
                                    book.name, book.author, book.bookUrl
                                )
                            )
                        },
                        hasMore = hasMore,
                        page = 1,
                        isLoadingMore = false
                    ))
                }
            }.onFailure { e ->
                _moduleContentStates.update { it + (mm.globalId to ModuleLoadState.Error(e.stackTraceStr)) }
            }
        }.also { it.invokeOnCompletion { loadJobs.remove(mm.globalId) } }
    }

    fun loadMoreModule(globalId: String) {
        val currentState = _moduleContentStates.value[globalId] as? ModuleLoadState.Loaded ?: return
        if (currentState.isLoadingMore || !currentState.hasMore) return
        val nextPage = currentState.page + 1
        _moduleContentStates.update { it + (globalId to currentState.copy(isLoadingMore = true)) }
        viewModelScope.launch {
            kotlin.runCatching {
                val mm = mergedModulesFlow.value.find { it.globalId == globalId }
                    ?: throw Exception("Module not found")
                val isRanking = mm.type == HomepageModuleType.Ranking.key ||
                        mm.type == HomepageModuleType.GridRanking.key
                val effectiveUrl = if (isRanking) {
                    parseRankingCategories(mm.args)?.firstOrNull()?.second?.ifBlank { null }
                        ?: mm.url
                } else {
                    mm.url
                }
                exploreBooksUseCase.execute(
                    sourceUrl = mm.sourceUrl,
                    moduleUrl = effectiveUrl,
                    args = mm.args,
                    page = nextPage
                )
            }.onSuccess { result ->
                _moduleContentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    val existingUrls = lastState.books.map { it.book.bookUrl }.toSet()
                    val deduped = result.books.filter { it.bookUrl !in existingUrls }.map { book ->
                        HomepageBookItemUi(
                            book = book,
                            shelfState = BookshelfMatcher.getState(
                                book.name, book.author, book.bookUrl
                            )
                        )
                    }
                    val finalHasMore = if (deduped.isEmpty()) false else result.hasMore
                    states + (globalId to ModuleLoadState.Loaded(
                        books = lastState.books + deduped,
                        hasMore = finalHasMore,
                        isLoadingMore = false,
                        page = nextPage
                    ))
                }
            }.onFailure { e ->
                _moduleContentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    states + (globalId to lastState.copy(isLoadingMore = false))
                }
                _effects.tryEmit(HomepageEffect.ShowSnackbar("加载更多失败: ${e.message}"))
            }
        }
    }

    fun refreshButtonGroup(globalId: String) {
        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == globalId } ?: return@launch
            loadModule(mm)
        }
    }

    fun onKindUrlClick(sourceUrl: String, url: String, title: String) =
        _effects.tryEmit(HomepageEffect.NavigateToExploreShow(title, sourceUrl, url))

    fun selectRankingTab(globalId: String, index: Int) {
        val prevState = _moduleContentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return
        _moduleContentStates.update { states ->
            val current = states[globalId] as? ModuleLoadState.RankingTabs ?: return@update states
            states + (globalId to current.copy(selectedIndex = index))
        }
        val tab = prevState.tabs.getOrNull(index) ?: return

        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == globalId } ?: return@launch
            val rssSource = appDb.rssSourceDao.getByKey(mm.sourceUrl)
            val state = _moduleContentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return@launch
            val currentTab = state.tabs.getOrNull(index) ?: return@launch
            if (currentTab.books == null && currentTab.errorMessage == null) {
                val tabJobKey = "${globalId}_tab_$index"
                if (loadJobs[tabJobKey]?.isActive != true) {
                    loadRankingTab(globalId, mm.sourceUrl, rssSource, index, currentTab.title, currentTab.exploreUrl ?: "", page = 1)
                }
            }
            if (preloadMode.value == 1) {
                listOf(index - 1, index + 1).forEach { adjacentIndex ->
                    val adjacentTab = state.tabs.getOrNull(adjacentIndex) ?: return@forEach
                    if (adjacentTab.books == null && adjacentTab.errorMessage == null) {
                        val adjJobKey = "${globalId}_tab_$adjacentIndex"
                        if (loadJobs[adjJobKey]?.isActive != true) {
                            loadRankingTab(globalId, mm.sourceUrl, rssSource, adjacentIndex, adjacentTab.title, adjacentTab.exploreUrl ?: "", page = 1)
                        }
                    }
                }
            }
        }
    }

    private fun loadRankingTab(
        moduleId: String,
        sourceUrl: String,
        rssSource: RssSource?,
        index: Int,
        title: String,
        url: String,
        page: Int = 1
    ) {
        val jobKey = "${moduleId}_tab_$index"
        loadJobs[jobKey]?.cancel()
        loadJobs[jobKey] = viewModelScope.launch {
            kotlin.runCatching {
                val books = if (rssSource != null) {
                    val (articles, _) = withContext(Dispatchers.IO) {
                        Rss.getArticlesAwait(title.ifBlank { rssSource.sourceName }, url, rssSource, page = page)
                    }
                    articles.map { article ->
                        SearchBook(
                            bookUrl = article.link,
                            origin = rssSource.sourceUrl,
                            originName = rssSource.sourceName,
                            name = article.title,
                            coverUrl = article.image,
                            intro = article.description?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() },
                            author = rssSource.sourceName,
                            latestChapterTitle = article.pubDate,
                        )
                    }
                } else {
                    val result = exploreBooksUseCase.execute(
                        sourceUrl = sourceUrl,
                        moduleUrl = url.ifBlank { null },
                        args = null,
                        page = page
                    )
                    result.books
                }
                books.map { book ->
                    HomepageBookItemUi(
                        book = book,
                        shelfState = BookshelfMatcher.getState(
                            book.name, book.author, book.bookUrl
                        )
                    )
                }
            }.onSuccess { bookItems ->
                _moduleContentStates.update { states ->
                    val current = states[moduleId] as? ModuleLoadState.RankingTabs ?: return@update states
                    val updatedTabs = current.tabs.toMutableList()
                    val oldTab = updatedTabs[index]
                    val existingUrls = oldTab.books?.map { it.book.bookUrl }?.toSet() ?: emptySet()
                    val deduped = bookItems.filter { it.book.bookUrl !in existingUrls }
                    val newBooks = if (oldTab.books != null) oldTab.books + deduped else bookItems
                    val hasMore = if (bookItems.isEmpty()) false else true
                    updatedTabs[index] = oldTab.copy(
                        books = newBooks,
                        page = page,
                        hasMore = hasMore,
                        isLoadingMore = false,
                        errorMessage = null
                    )
                    states + (moduleId to current.copy(tabs = updatedTabs))
                }
            }.onFailure { e ->
                _moduleContentStates.update { states ->
                    val current = states[moduleId] as? ModuleLoadState.RankingTabs ?: return@update states
                    val updatedTabs = current.tabs.toMutableList()
                    updatedTabs[index] = updatedTabs[index].copy(
                        errorMessage = e.stackTraceStr,
                        isLoadingMore = false
                    )
                    states + (moduleId to current.copy(tabs = updatedTabs))
                }
            }
        }.also { it.invokeOnCompletion { loadJobs.remove(jobKey) } }
    }

    fun loadMoreRankingTab(globalId: String, tabIndex: Int) {
        val state = _moduleContentStates.value[globalId] as? ModuleLoadState.RankingTabs ?: return
        val tab = state.tabs.getOrNull(tabIndex) ?: return

        if (tab.isLoadingMore) return

        val nextPage = tab.page + 1

        val effectiveHasMore = if (!tab.hasMore && tab.books != null && tab.books.isNotEmpty()) {
            true
        } else {
            tab.hasMore
        }
        if (!effectiveHasMore) return

        _moduleContentStates.update { states ->
            val current = states[globalId] as? ModuleLoadState.RankingTabs ?: return@update states
            val updatedTabs = current.tabs.toMutableList()
            updatedTabs[tabIndex] = updatedTabs[tabIndex].copy(
                isLoadingMore = true,
                hasMore = true,
                errorMessage = null
            )
            states + (globalId to current.copy(tabs = updatedTabs))
        }

        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == globalId } ?: return@launch
            val rssSource = appDb.rssSourceDao.getByKey(mm.sourceUrl)
            loadRankingTab(
                moduleId = globalId,
                sourceUrl = mm.sourceUrl,
                rssSource = rssSource,
                index = tabIndex,
                title = tab.title,
                url = tab.exploreUrl ?: "",
                page = nextPage
            )
        }
    }

    fun onRefresh(setName: String? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshingSetName.value = setName
            loadJobs.values.forEach { it.cancel() }
            loadJobs.clear()
            if (setName != null) {
                val setModules = uiState.value.modules.filter { it.setName == setName }
                val setModuleIds = setModules.map { it.globalId }.toSet()
                _refreshingModuleIds.value = setModuleIds
                _moduleContentStates.update { states ->
                    states.filterKeys { it !in setModuleIds }
                }
            } else {
                _refreshingModuleIds.value = uiState.value.modules.map { it.globalId }.toSet()
                _moduleContentStates.value = emptyMap()
            }
        }
    }

    fun retryModule(globalId: String) {
        _moduleContentStates.update { it + (globalId to ModuleLoadState.Loading) }
    }

    fun getCurrentBookShelfState(book: SearchBook): BookShelfState {
        return BookshelfMatcher.getState(
            name = book.name,
            author = book.author,
            bookUrl = book.bookUrl
        )
    }

    fun onAddToShelf(book: SearchBook) {
        execute {
            addToBookshelfUseCase.execute(book)
        }
    }

    fun onBookClick(book: SearchBook) {
        viewModelScope.launch {
            if (!appDb.rssSourceDao.has(book.origin)) {
                saveSearchBooksUseCase.save(book)
            }
            _effects.emit(
                HomepageEffect.NavigateToBookInfo(
                    book.name,
                    book.author,
                    book.bookUrl,
                    book.origin,
                    book.coverUrl
                )
            )
        }
    }

    fun onModuleHeaderClick(sourceUrl: String, exploreUrl: String?, title: String?) {
        viewModelScope.launch {
            _effects.emit(
                HomepageEffect.NavigateToExploreShow(title, sourceUrl, exploreUrl)
            )
        }
    }

    private fun computeShouldLoadModuleIds(
        modules: List<HomepageModuleUi>,
        layoutMode: Int,
        preloadMode: Int
    ): Set<String> {
        if (layoutMode == 0) {
            return modules.map { it.globalId }.toSet()
        }

        val currentSets = _currentSets.value
        val currentTabIndex = _currentTabIndex.value

        if (currentSets.isEmpty()) {
            return emptySet()
        }

        val indicesToLoad = if (preloadMode == 1) {
            val start = (currentTabIndex - 1).coerceAtLeast(0)
            val end = (currentTabIndex + 1).coerceAtMost(currentSets.lastIndex)
            (start..end).toList()
        } else {
            listOf(currentTabIndex.coerceIn(0, currentSets.lastIndex))
        }

        val setUrlsToLoad = indicesToLoad.mapNotNull { index ->
            currentSets.getOrNull(index)?.sourceUrl
        }

        return modules.filter { module ->
            setUrlsToLoad.any { setUrl ->
                when {
                    setUrl.startsWith("custom://") -> {
                        val setId = customSetIdFromUrl(setUrl)
                        module.customSetId == setId
                    }
                    setUrl.startsWith(BOOK_SOURCE_SET_PREFIX) -> {
                        // 书源集：模块 customSetId 为 null（在书源集中）或等于 setUrl
                        module.customSetId == setUrl || (module.customSetId == null && setUrl == bookSourceSetId(module.sourceUrl))
                    }
                    setUrl.startsWith(RSS_SOURCE_SET_PREFIX) -> {
                        // 订阅源集：模块 customSetId 等于 setUrl
                        module.customSetId == setUrl
                    }
                    else -> false
                }
            }
        }.map { it.globalId }.toSet()
    }

    // ==================== Management Methods ====================

    fun toggleManageMode() {
        _isManageMode.value = !_isManageMode.value
    }

    fun setLayoutMode(mode: Int) {
        HomepageConfig.homepageLayoutMode = mode
        notifyConfigChanged()
    }

    fun setPreloadMode(mode: Int) {
        HomepageConfig.homepagePreload = mode
        notifyConfigChanged()
    }

    fun updateCurrentTab(tabIndex: Int, sets: List<HomepageSourceManageUi>) {
        _currentTabIndex.value = tabIndex
        _currentSets.value = sets
    }

    private fun notifyConfigChanged() {
        _configVersion.update { it + 1 }
    }

    fun toggleSet(setUrl: String, visible: Boolean) {
        val hidden = hiddenSetUrls.toMutableSet()
        if (visible) hidden.remove(setUrl) else hidden.add(setUrl)
        saveHiddenSetUrls(hidden)
        notifyConfigChanged()
    }

    fun getSourceModules(sourceUrl: String, setId: String?): List<HomepageModuleManageUi> {
        // 优先从书源缓存获取（BrowseBookSources 页面使用），回退到首页书源流
        val exploreSource = _bookSourcesCache.value[sourceUrl]
        val homepageSource = homepageSourcesFlow.value.find { it.bookSourceUrl == sourceUrl }
        val json = exploreSource?.homepageModules ?: homepageSource?.homepageModules ?: return emptyList()
        val sourceName = exploreSource?.bookSourceName ?: homepageSource?.bookSourceName ?: sourceUrl
        val defs = parseModuleDefs(sourceUrl, json)
        val existing = allModulesCache.value.filter { it.sourceUrl == sourceUrl }
        return defs.map { def ->
            val globalId = ModuleDef.globalIdOf(sourceUrl, def.key, setId ?: bookSourceSetId(sourceUrl))
            val existingMod = existing.find { it.id == globalId || (it.sourceUrl == sourceUrl && it.moduleKey == def.key) }
            HomepageModuleManageUi(
                id = globalId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                moduleKey = def.key,
                title = def.title,
                customSetId = existingMod?.customSetId,
                isVisible = existingMod?.isEnabled ?: false,
                type = def.type,
                url = def.url,
                args = def.args,
                layoutConfig = def.layoutConfig,
                originalTitle = def.title,
            )
        }
    }

    /**
     * 同步书源模块（方案D中此操作不再需要——书源JSON变更后Flow自动重发）
     * 保留方法签名以兼容 UI 调用，实际为空操作。
     */
    fun syncSourceModules(sourceUrl: String) {
        // 方案D：无需手动同步，书源 JSON 变更后 Room Flow 自动重发
        notifyConfigChanged()
    }

    /**
     * 切换模块显隐（写入偏好表）
     */
    fun toggleModule(moduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == moduleId } ?: return@launch
            if (mm.isUserCreated) {
                // 用户创建模块：更新 user_modules 表
                appDb.homepageUserModuleDao.setEnabled(moduleId, enabled)
            } else {
                // 书源同步模块：更新偏好表
                appDb.homepageModulePrefDao.upsert(
                    HomepageModulePref(
                        sourceUrl = mm.sourceUrl,
                        moduleKey = mm.moduleKey,
                        isEnabled = enabled,
                        customTitle = modulePrefsFlow.value.find {
                            it.sourceUrl == mm.sourceUrl && it.moduleKey == mm.moduleKey
                        }?.customTitle,
                        sortOrder = modulePrefsFlow.value.find {
                            it.sourceUrl == mm.sourceUrl && it.moduleKey == mm.moduleKey
                        }?.sortOrder ?: 0,
                        customSetId = mm.customSetId,
                    )
                )
            }
            notifyConfigChanged()
        }
    }

    /**
     * 加入书源同步模块（启用偏好）
     */
    fun joinModule(sourceUrl: String, setId: String?, def: ModuleDef) {
        viewModelScope.launch {
            // 方案D：书源模块已从JSON实时解析，joinModule = 设置偏好为启用
            // 注意：书源集模块的 customSetId 保持 null（表示在书源集中），
            // 仅当分配到自定义集时才设置 customSetId
            val effectiveSetId = setId?.takeIf { HomepageViewModel.isCustomSetUrl(it) }
            val existingPref = appDb.homepageModulePrefDao.get(sourceUrl, def.key)
            if (existingPref != null) {
                appDb.homepageModulePrefDao.setEnabled(sourceUrl, def.key, true)
                if (effectiveSetId != null) {
                    appDb.homepageModulePrefDao.setCustomSetId(sourceUrl, def.key, effectiveSetId)
                }
            } else {
                appDb.homepageModulePrefDao.upsert(
                    HomepageModulePref(
                        sourceUrl = sourceUrl,
                        moduleKey = def.key,
                        isEnabled = true,
                        customSetId = effectiveSetId,
                    )
                )
            }
            notifyConfigChanged()
        }
    }

    /**
     * 添加自定义模块（用户手动创建，写入 user_modules 表）
     */
    fun addCustomModule(sourceUrl: String, setId: String?, def: ModuleDef) {
        viewModelScope.launch {
            val key = def.key.ifBlank { "custom_${System.currentTimeMillis()}" }
            val effectiveSetId = setId  // null 表示在书源集中
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId ?: bookSourceSetId(sourceUrl))
            appDb.homepageUserModuleDao.upsert(
                HomepageUserModule(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = def.type,
                    title = def.title,
                    args = def.args,
                    layoutConfig = def.layoutConfig,
                    url = def.url,
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    sourceType = "book",
                )
            )
            notifyConfigChanged()
        }
    }

    fun addButtonGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        kindTitles: List<String>
    ) {
        viewModelScope.launch {
            val effectiveSetId = setId  // null 表示在书源集中
            val key = "bg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId ?: bookSourceSetId(sourceUrl))
            appDb.homepageUserModuleDao.upsert(
                HomepageUserModule(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = HomepageModuleType.ButtonGroup.key,
                    title = title,
                    args = GSON.toJson(kindTitles.map { mapOf("t" to it, "u" to "") }),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    sourceType = "book",
                )
            )
            notifyConfigChanged()
        }
    }

    suspend fun getExploreKinds(sourceUrl: String): List<ExploreKind> {
        return runCatching {
            withContext(Dispatchers.IO) {
                appDb.bookSourceDao.getBookSource(sourceUrl)?.exploreKinds() ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getRssKinds(sourceUrl: String): List<Pair<String, String>> {
        val source = appDb.rssSourceDao.getByKey(sourceUrl) ?: return emptyList()
        return runCatching {
            source.sortUrls()
        }.getOrDefault(listOf(Pair("", sourceUrl)))
    }

    fun addRssCustomModule(sourceUrl: String, setId: String?, def: ModuleDef) {
        viewModelScope.launch {
            val effectiveSetId = setId  // null 表示在订阅源集中
            val key = def.key.ifBlank { "rss_${System.currentTimeMillis()}" }
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId ?: rssSourceSetId(sourceUrl))
            appDb.homepageUserModuleDao.upsert(
                HomepageUserModule(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = def.type,
                    title = def.title,
                    args = def.args,
                    layoutConfig = def.layoutConfig,
                    url = def.url,
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    sourceType = "rss",
                )
            )
            notifyConfigChanged()
        }
    }

    fun addRssButtonGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        kindTitles: List<String>
    ) {
        viewModelScope.launch {
            val effectiveSetId = setId  // null 表示在订阅源集中
            val key = "bg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId ?: rssSourceSetId(sourceUrl))
            appDb.homepageUserModuleDao.upsert(
                HomepageUserModule(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = HomepageModuleType.ButtonGroup.key,
                    title = title,
                    args = GSON.toJson(kindTitles.map { mapOf("t" to it, "u" to "") }),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    sourceType = "rss",
                )
            )
            notifyConfigChanged()
        }
    }

    fun addRankingGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        categories: List<Pair<String, String>>,
        rankingType: String = HomepageModuleType.Ranking.key
    ) {
        viewModelScope.launch {
            val effectiveSetId = setId  // null 表示在书源集中
            val key = "rg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId ?: bookSourceSetId(sourceUrl))
            val args = categories.map { mapOf("t" to it.first, "u" to it.second) }
            appDb.homepageUserModuleDao.upsert(
                HomepageUserModule(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = rankingType,
                    title = title,
                    args = GSON.toJson(args),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    sourceType = "book",
                )
            )
            notifyConfigChanged()
        }
    }

    fun addRssRankingGroupFromKinds(
        sourceUrl: String,
        setId: String?,
        title: String,
        categories: List<Pair<String, String>>,
        rankingType: String = HomepageModuleType.Ranking.key
    ) {
        viewModelScope.launch {
            val effectiveSetId = setId  // null 表示在订阅源集中
            val key = "rg_${System.currentTimeMillis()}"
            val globalId = ModuleDef.globalIdOf(sourceUrl, key, effectiveSetId ?: rssSourceSetId(sourceUrl))
            val args = categories.map { mapOf("t" to it.first, "u" to it.second) }
            appDb.homepageUserModuleDao.upsert(
                HomepageUserModule(
                    id = globalId,
                    sourceUrl = sourceUrl,
                    moduleKey = key,
                    type = rankingType,
                    title = title,
                    args = GSON.toJson(args),
                    isEnabled = true,
                    customSetId = effectiveSetId,
                    sortOrder = allModulesCache.value.count { it.customSetId == effectiveSetId },
                    sourceType = "rss",
                )
            )
            notifyConfigChanged()
        }
    }

    private fun parseRankingCategories(args: String?): List<Pair<String, String>>? {
        if (args.isNullOrBlank()) return null
        return try {
            val list = GSON.fromJsonArray<Map<String, String>>(args).getOrNull() ?: return null
            val result = list.mapNotNull { map ->
                val t = map["t"] ?: return@mapNotNull null
                val u = map["u"] ?: ""
                Pair(t, u)
            }
            if (result.isNotEmpty()) result else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseKindTitlesFromArgs(args: String?): List<String>? {
        if (args.isNullOrBlank()) return null
        try {
            val list = GSON.fromJsonArray<Map<String, String>>(args).getOrNull()
            if (list != null && list.isNotEmpty()) {
                return list.mapNotNull { it["t"] }
            }
        } catch (_: Exception) { }
        return try {
            GSON.fromJsonArray<String>(args).getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun updateModule(globalId: String, def: ModuleDef) {
        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == globalId } ?: return@launch
            if (mm.isUserCreated) {
                // 用户创建模块：更新 user_modules 表
                appDb.homepageUserModuleDao.upsert(
                    HomepageUserModule(
                        id = globalId,
                        sourceUrl = mm.sourceUrl,
                        moduleKey = mm.moduleKey,
                        type = def.type,
                        title = def.title,
                        args = def.args,
                        layoutConfig = def.layoutConfig,
                        url = def.url,
                        isEnabled = mm.isEnabled,
                        customSetId = mm.customSetId,
                        sortOrder = mm.sortOrder,
                        sourceType = mm.sourceType,
                    )
                )
            } else {
                // 书源同步模块：更新偏好表中的 customTitle
                val existingPref = appDb.homepageModulePrefDao.get(mm.sourceUrl, mm.moduleKey)
                appDb.homepageModulePrefDao.upsert(
                    HomepageModulePref(
                        sourceUrl = mm.sourceUrl,
                        moduleKey = mm.moduleKey,
                        isEnabled = mm.isEnabled,
                        customTitle = def.title.takeIf { it != mm.title },
                        sortOrder = existingPref?.sortOrder ?: mm.sortOrder,
                        customSetId = mm.customSetId,
                    )
                )
            }
            notifyConfigChanged()
        }
    }

    fun deleteModule(globalId: String) {
        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == globalId } ?: return@launch
            if (mm.isUserCreated) {
                // 用户创建模块：从 user_modules 表删除
                appDb.homepageUserModuleDao.delete(globalId)
            } else {
                // 书源同步模块：设置偏好为禁用
                appDb.homepageModulePrefDao.upsert(
                    HomepageModulePref(
                        sourceUrl = mm.sourceUrl,
                        moduleKey = mm.moduleKey,
                        isEnabled = false,
                        customTitle = modulePrefsFlow.value.find {
                            it.sourceUrl == mm.sourceUrl && it.moduleKey == mm.moduleKey
                        }?.customTitle,
                        sortOrder = modulePrefsFlow.value.find {
                            it.sourceUrl == mm.sourceUrl && it.moduleKey == mm.moduleKey
                        }?.sortOrder ?: 0,
                        customSetId = mm.customSetId,
                    )
                )
                // 如果有自定义集成员关系，也删除
                appDb.homepageCustomSetMemberDao.deleteBySource(mm.sourceUrl)
            }
            _moduleContentStates.update { it - globalId }
            loadJobs.remove(globalId)?.cancel()
            notifyConfigChanged()
        }
    }

    fun reorderModules(orderedIds: List<String>) {
        viewModelScope.launch {
            // 方案D：按模块类型分别更新排序
            orderedIds.forEachIndexed { index, id ->
                val mm = mergedModulesFlow.value.find { it.globalId == id } ?: return@forEachIndexed
                if (mm.isUserCreated) {
                    appDb.homepageUserModuleDao.setSortOrder(id, index)
                } else {
                    val existingPref = appDb.homepageModulePrefDao.get(mm.sourceUrl, mm.moduleKey)
                    appDb.homepageModulePrefDao.upsert(
                        HomepageModulePref(
                            sourceUrl = mm.sourceUrl,
                            moduleKey = mm.moduleKey,
                            isEnabled = existingPref?.isEnabled ?: true,
                            customTitle = existingPref?.customTitle,
                            sortOrder = index,
                            customSetId = existingPref?.customSetId,
                        )
                    )
                }
            }
            notifyConfigChanged()
        }
    }

    fun reorderCustomSets(orderedUrls: List<String>) {
        viewModelScope.launch {
            val orders = orderedUrls.mapIndexed { index, url ->
                customSetIdFromUrl(url) to index
            }.toMap()
            gateway.batchSetCustomSetSortOrders(orders)
            notifyConfigChanged()
        }
    }

    fun setCustomSetTitle(moduleId: String, title: String?) {
        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == moduleId } ?: return@launch
            if (mm.isUserCreated) {
                appDb.homepageUserModuleDao.setTitle(moduleId, title ?: mm.title)
            } else {
                val existingPref = appDb.homepageModulePrefDao.get(mm.sourceUrl, mm.moduleKey)
                appDb.homepageModulePrefDao.upsert(
                    HomepageModulePref(
                        sourceUrl = mm.sourceUrl,
                        moduleKey = mm.moduleKey,
                        isEnabled = existingPref?.isEnabled ?: true,
                        customTitle = title,
                        sortOrder = existingPref?.sortOrder ?: 0,
                        customSetId = existingPref?.customSetId,
                    )
                )
            }
            notifyConfigChanged()
        }
    }

    fun createCustomSet(name: String) {
        viewModelScope.launch {
            gateway.createCustomSet(name)
            notifyConfigChanged()
        }
    }

    fun renameCustomSet(id: String, name: String) {
        viewModelScope.launch {
            gateway.renameCustomSet(id, name)
            notifyConfigChanged()
        }
    }

    fun deleteCustomSet(id: String) {
        viewModelScope.launch {
            // 删除自定义集的成员关系
            appDb.homepageCustomSetMemberDao.deleteByCustomSet(id)
            // 删除该集中用户创建的模块
            appDb.homepageUserModuleDao.deleteByCustomSet(id)
            // 删除自定义集本身
            gateway.deleteCustomSet(id)
            // 清理内容状态
            val deletedModuleIds = allModulesCache.value.filter { it.customSetId == id }.map { it.id }
            deletedModuleIds.forEach { mid ->
                _moduleContentStates.update { it - mid }
                loadJobs.remove(mid)?.cancel()
            }
            notifyConfigChanged()
        }
    }

    fun assignModuleToCustomSet(moduleId: String, customSetId: String?) {
        viewModelScope.launch {
            val mm = mergedModulesFlow.value.find { it.globalId == moduleId } ?: return@launch
            if (customSetId == null) {
                // 从自定义集移除
                if (mm.isUserCreated) {
                    // 用户创建模块：设置 customSetId 为 null（回到源集）
                    appDb.homepageUserModuleDao.setCustomSetId(moduleId, null)
                } else {
                    // 书源同步模块：删除成员关系
                    appDb.homepageCustomSetMemberDao.delete(mm.customSetId ?: "", mm.sourceUrl, mm.moduleKey)
                }
            } else {
                // 分配到自定义集
                if (mm.isUserCreated) {
                    appDb.homepageUserModuleDao.setCustomSetId(moduleId, customSetId)
                } else {
                    // 书源同步模块：创建成员关系
                    appDb.homepageCustomSetMemberDao.upsert(
                        io.legado.app.data.entities.HomepageCustomSetMember(
                            customSetId = customSetId,
                            sourceUrl = mm.sourceUrl,
                            moduleKey = mm.moduleKey,
                            sortOrder = customSetMembersFlow.value.count { it.customSetId == customSetId },
                        )
                    )
                }
            }
            notifyConfigChanged()
        }
    }
}
