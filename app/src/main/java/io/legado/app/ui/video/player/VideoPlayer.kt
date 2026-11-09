package io.legado.app.ui.video.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shuyu.gsyvideoplayer.listener.LockClickListener
import com.shuyu.gsyvideoplayer.utils.CommonUtil
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer
import io.legado.app.R
import io.legado.app.model.VideoPlay
import io.legado.app.ui.video.config.SettingsDialog
import io.legado.app.utils.showDialogFragment
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.loader.IllegalDataException
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.ui.widget.DanmakuView
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoPlayer : StandardGSYVideoPlayer {
    companion object {
        /** 倍速面板档位：0.5X ~ 3.0X，面板内按从快到慢展示 */
        private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
        /** 最大倍速：面板档位的上限，也是选中值兜底夹取的上界 */
        private const val MAX_PLAY_SPEED = 3.0f
        /** 加载中播放键的透明度：可见但一眼能看出此刻不可点 */
        private const val START_BUTTON_DISABLED_ALPHA = 0.6f
        /** 一分钟的毫秒数：时钟按分钟对齐刷新 */
        private const val MINUTE_MS = 60_000L
        /** 进度类提示的停留时长：拖动/双击后要能看清两行数字，2.5 秒足够，又不会一直挡画面 */
        private const val TIP_HIDE_DELAY_MS = 2500L
    }

    constructor(context: Context?, fullFlag: Boolean?) : super(context, fullFlag) // 必须的,全屏时依靠这个构建知道获取全屏布局
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    private var episodeList: TextView? = null
    private var playbackSpeed: TextView? = null
    private var playSpeed: Float = 1.0f
    private var btnNext: ImageView? = null
    private var btnSettings: ImageView? = null
    private var tipView: TextView? = null

    /** 提示容器：整体淡入淡出，避免两行文字分别闪 */
    private var tipContainer: View? = null

    /** 提示第二行：本次调整的相对量（+10秒 / -1分） */
    private var tipDelta: TextView? = null

    /** 提示淡出任务：新提示到来时先取消，避免旧任务把新提示一起淡掉 */
    private val hideTipRunnable = Runnable {
        tipContainer?.apply {
            alpha = 0f
            visibility = INVISIBLE
        }
        tipView?.visibility = INVISIBLE
        tipDelta?.visibility = GONE
    }
    private var isChanging = false
    private var isLongPressSpeed = false

    private var mParser: BaseDanmakuParser? = null // 解析器对象
    private var mDanmakuView: DanmakuView? = null // 弹幕view
    private var mDanmakuContext: DanmakuContext? = null
    var mToggleDanmaku: TextView? = null // 弹幕开关
    private var mDanmakuStartSeekPosition: Long = -1

    /** 横屏右上角的当前时间 */
    private var tvClock: TextView? = null
    private var clockFormat: SimpleDateFormat? = null

    /**
     * 每分钟对齐刷新一次时钟，而不是每秒轮询：显示精度到分钟，
     * 每秒刷新纯属白耗电。延迟按"距离下一分钟还差多少"算，保证分钟跳变误差 < 1s。
     *
     * 注意不能写成字段初始化器、也不能用 by lazy：initView() 是在父类构造函数里
     * 调用的（GSYVideoView.<init> → init()），那时本类的字段初始化器与 lazy 委托都还没跑，
     * 引用会拿到 null，全屏切换复制播放器时就崩在这里。所以在 init() 收尾时手动创建。
     */
    private var clockTick: Runnable? = null

    // 音量调节相关
    private val mAudioManager: AudioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun getLayoutId(): Int = if (mIfCurrentIsFullscreen) {
        R.layout.video_layout_controller_full
    } else {
        R.layout.video_layout_controller
    }

    override fun getFullWindowPlayer(): VideoPlayer? {
        val activity = CommonUtil.scanForActivity(context) ?: return null
        val vp = activity.findViewById<View?>(Window.ID_ANDROID_CONTENT) as ViewGroup
        val full = vp.findViewById<View?>(fullId)
        var gsyVideoPlayer: VideoPlayer? = null
        if (full != null) {
            gsyVideoPlayer = full as VideoPlayer
        }
        return gsyVideoPlayer
    }
    override fun getSmallWindowPlayer(): VideoPlayer? = null

    override fun getCurrentPlayer(): VideoPlayer {
        val fullVideoPlayer = getFullWindowPlayer()
        if (fullVideoPlayer != null) {
            return fullVideoPlayer
        }
        val smallVideoPlayer = getSmallWindowPlayer()
        if (smallVideoPlayer != null) {
            return smallVideoPlayer
        }
        return this
    }

    fun getLockCurScreen() = mLockCurScreen

    public override fun lockTouchLogic() = super.lockTouchLogic()

    /**
     * 设置静音状态
     * 同时更新小屏和全屏播放器，确保静音立即生效
     */
    fun setMute(needMute: Boolean) {
        // 更新当前播放器
        gsyVideoManager.setNeedMute(needMute)
        // 同时更新全屏播放器（如果存在）
        getFullWindowPlayer()?.gsyVideoManager?.setNeedMute(needMute)
    }

    override fun init(context: Context) {
        super.init(context)
        initView()
        post {
            gestureDetector = GestureDetector(
                getContext().applicationContext,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        // 双击分区：左 1/3 快退、右 1/3 快进、中间 1/3 播放/暂停
                        if (!VideoPlay.doubleTapSeekEnabled || !mHadPlay) {
                            touchDoubleUp(e)
                            return true
                        }
                        val width = if (this@VideoPlayer.width > 0) {
                            this@VideoPlayer.width.toFloat()
                        } else {
                            resources.displayMetrics.widthPixels.toFloat()
                        }
                        when {
                            e.x < width / 3f -> doubleTapSeek(-1L)
                            e.x > width * 2f / 3f -> doubleTapSeek(1L)
                            else -> doubleTapPlayPause()
                        }
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!mChangePosition && !mChangeVolume && !mBrightness && mCurrentState != CURRENT_STATE_ERROR
                        ) {
                            onClickUiToggle(e)
                        }
                        return super.onSingleTapConfirmed(e)
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (mCurrentState == CURRENT_STATE_PLAYING) {
                            val speed = VideoPlay.longPressSpeed / 10.0f
                            setVideoSpeed(speed)
                            showOverlayTip("${speed}倍速播放中")
                            isLongPressSpeed = true
                        }
                        super.onLongPress(e)
                    }
                },
            )
            mLockClickListener = LockClickListener { view, lock ->
                VideoPlay.lockCurScreen = lock
            }
        }
    }
    /**
     * 双击快退/快进。direction < 0 快退，> 0 快进；步长取 VideoPlay.doubleTapSeekSeconds，
     * 边界处截断到 [0, duration]。
     */
    private fun doubleTapSeek(direction: Long) {
        val currentPosition = getCurrentPositionWhenPlaying()
        val duration = getDuration()
        val seekMs = VideoPlay.doubleTapSeekSeconds * 1000L * direction
        val newPosition = if (seekMs < 0) {
            (currentPosition + seekMs).coerceAtLeast(0)
        } else {
            (currentPosition + seekMs).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
        }
        seekTo(newPosition)
        // 副行用实际位移（边界处会被截断），不用名义步长，避免"已到头部却提示 +10 秒"
        showSeekTip(newPosition, newPosition - currentPosition)
    }

    /**
     * 双击中间区域：播放/暂停。
     * 与中央按钮走同一入口（[clickStartIcon]），保证图标、弹幕、计时器状态同步；
     * 缓冲/未就绪状态下不切换，避免在半准备状态上触发 seek 与计时器竞争。
     */
    private fun doubleTapPlayPause() {
        when (mCurrentState) {
            CURRENT_STATE_PLAYING -> {
                clickStartIcon()
                showOverlayTip("暂停", 600)
            }
            CURRENT_STATE_PAUSE -> {
                clickStartIcon()
                showOverlayTip("播放", 600)
            }
            CURRENT_STATE_PLAYING_BUFFERING_START, CURRENT_STATE_PREPAREING -> {
                showOverlayTip("缓冲中", 600)
            }
            else -> Unit
        }
    }

    override fun touchSurfaceUp() {
        if (isLongPressSpeed) {
            isLongPressSpeed = false
            setVideoSpeed(playSpeed)
            showOverlayTip()
            val time = getCurrentPositionWhenPlaying()
            resolveDanmakuStart(time)
        }
        // 滑动结束后隐藏亮度/音量提示
        if (mBrightness || mChangeVolume) {
            showOverlayTip()
        }
        super.touchSurfaceUp()
    }

    /**
     * 重写全屏滑动逻辑，根据配置控制亮度/音量调节
     * 左侧滑动调节亮度（需leftSlideBrightnessEnabled为true）
     * 右侧滑动调节音量（需rightSlideVolumeEnabled为true）
     */
    override fun touchSurfaceMoveFullLogic(absDeltaX: Float, absDeltaY: Float) {
        val curWidth = if (CommonUtil.getCurrentScreenLand(getActivityContext() as Activity)) {
            mScreenHeight
        } else {
            mScreenWidth
        }

        if (absDeltaX > mThreshold || absDeltaY > mThreshold) {
            cancelProgressTimer()
            if (absDeltaX >= mThreshold) {
                // 横向滑动：调节进度
                val screenWidth = CommonUtil.getScreenWidth(context)
                if (Math.abs(screenWidth - mDownX) > mSeekEndOffset) {
                    mChangePosition = true
                    mDownPosition = getCurrentPositionWhenPlaying()
                } else {
                    mShowVKey = true
                }
            } else {
                // 纵向滑动：调节亮度或音量
                val screenHeight = CommonUtil.getScreenHeight(context)
                val noEnd = Math.abs(screenHeight - mDownY) > mSeekEndOffset

                if (mFirstTouch) {
                    val isLeftSide = mDownX < curWidth * 0.5f

                    if (isLeftSide) {
                        // 左侧滑动：仅当leftSlideBrightnessEnabled=true时调节亮度
                        if (VideoPlay.leftSlideBrightnessEnabled && noEnd) {
                            mBrightness = true
                        }
                    } else {
                        // 右侧滑动：仅当rightSlideVolumeEnabled=true时调节音量
                        if (VideoPlay.rightSlideVolumeEnabled && noEnd) {
                            mChangeVolume = true
                            mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    }
                    mFirstTouch = false
                }
                mShowVKey = !noEnd
            }
        }
    }

    /**
     * 重写滑动进度显示，添加亮度/音量提示
     */
    override fun touchSurfaceMove(deltaX: Float, deltaY: Float, y: Float) {
        super.touchSurfaceMove(deltaX, deltaY, y)

        // 显示亮度/音量调节提示
        if (mBrightness) {
            val brightnessPercent = getBrightnessPercent(deltaY)
            showOverlayTip("亮度 $brightnessPercent%")
        } else if (mChangeVolume) {
            val volumePercent = getVolumePercent(deltaY)
            showOverlayTip("音量 $volumePercent%")
        }
    }

    /**
     * 计算亮度百分比
     */
    private fun getBrightnessPercent(deltaY: Float): Int {
        val screenHeight = CommonUtil.getScreenHeight(context)
        val slideRatio = -deltaY / screenHeight
        val brightness = (mBrightnessData + slideRatio * 0.5f).coerceIn(0f, 1f)
        mBrightnessData = brightness
        return (brightness * 100).toInt()
    }

    /**
     * 计算音量百分比
     */
    private fun getVolumePercent(deltaY: Float): Int {
        val maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val screenHeight = CommonUtil.getScreenHeight(context)
        val slideRatio = -deltaY / screenHeight
        val volumeDelta = (slideRatio * maxVolume * 0.5f).toInt()
        val newVolume = (mGestureDownVolume + volumeDelta).coerceIn(0, maxVolume)
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        return (newVolume * 100 / maxVolume)
    }

    private fun setVideoSpeed(speed: Float) {
        setSpeed(speed, true)
        if (mDanmakuView != null && !mDanmakuView!!.isPaused) {
            mDanmakuContext!!.setScrollSpeedFactor(VideoPlay.danmakuSpeed - (speed - 1f) / 6f)
            mDanmakuView!!.invalidate()
        }
    }

    /** 跳过片尾已触发标志，防止重复触发 */
    private var skipOutroTriggered = false

    override fun onPrepared() {
        super.onPrepared()
        // 确保切换章节后静音状态仍然生效
        // onPrepared 是播放器完全准备好之后的回调，此时 ExoPlayer 已存在，可直接设置音量
        setMute(VideoPlay.mutePlay)
        // 重置跳过片尾标志
        skipOutroTriggered = false
        // 跳过片头：仅在非续播（seekOnStart 为 0）且开启跳过片头时生效
        if (VideoPlay.skipIntroOutroEnabled &&
            VideoPlay.skipIntroSeconds > 0 &&
            seekOnStart <= 0
        ) {
            val skipMs = VideoPlay.skipIntroSeconds * 1000L
            val duration = getDuration()
            // 确保片头秒数不超过视频总时长
            if (duration > 0 && skipMs < duration) {
                seekTo(skipMs)
            }
        }
        onPrepareDanmaku(this)
    }
    private fun onPrepareDanmaku(gsyVideoPlayer: VideoPlayer) {
        val view = gsyVideoPlayer.mDanmakuView
        val par = gsyVideoPlayer.mParser
        val con = gsyVideoPlayer.mDanmakuContext
        if (view != null && !view.isPrepared && par != null) {
            view.prepare(par, con)
        }
    }

    override fun onVideoPause() {
        super.onVideoPause()
        danmakuOnPause()
    }
    fun danmakuOnPause() {
        if (mDanmakuView != null && mDanmakuView!!.isPrepared) {
            mDanmakuView!!.pause()
        }
    }

    override fun onVideoResume(isResume: Boolean) {
        super.onVideoResume(isResume)
        danmakuOnResume()
    }
    fun danmakuOnResume() {
        if (mDanmakuView != null && mDanmakuView!!.isPrepared && mDanmakuView!!.isPaused) {
            mDanmakuView!!.resume()
        }
    }

    override fun clickStartIcon() {
        super.clickStartIcon()
        if (mCurrentState == CURRENT_STATE_PLAYING) {
            danmakuOnResume()
        } else if (mCurrentState == CURRENT_STATE_PAUSE) {
            danmakuOnPause()
        }
    }

    override fun setProgressAndTime(
        progress: Long,
        secondProgress: Long,
        currentTime: Long,
        totalTime: Long,
        forceChange: Boolean,
    ) {
        super.setProgressAndTime(progress, secondProgress, currentTime, totalTime, forceChange)
        // 跳过片尾：当剩余时间小于设定秒数时，自动跳到下一集
        // setProgressAndTime 由进度定时器每秒调用，是检测播放位置的可靠回调
        if (VideoPlay.skipIntroOutroEnabled &&
            VideoPlay.skipOutroSeconds > 0 &&
            totalTime > 0 &&
            !skipOutroTriggered
        ) {
            val remainingMs = totalTime - currentTime
            val skipMs = VideoPlay.skipOutroSeconds * 1000L
            if (remainingMs <= skipMs) {
                skipOutroTriggered = true
                onAutoCompletion()
            }
        }
    }

    override fun onAutoCompletion() { // 播放完成
        super.onAutoCompletion()
        VideoPlay.upDurIndex(1, this)
    }

    override fun onCompletion() {
        super.onCompletion()
        releaseDanmaku(this)
    }
    fun releaseDanmaku(gsyVideoPlayer: VideoPlayer) {
        gsyVideoPlayer.mDanmakuView?.release()
    }

    override fun onSeekComplete() {
        super.onSeekComplete()
        val time = mProgressBar.progress * getDuration() / 100
        // 如果已经初始化过的，直接seek到对于位置
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            resolveDanmakuSeek(time)
        } else if (mHadPlay && mDanmakuView != null && !mDanmakuView!!.isPrepared) {
            // 如果没有初始化过的，记录位置等待
            mDanmakuStartSeekPosition = time
        }
    }

    /**
     * 播放器统一提示。无底板纯文字直接叠在画面上，靠文字阴影保证在亮画面里也读得清。
     *
     * 这里必须取消上一次的淡出任务：调进度时连续触发（拖动每帧一次、双击紧跟 seek），
     * 若旧任务的 postDelayed 不被取消，它会把刚设置的新提示一起淡掉，表现为"提示一闪就没"。
     *
     * @param message 主行文字（进度类为"当前 / 总时长"）
     * @param delay 停留时长；<= 0 表示一直显示，直到显式关闭
     * @param delta 副行文字（相对调整量），为空时不显示副行
     */
    fun showOverlayTip(message: String? = null, delay: Long = 0, delta: String? = null) {
        val container = tipContainer
        val view = tipView
        if (container == null || view == null) {
            return
        }
        container.removeCallbacks(hideTipRunnable)
        if (message == null) {
            hideTipRunnable.run()
            return
        }
        view.text = message
        view.visibility = VISIBLE
        tipDelta?.apply {
            if (delta.isNullOrEmpty()) {
                visibility = GONE
            } else {
                text = delta
                visibility = VISIBLE
            }
        }
        container.visibility = VISIBLE
        container.alpha = 1f
        if (delay > 0) {
            container.postDelayed(hideTipRunnable, delay)
        }
    }

    /**
     * 进度类提示：主行显示"当前 / 总时长"，副行显示本次调整量（+10秒 / -1分）。
     *
     * 拖动、双击、底栏快进快退全部走这里，保证三处弹出的样式与信息完全一致。
     *
     * @param position 调整后的目标位置（毫秒）
     * @param deltaMs 相对原位置的偏移（毫秒），拖动时传 seek 前后差值
     */
    fun showSeekTip(position: Long, deltaMs: Long? = null) {
        val duration = getDuration().coerceAtLeast(0)
        val current = position.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        showOverlayTip(
            "${CommonUtil.stringForTime(current)} / ${CommonUtil.stringForTime(duration)}",
            TIP_HIDE_DELAY_MS,
            formatDelta(deltaMs),
        )
    }

    /**
     * 相对调整量的文案，按时长逐级进位：
     * 不足 1 分钟用"秒"，不足 1 小时用"分"（整分）或"分秒"（如 +1分30秒），
     * 超过 1 小时用"时分秒"（如 +1时50分30秒）。偏移为 0 或未知时返回 null（不显示副行）。
     */
    private fun formatDelta(deltaMs: Long?): String? {
        if (deltaMs == null) return null
        val totalSeconds = deltaMs / 1000
        if (totalSeconds == 0L) return null
        val sign = if (totalSeconds > 0) "+" else "-"
        val abs = kotlin.math.abs(totalSeconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        val seconds = abs % 60
        val text = when {
            hours > 0 -> "${hours}时${minutes}分${seconds}秒"
            minutes > 0 && seconds > 0 -> "${minutes}分${seconds}秒"
            minutes > 0 -> "${minutes}分"
            else -> "${seconds}秒"
        }
        return sign + text
    }

    /**
     * 拖动进度条的浮层。基类会弹一个居中的独立 Dialog（带进度条与两个时间），
     * 与本播放器的提示风格不一致，这里改用统一提示：主行"当前 / 总时长"，副行相对调整量。
     *
     * 注意参数语义（GSY 11.3.0 `touchSurfaceMove` 里逐一传入）：
     * - `currentTime` 是拖动到的**目标位置**，不是原位；
     * - `currentDiff` 传的是 `mSeekTimePosition`（目标位置），**不是差值**。
     * 因此差值必须自己算：用 `mDownPosition`（按下时记录的原始位置）与之相减，
     * 否则副行会把"目标位置"当成"调整量"，看起来就是在显示当前播放进度。
     */
    override fun showProgressDialog(
        delta: Float,
        currentTime: String?,
        currentDiff: Long,
        totalTime: String?,
        totalDiff: Long,
    ) {
        val current = currentTime ?: ""
        val total = totalTime ?: ""
        val seekDelta = currentDiff - mDownPosition
        showOverlayTip(
            if (total.isEmpty()) current else "$current / $total",
            TIP_HIDE_DELAY_MS,
            formatDelta(seekDelta),
        )
    }

    override fun dismissProgressDialog() {
        showOverlayTip()
    }


    /**
     * 横屏右上角时钟。控件只在全屏布局（video_layout_controller_full）里存在，
     * 小窗播放时 findViewById 返回 null，直接跳过即可。
     * 用 24 小时制固定格式，不跟随系统 12/24 制开关 —— 视频全屏的场景里
     * "21:30" 比 "9:30" 更不容易被误读成播放时长。
     */
    private fun initClock() {
        tvClock = findViewById<TextView>(R.id.tv_clock) ?: return
        clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val tick = object : Runnable {
            override fun run() {
                val clock = tvClock ?: return
                clock.text = clockFormat?.format(Date()) ?: return
                val now = System.currentTimeMillis()
                clock.postDelayed(this, MINUTE_MS - now % MINUTE_MS)
            }
        }
        clockTick = tick
        tick.run()
    }

    override fun onDetachedFromWindow() {
        // 视图离屏时停掉分钟轮询，避免全屏销毁后仍在跑一个空转的 postDelayed 链
        tvClock?.removeCallbacks(clockTick)
        super.onDetachedFromWindow()
    }

    private fun initView() {
        isNeedLockFull = true // 使用锁定按钮
        playbackSpeed = findViewById(R.id.playback_speed)
        playbackSpeed?.setOnClickListener {
            if (mHadPlay && !isChanging) {
                showSpeedDialog()
            }
        }
        btnSettings = findViewById(R.id.iv_settings)
        btnSettings?.setOnClickListener {
            showSettingsDialog()
        }
        tipView = findViewById(R.id.tip_view)
        tipContainer = findViewById(R.id.tip_container)
        tipDelta = findViewById(R.id.tip_delta)
        initClock()
        if (mIfCurrentIsFullscreen && !VideoPlay.fullBottomProgressBar) {
            mBottomProgressBar = null
        }
        // 初始化全屏快捷跳转按钮
        initQuickJumpButtons()
        // 切换选集
        episodeList = findViewById(R.id.episode_list)
        btnNext = findViewById(R.id.next)
        if (VideoPlay.episodes == null) {
            episodeList?.visibility = GONE
            btnNext?.visibility = GONE
            return
        }
        episodeList?.setOnClickListener {
            if (mHadPlay && !isChanging) {
                showEpisodeDialog()
            }
        }
        btnNext?.setOnClickListener {
            VideoPlay.upDurIndex(1, this)
        }
    }

    /**
     * 更新全屏快捷跳转按钮状态（供外部在配置变更时调用）
     */
    fun updateQuickJumpButtons() {
        initQuickJumpButtons()
    }

    /**
     * 初始化全屏快捷跳转按钮
     */
    private fun initQuickJumpButtons() {
        if (!mIfCurrentIsFullscreen) return

        val quickJumpContainer = findViewById<LinearLayout>(R.id.quick_jump_container)
        val quickJumpBackA = findViewById<TextView>(R.id.quick_jump_back_a)
        val quickJumpBackB = findViewById<TextView>(R.id.quick_jump_back_b)
        val quickJumpForwardB = findViewById<TextView>(R.id.quick_jump_forward_b)
        val quickJumpForwardA = findViewById<TextView>(R.id.quick_jump_forward_a)

        if (!VideoPlay.quickJumpButtonsEnabled) {
            quickJumpContainer?.visibility = GONE
            return
        }

        val minutesA = VideoPlay.quickJumpMinutesA
        val minutesB = VideoPlay.quickJumpMinutesB

        quickJumpContainer?.visibility = VISIBLE
        quickJumpBackA?.apply {
            text = "-${minutesA}分"
            setOnClickListener { performQuickJump(-minutesA) }
        }
        quickJumpBackB?.apply {
            text = "-${minutesB}分"
            setOnClickListener { performQuickJump(-minutesB) }
        }
        quickJumpForwardB?.apply {
            text = "+${minutesB}分"
            setOnClickListener { performQuickJump(minutesB) }
        }
        quickJumpForwardA?.apply {
            text = "+${minutesA}分"
            setOnClickListener { performQuickJump(minutesA) }
        }
    }

    /**
     * 执行快捷跳转（分钟）
     */
    private fun performQuickJump(minutes: Int) {
        if (!mHadPlay) return
        val currentPosition = getCurrentPositionWhenPlaying()
        val duration = getDuration()
        val jumpMs = minutes * 60 * 1000L
        val newPosition = (currentPosition + jumpMs).coerceIn(0, duration)
        seekTo(newPosition)
        showSeekTip(newPosition, newPosition - currentPosition)
    }

    override fun setUp(url: String?, cacheWithPlay: Boolean, cachePath: File?, title: String?): Boolean {
        initDanmaku()
        val result = super.setUp(url, cacheWithPlay, cachePath, title)
        // 应用静音播放设置，根据用户配置决定是否静音
        gsyVideoManager.setNeedMute(VideoPlay.mutePlay)
        return result
    }

    private fun initDanmaku() {
        val danmakuFile = VideoPlay.danmakuFile
        val danmakuStr = VideoPlay.danmakuStr
        if (danmakuFile == null && danmakuStr.isNullOrBlank()) {
            mToggleDanmaku?.visibility = GONE
            return
        }
        mDanmakuView = findViewById<DanmakuView>(R.id.danmaku_view)?.also {
            it.visibility = VISIBLE
        }
        // 弹幕开关
        mToggleDanmaku = findViewById<TextView>(R.id.toggle_danmaku)?.also {
            it.visibility = VISIBLE
            it.setOnClickListener {
                // 按钮事件
                VideoPlay.danmakuShow = !VideoPlay.danmakuShow
                resolveDanmakuShow()
            }
        }
        if (mDanmakuView != null) {
            // 设置最大显示行数
            val maxLinesPair = HashMap<Int?, Int?>()
            maxLinesPair[BaseDanmaku.TYPE_SCROLL_RL] = 5 // 滚动弹幕最大显示5行
            // 设置是否禁止重叠
            val overlappingEnablePair = HashMap<Int?, Boolean?>()
            overlappingEnablePair[BaseDanmaku.TYPE_SCROLL_RL] = true
            overlappingEnablePair[BaseDanmaku.TYPE_FIX_TOP] = true
            val danmakuAdapter = DanmakuAdapter(mDanmakuView)
            mDanmakuContext = DanmakuContext.create() // 初始化上下文
            mDanmakuContext!!.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f) // 设置弹幕类型
                .setDuplicateMergingEnabled(false) // 设置是否合并重复弹幕
                .setScrollSpeedFactor(VideoPlay.danmakuSpeed) // 设置弹幕滚动速度
                .setScaleTextSize(1.0f) // 设置弹幕字体大小
                .setCacheStuffer(SpannedCacheStuffer(), danmakuAdapter) // 设置缓存绘制填充器 图文混排使用SpannedCacheStuffer
                .setMaximumLines(maxLinesPair) // 设置最大行数
                .preventOverlapping(overlappingEnablePair) // 设置是否禁止重叠
            mParser = createParser(danmakuFile, danmakuStr) // 加载弹幕资源文件
            mDanmakuView!!.setCallback(object : DrawHandler.Callback {
                override fun updateTimer(timer: DanmakuTimer?) {}
                override fun drawingFinished() {}
                override fun danmakuShown(danmaku: BaseDanmaku?) {}
                override fun prepared() {
                    if (mDanmakuView != null) {
                        mDanmakuView!!.start()
                        if (mDanmakuStartSeekPosition != -1L) {
                            resolveDanmakuSeek(mDanmakuStartSeekPosition)
                            mDanmakuStartSeekPosition = -1L
                        }
                        resolveDanmakuShow()
                    }
                }
            })
            mDanmakuView!!.enableDanmakuDrawingCache(true)
        }
    }

    /**
     * 弹幕偏移
     */
    private fun resolveDanmakuSeek(time: Long) {
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            mDanmakuView!!.seekTo(time)
        }
    }

    private fun resolveDanmakuStart(time: Long) {
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            mDanmakuView!!.seekTo(time)
        }
    }

    private fun resolveDanmakuShow() {
        post {
            if (VideoPlay.danmakuShow) {
                if (!mDanmakuView!!.isShown) mDanmakuView!!.show()
                mToggleDanmaku?.text = "关弹幕"
            } else {
                if (mDanmakuView!!.isShown) mDanmakuView!!.hide()
                mToggleDanmaku?.text = "开弹幕"
            }
        }
    }

    /**
     * 创建解析器对象，解析输入流
     *
     * @param stream
     * @return
     */
    private fun createParser(danmakuFile: File?, danmakuStr: String?): BaseDanmakuParser {
        val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
        try {
            if (danmakuFile != null) {
                loader.load(FileInputStream(danmakuFile))
            } else if (danmakuStr != null) {
                if (danmakuStr.startsWith("http", true)) {
                    loader.load(danmakuStr)
                } else {
                    loader.load(danmakuStr.byteInputStream())
                }
            }
        } catch (e: IllegalDataException) {
            e.printStackTrace()
        }
        val parser: BaseDanmakuParser = BiliDanmukuParser()
        val dataSource = loader.dataSource
        parser.load(dataSource)
        return parser
    }

    private fun showEpisodeDialog() {
        if (!mHadPlay || VideoPlay.episodes.isNullOrEmpty()) {
            return
        }
        isChanging = true
        val choiceEpisodeDialog = ChoiceEpisodeDialog(mContext)
        choiceEpisodeDialog.initList(
            VideoPlay.episodes!!,
            object :
                ChoiceEpisodeDialog.OnListItemClickListener {
                override fun onItemClick(position: Int) {
                    VideoPlay.chapterInVolumeIndex = position
                    VideoPlay.saveRead(0)
                    VideoPlay.startPlay(this@VideoPlayer)
                }

                override fun finishDialog() {
                    isChanging = false
                }
            },
            VideoPlay.chapterInVolumeIndex,
        )
        choiceEpisodeDialog.show()
    }

    private fun showSettingsDialog() {
        val activity = CommonUtil.scanForActivity(context)
        if (activity is AppCompatActivity) {
            activity.showDialogFragment(SettingsDialog(activity))
        }
    }

    private fun showSpeedDialog() {
        if (!mHadPlay) {
            return
        }
        isChanging = true
        val choiceSpeedDialog = ChoiceSpeedDialog(mContext)
        choiceSpeedDialog.initList(
            SPEED_OPTIONS.reversed(),
            object :
                ChoiceSpeedDialog.OnListItemClickListener {
                @SuppressLint("SetTextI18n")
                override fun onItemClick(value: Float) {
                    playSpeed = value.coerceIn(SPEED_OPTIONS.first(), MAX_PLAY_SPEED)
                    setSpeed(playSpeed, true)
                    if (playSpeed != 1.0f) {
                        playbackSpeed?.text = "${playSpeed}X"
                        showOverlayTip("${playSpeed}X", 1200)
                    } else {
                        playbackSpeed?.text = "倍速"
                    }
                }

                override fun finishDialog() {
                    isChanging = false
                }
            },
        )
        choiceSpeedDialog.show()
    }

    /**
     * 全屏播放键在"加载中"必须保持可见。
     *
     * 基类 `changeUiToPreparingShow` / `changeUiToPlayingBufferingShow` 会把 mStartButton 置为
     * INVISIBLE 只留 loading 圈，观感上就是"按钮没了"；这里在状态切换后重新拉回来：
     * 可见、半透明、不可点。
     */
    private fun keepStartButtonVisibleOnLoading() {
        if (!mIfCurrentIsFullscreen) return
        val loading = mCurrentState == CURRENT_STATE_PREPAREING ||
            mCurrentState == CURRENT_STATE_PLAYING_BUFFERING_START
        mStartButton?.apply {
            alpha = if (loading) START_BUTTON_DISABLED_ALPHA else 1f
            if (visibility != VISIBLE) visibility = VISIBLE
            isEnabled = !loading
            isClickable = !loading
        }
    }

    /**
     * 加载中不启动"自动隐藏控制层"计时器。
     *
     * 光把按钮拉回来还不够：基类在 PREPAREING、PLAYING 状态会启动一段倒计时，到点调用
     * [hideAllWidget] 把整层控制（含播放键）一起藏掉；横屏卡在缓冲时按钮就又消失了。
     * 这里在加载中直接取消计时，等状态真正进入播放/暂停时，基类会重新走一次
     * [resolveUIState]，计时器自然恢复。
     */
    override fun startDismissControlViewTimer() {
        val loading = mCurrentState == CURRENT_STATE_PREPAREING ||
            mCurrentState == CURRENT_STATE_PLAYING_BUFFERING_START
        if (loading) {
            cancelDismissControlViewTimer()
        } else {
            super.startDismissControlViewTimer()
        }
    }

    override fun changeUiToPreparingShow() {
        super.changeUiToPreparingShow()
        keepStartButtonVisibleOnLoading()
    }

    override fun changeUiToPlayingBufferingShow() {
        super.changeUiToPlayingBufferingShow()
        keepStartButtonVisibleOnLoading()
    }

    override fun changeUiToPlayingShow() {
        super.changeUiToPlayingShow()
        keepStartButtonVisibleOnLoading()
    }

    override fun changeUiToPauseShow() {
        super.changeUiToPauseShow()
        keepStartButtonVisibleOnLoading()
    }

    override fun changeUiToCompleteShow() {
        super.changeUiToCompleteShow()
        keepStartButtonVisibleOnLoading()
    }

    override fun changeUiToError() {
        super.changeUiToError()
        keepStartButtonVisibleOnLoading()
    }

    override fun updateStartImage() {
        if (mIfCurrentIsFullscreen) {
            if (mStartButton is ImageView) {
                val imageView = mStartButton as ImageView
                when (mCurrentState) {
                    CURRENT_STATE_PLAYING -> {
                        imageView.setImageResource(R.drawable.ic_pause_24dp)
                    }
                    CURRENT_STATE_ERROR -> {
                        imageView.setImageResource(R.drawable.ic_pause_outline_24dp)
                    }
                    else -> {
                        imageView.setImageResource(R.drawable.ic_play_24dp)
                    }
                }
            }
        } else {
            super.updateStartImage()
        }
    }

    override fun onError(what: Int, extra: Int) {
        super.onError(what, extra)
        VideoPlay.saveRead()
        mSeekOnStart = VideoPlay.durChapterPos.toLong()
    }

    /**
     * 处理播放器在全屏切换时，弹幕显示的逻辑
     * 需要格外注意的是，因为全屏和小屏，是切换了播放器，所以需要同步之间的弹幕状态
     */
    override fun startWindowFullscreen(
        context: Context?,
        actionBar: Boolean,
        statusBar: Boolean,
    ): VideoPlayer? {
        val gsyBaseVideoPlayer = super.startWindowFullscreen(context, actionBar, statusBar)
        if (gsyBaseVideoPlayer != null) {
            val gsyVideoPlayer = gsyBaseVideoPlayer as VideoPlayer
            // 对弹幕设置偏移记录
//            gsyVideoPlayer.mDanmakuView = this.mDanmakuView
            gsyVideoPlayer.mDanmakuStartSeekPosition = this.getCurrentPositionWhenPlaying()
            onPrepareDanmaku(gsyVideoPlayer)
            // 同步静音状态到全屏播放器
            gsyVideoPlayer.gsyVideoManager.setNeedMute(VideoPlay.mutePlay)
        }
        return gsyBaseVideoPlayer
    }

    /**
     * 处理播放器在退出全屏时，弹幕显示的逻辑
     * 需要格外注意的是，因为全屏和小屏，是切换了播放器，所以需要同步之间的弹幕状态
     */
    override fun resolveNormalVideoShow(
        oldF: View?,
        vp: ViewGroup?,
        gsyVideoPlayer: GSYVideoPlayer?,
    ) {
        super.resolveNormalVideoShow(oldF, vp, gsyVideoPlayer)
        if (gsyVideoPlayer != null) {
            val videoPlayer = gsyVideoPlayer as VideoPlayer
            if (mDanmakuView != null && mDanmakuView!!.isPrepared) {
                resolveDanmakuSeek(videoPlayer.getCurrentPositionWhenPlaying())
                resolveDanmakuShow()
                releaseDanmaku(videoPlayer)
            }
        }
    }

    override fun release() {
        super.release()
        releaseDanmaku(this)
    }

    /**********以下重载GSYVideoPlayer的GSYVideoViewBridge相关实现***********/
    override fun getGSYVideoManager(): ExoVideoManager = VideoPlay.videoManager.apply { initContext(context.applicationContext) }
    public override fun backFromFull(context: Context?): Boolean = VideoPlay.backFromWindowFull(context)
    override fun releaseVideos() {
        VideoPlay.releaseAllVideos()
    }

    override fun getFullId(): Int = ExoVideoManager.FULLSCREEN_ID

    override fun getSmallId(): Int = ExoVideoManager.SMALL_ID
    override fun setDisplay(surface: Surface?) {
        if (surface != null && mTextureView.getShowView() is SurfaceView) {
            val surfaceView = (mTextureView.getShowView() as SurfaceView?)
            gsyVideoManager.setDisplayNew(surfaceView)
        } else if (surface != null) {
            gsyVideoManager.setDisplay(surface)
        } else {
            gsyVideoManager.setDisplayNew(null)
        }
    }
    fun nextUI() {
        resetProgressAndTime()
    }

    // 播放器转移
    fun setSurfaceToPlay() {
        addTextureView()
        gsyVideoManager.setListener(this)
        checkoutState()
    }

    var needDestroy: Boolean = true
    override fun onSurfaceDestroyed(surface: Surface?): Boolean {
        if (needDestroy) {
            return super.onSurfaceDestroyed(surface)
        } else {
            releaseSurface(surface)
            needDestroy = true
            return true
        }
    }

    fun saveState(): VideoPlayer = this

    fun cloneState(switchVideo: StandardGSYVideoPlayer) {
        cloneParams(switchVideo, this)
    }
}
