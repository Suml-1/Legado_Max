package io.legado.app.ui.video.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shuyu.gsyvideoplayer.listener.LockClickListener
import com.shuyu.gsyvideoplayer.utils.CommonUtil
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer
import io.legado.app.R
import io.legado.app.constant.AppConst
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
import java.lang.ref.WeakReference
import kotlin.math.abs

class VideoPlayer: StandardGSYVideoPlayer {
    constructor(context: Context?, fullFlag: Boolean?) : super(context, fullFlag) //必须的,全屏时依靠这个构建知道获取全屏布局
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    private var episodeList: TextView? = null
    private var playbackSpeed: TextView? = null
    private var playSpeed: Float = 1.0f
    private var btnNext: ImageView? = null
    private var btnPrev: ImageView? = null
    /** 横屏底栏的小窗按钮：竖屏全屏（竖屏视频全屏播放）时隐藏 */
    private var btnPip: ImageView? = null
    private var btnSettings: ImageView? = null
    private var tipView: TextView? = null

    //R4：双击三区（左1/4快退/右1/4快进/中间播放暂停）提示面板
    private var dtpRoot: View? = null
    private var dtpIcon: ImageView? = null
    private var dtpTitle: TextView? = null
    private var dtpTime: TextView? = null
    private var dtpProgress: ProgressBar? = null
    /** 上次双击时间：onSingleTapConfirmed 用 350ms 门闩防"暂停同时开关控制栏" */
    private var lastDoubleTapAt = 0L

    //R7：横屏小窗按钮回调（与竖屏标题栏菜单同一个入口，由 Activity 注入）
    var onFloatWindowClick: (() -> Unit)? = null

    //R8：横屏右上角当前时间（HH:mm，每分钟整点刷新）
    private var tvCurrentTime: TextView? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            tvCurrentTime?.text = AppConst.timeFormat.format(System.currentTimeMillis())
            //对齐下一分钟整点，避免累积误差
            val delay = 60_000L - System.currentTimeMillis() % 60_000L
            clockHandler.postDelayed(this, delay)
        }
    }

    //R4：拖动进度预览（居中大号时间 + 偏移秒数）
    private var seekPreview: View? = null
    private var seekPreviewTime: TextView? = null
    private var seekPreviewDelta: TextView? = null
    private var seekPreviewHideRunnable: Runnable? = null
    /** 进度条自实现拖动：按下时的进度（0~100），MOVE 按位移增量累积 */
    private var isBarSeeking = false
    private var barDownX = 0f
    private var barStartProgress = 0
    /** 拖动前控制条是否可见：拖动结束后恢复，避免暂停按钮消失 */
    private var barsVisibleBeforeSeek = false
    private var isChanging = false
    private var isLongPressSpeed = false

    private var mParser: BaseDanmakuParser? = null //解析器对象
    private var mDanmakuView: DanmakuView? = null //弹幕view
    private var mDanmakuContext: DanmakuContext? = null
    var mToggleDanmaku: TextView? = null //弹幕开关
    private var mDanmakuStartSeekPosition: Long = -1

    // 音量调节相关
    private val mAudioManager: AudioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }


    override fun getLayoutId(): Int {
        return if (mIfCurrentIsFullscreen)
            R.layout.video_layout_controller_full
        else R.layout.video_layout_controller
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
                        //R4：锁屏状态双击不生效
                        if (mLockCurScreen) return true
                        lastDoubleTapAt = SystemClock.uptimeMillis()
                        val w = width.toFloat()
                        //R4：三区分区——左1/4快退、右1/4快进、中间（含未启用双击快进时）播放/暂停
                        val inLeftZone = e.x < w * 0.25f
                        val inRightZone = e.x >= w * 0.75f
                        val seekZone = VideoPlay.doubleTapSeekEnabled &&
                            mCurrentState == CURRENT_STATE_PLAYING &&
                            (inLeftZone || inRightZone)
                        if (seekZone && mHadPlay) {
                            val forward = inRightZone
                            val cur = getCurrentPositionWhenPlaying()
                            val seekSeconds = VideoPlay.doubleTapSeekSeconds
                            val duration = getDuration()
                            val target = (if (forward) cur + seekSeconds * 1000
                                          else cur - seekSeconds * 1000)
                                .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                            seekTo(target)
                            //与拖动进度同一套视觉：居中大号"当前/总时长 + 偏移秒数"，
                            //比小图标+标题的双击面板更直接（参考图确认的样式）
                            showSeekPreviewBriefly(target, duration, target - cur)
                        } else {
                            //中间区/未起播：等价点击中央按钮（复用其播放/暂停与弹幕同步逻辑）
                            clickStartIcon()
                            //竖屏中央那个圆形按钮本身就是状态指示，再弹一层"继续播放"会重复两次提示；
                            //横屏没有中央按钮，才需要文字反馈
                            if (mIfCurrentIsFullscreen) {
                                val paused = mCurrentState != CURRENT_STATE_PLAYING
                                showDoubleTapTip(
                                    if (paused) R.drawable.ic_player_play_circle
                                    else R.drawable.ic_player_pause_circle,
                                    context.getString(
                                        if (paused) R.string.video_double_tap_pause
                                        else R.string.video_double_tap_resume
                                    ),
                                    null, 0, 600L
                                )
                            }
                        }
                        //返回 true 表示事件已消费，避免再走单击分支
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        //R4：350ms 门闩——慢速双击时 GestureDetector 会走两次单击，
                        //吞掉紧跟双击的单击，避免"暂停同时开关控制栏"
                        if (SystemClock.uptimeMillis() - lastDoubleTapAt < 350L) {
                            return true
                        }
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
                }
            )
            mLockClickListener = LockClickListener { view, lock ->
                VideoPlay.lockCurScreen = lock
            }
        }
    }
    override fun touchSurfaceUp(){
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
            showOverlayTip("亮度 ${brightnessPercent}%")
        } else if (mChangeVolume) {
            val volumePercent = getVolumePercent(deltaY)
            showOverlayTip("音量 ${volumePercent}%")
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
        if (mDanmakuView != null&& !mDanmakuView!!.isPaused) {
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
        //R5：切集起播后刷新上一集按钮置灰状态
        updatePrevButtonState()
        //竖屏全屏时隐藏小窗按钮（视频本身是竖屏时全屏播放用不上小窗）
        updatePipButtonState()
        // 跳过片头：仅在非续播（seekOnStart 为 0）且开启跳过片头时生效
        if (VideoPlay.skipIntroOutroEnabled && VideoPlay.skipIntroSeconds > 0
            && seekOnStart <= 0
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
        if ( view != null && !view.isPrepared && par != null) {
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
        forceChange: Boolean
    ) {
        super.setProgressAndTime(progress, secondProgress, currentTime, totalTime, forceChange)
        // 跳过片尾：当剩余时间小于设定秒数时，自动跳到下一集
        // setProgressAndTime 由进度定时器每秒调用，是检测播放位置的可靠回调
        if (VideoPlay.skipIntroOutroEnabled && VideoPlay.skipOutroSeconds > 0
            && totalTime > 0 && !skipOutroTriggered
        ) {
            val remainingMs = totalTime - currentTime
            val skipMs = VideoPlay.skipOutroSeconds * 1000L
            if (remainingMs <= skipMs) {
                skipOutroTriggered = true
                onAutoCompletion()
            }
        }
    }

    override fun onAutoCompletion() { //播放完成
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
        //如果已经初始化过的，直接seek到对于位置
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            resolveDanmakuSeek(time)
        } else if (mHadPlay && mDanmakuView != null && !mDanmakuView!!.isPrepared) {
            //如果没有初始化过的，记录位置等待
            mDanmakuStartSeekPosition = time
        }
    }


    fun showOverlayTip(message: String? = null, delay: Long = 0) {
        tipView?.apply {
            message?.also {
                text = it
                visibility = VISIBLE
                alpha = 1f
                if (delay > 0) {
                    postDelayed({
                        alpha = 0f
                    }, delay)
                }
            } ?: run {
                visibility = INVISIBLE
                alpha = 0f
            }
        }
    }

    private fun initView() {
        isNeedLockFull = true //使用锁定按钮
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
        //R4：拖动进度预览（两套布局都有同名 id，findViewById 直接适配竖屏/横屏）
        seekPreview = findViewById(R.id.seek_preview)
        seekPreviewTime = findViewById(R.id.seek_preview_time)
        seekPreviewDelta = findViewById(R.id.seek_preview_delta)
        //R4：进度条拖动自实现接管——优先响应"按住拖动"，与双击分区手势互不干扰。
        //返回 true 会吞掉 SeekBar 自身与 gsy 的 OnSeekBarChangeListener 拖动链路，
        //所以点按跳转也要在这里自己算（见 ACTION_UP 分支）。
        mProgressBar?.setOnTouchListener { _, e -> handleProgressBarTouch(e) }
        if (mIfCurrentIsFullscreen && !VideoPlay.fullBottomProgressBar) {
            mBottomProgressBar = null
        }
        //初始化全屏快捷跳转按钮
        initQuickJumpButtons()
        //切换选集
        episodeList = findViewById(R.id.episode_list)
        btnNext = findViewById(R.id.next)
        //R5：上一集按钮（仅横屏布局存在；竖屏布局无此 id，findViewById 为 null 天然不生效）
        btnPrev = findViewById(R.id.prev)
        //R7：横屏小窗按钮（与竖屏标题栏菜单共用 onFloatWindowClick 入口）
        btnPip = findViewById(R.id.iv_float_window)
        btnPip?.setOnClickListener {
            onFloatWindowClick?.invoke()
        }
        if (VideoPlay.episodes == null) {
            episodeList?.visibility = GONE
            btnNext?.visibility = GONE
            btnPrev?.visibility = GONE
            return
        }
        episodeList?.setOnClickListener {
            if (mHadPlay && !isChanging) {
                showEpisodeDialog()
            }
        }
        btnNext?.setOnClickListener {
            VideoPlay.upDurIndex(1,this)
        }
        btnPrev?.setOnClickListener {
            VideoPlay.upDurIndex(-1, this)
        }
        updatePrevButtonState()
        updatePipButtonState()
    }

    /**
     * R5：上一集/下一集按钮状态——首集/末集置灰不可点。
     * initView 只在实例创建时执行一次，切集后由 onPrepared 重新刷新，避免状态陈旧。
     */
    private fun updatePrevButtonState() {
        val episodes = VideoPlay.episodes
        val index = VideoPlay.chapterInVolumeIndex
        val atFirst = episodes == null || index <= 0
        btnPrev?.let {
            it.isEnabled = !atFirst
            it.alpha = if (atFirst) 0.4f else 1f
        }
        //末集没有"下一集"：同样置灰，避免点了只弹"已播放完"
        val atLast = episodes == null || index >= episodes.size - 1
        btnNext?.let {
            it.isEnabled = !atLast
            it.alpha = if (atLast) 0.4f else 1f
        }
    }

    /**
     * 小窗按钮的显隐：竖屏全屏时隐藏。
     *
     * 背景：横屏与竖屏全屏共用 `video_layout_controller_full` 布局，
     * 但按钮只在"横屏全屏"下有意义 —— 视频本身是竖屏（`isPortraitVideo`）时，
     * 全屏播放的画面本身就是竖向的，再切小窗没有收益，反而占着底栏位置。
     *
     * 为什么在 onPrepared 里调：`isPortraitVideo` 要等拿到视频宽高（aspectRatio > 1.2）
     * 才知道，initView 时该值还是 false，那时隐藏会误伤横屏视频。
     */
    private fun updatePipButtonState() {
        if (btnPip == null) {
            btnPip = findViewById(R.id.iv_float_window)
        }
        btnPip?.visibility = if (VideoPlay.isPortraitVideo) View.GONE else View.VISIBLE
    }

    /**
     * R4：双击提示面板。与 showOverlayTip 不同——这里用 animate().cancel() +
     * setStartDelay 的单动画链，连击不会堆叠多个延时闭包把新提示提前抹掉。
     * timeText 为 null 时表示播放/暂停提示，隐藏时间与进度条。
     */
    private fun showDoubleTapTip(
        icon: Int,
        title: String,
        timeText: String?,
        progress: Int,
        stayMs: Long
    ) {
        if (dtpRoot == null) {
            dtpRoot = findViewById(R.id.double_tap_tip)
            dtpIcon = findViewById(R.id.dtp_icon)
            dtpTitle = findViewById(R.id.dtp_title)
            dtpTime = findViewById(R.id.dtp_time)
            dtpProgress = findViewById(R.id.dtp_progress)
        }
        val root = dtpRoot ?: return
        root.animate().cancel()
        root.visibility = View.VISIBLE
        root.alpha = 1f
        dtpIcon?.setImageResource(icon)
        //播放/暂停提示图标放大到 48dp 视觉，快进/快退 24dp
        val density = resources.displayMetrics.density
        val size = ((if (timeText == null) 48 else 24) * density).toInt()
        dtpIcon?.layoutParams = dtpIcon?.layoutParams?.apply {
            width = size
            height = size
        }
        dtpIcon?.requestLayout()
        dtpTitle?.text = title
        if (timeText == null) {
            dtpTime?.visibility = View.GONE
            dtpProgress?.visibility = View.GONE
        } else {
            dtpTime?.visibility = View.VISIBLE
            dtpProgress?.visibility = View.VISIBLE
            dtpTime?.text = timeText
            dtpProgress?.progress = progress
        }
        root.animate()
            .alpha(0f)
            .setStartDelay(stayMs)
            .setDuration(200L)
            .withEndAction { root.visibility = View.INVISIBLE }
            .start()
    }

    /**
     * R4：进度条触摸自实现——按住拖动按位移增量改进度，松手（或点按）跳转。
     *
     * 必须自己算点按位置：这个 OnTouchListener 返回 true 会连带吞掉 SeekBar 自身的
     * 拖动逻辑（gsy 的 OnSeekBarChangeListener 靠它触发），所以 ACTION_UP 里既要处理
     * "拖动结束"，也要处理"点按跳转"，否则点进度条会失灵。
     */
    private fun handleProgressBarTouch(e: MotionEvent): Boolean {
        val seekBar = mProgressBar ?: return false
        val duration = getDuration()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                barDownX = e.x
                barStartProgress = seekBar.progress
                isBarSeeking = false
                //拖动中不让父容器抢事件，否则控制条会跟着滑
                seekBar.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (duration <= 0) return true
                //8px 死区：小于它视为点按，避免"想点一下"变成微抖动跳转
                if (!isBarSeeking && abs(e.x - barDownX) > 8f) {
                    isBarSeeking = true
                    //借用 gsy 的标记暂停它自己的进度刷新，避免和预览打架
                    mTouchingProgressBar = true
                    barsVisibleBeforeSeek = findViewById<View>(R.id.layout_bottom)?.visibility == VISIBLE
                    cancelDismissControlViewTimer()
                }
                if (isBarSeeking) {
                    val track = (seekBar.width - seekBar.paddingLeft - seekBar.paddingRight)
                        .coerceAtLeast(1)
                    val progress = (barStartProgress +
                        (e.x - barDownX) / track * 100).toInt().coerceIn(0, 100)
                    seekBar.progress = progress
                    val target = duration * progress / 100L
                    mCurrentTimeTextView?.text = CommonUtil.stringForTime(target)
                    showSeekPreview(
                        target, duration,
                        target - duration * barStartProgress / 100L
                    )
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasSeeking = isBarSeeking
                isBarSeeking = false
                hideSeekPreview()
                if (e.actionMasked == MotionEvent.ACTION_UP && duration > 0) {
                    val track = (seekBar.width - seekBar.paddingLeft - seekBar.paddingRight)
                        .coerceAtLeast(1)
                    val target = if (wasSeeking) {
                        duration * seekBar.progress / 100L
                    } else {
                        //点按：直接跳到触点位置
                        (duration *
                            ((e.x - seekBar.paddingLeft) / track).coerceIn(0f, 1f)).toLong()
                    }
                    seekTo(target)
                    if (mDanmakuView != null && mDanmakuView!!.isPrepared) {
                        mDanmakuView!!.seekTo(target)
                    }
                    //立即变更进度条与时间显示，不等缓冲完成
                    seekBar.progress = (target * 100 / duration).toInt()
                    mCurrentTimeTextView?.text = CommonUtil.stringForTime(target)
                }
                //UP 后短暂挂起 gsy 的进度刷新，避免刚跳转的位置被旧进度覆盖
                mTouchingProgressBar = wasSeeking
                if (wasSeeking) {
                    postDelayed({ mTouchingProgressBar = false }, 800L)
                }
                restoreBarsAfterSeek()
                return true
            }
        }
        return false
    }

    /**
     * 拖动结束后恢复控制条：拖动期间 gsy 的自动隐藏定时器被取消，
     * 若拖动前控制条是显示的，这里补回显示状态，否则松手后暂停按钮会消失。
     */
    private fun restoreBarsAfterSeek() {
        if (barsVisibleBeforeSeek) {
            listOf(R.id.layout_bottom, R.id.layout_top).forEach { id ->
                findViewById<View>(id)?.let { v ->
                    if (v.visibility != View.VISIBLE) {
                        v.visibility = View.VISIBLE
                        v.alpha = 1f
                    }
                }
            }
        }
        barsVisibleBeforeSeek = false
        startDismissControlViewTimer()
    }

    /** R4：拖动进度预览——当前时间/总时长 + 偏移秒数 */
    private fun showSeekPreview(position: Long, duration: Long, delta: Long) {
        val preview = seekPreview ?: return
        seekPreviewTime?.text =
            "${CommonUtil.stringForTime(position)}/${CommonUtil.stringForTime(duration)}"
        val seconds = abs(delta) / 1000
        seekPreviewDelta?.text = when {
            seconds <= 0L -> "0s"
            delta > 0 -> "+${seconds}s"
            else -> "-${seconds}s"
        }
        if (preview.visibility != View.VISIBLE) {
            preview.visibility = View.VISIBLE
        }
    }

    private fun hideSeekPreview() {
        seekPreview?.visibility = View.GONE
        seekPreviewHideRunnable?.let { removeCallbacks(it) }
        seekPreviewHideRunnable = null
    }

    /**
     * R4：双击快进/快退后的短暂预览——复用拖动进度的居中面板（大号时间 + 偏移秒数），
     * 800ms 后自动隐藏；再次双击会重置倒计时，不会提前熄灭。
     */
    private fun showSeekPreviewBriefly(position: Long, duration: Long, delta: Long) {
        showSeekPreview(position, duration, delta)
        seekPreviewHideRunnable?.let { removeCallbacks(it) }
        val hide = Runnable { hideSeekPreview() }
        seekPreviewHideRunnable = hide
        removeCallbacks(hide)
        postDelayed(hide, 800L)
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
        showOverlayTip("${if (minutes > 0) "+" else ""}${minutes}分", 1000)
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
        //弹幕开关
        mToggleDanmaku = findViewById<TextView>(R.id.toggle_danmaku)?.also {
            it.visibility = VISIBLE
            it.setOnClickListener { //按钮事件
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
            mDanmakuContext = DanmakuContext.create() //初始化上下文
            mDanmakuContext!!.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f) //设置弹幕类型
                .setDuplicateMergingEnabled(false) //设置是否合并重复弹幕
                .setScrollSpeedFactor(VideoPlay.danmakuSpeed) //设置弹幕滚动速度
                .setScaleTextSize(1.0f) //设置弹幕字体大小
                .setCacheStuffer(SpannedCacheStuffer(), danmakuAdapter) //设置缓存绘制填充器 图文混排使用SpannedCacheStuffer
                .setMaximumLines(maxLinesPair) //设置最大行数
                .preventOverlapping(overlappingEnablePair) //设置是否禁止重叠
            mParser = createParser(danmakuFile, danmakuStr) //加载弹幕资源文件
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
                if (danmakuStr.startsWith("http",true)) {
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
            object : ChoiceEpisodeDialog.OnListItemClickListener {
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
            //用播放器实测高度：这才是面板真正可用的区域
            usableHeight = height
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
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f).reversed(),
            object : ChoiceSpeedDialog.OnListItemClickListener {
                @SuppressLint("SetTextI18n")
                override fun onItemClick(value: Float) {
                    playSpeed = value
                    setSpeed(playSpeed, true)
                    //倍速状态只由面板选中项表达，按钮文字固定为"倍速"：
                    //两处都改会让底栏文字长度跳动（历史上就是"×n"与面板高亮打架）
                    playbackSpeed?.text = "倍速"
                }

                override fun finishDialog() {
                    isChanging = false
                }
            },
            currentSpeed = playSpeed,
            //用播放器实测高度：这才是控制条/面板真正可用的区域
            usableHeight = height
        )
        choiceSpeedDialog.show()
    }

    /**
     * R6/缓冲态：gsy 在缓冲（以及首次未缓冲的 PREPARING）时会主动把
     * mStartButton 设为 INVISIBLE、mLockScreen 设为 GONE——
     * 结果是"转圈缓冲时锁按钮和播放/暂停按钮一起消失"，用户既看不到状态也没法暂停。
     * 这里在 super 之后把这两个还回去，并重设图标（缓冲中视频仍在推进，显示暂停态）。
     */
    override fun changeUiToPlayingBufferingShow() {
        super.changeUiToPlayingBufferingShow()
        keepButtonsWhileBuffering()
    }

    override fun changeUiToPreparingShow() {
        super.changeUiToPreparingShow()
        keepButtonsWhileBuffering()
    }

    /**
     * 缓冲态下必须常显的控件。
     * 注意 [setViewShowState] 是 gsy 控制条显隐的唯一入口（mTopContainer/mBottomContainer
     * 都经它），所以这里用同一个入口，状态机不会被打乱。
     */
    private fun keepButtonsWhileBuffering() {
        setViewShowState(mBottomContainer, View.VISIBLE)
        setViewShowState(mStartButton, View.VISIBLE)
        updateStartImage()
        setViewShowState(mLockScreen, View.VISIBLE)
        //缓冲期间不自动隐藏：否则等缓冲结束，控制条已经淡出，用户又要点一次
        cancelDismissControlViewTimer()
    }

    /**
     * R4：长按画面左右滑动调进度时，gsy 默认弹一个 152dp 的圆形卡片（蓝色进度 + 圆角底板），
     * 与播放器其余部分风格割裂。这里接管成和双击快进/拖动进度条完全一致的
     * 居中大号"当前/总时长 + 偏移秒数"，并且不再创建 gsy 的 mProgressDialog。
     */
    override fun showProgressDialog(
        delta: Float,
        seekTime: String?,
        seekTimePosition: Long,
        totalTime: String?,
        totalTimeDuration: Long
    ) {
        val total = if (totalTimeDuration > 0) totalTimeDuration else getDuration()
        val base = mDownPosition
        showSeekPreviewBriefly(seekTimePosition, total, seekTimePosition - base)
    }

    override fun dismissProgressDialog() {
        hideSeekPreview()
    }

    override fun updateStartImage() {
        //R6：两套布局各自的视觉语言——
        //横屏底栏是一行 24dp 线性功能键（⏮ ▶ ⏭），播放键跟随线性白色三角/双竖条；
        //竖屏中央按钮悬在画面上，用"白圆底 + 深色图形"（本项目自绘的 ic_player_*_circle）。
        //缓冲中视频仍在推进，按"播放中"显示暂停态，避免缓冲时闪成播放三角。
        if (mStartButton is ImageView) {
            val imageView = mStartButton as ImageView
            val playing = mCurrentState == CURRENT_STATE_PLAYING ||
                mCurrentState == CURRENT_STATE_PLAYING_BUFFERING_START
            imageView.setImageResource(
                if (mIfCurrentIsFullscreen) {
                    if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp
                } else {
                    if (playing) R.drawable.ic_player_pause_circle
                    else R.drawable.ic_player_play_circle
                }
            )
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
        statusBar: Boolean
    ): VideoPlayer? {
        val gsyBaseVideoPlayer = super.startWindowFullscreen(context, actionBar, statusBar)
        if (gsyBaseVideoPlayer != null) {
            val gsyVideoPlayer = gsyBaseVideoPlayer as VideoPlayer
            //对弹幕设置偏移记录
//            gsyVideoPlayer.mDanmakuView = this.mDanmakuView
            gsyVideoPlayer.mDanmakuStartSeekPosition = this.getCurrentPositionWhenPlaying()
            onPrepareDanmaku(gsyVideoPlayer)
            // 同步静音状态到全屏播放器
            gsyVideoPlayer.gsyVideoManager.setNeedMute(VideoPlay.mutePlay)
            //R7：全屏是新实例，必须把小窗回调带过去，否则横屏按钮无响应
            gsyVideoPlayer.onFloatWindowClick = onFloatWindowClick
            // 记下克隆出来的全屏实例：Activity 销毁时窗口树已拆，getFullWindowPlayer() 可能找不到了，
            // 但它注册的网络监听还在 LoadedApk.mReceivers 里，必须能被单独清理（见 releaseNetworkStateForGood）。
            // 用弱引用持有：这个字段本身不该成为"把小屏 Activity 吊住"的新理由。
            fullWindowPlayer = WeakReference(gsyVideoPlayer)
        }
        return gsyBaseVideoPlayer
    }

    /** 全屏克隆实例（横屏时真正在播放的那个），仅供销毁时清理网络监听使用 */
    private var fullWindowPlayer: WeakReference<VideoPlayer>? = null

    /** 取全屏克隆实例（窗口拆掉后仍可取到，用于补清理网络监听） */
    fun peekFullWindowPlayer(): VideoPlayer? = fullWindowPlayer?.get()

    /**
     * 处理播放器在退出全屏时，弹幕显示的逻辑
     * 需要格外注意的是，因为全屏和小屏，是切换了播放器，所以需要同步之间的弹幕状态
     */
    override fun resolveNormalVideoShow(
        oldF: View?,
        vp: ViewGroup?,
        gsyVideoPlayer: GSYVideoPlayer?
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
        //R8：释放时钟，避免 Handler 泄漏
        stopClock()
        super.release()
        // gsy 只在 onAutoCompletion / onError 里调 releaseNetWorkState()，
        // 正常销毁路径不会走 —— mNetInfoModule 连同它持有的 NetChangeListener 会一直挂住
        // VideoView → Activity，这就是 LeakCanary 里 NetInfoModule 那条 5MB 泄漏。
        // 这里在销毁时显式释放（方法为 protected，子类可调，内部会把 mNetInfoModule 置 null）。
        releaseNetWorkState()
        releaseDanmaku(this)
    }

    /**
     * 显式注销网络状态监听。
     *
     * LeakCanary 实测：全屏克隆出来的那个实例并不一定走上面的 [release]，
     * 它的 `NetInfoModule$ConnectivityBroadcastReceiver` 会一直留在
     * `LoadedApk.mReceivers` 里，沿 `mNetChangeListener → GSYVideoView$3 → this$0`
     * 把 VideoPlayer 和已销毁的 Activity 一起挂住。
     * 所以 Activity 销毁时对**两个实例**都要调一次（`releaseNetWorkState()` 是 protected，
     * 这里开成 public 方法供外部调用；内部会把 mNetInfoModule 置 null，重复调用安全）。
     */
    fun releaseNetworkStateForGood() {
        try {
            releaseNetWorkState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAttachedToWindow() {
        //GSY 在 attach 时会 addTextureView，必须先走 super，顺序错了会黑屏
        super.onAttachedToWindow()
        //R8：仅横屏实例显示时钟（竖屏布局无 tv_current_time）
        if (mIfCurrentIsFullscreen) {
            startClock()
        }
    }

    override fun onDetachedFromWindow() {
        //R8：退出全屏时先停时钟再走 GSY 的 releaseVideos
        stopClock()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        //R8：不可见时双保险停表（横屏退出后全屏实例被移除）
        if (visibility != View.VISIBLE) {
            stopClock()
        }
    }

    /** R8：启动右上角时钟，先立即赋值再按分钟整点续排 */
    private fun startClock() {
        if (tvCurrentTime == null) {
            tvCurrentTime = findViewById(R.id.tv_current_time)
        }
        if (tvCurrentTime != null) {
            clockHandler.removeCallbacks(clockTick)
            clockTick.run()
        }
    }

    private fun stopClock() {
        clockHandler.removeCallbacks(clockTick)
    }

    /**********以下重载GSYVideoPlayer的GSYVideoViewBridge相关实现***********/
    override fun getGSYVideoManager(): ExoVideoManager {
        return VideoPlay.videoManager.apply { initContext(context.applicationContext) }
    }
    public override fun backFromFull(context: Context?): Boolean {
        return VideoPlay.backFromWindowFull(context)
    }
    override fun releaseVideos() {
        VideoPlay.releaseAllVideos()
    }

    override fun getFullId(): Int {
        return ExoVideoManager.FULLSCREEN_ID
    }

    override fun getSmallId(): Int {
        return ExoVideoManager.SMALL_ID
    }
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
    fun nextUI() { resetProgressAndTime() }


    //播放器转移
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

    fun saveState(): VideoPlayer {
        return this
    }

    fun cloneState(switchVideo: StandardGSYVideoPlayer) {
        cloneParams(switchVideo, this)
    }
}