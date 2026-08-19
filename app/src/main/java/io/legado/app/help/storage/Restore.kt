package io.legado.app.help.storage

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.CoverGalleryGroup
import io.legado.app.data.entities.CoverGalleryImage
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.HomepageCustomSet
import io.legado.app.data.entities.HomepageModule
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.ui.book.read.websearch.SearchEngine
import io.legado.app.ui.book.read.websearch.SearchEngineHelper
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.AppCacheManager
import io.legado.app.help.CacheManager
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStore
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.openInputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复管理类
 * 
 * 负责从备份文件恢复应用数据，包括：
 * - 解压备份ZIP文件
 * - 恢复数据库数据（书籍、书签、书源等）
 * - 恢复SharedPreferences配置
 * - 恢复自定义配置文件
 * 
 * 恢复流程：
 * 1. 解压ZIP文件到临时目录
 * 2. 读取JSON文件并导入数据库
 * 3. 恢复SharedPreferences配置
 * 4. 应用主题和阅读配置
 * 5. 清理临时文件
 * 
 * 特殊处理：
 * - 书籍数据：支持忽略本地书籍，更新已存在书籍
 * - 阅读记录：恢复前清空本地记录，再导入备份记录
 * - 服务器配置：需要解密
 * - WebDav密码：需要解密
 */
object Restore {
    private const val runtimeSourceCacheFileName = "runtimeSourceCache.json"
    private const val bookCacheFolderName = "book_cache"
    private const val bookCacheIndexFileName = "bookCacheIndex.json"
    private const val bookCacheBooksFileName = "bookCacheBooks.json"

    /** 互斥锁，防止并发恢复操作 */
    private val mutex = Mutex()

    private const val TAG = "Restore"
    private val themeRestorePrefKeys = arrayOf(
        PreferKey.dThemeName,
        PreferKey.dNThemeName,
        PreferKey.cPrimary,
        PreferKey.cAccent,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.bgImage,
        PreferKey.bgImageBlurring,
        PreferKey.tNavBar,
        PreferKey.cNPrimary,
        PreferKey.cNAccent,
        PreferKey.cNBackground,
        PreferKey.cNBBackground,
        PreferKey.bgImageN,
        PreferKey.bgImageNBlurring,
        PreferKey.tNavBarN
    )

    /**
     * 从URI恢复备份
     * 支持SAF（Storage Access Framework）和普通文件路径
     * 
     * @param context Android Context
     * @param uri 备份文件URI
     */
    suspend fun restore(
        context: Context,
        uri: Uri,
        onProgress: ((String) -> Unit)? = null
    ) {
        LogUtils.d(TAG, "开始恢复备份 uri:$uri")
        kotlin.runCatching {
            onProgress?.invoke(BackupInfoHelper.getDisplayName("unzipBackup"))
            FileUtils.delete(Backup.backupPath)
            if (uri.isContentScheme()) {
                DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                    ZipUtils.unZipToPath(it, Backup.backupPath)
                }
            } else {
                ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
            }
        }.onFailure {
            AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            return
        }
        kotlin.runCatching {
            restoreLocked(Backup.backupPath, onProgress)
            LocalConfig.lastBackup = System.currentTimeMillis()
            LocalConfig.lastRestore = System.currentTimeMillis()
        }.onFailure {
            appCtx.toastOnUi("恢复备份出错\n${it.localizedMessage}")
            AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
        }
    }

    /**
     * 带锁的恢复方法
     * 使用互斥锁确保同一时间只有一个恢复操作在执行
     * 
     * @param path 备份文件解压后的目录路径
     */
    suspend fun restoreLocked(
        path: String,
        onProgress: ((String) -> Unit)? = null
    ) {
        mutex.withLock {
            restore(path, onProgress)
        }
    }

    /**
     * 选择性恢复方法
     * 只恢复用户选中的文件
     * 
     * @param context Android Context
     * @param path 已解压的备份目录路径
     * @param selectedFiles 选中的文件名列表
     */
    suspend fun restoreSelected(
        context: Context,
        path: String,
        selectedFiles: List<String>,
        onProgress: ((String) -> Unit)? = null
    ) {
        LogUtils.d(TAG, "开始选择性恢复备份 path:$path, files:${selectedFiles.joinToString()}")
        mutex.withLock {
            try {
                restoreSelectedFiles(path, selectedFiles, onProgress)
                LocalConfig.lastBackup = System.currentTimeMillis()
                LocalConfig.lastRestore = System.currentTimeMillis()
            } catch (e: Exception) {
                appCtx.toastOnUi("恢复备份出错\n${e.localizedMessage}")
                AppLog.put("选择性恢复备份出错\n${e.localizedMessage}", e)
            }
        }
    }

    /**
     * 核心选择性恢复逻辑
     * 
     * @param path 备份文件解压后的目录路径
     * @param selectedFiles 选中的文件名列表
     */
    private suspend fun restoreSelectedFiles(
        path: String,
        selectedFiles: List<String>,
        onProgress: ((String) -> Unit)? = null
    ) {
        val aes = BackupAES()
        val selectedSet = selectedFiles.toSet()
        fun progress(fileName: String) {
            onProgress?.invoke(BackupInfoHelper.getDisplayName(fileName))
        }

        // 恢复书架数据
        if ("bookshelf.json" in selectedSet) {
            progress("bookshelf.json")
            appDb.bookDao.deleteAll()
            fileToListT<Book>(path, "bookshelf.json")?.let {
                it.forEach { book -> book.upType() }
                it.filter { book -> book.isLocal }
                    .forEach { book -> book.coverUrl = LocalBook.getCoverPath(book) }
                val ignoreLocalBook = BackupConfig.ignoreLocalBook
                val books = it.filterNot { book -> ignoreLocalBook && book.isLocal }
                appDb.bookDao.insert(*books.toTypedArray())
            }
        }

        // 恢复书签
        if ("bookmark.json" in selectedSet) {
            progress("bookmark.json")
            appDb.bookmarkDao.deleteAll()
            fileToListT<Bookmark>(path, "bookmark.json")?.let {
                appDb.bookmarkDao.insert(*it.toTypedArray())
            }
        }

        // 恢复书籍分组
        if ("bookGroup.json" in selectedSet) {
            progress("bookGroup.json")
            appDb.bookGroupDao.deleteAll()
            fileToListT<BookGroup>(path, "bookGroup.json")?.let {
                appDb.bookGroupDao.insert(*it.toTypedArray())
            }
        }

        // 恢复书源
        if ("bookSource.json" in selectedSet) {
            progress("bookSource.json")
            appDb.bookSourceDao.deleteAll()
            fileToListT<BookSource>(path, "bookSource.json")?.let {
                appDb.bookSourceDao.insert(*it.toTypedArray())
            } ?: run {
                val bookSourceFile = File(path, "bookSource.json")
                if (bookSourceFile.exists()) {
                    val json = bookSourceFile.readText()
                    ImportOldData.importOldSource(json)
                }
            }
        }

        // 恢复RSS源
        if ("rssSources.json" in selectedSet) {
            progress("rssSources.json")
            appDb.rssSourceDao.deleteAll()
            fileToListT<RssSource>(path, "rssSources.json")?.let {
                appDb.rssSourceDao.insert(*it.toTypedArray())
            }
        }

        // 恢复RSS收藏
        if ("rssStar.json" in selectedSet) {
            progress("rssStar.json")
            appDb.rssStarDao.deleteAll()
            fileToListT<RssStar>(path, "rssStar.json")?.let {
                appDb.rssStarDao.insert(*it.toTypedArray())
            }
        }

        // 恢复源订阅链接
        if ("sourceSub.json" in selectedSet) {
            progress("sourceSub.json")
            appDb.ruleSubDao.deleteAll()
            fileToListT<RuleSub>(path, "sourceSub.json")?.let {
                appDb.ruleSubDao.insert(*it.toTypedArray())
            }
        }

        // 恢复搜索引擎规则
        if ("webSearchEngines.json" in selectedSet) {
            progress("webSearchEngines.json")
            val enginesFile = File(path, "webSearchEngines.json")
            if (enginesFile.exists()) {
                try {
                    val enginesJson = enginesFile.readText()
                    val engines = GSON.fromJsonArray<SearchEngine>(enginesJson).getOrNull()
                    if (engines != null) {
                        SearchEngineHelper.saveSearchEngines(appCtx, engines)
                    }
                } catch (e: Exception) {
                    AppLog.put("恢复搜索引擎规则出错\n${e.localizedMessage}", e)
                }
            }
        }

        // 恢复首页数据
        if ("homepage.json" in selectedSet) {
            progress("homepage.json")
            val file = File(path, "homepage.json")
            if (file.exists()) {
                val json = file.readText()
                val obj = GSON.fromJsonObject<Map<String, JsonElement>>(json).getOrNull()
                if (obj != null) {
                    appDb.homepageModuleDao.deleteAll()
                    // 同时清理方案D新表
                    appDb.homepageModulePrefDao.deleteAll()
                    appDb.homepageUserModuleDao.deleteAll()
                    appDb.homepageCustomSetMemberDao.deleteAll()
                    (obj["modules"] as? JsonArray)?.let { array ->
                        val modules = GSON.fromJsonArray<HomepageModule>(array.toString()).getOrNull()
                        modules?.let { appDb.homepageModuleDao.upsertAll(it) }
                    }
                    appDb.homepageCustomSetDao.deleteAll()
                    (obj["customSets"] as? JsonArray)?.let { array ->
                        val sets = GSON.fromJsonArray<HomepageCustomSet>(array.toString()).getOrNull()
                        sets?.forEach { set -> appDb.homepageCustomSetDao.upsert(set) }
                    }
                    // 恢复方案D新表数据
                    (obj["modulePrefs"] as? JsonArray)?.let { array ->
                        val prefs = GSON.fromJsonArray<io.legado.app.data.entities.HomepageModulePref>(array.toString()).getOrNull()
                        prefs?.let { appDb.homepageModulePrefDao.upsertAll(it) }
                    }
                    (obj["userModules"] as? JsonArray)?.let { array ->
                        val modules = GSON.fromJsonArray<io.legado.app.data.entities.HomepageUserModule>(array.toString()).getOrNull()
                        modules?.let { appDb.homepageUserModuleDao.upsertAll(it) }
                    }
                    (obj["customSetMembers"] as? JsonArray)?.let { array ->
                        val members = GSON.fromJsonArray<io.legado.app.data.entities.HomepageCustomSetMember>(array.toString()).getOrNull()
                        members?.let { appDb.homepageCustomSetMemberDao.upsertAll(it) }
                    }
                }
            }
        }

        // 恢复替换规则
        if ("replaceRule.json" in selectedSet) {
            progress("replaceRule.json")
            appDb.replaceRuleDao.deleteAll()
            fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
                appDb.replaceRuleDao.insert(*it.toTypedArray())
            }
        }

        // 恢复搜索历史
        if (HighlightRuleStore.backupFileName in selectedSet) {
            progress(HighlightRuleStore.backupFileName)
            File(path, HighlightRuleStore.backupFileName).takeIf { it.exists() }?.runCatching {
                GSON.fromJsonObject<HighlightRuleStore.BackupData>(readText()).getOrNull()?.let {
                    HighlightRuleStore.restoreBackupData(appCtx, it, path)
                }
            }?.onFailure {
                AppLog.put("恢复高亮规则出错\n${it.localizedMessage}", it)
            }
        }
        if ("searchHistory.json" in selectedSet) {
            progress("searchHistory.json")
            appDb.searchKeywordDao.deleteAll()
            fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
                appDb.searchKeywordDao.insert(*it.toTypedArray())
            }
        }

        // 恢复TXT目录规则
        if ("txtTocRule.json" in selectedSet) {
            progress("txtTocRule.json")
            appDb.txtTocRuleDao.deleteAll()
            fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
                appDb.txtTocRuleDao.insert(*it.toTypedArray())
            }
        }

        // 恢复HTTP TTS配置
        if ("httpTTS.json" in selectedSet) {
            progress("httpTTS.json")
            appDb.httpTTSDao.deleteAll()
            fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
                appDb.httpTTSDao.insert(*it.toTypedArray())
            }
        }

        // 恢复词典规则
        if ("dictRule.json" in selectedSet) {
            progress("dictRule.json")
            appDb.dictRuleDao.deleteAll()
            fileToListT<DictRule>(path, "dictRule.json")?.let {
                appDb.dictRuleDao.insert(*it.toTypedArray())
            }
        }

        // 恢复键盘辅助
        if ("keyboardAssists.json" in selectedSet) {
            progress("keyboardAssists.json")
            appDb.keyboardAssistsDao.deleteAll()
            fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
                appDb.keyboardAssistsDao.insert(*it.toTypedArray())
            }
        }

        if (CoverGalleryRepository.backupDirName in selectedSet) {
            progress(CoverGalleryRepository.backupDirName)
            restoreCoverGallery(path)
        }

        // 恢复阅读记录
        if ("readRecord.json" in selectedSet || "readRecordDetail.json" in selectedSet || "readRecordSession.json" in selectedSet) {
            progress("readRecord.json")
            val readRecords = if ("readRecord.json" in selectedSet) fileToListT<ReadRecord>(path, "readRecord.json").orEmpty() else emptyList()
            val readRecordDetails = if ("readRecordDetail.json" in selectedSet) fileToListT<ReadRecordDetail>(path, "readRecordDetail.json").orEmpty() else emptyList()
            val readRecordSessions = if ("readRecordSession.json" in selectedSet) fileToListT<ReadRecordSession>(path, "readRecordSession.json").orEmpty() else emptyList()
            if (readRecords.isNotEmpty() || readRecordDetails.isNotEmpty() || readRecordSessions.isNotEmpty()) {
                // 预加载书名→作者映射，避免 repairRecords 中逐条查询 bookDao
                val bookAuthorMap = appDb.bookDao.all
                    .mapNotNull { book -> book.author.trim().ifBlank { null }?.let { book.name to it } }
                    .toMap()
                // 整个清空+导入+修复在一个事务中执行，避免 N 次事务提交开销
                appDb.withTransaction {
                    appDb.readRecordDao.clear()
                    appDb.readRecordDao.clearDetails()
                    appDb.readRecordDao.clearSessions()
                    ReadRecordRepository(appDb.readRecordDao).apply {
                        importRecords(readRecords, readRecordDetails, readRecordSessions)
                        repairRecords { bookName -> bookAuthorMap[bookName] }
                    }
                }
                appCtx.putPrefInt(PreferKey.readRecordRepairVersion, ReadRecordRepository.CURRENT_REPAIR_VERSION)
            }
        }

        // 恢复服务器配置
        if ("servers.json" in selectedSet) {
            progress("servers.json")
            appDb.serverDao.deleteAll()
            File(path, "servers.json").takeIf { it.exists() }?.runCatching {
                var json = readText()
                if (!json.isJsonArray()) { json = aes.decryptStr(json) }
                GSON.fromJsonArray<Server>(json).getOrNull()?.let { appDb.serverDao.insert(*it.toTypedArray()) }
            }?.onFailure { AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it) }
        }

        // 恢复直链上传配置
        if (DirectLinkUpload.ruleFileName in selectedSet) {
            progress(DirectLinkUpload.ruleFileName)
            File(path, DirectLinkUpload.ruleFileName).takeIf { it.exists() }?.runCatching {
                val json = readText()
                ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
            }?.onFailure { AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it) }
        }

        // 恢复主题配置
        if (ThemeConfig.configFileName in selectedSet) {
            progress(ThemeConfig.configFileName)
            File(path, ThemeConfig.configFileName).takeIf { it.exists() }?.runCatching {
                val configs = GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrNull()
                FileUtils.delete(ThemeConfig.configFilePath)
                copyTo(File(ThemeConfig.configFilePath))
                ThemeConfig.replaceConfigs(configs)
            }?.onFailure { AppLog.put("恢复主题出错\n${it.localizedMessage}", it) }
        }

        // 恢复封面规则配置
        if (BookCover.configFileName in selectedSet) {
            progress(BookCover.configFileName)
            File(path, BookCover.configFileName).takeIf { it.exists() }?.runCatching {
                val json = readText()
                BookCover.saveCoverRule(json)
                // 清除封面缓存，确保使用新配置生成封面
                CoverImageView.clearAllCache()
            }?.onFailure { AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it) }
        }

        // 恢复阅读界面配置
        if (!BackupConfig.ignoreReadConfig && (ReadBookConfig.configFileName in selectedSet || ReadBookConfig.shareConfigFileName in selectedSet)) {
            progress("backgroundImages")
            restoreReadConfigBackgrounds(path)
            if (ReadBookConfig.configFileName in selectedSet) {
                progress(ReadBookConfig.configFileName)
                File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
                    FileUtils.delete(ReadBookConfig.configFilePath)
                    copyTo(File(ReadBookConfig.configFilePath))
                    ReadBookConfig.initConfigs()
                }?.onFailure { AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it) }
            }
            if (ReadBookConfig.shareConfigFileName in selectedSet) {
                progress(ReadBookConfig.shareConfigFileName)
                File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
                    FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                    copyTo(File(ReadBookConfig.shareConfigFilePath))
                    ReadBookConfig.initShareConfig()
                }?.onFailure { AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it) }
            }
        }

        // 恢复主题背景图片
        fixReadConfigBackgroundPaths()

        // 恢复SharedPreferences配置
        if ("config.xml" in selectedSet) {
            progress("config.xml")
            // 旧备份没有 highlightRule.json，需要允许 config.xml 恢复高亮规则键
            val allowHighlightKeys = !File(path, HighlightRuleStore.backupFileName).exists()
            readBackupPrefs(path, "config")?.let { map ->
                clearThemeRestorePrefs()
                val edit = appCtx.defaultSharedPreferences.edit()
                map.forEach { (key, value) ->
                    if (BackupConfig.keyIsNotIgnore(key, allowHighlightKeys) || key in themeRestorePrefKeys) {
                        when (key) {
                            PreferKey.webDavPassword -> {
                                kotlin.runCatching { aes.decryptStr(value.toString()) }.getOrNull()?.let {
                                    edit.putString(key, it)
                                } ?: let {
                                    if (appCtx.getPrefString(PreferKey.webDavPassword).isNullOrBlank()) {
                                        edit.putString(key, value.toString())
                                    }
                                }
                            }
                            else -> when (value) {
                                is Int -> edit.putInt(key, value)
                                is Boolean -> edit.putBoolean(key, value)
                                is Long -> edit.putLong(key, value)
                                is Float -> edit.putFloat(key, value)
                                is String -> edit.putString(key, value)
                            }
                        }
                    }
                }
                edit.apply()
            }
            // 如果从 config.xml 恢复了高亮规则，清除缓存以强制重新加载
            if (allowHighlightKeys) {
                HighlightRuleStore.clearCache()
            }
        }

        // 修正主题背景图片路径
        progress("themeBackgroundImages")
        restoreThemeBackgrounds(
            backupPath = path,
            clearExisting = "config.xml" in selectedSet || ThemeConfig.configFileName in selectedSet
        )
        fixThemeBackgroundPaths()
        fixThemeConfigBackgroundPaths()

        // 恢复视频播放配置
        if ("videoConfig.xml" in selectedSet) {
            progress("videoConfig.xml")
            readBackupPrefs(path, "videoConfig")?.let { map ->
                appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                    clear()
                    map.forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is String -> putString(key, value)
                        }
                    }
                    apply()
                }
            }
        }

        // 应用阅读配置
        if (runtimeSourceCacheFileName in selectedSet) {
            progress(runtimeSourceCacheFileName)
            restoreRuntimeSourceCaches(path)
        }

        // 恢复书籍缓存和章节目录
        LogUtils.d(TAG, "检查是否需要恢复书籍缓存")
        LogUtils.d(TAG, "selectedSet 内容: ${selectedSet.joinToString(", ")}")
        LogUtils.d(TAG, "bookCacheFolderName: $bookCacheFolderName, 是否在 selectedSet: ${bookCacheFolderName in selectedSet}")
        LogUtils.d(TAG, "bookCacheIndexFileName: $bookCacheIndexFileName, 是否在 selectedSet: ${bookCacheIndexFileName in selectedSet}")
        LogUtils.d(TAG, "bookCacheBooksFileName: $bookCacheBooksFileName, 是否在 selectedSet: ${bookCacheBooksFileName in selectedSet}")
        LogUtils.d(TAG, "bookChapterCache.json: bookChapterCache.json, 是否在 selectedSet: ${"bookChapterCache.json" in selectedSet}")
        
        if (
            bookCacheFolderName in selectedSet ||
            bookCacheIndexFileName in selectedSet ||
            bookCacheBooksFileName in selectedSet ||
            "bookChapterCache.json" in selectedSet
        ) {
            LogUtils.d(TAG, "满足书籍缓存恢复条件，开始恢复")
            progress(bookCacheFolderName)
            restoreBookCache(path)
        } else {
            LogUtils.d(TAG, "不满足书籍缓存恢复条件，跳过")
        }

        progress("applyRestoreConfig")
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }

        appCtx.toastOnUi(R.string.restore_success)

        // 应用主题和图标变更
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    /**
     * 核心恢复逻辑
     * 
     * 执行步骤：
     * 1. 恢复数据库数据（书籍、书签、书源等）
     * 2. 恢复自定义配置文件（主题、阅读样式等）
     * 3. 恢复SharedPreferences配置
     * 4. 应用配置变更
     * 
     * @param path 备份文件解压后的目录路径
     */
    private suspend fun restore(
        path: String,
        onProgress: ((String) -> Unit)? = null
    ) {
        val aes = BackupAES()
        fun progress(fileName: String) {
            onProgress?.invoke(BackupInfoHelper.getDisplayName(fileName))
        }

        // 恢复书架数据
        progress("bookshelf.json")
        appDb.bookDao.deleteAll()
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            val books = it.filterNot { book -> ignoreLocalBook && book.isLocal }
            appDb.bookDao.insert(*books.toTypedArray())
        }

        // 恢复书签
        progress("bookmark.json")
        appDb.bookmarkDao.deleteAll()
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }

        // 恢复书籍分组
        progress("bookGroup.json")
        appDb.bookGroupDao.deleteAll()
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            appDb.bookGroupDao.insert(*it.toTypedArray())
        }

        // 恢复书源（兼容旧版本格式）
        progress("bookSource.json")
        appDb.bookSourceDao.deleteAll()
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }

        // 恢复RSS源
        progress("rssSources.json")
        appDb.rssSourceDao.deleteAll()
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }

        // 恢复RSS收藏
        progress("rssStar.json")
        appDb.rssStarDao.deleteAll()
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }

        // 恢复源订阅
        progress("sourceSub.json")
        appDb.ruleSubDao.deleteAll()
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            appDb.ruleSubDao.insert(*it.toTypedArray())
        }

        // 恢复搜索引擎规则
        progress("webSearchEngines.json")
        val enginesFile = File(path, "webSearchEngines.json")
        if (enginesFile.exists()) {
            try {
                val enginesJson = enginesFile.readText()
                val engines = GSON.fromJsonArray<SearchEngine>(enginesJson).getOrNull()
                if (engines != null) {
                    SearchEngineHelper.saveSearchEngines(appCtx, engines)
                }
            } catch (e: Exception) {
                AppLog.put("恢复搜索引擎规则出错\n${e.localizedMessage}", e)
            }
        }

        // 恢复首页数据
        progress("homepage.json")
        val homepageFile = File(path, "homepage.json")
        if (homepageFile.exists()) {
            val json = homepageFile.readText()
            val obj = GSON.fromJsonObject<Map<String, JsonElement>>(json).getOrNull()
            if (obj != null) {
                appDb.homepageModuleDao.deleteAll()
                // 同时清理方案D新表
                appDb.homepageModulePrefDao.deleteAll()
                appDb.homepageUserModuleDao.deleteAll()
                appDb.homepageCustomSetMemberDao.deleteAll()
                (obj["modules"] as? JsonArray)?.let { array ->
                    val modules = GSON.fromJsonArray<HomepageModule>(array.toString()).getOrNull()
                    modules?.let { appDb.homepageModuleDao.upsertAll(it) }
                }
                appDb.homepageCustomSetDao.deleteAll()
                (obj["customSets"] as? JsonArray)?.let { array ->
                    val sets = GSON.fromJsonArray<HomepageCustomSet>(array.toString()).getOrNull()
                    sets?.forEach { set -> appDb.homepageCustomSetDao.upsert(set) }
                }
                // 恢复方案D新表数据
                (obj["modulePrefs"] as? JsonArray)?.let { array ->
                    val prefs = GSON.fromJsonArray<io.legado.app.data.entities.HomepageModulePref>(array.toString()).getOrNull()
                    prefs?.let { appDb.homepageModulePrefDao.upsertAll(it) }
                }
                (obj["userModules"] as? JsonArray)?.let { array ->
                    val modules = GSON.fromJsonArray<io.legado.app.data.entities.HomepageUserModule>(array.toString()).getOrNull()
                    modules?.let { appDb.homepageUserModuleDao.upsertAll(it) }
                }
                (obj["customSetMembers"] as? JsonArray)?.let { array ->
                    val members = GSON.fromJsonArray<io.legado.app.data.entities.HomepageCustomSetMember>(array.toString()).getOrNull()
                    members?.let { appDb.homepageCustomSetMemberDao.upsertAll(it) }
                }
            }
        }

        // 恢复替换规则
        progress("replaceRule.json")
        appDb.replaceRuleDao.deleteAll()
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }

        // 恢复搜索历史
        progress(HighlightRuleStore.backupFileName)
        File(path, HighlightRuleStore.backupFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<HighlightRuleStore.BackupData>(readText()).getOrNull()?.let {
                HighlightRuleStore.restoreBackupData(appCtx, it, path)
            }
        }?.onFailure {
            AppLog.put("恢复高亮规则出错\n${it.localizedMessage}", it)
        }
        progress("searchHistory.json")
        appDb.searchKeywordDao.deleteAll()
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }

        // 恢复TXT目录规则
        progress("txtTocRule.json")
        appDb.txtTocRuleDao.deleteAll()
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }

        // 恢复HTTP TTS配置
        progress("httpTTS.json")
        appDb.httpTTSDao.deleteAll()
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            appDb.httpTTSDao.insert(*it.toTypedArray())
        }

        // 恢复词典规则
        progress("dictRule.json")
        appDb.dictRuleDao.deleteAll()
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }

        // 恢复键盘辅助（先删除再插入，保证与备份数据一致）
        progress("keyboardAssists.json")
        appDb.keyboardAssistsDao.deleteAll()
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }

        progress(CoverGalleryRepository.backupDirName)
        restoreCoverGallery(path)

        // 恢复阅读记录（先清空再导入）
        progress("readRecord.json")
        val readRecords = fileToListT<ReadRecord>(path, "readRecord.json").orEmpty()
        val readRecordDetails = fileToListT<ReadRecordDetail>(path, "readRecordDetail.json").orEmpty()
        val readRecordSessions = fileToListT<ReadRecordSession>(path, "readRecordSession.json").orEmpty()
        if (readRecords.isNotEmpty() || readRecordDetails.isNotEmpty() || readRecordSessions.isNotEmpty()) {
            // 预加载书名→作者映射，避免 repairRecords 中逐条查询 bookDao
            val bookAuthorMap = appDb.bookDao.all
                .mapNotNull { book -> book.author.trim().ifBlank { null }?.let { book.name to it } }
                .toMap()
            // 整个清空+导入+修复在一个事务中执行，避免 N 次事务提交开销
            appDb.withTransaction {
                appDb.readRecordDao.clear()
                appDb.readRecordDao.clearDetails()
                appDb.readRecordDao.clearSessions()
                ReadRecordRepository(appDb.readRecordDao).apply {
                    importRecords(
                        readRecords,
                        readRecordDetails,
                        readRecordSessions
                    )
                    repairRecords { bookName ->
                        bookAuthorMap[bookName]
                    }
                }
            }
            appCtx.putPrefInt(
                PreferKey.readRecordRepairVersion,
                ReadRecordRepository.CURRENT_REPAIR_VERSION
            )
        }

        // 恢复服务器配置（需要解密）
        progress("servers.json")
        appDb.serverDao.deleteAll()
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrNull()?.let {
                appDb.serverDao.insert(*it.toTypedArray())
            }
        }?.onFailure {
            AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
        }

        // 恢复直链上传配置
        progress(DirectLinkUpload.ruleFileName)
        DirectLinkUpload.delConfig()
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }

        // 恢复主题配置
        progress(ThemeConfig.configFileName)
        ThemeConfig.replaceConfigs(emptyList())
        File(path, ThemeConfig.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val configs = GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrNull()
            FileUtils.delete(ThemeConfig.configFilePath)
            copyTo(File(ThemeConfig.configFilePath))
            ThemeConfig.replaceConfigs(configs)
        }?.onFailure {
            AppLog.put("恢复主题出错\n${it.localizedMessage}", it)
        }

        // 恢复封面规则配置
        progress(BookCover.configFileName)
        BookCover.delCoverRule()
        File(path, BookCover.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
            // 清除封面缓存，确保使用新配置生成封面
            CoverImageView.clearAllCache()
        }?.onFailure {
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }

        // 恢复阅读界面配置（可配置忽略）
        if (!BackupConfig.ignoreReadConfig) {
            progress("backgroundImages")
            restoreReadConfigBackgrounds(path)
            //恢复阅读界面配置
            progress(ReadBookConfig.configFileName)
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            progress(ReadBookConfig.shareConfigFileName)
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }

        // 恢复主题背景图片
        fixReadConfigBackgroundPaths()

        // 恢复SharedPreferences配置（应用主配置）
        progress("config.xml")
        // 旧备份没有 highlightRule.json，需要允许 config.xml 恢复高亮规则键
        val allowHighlightKeys = !File(path, HighlightRuleStore.backupFileName).exists()
        readBackupPrefs(path, "config")?.let { map ->
            clearThemeRestorePrefs()
            val edit = appCtx.defaultSharedPreferences.edit()

            map.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key, allowHighlightKeys) || key in themeRestorePrefKeys) {
                    when (key) {
                        // WebDav密码需要解密
                        PreferKey.webDavPassword -> {
                            kotlin.runCatching {
                                aes.decryptStr(value.toString())
                            }.getOrNull()?.let {
                                edit.putString(key, it)
                            } ?: let {
                                // 解密失败时，如果本地密码为空则使用备份中的值
                                if (appCtx.getPrefString(PreferKey.webDavPassword)
                                        .isNullOrBlank()
                                ) {
                                    edit.putString(key, value.toString())
                                }
                            }
                        }

                        else -> when (value) {
                            is Int -> edit.putInt(key, value)
                            is Boolean -> edit.putBoolean(key, value)
                            is Long -> edit.putLong(key, value)
                            is Float -> edit.putFloat(key, value)
                            is String -> edit.putString(key, value)
                        }
                    }
                }
            }
            edit.apply()
        }
        // 如果从 config.xml 恢复了高亮规则，清除缓存以强制重新加载
        if (allowHighlightKeys) {
            HighlightRuleStore.clearCache()
        }

        // 修正主题背景图片路径
        progress("themeBackgroundImages")
        restoreThemeBackgrounds(path, clearExisting = true)
        progress(runtimeSourceCacheFileName)
        restoreRuntimeSourceCaches(path)
        progress(bookCacheFolderName)
        restoreBookCache(path)
        fixThemeBackgroundPaths()
        fixThemeConfigBackgroundPaths()

        // 恢复视频播放配置
        progress("videoConfig.xml")
        readBackupPrefs(path, "videoConfig")?.let { map ->
            appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                clear()
                map.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
                apply()
            }
        }

        // 应用阅读配置
        progress("applyRestoreConfig")
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }

        appCtx.toastOnUi(R.string.restore_success)

        // 应用主题和图标变更
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    /**
     * 从JSON文件读取列表数据
     * 
     * @param T 数据类型
     * @param path 备份目录路径
     * @param fileName JSON文件名
     * @return 解析后的列表，文件不存在或解析失败返回null
     */
    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("$fileName\n读取文件出错\n${e.localizedMessage}")
        }
        return null
    }

    private fun restoreRuntimeSourceCaches(path: String) {
        val runtimeCacheFile = File(path, runtimeSourceCacheFileName)
        if (!runtimeCacheFile.exists()) return
        val caches = fileToListT<Cache>(path, runtimeSourceCacheFileName).orEmpty()
        appDb.cacheDao.deleteAllRuntimeSourceCaches()
        AppCacheManager.clearSourceVariables()
        if (caches.isNotEmpty()) {
            appDb.cacheDao.insert(*caches.toTypedArray())
        }
    }

    private suspend fun restoreCoverGallery(path: String) {
        val galleryDir = File(path, CoverGalleryRepository.backupDirName)
        if (!galleryDir.exists() || !galleryDir.isDirectory) return
        val oldGroupIds = appDb.coverGalleryDao.allGroups.map { it.id }

        appDb.coverGalleryDao.deleteAllImages()
        appDb.coverGalleryDao.deleteAllGroups()

        appDb.cacheDao.deleteRuntimeSourceCachesByPrefix(CoverGalleryRepository.randomSeedKeyPrefix)
        oldGroupIds.forEach {
            CacheManager.deleteMemory(CoverGalleryRepository.randomSeedKeyPrefix + it)
        }

        val targetDir = appCtx.externalFiles.getFile("covers").createFolderIfNotExist()
        val usedImageNames = hashSetOf<String>()
        galleryDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEachIndexed { groupIndex, groupDir ->
                val groupId = appDb.coverGalleryDao.insertGroup(
                    CoverGalleryGroup(
                        name = groupDir.name,
                        order = groupIndex
                    )
                )
                val images = groupDir.listFiles()
                    ?.filter { it.isFile && it.isCoverGalleryImageFile() }
                    ?.sortedBy { it.name }
                    ?.mapIndexed { imageIndex, imageFile ->
                        val targetFile = File(
                            targetDir,
                            uniqueCoverGalleryImageName(imageFile.name, usedImageNames)
                        )
                        imageFile.copyTo(targetFile, overwrite = true)
                        CoverGalleryImage(
                            groupId = groupId,
                            path = targetFile.absolutePath,
                            order = imageIndex
                        )
                    }
                    .orEmpty()
                if (images.isNotEmpty()) {
                    appDb.coverGalleryDao.insertImages(*images.toTypedArray())
                }
            }

        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    private fun File.isCoverGalleryImageFile(): Boolean {
        return extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }

    private fun uniqueCoverGalleryImageName(
        fileName: String,
        usedImageNames: MutableSet<String>
    ): String {
        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = fileName
        var suffix = 2
        while (!usedImageNames.add(candidate)) {
            candidate = if (extension.isBlank()) {
                "$nameWithoutExtension-$suffix"
            } else {
                "$nameWithoutExtension-$suffix.$extension"
            }
            suffix++
        }
        return candidate
    }

    private fun readBackupPrefs(path: String, fileName: String): Map<String, Any>? {
        val file = File(path, "$fileName.xml")
        if (!file.exists()) return null
        return runCatching {
            val map = linkedMapOf<String, Any>()
            file.inputStream().use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "utf-8")
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        val name = parser.getAttributeValue(null, "name")
                        if (!name.isNullOrBlank()) {
                            when (parser.name) {
                                "string" -> map[name] = parser.nextText()
                                "int" -> parser.getAttributeValue(null, "value")?.toIntOrNull()
                                    ?.let { map[name] = it }
                                "long" -> parser.getAttributeValue(null, "value")?.toLongOrNull()
                                    ?.let { map[name] = it }
                                "float" -> parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                    ?.let { map[name] = it }
                                "boolean" -> parser.getAttributeValue(null, "value")?.toBooleanStrictOrNull()
                                    ?.let { map[name] = it }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            map
        }.onFailure {
            AppLog.put("$fileName.xml\n读取配置出错\n${it.localizedMessage}", it)
        }.getOrNull()
    }

    private fun restoreReadConfigBackgrounds(path: String) {
        val bgNames = linkedSetOf<String>()
        File(path, ReadBookConfig.configFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonArray<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.forEach { config ->
            collectBgNames(config, bgNames)
        }
        File(path, ReadBookConfig.shareConfigFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonObject<ReadBookConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.let { config ->
            collectBgNames(config, bgNames)
        }
        clearReadConfigBackgrounds()
        if (bgNames.isEmpty()) return
        val bgDir = appCtx.externalFiles.getFile("bg")
        if (!bgDir.exists()) {
            bgDir.mkdirs()
        }
        bgNames.forEach { bgName ->
            val backupFile = File(path, "bg${File.separator}$bgName")
                .takeIf { it.exists() && it.isFile }
                ?: File(path, bgName).takeIf { it.exists() && it.isFile }
            backupFile?.copyTo(
                File(bgDir, bgName),
                overwrite = true
            )
        }
    }

    private fun collectBgNames(
        config: ReadBookConfig.Config,
        bgNames: MutableSet<String>
    ) {
        if (config.bgType == 2) {
            bgNames.add(File(config.bgStr).name)
        }
        if (config.bgTypeNight == 2) {
            bgNames.add(File(config.bgStrNight).name)
        }
        if (config.bgTypeEInk == 2) {
            bgNames.add(File(config.bgStrEInk).name)
        }
    }

    private fun clearReadConfigBackgrounds() {
        val bgDir = appCtx.externalFiles.getFile("bg")
        FileUtils.delete(bgDir)
        bgDir.mkdirs()
    }

    private fun clearThemeBackgrounds() {
        listOf(PreferKey.bgImage, PreferKey.bgImageN).forEach { prefKey ->
            val bgDir = appCtx.externalFiles.getFile(prefKey)
            FileUtils.delete(bgDir)
            bgDir.mkdirs()
        }
    }

    private fun fixReadConfigBackgroundPaths() {
        var updated = false
        ReadBookConfig.configList.forEach { config ->
            if (fixReadConfigBackgroundPath(config)) {
                updated = true
            }
        }
        runCatching { ReadBookConfig.shareConfig }.getOrNull()?.let { shareConfig ->
            if (fixReadConfigBackgroundPath(shareConfig)) {
                updated = true
            }
        }
        if (updated) {
            ReadBookConfig.save()
        }
    }

    private fun fixReadConfigBackgroundPath(config: ReadBookConfig.Config): Boolean {
        var updated = false
        if (config.bgType == 2) {
            val fixedPath = fixReadBgPath(config.bgStr)
            if (fixedPath != config.bgStr) {
                config.bgStr = fixedPath
                updated = true
            }
        }
        if (config.bgTypeNight == 2) {
            val fixedPath = fixReadBgPath(config.bgStrNight)
            if (fixedPath != config.bgStrNight) {
                config.bgStrNight = fixedPath
                updated = true
            }
        }
        if (config.bgTypeEInk == 2) {
            val fixedPath = fixReadBgPath(config.bgStrEInk)
            if (fixedPath != config.bgStrEInk) {
                config.bgStrEInk = fixedPath
                updated = true
            }
        }
        return updated
    }

    private fun fixReadBgPath(bgPath: String): String {
        if (bgPath.isBlank()) return bgPath
        val bgName = File(bgPath).name
        val localFile = appCtx.externalFiles.getFile("bg", bgName)
        return if (localFile.exists()) {
            localFile.absolutePath
        } else {
            bgPath
        }
    }

    private fun restoreThemeBackgrounds(backupPath: String, clearExisting: Boolean) {
        if (clearExisting) {
            clearThemeBackgrounds()
        }
        // 从 config.xml 中读取主题背景图片路径
        val configPrefs = readBackupPrefs(backupPath, "config")
        
        // 恢复白天主题背景
        (configPrefs?.get(PreferKey.bgImage) as? String)?.let { bgPath ->
            restoreThemeBgFile(backupPath, bgPath, PreferKey.bgImage)
        }
        
        // 恢复夜间主题背景
        (configPrefs?.get(PreferKey.bgImageN) as? String)?.let { bgPath ->
            restoreThemeBgFile(backupPath, bgPath, PreferKey.bgImageN)
        }
        File(backupPath, ThemeConfig.configFileName).takeIf { it.exists() }?.runCatching {
            GSON.fromJsonArray<ThemeConfig.Config>(readText()).getOrThrow()
        }?.getOrNull()?.forEach { config ->
            val bgPath = config.backgroundImgPath ?: return@forEach
            val prefKey = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
            restoreThemeBgFile(backupPath, bgPath, prefKey)
        }
    }
    
    private fun restoreThemeBgFile(backupPath: String, bgPath: String, prefKey: String) {
        if (bgPath.isBlank()) return
        
        val bgFile = if (bgPath.startsWith("http")) {
            // 在线图片，文件名从 URL 计算
            val name = ThemeConfig.getUrlToFile(bgPath)
            appCtx.externalFiles.getFile(prefKey, name)
        } else if (bgPath.contains(File.separator)) {
            // 本地路径，提取文件名
            val name = File(bgPath).name
            appCtx.externalFiles.getFile(prefKey, name)
        } else {
            // 已经是文件名
            appCtx.externalFiles.getFile(prefKey, bgPath)
        }
        
        // 从备份目录复制文件
        val bgName = if (bgPath.startsWith("http")) {
            ThemeConfig.getUrlToFile(bgPath)
        } else {
            File(bgPath).name
        }
        val backupFile = File(backupPath, "$prefKey${File.separator}$bgName")
            .takeIf { it.exists() && it.isFile }
            ?: File(backupPath, bgName).takeIf { it.exists() && it.isFile }
        if (backupFile != null) {
            val targetDir = appCtx.externalFiles.getFile(prefKey)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            backupFile.copyTo(File(targetDir, bgName), overwrite = true)
            LogUtils.d(TAG, "恢复主题背景: $bgName -> ${bgFile.absolutePath}")
        }
    }

    private fun clearThemeRestorePrefs() {
        appCtx.defaultSharedPreferences.edit {
            themeRestorePrefKeys.forEach(::remove)
        }
    }

    private fun fixThemeBackgroundPaths() {
        // 修正白天主题背景路径
        appCtx.getPrefString(PreferKey.bgImage)?.let { bgPath ->
            val fixedPath = fixThemeBgPath(bgPath, PreferKey.bgImage)
            if (fixedPath != bgPath) {
                appCtx.putPrefString(PreferKey.bgImage, fixedPath)
                LogUtils.d(TAG, "修正白天主题背景路径: $bgPath -> $fixedPath")
            }
        }
        
        // 修正夜间主题背景路径
        appCtx.getPrefString(PreferKey.bgImageN)?.let { bgPath ->
            val fixedPath = fixThemeBgPath(bgPath, PreferKey.bgImageN)
            if (fixedPath != bgPath) {
                appCtx.putPrefString(PreferKey.bgImageN, fixedPath)
                LogUtils.d(TAG, "修正夜间主题背景路径: $bgPath -> $fixedPath")
            }
        }
    }

    private suspend fun fixThemeConfigBackgroundPaths() {
        var updated = false
        ThemeConfig.configList.forEachIndexed { index, config ->
            val bgPath = config.backgroundImgPath ?: return@forEachIndexed
            val prefKey = if (config.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
            val fixedPath = fixThemeBgPath(bgPath, prefKey)
            if (fixedPath != bgPath) {
                ThemeConfig.configList[index] = config.copy(backgroundImgPath = fixedPath)
                updated = true
                LogUtils.d(TAG, "修正主题配置背景路径: $bgPath -> $fixedPath")
            }
        }
        if (updated) {
            ThemeConfig.save()
        }
    }
    
    private fun fixThemeBgPath(bgPath: String, prefKey: String): String {
        if (bgPath.isBlank()) return bgPath
        // 在线图片路径不需要修正
        if (bgPath.startsWith("http")) return bgPath
        // 已经是文件名，不需要修正
        if (!bgPath.contains(File.separator)) return bgPath
        
        // 提取文件名，拼接新设备路径
        val bgName = File(bgPath).name
        val newFile = appCtx.externalFiles.getFile(prefKey, bgName)
        if (newFile.exists()) {
            return newFile.absolutePath
        }
        // 如果新路径不存在，返回文件名（ThemeConfig.getBgImage 会自动处理）
        return bgName
    }

    /**
     * 恢复书籍缓存
     * 
     * 流程：
     * 1. 恢复章节目录（如果有）
     * 2. 读取缓存索引文件
     * 3. 遍历索引，匹配当前设备上的书籍
     * 4. 获取当前书籍的章节列表
     * 5. 根据章节标题匹配，重命名章节文件
     * 6. 复制缓存文件到对应位置
     * 
     * 匹配策略：
     * 1. 优先按章节序号精确匹配
     * 2. 其次按章节标题匹配
     */
    private fun restoreBookCache(path: String) {
        LogUtils.d(TAG, "开始恢复书籍缓存，路径: $path")
        
        if (BackupConfig.ignoreBookCache) {
            LogUtils.d(TAG, "忽略书籍缓存恢复（配置项已禁用）")
            AppLog.put("书籍缓存恢复被忽略，请在恢复配置中启用")
            return
        }
        
        // 先恢复章节目录
        val indexFile = File(path, bookCacheIndexFileName)
        if (!indexFile.exists()) {
            LogUtils.d(TAG, "书籍缓存索引文件不存在: ${indexFile.absolutePath}")
            AppLog.put("书籍缓存索引文件不存在，无法恢复书籍缓存")
            
            // 尝试从 bookCacheBooks.json 直接恢复书籍信息
            val booksFile = File(path, bookCacheBooksFileName)
            if (booksFile.exists()) {
                LogUtils.d(TAG, "尝试从 bookCacheBooks.json 直接恢复书籍信息")
                try {
                    ensureDefaultBookGroups()
                    val books = fileToListT<Book>(path, bookCacheBooksFileName)
                        .orEmpty()
                        .mapNotNull { it.sanitizeForCacheRestore() }
                    
                    if (books.isNotEmpty()) {
                        LogUtils.d(TAG, "从 bookCacheBooks.json 读取到 ${books.size} 本书")
                        
                        val localBooks = appDb.bookDao.all
                        LogUtils.d(TAG, "当前数据库中有 ${localBooks.size} 本书")
                        
                        val missingBooks = books.filter { book ->
                            val exists = localBooks.any { it.bookUrl == book.bookUrl || it.name == book.name }
                            LogUtils.d(TAG, "书籍《${book.name}》${if (exists) "已存在" else "不存在"}")
                            !exists
                        }.map { book ->
                            book.copy(
                                group = 0,
                                type = book.type and BookType.notShelf.inv()
                            )
                        }
                        
                        if (missingBooks.isNotEmpty()) {
                            appDb.bookDao.insert(*missingBooks.toTypedArray())
                            LogUtils.d(TAG, "从 bookCacheBooks.json 恢复书籍: ${missingBooks.size}")
                            AppLog.put("从书籍缓存恢复 ${missingBooks.size} 本书到书架")
                            
                            // 发送书架刷新事件
                            postEvent(EventBus.BOOKSHELF_REFRESH, "")
                        } else {
                            LogUtils.d(TAG, "所有书籍已存在，无需恢复")
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.d(TAG, "从 bookCacheBooks.json 恢复失败: ${e.message}")
                    AppLog.put("从 bookCacheBooks.json 恢复失败\n${e.localizedMessage}", e)
                }
            }
            return
        }
        
        LogUtils.d(TAG, "找到书籍缓存索引文件: ${indexFile.absolutePath}, 大小: ${indexFile.length()}")
        
        val cacheIndexList = runCatching {
            val json = indexFile.readText()
            LogUtils.d(TAG, "索引文件内容长度: ${json.length}")
            parseBookCacheIndexList(json)
        }.getOrNull() ?: run {
            LogUtils.d(TAG, "解析书籍缓存索引失败")
            AppLog.put("书籍缓存索引文件解析失败")
            return
        }
        
        if (cacheIndexList.isEmpty()) {
            LogUtils.d(TAG, "书籍缓存索引为空")
            AppLog.put("书籍缓存索引为空")
            return
        }
        
        LogUtils.d(TAG, "解析到 ${cacheIndexList.size} 个书籍缓存索引")
        cacheIndexList.forEach { index ->
            LogUtils.d(TAG, "  - 《${index.bookName}》作者: ${index.author}, 目录: ${index.folderName}, 章节数: ${index.chapters.size}")
        }
        
        restoreBookCacheBooks(path, cacheIndexList)
        restoreBookChapterCache(path)

        val backupCacheDir = resolveBackupCacheDir(path, cacheIndexList)
        if (backupCacheDir == null) {
            LogUtils.d(TAG, "备份缓存目录不存在")
            return
        }
        
        val targetCacheDir = File(BookHelp.cachePath)
        if (!targetCacheDir.exists()) {
            targetCacheDir.mkdirs()
        }
        
        val allBooks = appDb.bookDao.all
        var restoredCount = 0
        var chapterRestoredCount = 0
        
        cacheIndexList.forEach { cacheIndex ->
            val matchedBook = findMatchingBook(cacheIndex, allBooks)
            if (matchedBook == null) {
                LogUtils.d(TAG, "未找到匹配书籍: ${cacheIndex.bookName}")
                return@forEach
            }
            
            val sourceCacheDir = File(backupCacheDir, cacheIndex.folderName)
            if (!sourceCacheDir.exists()) {
                LogUtils.d(TAG, "备份缓存目录不存在: ${cacheIndex.folderName}")
                return@forEach
            }
            
            val targetFolderName = matchedBook.getFolderName()
            val targetBookDir = File(targetCacheDir, targetFolderName)
            if (!targetBookDir.exists()) {
                targetBookDir.mkdirs()
            }
            
            // 获取当前书籍的章节列表
            val currentChapters = appDb.bookChapterDao.getChapterList(matchedBook.bookUrl)
            val currentChapterByIndex = currentChapters.associateBy { it.index }
            val currentChapterByTitle = currentChapters.associateBy { it.title }
            
            // 恢复章节文件，根据需要重命名
            val copiedSourceNames = hashSetOf<String>()
            cacheIndex.chapters.forEach { chapterInfo ->
                val sourceFile = File(sourceCacheDir, chapterInfo.fileName)
                if (!sourceFile.exists()) {
                    return@forEach
                }
                
                // 查找匹配的当前章节
                val targetChapter = currentChapterByIndex[chapterInfo.index]
                    ?: currentChapterByTitle[chapterInfo.title]
                
                if (targetChapter == null) {
                    LogUtils.d(TAG, "未找到匹配章节: ${chapterInfo.title}")
                    return@forEach
                }
                
                // 计算目标文件名
                val targetFileName = targetChapter.getFileName()
                val targetFile = File(targetBookDir, targetFileName)
                
                // 复制文件（如果文件名不同则重命名）
                sourceFile.copyTo(targetFile, overwrite = true)
                copiedSourceNames.add(sourceFile.name)
                chapterRestoredCount++
            }
            sourceCacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".nb") && it.name !in copiedSourceNames }
                ?.forEach { sourceFile ->
                    sourceFile.copyTo(File(targetBookDir, sourceFile.name), overwrite = true)
                    chapterRestoredCount++
                }
            
            // 复制图片文件夹（如果有）
            val sourceImageDir = File(sourceCacheDir, "images")
            if (sourceImageDir.exists()) {
                val targetImageDir = File(targetBookDir, "images")
                sourceImageDir.copyRecursively(targetImageDir, overwrite = true)
            }
            
            restoredCount++
            LogUtils.d(TAG, "恢复书籍缓存: ${matchedBook.name} -> $targetFolderName")
        }
        
        LogUtils.d(TAG, "书籍缓存恢复完成，共恢复 $restoredCount 本书，$chapterRestoredCount 个章节")
    }
    
    /**
     * 恢复章节目录
     * 从 bookChapterCache.json 恢复章节目录数据
     * 
     * @param path 备份文件解压后的目录路径
     */
    private fun restoreBookCacheBooks(path: String, cacheIndexList: List<BookCacheIndex>) {
        LogUtils.d(TAG, "开始恢复书籍缓存书架信息")
        
        ensureDefaultBookGroups()
        LogUtils.d(TAG, "已确保默认书籍分组存在")
        
        val backupBooks = fileToListT<Book>(path, bookCacheBooksFileName)
            .orEmpty()
            .mapNotNull { it.sanitizeForCacheRestore() }
        
        LogUtils.d(TAG, "从 $bookCacheBooksFileName 读取到 ${backupBooks.size} 本书")
        
        val books = backupBooks.ifEmpty {
            LogUtils.d(TAG, "使用缓存索引生成最小书籍记录")
            cacheIndexList.map {
                Book(
                    bookUrl = it.bookUrl,
                    name = it.bookName,
                    author = it.author,
                    originName = it.bookName
                )
            }
        }
        
        if (books.isEmpty()) {
            LogUtils.d(TAG, "没有需要恢复的书籍")
            return
        }

        val localBooks = appDb.bookDao.all
        LogUtils.d(TAG, "当前数据库中有 ${localBooks.size} 本书")
        
        val missingBooks = books
            .filter { book ->
                val matched = findMatchingBook(
                    BookCacheIndex(
                        bookUrl = book.bookUrl,
                        bookName = book.name,
                        author = book.author,
                        folderName = book.getFolderName()
                    ),
                    localBooks
                )
                val exists = matched != null
                LogUtils.d(TAG, "书籍《${book.name}》${if (exists) "已存在 (匹配: ${matched?.name})" else "不存在，将恢复"}")
                !exists
            }
            .map { book ->
                book.copy(
                    group = 0,
                    type = book.type and BookType.notShelf.inv()
                )
            }
        
        if (missingBooks.isNotEmpty()) {
            LogUtils.d(TAG, "准备插入 ${missingBooks.size} 本缺失书籍")
            missingBooks.forEach { book ->
                LogUtils.d(TAG, "  - 《${book.name}》作者: ${book.author}, bookUrl: ${book.bookUrl}, type: ${book.type}, group: ${book.group}")
            }
            
            appDb.bookDao.insert(*missingBooks.toTypedArray())
            LogUtils.d(TAG, "恢复书籍缓存书架信息: ${missingBooks.size}")
            AppLog.put("从书籍缓存恢复 ${missingBooks.size} 本书到书架")
            
            // 发送书架刷新事件
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        } else {
            LogUtils.d(TAG, "所有书籍已存在，无需恢复")
        }
    }

    private fun ensureDefaultBookGroups() {
        val defaults = arrayOf(
            BookGroup(BookGroup.IdAll, appCtx.getString(R.string.all), order = -10, show = true),
            BookGroup(
                BookGroup.IdLocal,
                appCtx.getString(R.string.local),
                order = -9,
                enableRefresh = false,
                show = true
            ),
            BookGroup(BookGroup.IdAudio, appCtx.getString(R.string.audio), order = -8, show = true),
            BookGroup(
                BookGroup.IdNetNone,
                appCtx.getString(R.string.net_no_group),
                order = -7,
                show = true
            ),
            BookGroup(
                BookGroup.IdLocalNone,
                appCtx.getString(R.string.local_no_group),
                order = -6,
                show = false
            ),
            BookGroup(BookGroup.IdVideo, appCtx.getString(R.string.video), order = -5, show = true),
            BookGroup(
                BookGroup.IdError,
                appCtx.getString(R.string.update_book_fail),
                order = -1,
                show = true
            )
        ).filter { appDb.bookGroupDao.getByID(it.groupId) == null }

        if (defaults.isNotEmpty()) {
            appDb.bookGroupDao.insert(*defaults.toTypedArray())
        }
    }

    private fun resolveBackupCacheDir(path: String, cacheIndexList: List<BookCacheIndex>): File? {
        val cacheDir = File(path, bookCacheFolderName)
        if (cacheDir.exists()) {
            return cacheDir
        }
        return File(path).takeIf { rootDir ->
            cacheIndexList.any { File(rootDir, it.folderName).exists() }
        }
    }

    private fun restoreBookChapterCache(path: String) {
        val chapterFile = File(path, "bookChapterCache.json")
        if (!chapterFile.exists()) {
            LogUtils.d(TAG, "章节目录文件不存在")
            return
        }
        
        val chapters = fileToListT<BookChapter>(path, "bookChapterCache.json")
        if (chapters.isNullOrEmpty()) {
            LogUtils.d(TAG, "章节目录为空")
            return
        }
        
        // 按 bookUrl 分组
        val chaptersByBook = chapters.groupBy { it.bookUrl }
        var restoredBookCount = 0
        var restoredChapterCount = 0
        
        chaptersByBook.forEach { (bookUrl, chapterList) ->
            // 检查书籍是否存在
            val book = appDb.bookDao.getBook(bookUrl)
            if (book == null) {
                // 尝试通过缓存索引中的书名匹配
                val cacheIndexFile = File(path, bookCacheIndexFileName)
                if (cacheIndexFile.exists()) {
                    val cacheIndexList = runCatching {
                        parseBookCacheIndexList(cacheIndexFile.readText())
                    }.getOrNull()
                    
                    val cacheIndex = cacheIndexList?.find { it.bookUrl == bookUrl }
                    if (cacheIndex != null) {
                        val matchedBook = appDb.bookDao.all.find { it.name == cacheIndex.bookName }
                        if (matchedBook != null) {
                            // 更新章节的 bookUrl
                            val updatedChapters = chapterList.map { chapter ->
                                chapter.copy(bookUrl = matchedBook.bookUrl)
                            }
                            // 删除旧的章节，插入新的
                            appDb.bookChapterDao.delByBook(matchedBook.bookUrl)
                            appDb.bookChapterDao.insert(*updatedChapters.toTypedArray())
                            restoredBookCount++
                            restoredChapterCount += updatedChapters.size
                            LogUtils.d(TAG, "恢复章节目录: ${matchedBook.name}, ${updatedChapters.size} 章")
                        }
                    }
                }
            } else {
                // 书籍存在，直接恢复章节
                appDb.bookChapterDao.delByBook(bookUrl)
                appDb.bookChapterDao.insert(*chapterList.toTypedArray())
                restoredBookCount++
                restoredChapterCount += chapterList.size
                LogUtils.d(TAG, "恢复章节目录: ${book.name}, ${chapterList.size} 章")
            }
        }
        
        LogUtils.d(TAG, "章节目录恢复完成，共 $restoredBookCount 本书，$restoredChapterCount 章")
    }
    
    /**
     * 查找匹配的书籍
     * 
     * @param cacheIndex 缓存索引信息
     * @param allBooks 所有书籍列表
     * @return 匹配的书籍，未找到返回null
     */
    private fun findMatchingBook(
        cacheIndex: BookCacheIndex,
        allBooks: List<Book>
    ): Book? {
        // 优先按 bookUrl 精确匹配
        allBooks.find { it.bookUrl == cacheIndex.bookUrl }?.let { return it }
        
        // 其次按 书名+作者 匹配
        val normalizedAuthor = cacheIndex.author.trim()
        allBooks.filter { 
            it.name == cacheIndex.bookName && 
            (it.author?.trim() ?: "") == normalizedAuthor 
        }.firstOrNull()?.let { return it }
        
        // 最后按书名模糊匹配（作者可能为空或不一致）
        allBooks.filter { it.name == cacheIndex.bookName }.firstOrNull()?.let { return it }
        
        return null
    }

    private fun parseBookCacheIndexList(json: String): List<BookCacheIndex>? {
        return runCatching {
            val root = JsonParser.parseString(json)
            if (!root.isJsonArray) {
                return@runCatching null
            }
            root.asJsonArray.mapNotNull { element ->
                val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val bookUrl = obj.stringOrBlank("bookUrl")
                val bookName = obj.stringOrBlank("bookName")
                val folderName = obj.stringOrBlank("folderName")
                if (folderName.isBlank() || (bookUrl.isBlank() && bookName.isBlank())) {
                    return@mapNotNull null
                }
                BookCacheIndex(
                    bookUrl = bookUrl,
                    bookName = bookName,
                    author = obj.stringOrBlank("author"),
                    folderName = folderName,
                    chapters = obj.arrayOrEmpty("chapters").mapNotNull { chapterElement ->
                        val chapter = chapterElement.asJsonObjectOrNull() ?: return@mapNotNull null
                        val fileName = chapter.stringOrBlank("fileName")
                        if (fileName.isBlank()) {
                            return@mapNotNull null
                        }
                        ChapterCacheInfo(
                            index = chapter.intOrZero("index"),
                            title = chapter.stringOrBlank("title"),
                            titleMD5 = chapter.stringOrBlank("titleMD5"),
                            fileName = fileName
                        )
                    }
                )
            }.sanitizeBookCacheIndexes()
        }.onFailure {
            AppLog.put("$bookCacheIndexFileName\n读取解析出错\n${it.localizedMessage}", it)
        }.getOrNull()
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.stringOrBlank(name: String): String {
        val element = get(name) ?: return ""
        return runCatching {
            if (element.isJsonNull) "" else element.asString ?: ""
        }.getOrDefault("")
    }

    private fun JsonObject.intOrZero(name: String): Int {
        val element = get(name) ?: return 0
        return runCatching {
            if (element.isJsonNull) 0 else element.asInt
        }.getOrDefault(0)
    }

    private fun JsonObject.arrayOrEmpty(name: String): List<JsonElement> {
        val element = get(name) ?: return emptyList()
        return if (element.isJsonArray) element.asJsonArray.toList() else emptyList()
    }

    private fun List<BookCacheIndex>.sanitizeBookCacheIndexes(): List<BookCacheIndex> {
        LogUtils.d(TAG, "开始清理书籍缓存索引，原始数量: ${this.size}")
        
        return mapNotNull { cacheIndex ->
            @Suppress("USELESS_CAST")
            val bookUrl = (cacheIndex.bookUrl as String?) ?: ""
            @Suppress("USELESS_CAST")
            val bookName = (cacheIndex.bookName as String?) ?: ""
            @Suppress("USELESS_CAST")
            val folderName = (cacheIndex.folderName as String?) ?: ""
            
            LogUtils.d(TAG, "处理索引: bookUrl='$bookUrl', bookName='$bookName', folderName='$folderName'")
            
            if (folderName.isBlank() || (bookUrl.isBlank() && bookName.isBlank())) {
                LogUtils.d(TAG, "跳过无效书籍缓存索引: bookUrl=$bookUrl, bookName=$bookName, folderName=$folderName")
                return@mapNotNull null
            }
            @Suppress("USELESS_CAST")
            val chapters = (cacheIndex.chapters as List<ChapterCacheInfo>?)
                .orEmpty()
                .mapNotNull { chapterInfo ->
                    @Suppress("USELESS_CAST")
                    val fileName = (chapterInfo.fileName as String?) ?: ""
                    if (fileName.isBlank()) {
                        return@mapNotNull null
                    }
                    @Suppress("USELESS_CAST")
                    val title = (chapterInfo.title as String?) ?: ""
                    @Suppress("USELESS_CAST")
                    val titleMD5 = (chapterInfo.titleMD5 as String?) ?: ""
                    chapterInfo.copy(
                        title = title,
                        titleMD5 = titleMD5,
                        fileName = fileName
                    )
                }
            @Suppress("USELESS_CAST")
            cacheIndex.copy(
                bookUrl = bookUrl,
                bookName = bookName,
                author = (cacheIndex.author as String?) ?: "",
                folderName = folderName,
                chapters = chapters
            )
        }
    }

    private fun Book.sanitizeForCacheRestore(): Book? {
        @Suppress("USELESS_CAST")
        bookUrl = (bookUrl as String?) ?: ""
        @Suppress("USELESS_CAST")
        name = (name as String?) ?: ""
        @Suppress("USELESS_CAST")
        author = (author as String?) ?: ""
        @Suppress("USELESS_CAST")
        originName = (originName as String?) ?: name
        if (bookUrl.isBlank() && name.isBlank()) {
            LogUtils.d(TAG, "跳过无效缓存书籍信息")
            return null
        }
        return this
    }

}
