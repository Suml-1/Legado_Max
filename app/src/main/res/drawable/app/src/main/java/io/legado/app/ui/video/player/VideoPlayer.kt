package io.legado.app.ui.video.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
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
import android.widget.RelativeLayout
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
import kotlin.math.roundToInt

class VideoPlayer : StandardGSYVideoPlayer {
    companion object {
        // 倍速面板档位，面板内按从快到慢展示
        private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
        // 播放倍速上限
        private const val MAX_PLAY_SPEED = 3.0f
        // 加载中播放键的透明度
        private const val START_BUTTON_DISABLED_ALPHA = 0.6f
        // 一分钟的毫秒数，时钟按分钟对齐刷新
        private const val MINUTE_MS = 60_000L
        // 进度类提示的停留时长
        private const val TIP_HIDE_DELAY_MS = 1200L
        // 亮度写入窗口属性时的量化级数
        private const val BRIGHTNESS_MAX_LEVEL = 100
        // 亮度滑动灵敏度：滑满一屏高度对应整个亮度区间
        private const val BRIGHTNESS_SENSITIVITY = 1.0f
        // 音量滑动灵敏度：滑满一屏高度对应整个音量区间
        private const val VOLUME_SENSITIVITY = 1.0f
    }

    constructor(context: Context?, fullFlag: Boolean?) : super(context, fullFlag) // 必须的,全屏时依靠这个构建知道获取全屏布局
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    private var episodeList: TextView? = null
    private var playbackSpeed: TextView? = null
    private var playSpeed: Float = 1.0f
    private var btnNext: ImageView? = null
    private var btnMute: ImageView? = null
    private var btnSettings: ImageView? = null
    private var tipView: TextView? = null

    // 提示容器，整体淡入淡出
    private var tipContainer: View? = null

    // 提示副行，显示本次调整的相对量
    private var tipDelta: TextView? = null

    // 提示淡出任务
    private val hideTipRunnable = Runnable {
        tipContainer?.apply {
            alpha = 0f
            visibility = INVISIBLE
        }
        tipView?.visibility = INVISIBLE
        tipDelta?.visibility = GONE
        tipPinned = false
    }

    // 一次性进度提示是否钉住：钉住的提示不随抬手收起
    private var tipPinned = false
    private var isChanging = false
    private var isLongPressSpeed = false

    private var mParser: BaseDanmakuParser? = null // 解析器对象
    private var mDanmakuView: DanmakuView? = null // 弹幕view
    private var mDanmakuContext: DanmakuContext? = null
    var mToggleDanmaku: TextView? = null // 弹幕开关
    private var mDanmakuStartSeekPosition: Long = -1

    // 横屏右上角的当前时间
    private var tvClock: TextView? = null
    private var clockFormat: SimpleDateFormat? = null
    // 时钟刷新任务，在 initView() 中创建
    private var clockTick: Runnable? = null

    // 音量调节相关
    private val mAudioManager: AudioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var gestureIndicator: View? = null
    private var gestureIndicatorHost: View? = null
    private var gestureIndicatorFill: View? = null
    private var gestureIndicatorIcon: ImageView? = null
    private val hideGestureRunnable = Runnable { hideGestureIndicator() }

    // 亮度 / 音量按下时的起点值及上次写入的档位
    private var brightnessDown = 0.5f
    private var volumeDown = 0
    private var lastAppliedVolume = -1
    private var lastAppliedBrightnessLevel = -1

    // 指示器当前是否在右侧，位置未变时跳过重新布局
    private var indicatorAlignedRight = false

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

    // 设置静音，同步应用到当前播放器与全屏播放器
    fun setMute(needMute: Boolean) {
        gsyVideoManager.setNeedMute(needMute)
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
                            showOverlayTip("▶▶${formatSpeed(speed)}x")
                            isLongPressSpeed = true
                        }
                        super.onLongPress(e)
                    }
                },
            )
            mLockClickListener = LockClickListener { view, lock ->
                VideoPlay.lockCurScreen = lock
                // 图标随锁定状态切换
                (view as? ImageView)?.setImageResource(
                    if (lock) R.drawable.ic_lock_closed else R.drawable.ic_lock_open,
                )
            }
        }
    }
    // 双击快退 / 快进。direction < 0 快退，> 0 快进；步长取 doubleTapSeekSeconds，边界处截断
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
        showSeekTip(newPosition, newPosition - currentPosition)
    }

    // 双击中间区域：播放 / 暂停，与中央按钮同走 clickStartIcon()，保证图标与状态同步
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
        super.touchSurfaceUp()
    }

    // 全屏滑动手势：横向调进度，纵向调亮度（左半屏）/ 音量（右半屏），可在设置中开关
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
                            brightnessDown = systemBrightnessRatio()
                            lastAppliedBrightnessLevel =
                                (brightnessDown * BRIGHTNESS_MAX_LEVEL).roundToInt()
                        }
                    } else {
                        // 右侧滑动：仅当rightSlideVolumeEnabled=true时调节音量
                        if (VideoPlay.rightSlideVolumeEnabled && noEnd) {
                            mChangeVolume = true
                            volumeDown = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            lastAppliedVolume = volumeDown
                        }
                    }
                    mFirstTouch = false
                }
                mShowVKey = !noEnd
            }
        }
    }

    // 滑动更新：进度交给基类，亮度 / 音量自行处理
    override fun touchSurfaceMove(deltaX: Float, deltaY: Float, y: Float) {
        when {
            mBrightness -> showGestureIndicator(
                getBrightnessRatio(deltaY),
                R.drawable.ic_brightness,
                alignRight = true,
            )
            mChangeVolume -> showGestureIndicator(
                getVolumeRatio(deltaY),
                R.drawable.ic_volume_up,
                alignRight = false,
            )
            else -> super.touchSurfaceMove(deltaX, deltaY, y)
        }
    }

    // 更新亮度 / 音量指示器，ratio 为 0f~1f
    private fun showGestureIndicator(ratio: Float, iconRes: Int, alignRight: Boolean) {
        val indicator = gestureIndicator ?: return
        val fill = gestureIndicatorFill ?: return
        fill.pivotY = fill.height.toFloat()
        fill.scaleY = ratio.coerceIn(0f, 1f).coerceAtLeast(0.002f)
        if (gestureIndicatorIcon?.tag != iconRes) {
            gestureIndicatorIcon?.tag = iconRes
            gestureIndicatorIcon?.setImageResource(iconRes)
        }
        alignIndicator(alignRight)
        if (indicator.visibility != VISIBLE) {
            indicator.visibility = VISIBLE
        }
        if (indicator.alpha != 1f) {
            indicator.alpha = 1f
        }
        indicator.removeCallbacks(hideGestureRunnable)
        indicator.postDelayed(hideGestureRunnable, 600)
    }

    // 切换指示器左右位置：音量靠左，亮度靠右
    private fun alignIndicator(alignRight: Boolean) {
        if (indicatorAlignedRight == alignRight) return
        indicatorAlignedRight = alignRight
        val host = gestureIndicatorHost ?: return
        val lp = host.layoutParams as? RelativeLayout.LayoutParams ?: return
        lp.removeRule(RelativeLayout.ALIGN_PARENT_START)
        lp.removeRule(RelativeLayout.ALIGN_PARENT_END)
        val margin = resources.getDimensionPixelSize(R.dimen.gesture_indicator_margin)
        if (alignRight) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            lp.marginStart = 0
            lp.marginEnd = margin
        } else {
            lp.addRule(RelativeLayout.ALIGN_PARENT_START)
            lp.marginEnd = 0
            lp.marginStart = margin
        }
        host.layoutParams = lp
    }

    private fun hideGestureIndicator() {
        val indicator = gestureIndicator ?: return
        indicator.removeCallbacks(hideGestureRunnable)
        indicator.animate().cancel()
        indicator.animate().alpha(0f).setDuration(150).withEndAction {
            indicator.visibility = INVISIBLE
        }.start()
    }

    // 屏蔽基类的音量 / 亮度弹窗，统一由指示器表达
    override fun showVolumeDialog(delta: Float, volume: Int) = Unit

    override fun dismissVolumeDialog() = Unit

    override fun showBrightnessDialog(delta: Float) = Unit

    override fun dismissBrightnessDialog() = Unit

    // 计算亮度比例并应用到窗口，返回连续值供指示器使用
    private fun getBrightnessRatio(deltaY: Float): Float {
        val screenHeight = CommonUtil.getScreenHeight(context).coerceAtLeast(1)
        val raw = (brightnessDown + (-deltaY / screenHeight) * BRIGHTNESS_SENSITIVITY)
            .coerceIn(0f, 1f)
        val level = (raw * BRIGHTNESS_MAX_LEVEL).roundToInt().coerceIn(0, BRIGHTNESS_MAX_LEVEL)
        if (level != lastAppliedBrightnessLevel) {
            lastAppliedBrightnessLevel = level
            (CommonUtil.scanForActivity(context))?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = level / BRIGHTNESS_MAX_LEVEL.toFloat()
                window.attributes = lp
            }
        }
        return raw
    }

    // 计算音量比例并应用到系统音量，返回连续值供指示器使用
    private fun getVolumeRatio(deltaY: Float): Float {
        val maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val screenHeight = CommonUtil.getScreenHeight(context).coerceAtLeast(1)
        val raw = (volumeDown / maxVolume.toFloat() +
            (-deltaY / screenHeight) * VOLUME_SENSITIVITY).coerceIn(0f, 1f)
        val level = (raw * maxVolume).roundToInt().coerceIn(0, maxVolume)
        if (level != lastAppliedVolume) {
            lastAppliedVolume = level
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        }
        return raw
    }

    // 读取系统当前亮度，归一化到 0~1
    private fun systemBrightnessRatio(): Float {
        val activity = CommonUtil.scanForActivity(context) ?: return 0.5f
        val current = activity.window?.attributes?.screenBrightness ?: -1f
        if (current >= 0f) return current
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            ) / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    private fun setVideoSpeed(speed: Float) {
        setSpeed(speed, true)
        if (mDanmakuView != null && !mDanmakuView!!.isPaused) {
            mDanmakuContext!!.setScrollSpeedFactor(VideoPlay.danmakuSpeed - (speed - 1f) / 6f)
            mDanmakuView!!.invalidate()
        }
    }

    // 倍速文案：整数去掉小数位，保留一位小数
    private fun formatSpeed(speed: Float): String =
        if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

    // 跳过片尾已触发标志
    private var skipOutroTriggered = false

    override fun onPrepared() {
        super.onPrepared()
        // 切换章节后重新应用静音状态
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
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            resolveDanmakuSeek(time)
        } else if (mHadPlay && mDanmakuView != null && !mDanmakuView!!.isPrepared) {
            mDanmakuStartSeekPosition = time
        }
    }

    // 播放器统一提示，message 为空即隐藏
    fun showOverlayTip(
        message: String? = null,
        delay: Long = 0,
        delta: String? = null,
        pinned: Boolean = false,
    ) {
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
        tipPinned = pinned
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

    // 进度类提示：主行显示当前 / 总时长，副行显示本次调整量
    fun showSeekTip(position: Long, deltaMs: Long? = null) {
        val duration = getDuration().coerceAtLeast(0)
        val current = position.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        showOverlayTip(
            "${CommonUtil.stringForTime(current)} / ${CommonUtil.stringForTime(duration)}",
            TIP_HIDE_DELAY_MS,
            formatDelta(deltaMs),
            pinned = true,
        )
    }

    // 相对调整量文案，秒 / 分 / 时分秒逐级进位
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

    // 拖动进度条的浮层，复用统一提示样式
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
        if (tipPinned) return
        showOverlayTip()
    }


    // 横屏右上角时钟，24 小时制
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
        // 亮度 / 音量滑动指示器
        gestureIndicator = findViewById(R.id.gesture_indicator)
        gestureIndicatorHost = findViewById(R.id.gesture_indicator_host)
        gestureIndicatorFill = findViewById(R.id.gesture_indicator_fill)
        gestureIndicatorIcon = findViewById(R.id.gesture_indicator_icon)
        gestureIndicatorFill?.scaleY = 0.002f
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
        // 静音切换：状态写到播放器设置，图标同步
        btnMute = findViewById(R.id.btn_mute)
        btnMute?.setOnClickListener {
            val mute = !VideoPlay.mutePlay
            VideoPlay.mutePlay = mute
            setMute(mute)
            updateMuteIcon()
        }
        updateMuteIcon()
    }

    // 静音图标跟随播放器设置中的静音状态
    private fun updateMuteIcon() {
        btnMute?.setImageResource(
            if (VideoPlay.mutePlay) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
        )
    }

    // 更新全屏快捷跳转按钮状态，供外部在配置变更时调用
    fun updateQuickJumpButtons() {
        initQuickJumpButtons()
    }

    // 初始化全屏快捷跳转按钮
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

    // 执行快捷跳转，minutes 为跳转分钟数
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

    //弹幕偏移
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

    //创建解析器对象，解析输入流
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

    // 加载中保持播放键可见
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

    // 加载中取消自动隐藏控制层计时器
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

    //处理播放器在全屏切换时，弹幕显示的逻辑
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

    //处理播放器在退出全屏时，弹幕显示的逻辑
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
