package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.edit
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener
import com.shuyu.gsyvideoplayer.utils.CommonUtil
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.SourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.model.BookCover
import io.legado.app.exception.ContentEmptyException
import io.legado.app.help.CacheManager
import io.legado.app.help.book.CacheManifestHelper
import io.legado.app.help.book.getDanmaku
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.help.exoplayer.mediaExtensionOfUrl
import io.legado.app.ui.video.player.ExoVideoManager
import io.legado.app.ui.video.player.ExoVideoManager.Companion.FULLSCREEN_ID
import io.legado.app.ui.video.player.FloatingPlayer
import io.legado.app.ui.video.player.VideoPlayer
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.rss.Rss
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.externalCache
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

object VideoPlay : CoroutineScope by MainScope() {
    private const val VIDEO_POS_NAME = "video_pos_" // 单链接播放进度
    private const val VIDEO_POS_SAVE_TIME = 60 * 60 * 24 * 20 // 20天
    private var needClearTemp = true // 需要清理缓存
    private const val VIDEO_TEMP_PATH = "video_temp"
    private val videoTempFile by lazy { File(FileUtils.getCachePath(), VIDEO_TEMP_PATH) }

    const val VIDEO_PREF_NAME = "video_config"

    private val videoPrefs: SharedPreferences by lazy { appCtx.getSharedPreferences(VIDEO_PREF_NAME, MODE_PRIVATE) }

    /**  是否自动播放  **/
    var autoPlay
        get() = videoPrefs.getBoolean("autoPlay", true)
        set(value) {
            videoPrefs.edit { putBoolean("autoPlay", value) }
        }

    /**  直接全屏，需先启用自动播放 **/
    var startFull
        get() = videoPrefs.getBoolean("startFull", false)
        set(value) {
            videoPrefs.edit { putBoolean("startFull", value) }
        }

    /**  长按倍速  **/
    var longPressSpeed
        get() = videoPrefs.getInt("longPressSpeed", 30)
        set(value) {
            videoPrefs.edit { putInt("longPressSpeed", value) }
        }

    /**  全屏底部进度条  **/
    var fullBottomProgressBar
        get() = videoPrefs.getBoolean("fullBottomProgressBar", true)
        set(value) {
            videoPrefs.edit { putBoolean("fullBottomProgressBar", value) }
        }

    /**  静音播放，默认不静音  **/
    var mutePlay
        get() = videoPrefs.getBoolean("mutePlay", false)
        set(value) {
            videoPrefs.edit { putBoolean("mutePlay", value) }
        }

    /**  边播放边缓存：开启后播放的数据会写进书籍视频缓存目录（看完即可离线），默认关闭  **/
    var playCacheEnabled
        get() = videoPrefs.getBoolean("playCacheEnabled", false)
        set(value) {
            videoPrefs.edit { putBoolean("playCacheEnabled", value) }
        }

    /**  双击快退/快进功能开关  **/
    var doubleTapSeekEnabled
        get() = videoPrefs.getBoolean("doubleTapSeekEnabled", true)
        set(value) {
            videoPrefs.edit { putBoolean("doubleTapSeekEnabled", value) }
        }

    /**  双击跳转秒数  **/
    var doubleTapSeekSeconds
        get() = videoPrefs.getInt("doubleTapSeekSeconds", 10)
        set(value) {
            videoPrefs.edit { putInt("doubleTapSeekSeconds", value) }
        }

    /**  快捷跳转按钮开关  **/
    var quickJumpButtonsEnabled
        get() = videoPrefs.getBoolean("quickJumpButtonsEnabled", false)
        set(value) {
            videoPrefs.edit { putBoolean("quickJumpButtonsEnabled", value) }
        }

    /**  快捷跳转a分钟数  **/
    var quickJumpMinutesA
        get() = videoPrefs.getInt("quickJumpMinutesA", 5)
        set(value) {
            videoPrefs.edit { putInt("quickJumpMinutesA", value) }
        }

    /**  快捷跳转b分钟数  **/
    var quickJumpMinutesB
        get() = videoPrefs.getInt("quickJumpMinutesB", 1)
        set(value) {
            videoPrefs.edit { putInt("quickJumpMinutesB", value) }
        }

    /**  左侧滑动调节亮度开关  **/
    var leftSlideBrightnessEnabled
        get() = videoPrefs.getBoolean("leftSlideBrightnessEnabled", true)
        set(value) {
            videoPrefs.edit { putBoolean("leftSlideBrightnessEnabled", value) }
        }

    /**  右侧滑动调节音量开关  **/
    var rightSlideVolumeEnabled
        get() = videoPrefs.getBoolean("rightSlideVolumeEnabled", true)
        set(value) {
            videoPrefs.edit { putBoolean("rightSlideVolumeEnabled", value) }
        }

    /**  跳过片头片尾开关  **/
    var skipIntroOutroEnabled
        get() = videoPrefs.getBoolean("skipIntroOutroEnabled", false)
        set(value) {
            videoPrefs.edit { putBoolean("skipIntroOutroEnabled", value) }
        }

    /**  跳过片头秒数  **/
    var skipIntroSeconds
        get() = videoPrefs.getInt("skipIntroSeconds", 30)
        set(value) {
            videoPrefs.edit { putInt("skipIntroSeconds", value) }
        }

    /**  跳过片尾秒数  **/
    var skipOutroSeconds
        get() = videoPrefs.getInt("skipOutroSeconds", 30)
        set(value) {
            videoPrefs.edit { putInt("skipOutroSeconds", value) }
        }

    /**  弹幕滚动速度  **/
    var danmakuSpeed = 1.2f

    /**  锁屏  **/
    var lockCurScreen = false

    /**  竖屏视频  **/
    var isPortraitVideo = false

    val videoManager by lazy { ExoVideoManager() }

    /**
     * 根据 URL 设置播放器的 overrideExtension，确保 ExoPlayer 能正确识别流媒体类型
     *
     * 当 URL 的 path 部分没有明确的文件扩展名（如 API 接口 URL），但实际返回的是 HLS(m3u8)/DASH(mpd) 流时，
     * ExoSourceManager 会通过 Util.inferContentType(Uri) 从 path 扩展名推断类型，导致识别失败，
     * 这里用 [ExoPlayerHelper.mediaExtensionOfUrl] 显式指定。
     *
     * 判定与缓存侧共用同一个口径：两侧对"这条地址是什么流"必须一致，否则会出现
     * "按普通文件缓存、按 HLS 播放"（缓存里只有播放列表、切片没下过）这种看不了的情况。
     */
    private fun applyOverrideExtension(player: GSYBaseVideoPlayer, url: String?) {
        if (url.isNullOrBlank()) return
        player.setOverrideExtension(mediaExtensionOfUrl(url))
    }
    private var isLoading = false
    private val loadScope = CoroutineScope(SupervisorJob() + IO)
    var videoUrl: String? = null // 播放链接
    var singleUrl = false
    var videoTitle: String? = null
    var source: BaseSource? = null
    var book: Book? = null
    var toc: List<BookChapter>? = null
    var chapter: BookChapter? = null
    var volumes = arrayListOf<BookChapter>()
    var episodes: List<BookChapter>? = null

    /**  在当前episodes中的位置  **/
    var chapterInVolumeIndex = 0

    /**  卷章节-> 线路或者季  **/
    var durVolumeIndex = 0

    /**  当前卷  **/
    var durVolume: BookChapter? = null

    /**  本集的进度  **/
    var durChapterPos = 0
    var inBookshelf = true

    /**  订阅收藏  **/
    var rssStar: RssStar? = null

    /**  订阅历史记录,收藏优先  **/
    var rssRecord: RssReadRecord? = null

    /**  弹幕相关  **/
    var danmakuFile: File? = null
    var danmakuStr: String? = null
    var danmakuShow = true

    /**
     * 标记阅读开始，开启阅读会话
     */
    fun markReadStart() {
        if (!AppConfig.enableReadRecord || book == null) return
        val book = book ?: return
        ReadSessionRecorder.onReadStart(
            deviceId = AppConst.androidId,
            bookName = book.name,
            bookAuthor = book.author,
            chapterTitle = book.durChapterTitle.orEmpty(),
        )
    }

    /**
     * 阅读暂停/退出：以当前章节名收口并落库当前会话
     */
    fun flushReadTime() {
        if (!AppConfig.enableReadRecord || book == null) return
        val book = book ?: return
        ReadSessionRecorder.onReadTick(
            deviceId = AppConst.androidId,
            bookName = book.name,
            bookAuthor = book.author,
            chapterTitle = book.durChapterTitle.orEmpty(),
        )
        ReadSessionRecorder.flush()
    }

    /**
     * 开始播放
     */
    fun startPlay(player: StandardGSYVideoPlayer) {
        if (source == null) return
        danmakuStr = null
        danmakuFile = null
        val player = player.getCurrentPlayer()
        if (singleUrl) {
            val mUrl = videoUrl ?: return
            Coroutine.async(loadScope, IO) {
                CacheManager.getLong(VIDEO_POS_NAME + mUrl)?.let {
                    player.seekOnStart = it
                }
                inBookshelf = true
                val analyzeUrl = AnalyzeUrl(
                    mUrl,
                    source = source,
                    ruleData = book,
                    chapter = null,
                )
                withContext(Main) {
                    player.mapHeadData = analyzeUrl.headerMap
                    val url = analyzeUrl.url
                    applyOverrideExtension(player, url)
                    player.setUp(url, false, File(appCtx.externalCache, "exoplayer"), videoTitle)
                    if (autoPlay) {
                        player.startPlayLogic()
                    }
                }
            }.onError {
                AppLog.put("加载视频链接失败", it, true)
            }
            isLoading = false
            return
        }
        durChapterPos.takeIf { it > 0 }?.toLong()?.let { player.seekOnStart = it }
        (source as? RssSource)?.let { s ->
            val rssArticle = rssStar?.toRssArticle() ?: rssRecord?.toRssArticle()
            if (rssArticle == null) {
                appCtx.toastOnUi("未找到订阅文章")
                return
            }
            val ruleContent = s.ruleContent
            if (ruleContent.isNullOrBlank()) {
                Coroutine.async(loadScope, IO) {
                    val mUrl = rssArticle.link
                    videoUrl = mUrl
                    val analyzeUrl = AnalyzeUrl(
                        mUrl,
                        source = source,
                        ruleData = rssArticle,
                    )
                    withContext(Main) {
                        player.mapHeadData = analyzeUrl.headerMap
                        applyOverrideExtension(player, analyzeUrl.url)
                        player.setUp(
                            analyzeUrl.url,
                            false,
                            File(appCtx.externalCache, "exoplayer"),
                            rssArticle.title,
                        )
                        if (autoPlay) {
                            player.startPlayLogic()
                        }
                    }
                }.onError {
                    AppLog.put("加载订阅源视频链接失败", it, true)
                }
            } else {
                Rss.getContent(loadScope, rssArticle, ruleContent, s)
                    .onSuccess(IO) { content ->
                        val content = content.trim()
                        val mUrl = if (content.isEmpty()) {
                            throw ContentEmptyException("正文为空")
                        } else if (content.startsWith("<")) { // 当作mpd文本
                            val name = MD5Utils.md5Encode(content) + ".mpd"
                            val file = FileUtils.createFileIfNotExist(videoTempFile, name)
                            file.writeText(content)
                            Uri.fromFile(file).toString()
                        } else {
                            NetworkUtils.getAbsoluteURL(rssArticle.link, content)
                        }
                        videoUrl = mUrl
                        val analyzeUrl = AnalyzeUrl(
                            mUrl,
                            source = source,
                            ruleData = rssArticle,
                        )
                        val playUrl = analyzeUrl.url
                        withContext(Main) {
                            player.mapHeadData = analyzeUrl.headerMap
                            applyOverrideExtension(player, playUrl)
                            player.setUp(playUrl, false, File(appCtx.externalCache, "exoplayer"), rssArticle.title)
                            if (autoPlay) {
                                player.startPlayLogic()
                            }
                        }
                    }.onError {
                        AppLog.put("加载订阅源为链接的正文失败", it, true)
                    }
            }
            isLoading = false
            return
        }
        val book = book
        if (book == null) {
            appCtx.toastOnUi("未找到书籍")
            return
        }
        chapter = if (episodes.isNullOrEmpty()) {
            // 没有卷目录，那么卷就是播放的章节（适合电影类，没有剧集，全是线路卷章节，如果全是章节没有卷的写法，播放完后会继续下一个线路重复播放）
            val durVolume = durVolume
            when {
                durVolume == null -> null
                durVolume.url.startsWith(durVolume.title) -> null // 卷章节没获取到链接（链接以标题开头）则返回null
                else -> durVolume
            }
        } else {
            // 优先获取当前索引的剧集，如果不存在则尝试获取第一个剧集
            episodes?.getOrNull(chapterInVolumeIndex) ?: run {
                chapterInVolumeIndex = 0
                episodes?.getOrNull(chapterInVolumeIndex)
            }
        }
        val chapter = chapter
        if (chapter == null) {
            appCtx.toastOnUi("未找到章节")
            return
        }
        val bookSource = source as BookSource
        // 已经离线缓存的章节直接播本地缓存，不再解析链接（链接可能已过期）；
        // 缓存按缓存当时的地址做 key，所以这里取"确实有缓存的地址"，章节表里的地址变了也能读到缓存；
        // 清单功能之前缓存的老书连地址都没留下，只有一章时按缓存内容反推一次
        val cachedUrl = CacheManifestHelper.cachedMediaUrl(book, chapter)
            ?: CacheManifestHelper.recoverLegacyMediaUrl(book)
        if (cachedUrl != null) {
            videoUrl = cachedUrl
            when (val danmaku = chapter.getDanmaku()) {
                is String -> danmakuStr = danmaku
                is File -> danmakuFile = danmaku
            }
            Coroutine.async(loadScope, IO) {
                // 请求头仍按书源规则生成：缓存不完整需要回源时，缺 Referer/Cookie 会被拒。
                // 取不到请求头不影响本地播放，失败就用空请求头
                val headers = runCatching {
                    AnalyzeUrl(
                        cachedUrl,
                        source = bookSource,
                        ruleData = book,
                        chapter = chapter,
                    ).headerMap
                }.getOrDefault(emptyMap())
                playVideo(player, book, cachedUrl, chapter, headers)
            }
            isLoading = false
            return
        }
        WebBook.getContent(loadScope, bookSource, book, chapter)
            .onSuccess(IO) { content ->
                val content = content.trim()
                val mUrl = if (content.isEmpty()) {
                    throw ContentEmptyException("正文为空")
                } else if (content.startsWith("<")) { // 当作mpd文本
                    val name = MD5Utils.md5Encode(content) + ".mpd"
                    val file = FileUtils.createFileIfNotExist(videoTempFile, name)
                    file.writeText(content)
                    Uri.fromFile(file).toString()
                } else {
                    content
                }
                videoUrl = mUrl
                val analyzeUrl = AnalyzeUrl(
                    mUrl,
                    source = source,
                    ruleData = book,
                    chapter = chapter,
                )
                when (val danmaku = chapter.getDanmaku()) {
                    is String -> danmakuStr = danmaku
                    is File -> danmakuFile = danmaku
                }
                val playUrl = analyzeUrl.url
                // 解析出的真实地址写回章节，离线缓存与缓存判定都依赖它。
                // 正文是 mpd 文本时 playUrl 是刚落盘的本地临时文件（file://，退出播放即被清理），
                // 它既不是媒体地址、也无法再解析出来，写进章节表会污染缓存判定与缓存清单，所以只写网络地址
                if (playUrl.isAbsUrl() && chapter.resourceUrl != playUrl) {
                    chapter.resourceUrl = playUrl
                    appDb.bookChapterDao.upResourceUrl(chapter.bookUrl, chapter.url, playUrl)
                }
                playVideo(player, book, playUrl, chapter, analyzeUrl.headerMap)
            }.onError {
                AppLog.put("获取资源链接出错\n$it", it, true)
            }
        isLoading = false
    }

    /**
     * 播放已解析出的媒体地址
     *
     * 缓存目录指向书级视频缓存目录：已离线缓存的章节直接读本地，
     * 播放过程中加载的数据也会写进同一个目录，与离线缓存共用一份缓存
     */
    private suspend fun playVideo(
        player: GSYBaseVideoPlayer,
        book: Book,
        playUrl: String,
        chapter: BookChapter,
        headers: Map<String, String>,
    ) = withContext(Main) {
        val cacheDir = ExoPlayerHelper.videoBookCacheDir(book)
        player.mapHeadData = headers.toMutableMap()
        applyOverrideExtension(player, playUrl)
        player.setUp(playUrl, false, cacheDir, chapter.title)
        if (autoPlay) {
            player.startPlayLogic()
        }
    }

    /**
     * 退出全屏，主要用于返回
     *
     * @return 返回是否全屏
     */
    fun backFromWindowFull(context: Context?): Boolean {
        var backFrom = false
        val vp =
            (CommonUtil.scanForActivity(context)).findViewById<View?>(Window.ID_ANDROID_CONTENT) as ViewGroup
        val oldF = vp.findViewById<View?>(FULLSCREEN_ID)
        if (oldF != null) {
            backFrom = true
            CommonUtil.hideNavKey(context)
            if (videoManager.lastListener() != null) {
                videoManager.lastListener().onBackFullscreen()
            }
        }
        return backFrom
    }

    /**
     * 停止当前播放（释放媒体播放器），但不重置状态
     * 用于新会话启动时清理旧媒体，防止 onResume 恢复旧视频
     */
    fun stopPlayback() {
        if (videoManager.listener() != null) {
            videoManager.listener().onCompletion()
        }
        videoManager.releaseMediaPlayer()
    }

    /**
     * 页面销毁了记得调用是否所有的video
     */
    fun releaseAllVideos() {
        flushReadTime()
        if (videoManager.listener() != null) {
            videoManager.listener().onCompletion()
        }
        videoManager.releaseMediaPlayer()
        if (!isLoading) {
            // 还原所有状态
            videoUrl = null
            singleUrl = false
            videoTitle = null
            source = null
            book = null
            toc = null
            chapter = null
            volumes.clear()
            episodes = null
            chapterInVolumeIndex = 0
            durVolumeIndex = 0
            durVolume = null
            durChapterPos = 0
            inBookshelf = true
            rssStar = null
            rssRecord = null
            danmakuStr = null
            danmakuFile = null
            lockCurScreen = false
            isPortraitVideo = false
            release()
            if (needClearTemp) {
                needClearTemp = false
                FileUtils.delete(videoTempFile)
            }
        }
    }

    /**
     * 暂停播放
     */
    fun onPause() {
        flushReadTime()
        if (videoManager.listener() != null) {
            videoManager.listener().onVideoPause()
        }
    }

    /**
     * 恢复播放
     */
    fun onResume() {
        markReadStart()
        if (videoManager.listener() != null) {
            videoManager.listener().onVideoResume()
        }
    }

    /**
     * 恢复暂停状态
     * @param seek 是否产生seek动作,直播设置为false
     */
    fun onResume(seek: Boolean) {
        if (videoManager.listener() != null) {
            videoManager.listener().onVideoResume(seek)
        }
    }

    // 播放器移除 - 辅助函数
    @SuppressLint("StaticFieldLeak")
    private var sSwitchVideo: StandardGSYVideoPlayer? = null
    private var sMediaPlayerListener: GSYMediaPlayerListener? = null
    fun savePlayState(switchVideo: StandardGSYVideoPlayer) {
        when (switchVideo) {
            is VideoPlayer -> sSwitchVideo = switchVideo.saveState()
            is FloatingPlayer -> sSwitchVideo = switchVideo.saveState()
        }
        sMediaPlayerListener = switchVideo
    }
    fun clonePlayState(switchVideo: StandardGSYVideoPlayer) {
        when (switchVideo) {
            is VideoPlayer -> sSwitchVideo?.let { switchVideo.cloneState(it) }
            is FloatingPlayer -> sSwitchVideo?.let { switchVideo.cloneState(it) }
        }
    }

    fun release() {
        sMediaPlayerListener?.onAutoCompletion()
        sMediaPlayerListener = null
        sSwitchVideo = null
    }

    fun stopLoading() {
        loadScope.coroutineContext.cancelChildren()
    }

    fun initSource(sourceKey: String?, sourceType: Int?, bookUrl: String?, record: String?): Boolean {
        isLoading = true
        // 重置可能残留的上一次会话状态，防止旧数据泄漏（当旧 Activity 未被销毁时尤为重要）
        rssStar = null
        rssRecord = null
        danmakuStr = null
        danmakuFile = null
        lockCurScreen = false
        isPortraitVideo = false
        chapter = null
        durVolume = null
        toc = null
        volumes.clear()
        episodes = null
        chapterInVolumeIndex = 0
        durVolumeIndex = 0
        durChapterPos = 0
        source = sourceKey?.let {
            when (sourceType) {
                SourceType.book -> appDb.bookSourceDao.getBookSource(it)
                SourceType.rss -> appDb.rssSourceDao.getByKey(it)
                else -> null
            }
        }
        book = bookUrl?.let {
            toc = appDb.bookChapterDao.getChapterList(it)
            volumes.clear()
            toc?.forEach { t ->
                if (t.isVolume) {
                    volumes.add(t)
                }
            }
            appDb.bookDao.getBook(it) ?: appDb.searchBookDao.getSearchBook(it)?.toBook()
        }?.also { b ->
            chapterInVolumeIndex = b.chapterInVolumeIndex
            durVolumeIndex = b.durVolumeIndex
            durChapterPos = b.durChapterPos
            source = appDb.bookSourceDao.getBookSource(b.origin)
            SourceCallBack.callBackBook(SourceCallBack.START_READ, source as BookSource?, b, chapter)
        }
        upEpisodes()
        if (source == null) {
            appCtx.toastOnUi("未找到源")
            return false
        }
        record?.let {
            // 订阅记录
            val sourceKey = sourceKey ?: return@let
            rssStar = appDb.rssStarDao.get(sourceKey, it)?.also { r ->
                durChapterPos = r.durPos
            }
            if (rssStar == null) {
                rssRecord = appDb.rssReadRecordDao.getRecord(it, sourceKey)?.also { r ->
                    durChapterPos = r.durPos
                }
            }
        }
        return true
    }

    fun upEpisodes() {
        val volumes = volumes
        if (volumes.isEmpty()) {
            durVolume = null
            episodes = toc
            return
        }
        val toc = toc ?: return
        durVolume = volumes.getOrNull(durVolumeIndex)
        if (durVolume == null) {
            durVolumeIndex = 0
            durVolume = volumes.getOrNull(durVolumeIndex)
        }
        val startInt = durVolume?.index ?: 0
        val endInt = volumes.getOrNull(durVolumeIndex + 1)?.index ?: toc.size
        episodes = toc.subList(startInt + 1, endInt)
    }

    fun upDurIndex(offset: Int, player: StandardGSYVideoPlayer): Boolean {
        val episodes = episodes ?: return false
        val index = chapterInVolumeIndex + offset
        if (index < 0) {
            appCtx.toastOnUi("已到开头")
            return false
        }
        if (index >= episodes.size) {
            appCtx.toastOnUi("已播放完")
            return false
        }
        chapterInVolumeIndex = index
        saveRead(0)
        startPlay(player)
        postEvent(EventBus.UP_VIDEO_INFO, arrayListOf(1)) // 更新选集视图
        return true
    }

    fun saveRead(durPos: Int? = null) {
        val book = book
        val rssStar = rssStar
        val rssRecord = rssRecord
        val durPos = durPos ?: videoManager.currentPosition.toInt()
        durChapterPos = durPos
        if (book == null && rssStar == null && rssRecord == null) {
            videoUrl?.let { videoUrl ->
                CacheManager.put(VIDEO_POS_NAME + videoUrl, durPos, VIDEO_POS_SAVE_TIME)
            }
            return
        }
        val durVolumeIndex = durVolumeIndex
        val chapterInVolumeIndex = chapterInVolumeIndex
        val source = source
        val volumes = volumes.toList()
        val durVolume = durVolume
        val toc = toc
        Coroutine.async {
            book?.let { book ->
                book.lastCheckCount = 0
                val durTime = System.currentTimeMillis()
                book.durChapterTime = durTime
                book.durVolumeIndex = durVolumeIndex
                book.chapterInVolumeIndex = chapterInVolumeIndex
                val durChapterIndex = if (volumes.isEmpty()) {
                    chapterInVolumeIndex
                } else {
                    (durVolume?.index ?: 0) + chapterInVolumeIndex + 1
                }
                book.durChapterIndex = durChapterIndex
                book.durChapterPos = durPos
                val chapter = toc?.getOrNull(durChapterIndex)
                videoTitle = chapter?.title
                book.durChapterTitle = chapter?.title
                SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, source as BookSource?, book, chapter, durTime.toString())
                book.update()
            }
            rssStar?.let {
                it.durPos = durPos
                videoTitle = it.title
                appDb.rssStarDao.update(it)
            }
            rssRecord?.let {
                it.durPos = durPos
                videoTitle = it.title
                appDb.rssReadRecordDao.update(it)
            }
            postEvent(EventBus.VIDEO_SUB_TITLE, videoTitle ?: appCtx.getString(R.string.data_loading))
        }
    }

    fun getDisplayCover(): String? = book?.let { BookCover.getDisplayCover(it) } ?: rssStar?.image ?: rssRecord?.image
}
