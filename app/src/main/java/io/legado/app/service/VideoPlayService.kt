package io.legado.app.service

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.help.MediaHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.glide.ImageLoader
import io.legado.app.ui.video.player.FloatingPlayer
import io.legado.app.model.VideoPlay
import io.legado.app.model.BookCover
import io.legado.app.receiver.MediaButtonReceiver
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.broadcastPendingIntent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import kotlin.math.abs
import kotlin.math.min

/**
 * 视频悬浮窗服务
 */
class VideoPlayService : BaseService() {
    companion object {
        @JvmStatic
        var pause = true
            private set
        private const val APP_ACTION_STOP = "Stop"
    }
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private val mediaSessionCompat by lazy {
        MediaSessionCompat(this, "videoPlayService")
    }
    private val floatingView by lazy {
        LayoutInflater.from(this).inflate(R.layout.floating_video_player, FrameLayout(this), false)
    }
    private val playerView by lazy { floatingView.findViewById<FloatingPlayer>(R.id.floatingPlayerView) }
    private var isNew = true
    private var upNotificationJob: Coroutine<*>? = null
    private var animator: SpringAnimation? = null
    private var cover: Bitmap =
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.icon_read_book)
    private var upPlayProgressJob: Job? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is VideoPlayerActivity) {
                // 确保 Activity 创建完成后才停止服务,留够时间复制播放器
                stop()
            }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private fun updateViewPosition() {
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelAnimator() {
        animator?.cancel()
    }

    private val layoutParamsXProperty =
        object : FloatPropertyCompat<WindowManager.LayoutParams>("x") {
            override fun getValue(layoutParams: WindowManager.LayoutParams): Float {
                return layoutParams.x.toFloat()
            }

            override fun setValue(layoutParams: WindowManager.LayoutParams, value: Float) {
                layoutParams.x = value.toInt()
                updateViewPosition()
            }
        }

    private fun startEdgeAnimation() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val viewWidth = floatingView.width
        val viewHeight = floatingView.height
        val currentX = params.x
        val endX = if (viewWidth == screenWidth) {
            0
        } else if (currentX + viewWidth / 2 > screenWidth / 2) {
            screenWidth - viewWidth - 30
        } else {
            30
        }
        val currentY = params.y
        var endY = currentY
        if (currentY < 30) {
            endY = 30
        } else if (currentY > screenHeight - viewHeight - 60) {
            endY = screenHeight - viewHeight - 60
        }
        if (endY != currentY) {
            ObjectAnimator.ofInt(currentY, endY).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    params.y = animation.animatedValue as Int
                    updateViewPosition()
                }
                start()
            }
        }
        animator = SpringAnimation(params, layoutParamsXProperty, endX.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW  // 低刚度更Q弹
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY  // 中等阻尼
            start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        initMediaSession()
        initBroadcastReceiver()
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        execute {
            ImageLoader
                .loadBitmap(this@VideoPlayService, VideoPlay.getDisplayCover())
                .submit()
                .get()
        }.onSuccess {
            if (it.width > 16 && it.height > 16) {
                cover = it
                upMediaMetadata()
                upVideoPlayNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                checkFloatPermission()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (intent == null) return START_NOT_STICKY
        intent.action?.let { action ->
            when (action) {
                IntentAction.pause -> pause()
                IntentAction.resume -> resume()
                IntentAction.prev -> VideoPlay.upDurIndex(-1, playerView)
                IntentAction.next -> VideoPlay.upDurIndex(1, playerView)
                IntentAction.stop -> stop()
            }
            return super.onStartCommand(intent, flags, startId)
        }
        isNew = intent.getBooleanExtra("isNew", true)
        if (isNew) {
            // 停止上一次的播放并释放媒体，防止旧视频继续播放
            VideoPlay.stopLoading()
            VideoPlay.stopPlayback()
            // 重置可能残留的上一次播放状态，防止旧链接/标题泄漏到新会话
            VideoPlay.singleUrl = false
            VideoPlay.videoUrl = null
            VideoPlay.videoTitle = null
            intent.getStringExtra("videoUrl")?.let {
                VideoPlay.videoUrl = it
                VideoPlay.singleUrl = true
            }
            intent.getStringExtra("videoTitle")?.let {
                VideoPlay.videoTitle = it
            }
            val sourceKey = intent.getStringExtra("sourceKey")
            val sourceType = intent.getIntExtra("sourceType", 0)
            val bookUrl = intent.getStringExtra("bookUrl")
            val record = intent.getStringExtra("record")
            VideoPlay.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            if (!VideoPlay.initSource(sourceKey, sourceType, bookUrl, record)) {
                stopSelf()
                return START_NOT_STICKY
            }
            VideoPlay.startPlay(playerView)
            VideoPlay.saveRead()
        } else {
            VideoPlay.clonePlayState(playerView)
            // 参数已拷到本服务的播放器，立刻断开对交出方（已销毁的 Activity）的静态强引用，
            // 否则 LeakCanary 报 VideoPlay.sMediaPlayerListener → VideoPlayer → Activity(mDestroyed)
            VideoPlay.clearPlayState()
        }
        setupPlayerView()
        if (!isNew) {
            // 关键：这里清的是"服务自己的 FloatingPlayer"（刚才 setupPlayerView 建好、cloneParams 已把
            // 播放器里 mBackFromFullScreenListener 拷过来的那个实例），不是 Activity 里的 playerView。
            // 该监听器是 VideoPlayerActivity 的 lambda（捕获 this），小窗作为长命对象持有它，
            // 就会一直吊住已销毁的 Activity —— LeakCanary 报的
            // FloatingPlayer → mBackFromFullScreenListener → VideoPlayerActivity$$ExternalSyntheticLambda35。
            // 必须在 setupPlayerView() 之后清：那里的 setVideoAllCallBack 是服务自己的、不能动。
            // FloatingPlayer 的重试/退出全屏有自己的 fullscreenB 监听，不依赖这个回调。
            playerView.setBackFromFullScreenListener(null)
            playerView.setSurfaceToPlay()
            playerView.startAfterPrepared()
        }
        if (floatingView.parent == null) {
            createFloatingWindow()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun initMediaSession() {
        mediaSessionCompat.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
            override fun onSeekTo(pos: Long) = playerView.seekTo(pos)
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onCustomAction(action: String?, extras: Bundle?) {
                action ?: return
                when (action) {
                    APP_ACTION_STOP -> stop()
                }
            }
            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                VideoPlay.upDurIndex(-1, playerView)
            }
            override fun onSkipToNext() {
                super.onSkipToNext()
                VideoPlay.upDurIndex(1, playerView)
            }
        })
        mediaSessionCompat.setMediaButtonReceiver(
            broadcastPendingIntent<MediaButtonReceiver>(Intent.ACTION_MEDIA_BUTTON)
        )
        mediaSessionCompat.isActive = true
    }

    private fun upVideoPlayNotification() {
        upNotificationJob = execute {
            try {
                val notification = createNotification()
                notificationManager.notify(NotificationId.VideoPlayService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建视频播放通知出错,${e.localizedMessage}", e, true)
            }
        }
    }

    override fun startForegroundNotification() {
        try {
            val notification = createNotification()
            startForeground(NotificationId.VideoPlayService, notification.build())
        } catch (e: Exception) {
            AppLog.put("创建视频播放通知出错,${e.localizedMessage}", e, true)
            //创建通知出错不结束服务就会崩溃,服务必须绑定通知
            stopSelf()
        }
    }

    /**
     * 断开耳机监听
     */
    private fun initBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                    pause()
                }
            }
        }
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 暂停播放
     */
    private fun pause(fromCB: Boolean = false) {
        try {
            pause = true
            upPlayProgressJob?.cancel()
            if (!fromCB) {
                playerView.onVideoPause()
            }
            upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            upVideoPlayNotification()
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    /**
     * 恢复播放
     */
    @SuppressLint("WakelockTimeout")
    private fun resume(fromCB: Boolean = false) {
        try {
            pause = false
            if (!fromCB) {
                playerView.onVideoResume()
            }
            upPlayProgress()
            upVideoPlayNotification()
        } catch (e: Exception) {
            e.printOnDebug()
            stop()
        }
    }

    /**
     * 每隔0.5秒发送播放进度
     */
    private fun upPlayProgress() {
        upPlayProgressJob?.cancel()
        upPlayProgressJob = lifecycleScope.launch {
            while (isActive) {
                upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                pause = false
                delay(500)
            }
        }
    }

    private fun upMediaSessionPlaybackState(state: Int) {
        mediaSessionCompat.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(MediaHelp.MEDIA_SESSION_ACTIONS)
                .setState(state, playerView.getCurrentPositionWhenPlaying(), 1f)
                .addCustomAction(
                    APP_ACTION_STOP,
                    getString(R.string.stop),
                    R.drawable.ic_stop_black_24dp
                )
                .build()
        )
    }

    private fun createNotification(): NotificationCompat.Builder {
        val nTitle = getString(R.string.audio_play_t) + ": $VideoPlay.videoTitle"
        val nSubtitle = getString(R.string.audio_play_s)
        val builder = NotificationCompat.Builder(this@VideoPlayService, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(getString(R.string.video))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nTitle)
            .setContentText(nSubtitle)
            .setContentIntent(
                activityPendingIntent<VideoPlayerActivity>("activity")
            )
        builder.setLargeIcon(cover)
        builder.addAction(
            R.drawable.ic_skip_previous,
            getString(R.string.previous),
            servicePendingIntent<VideoPlayService>(IntentAction.prev)
        )
        if (pause) {
            builder.addAction(
                R.drawable.ic_play_24dp,
                getString(R.string.resume),
                servicePendingIntent<VideoPlayService>(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause_24dp,
                getString(R.string.pause),
                servicePendingIntent<VideoPlayService>(IntentAction.pause)
            )
        }
        builder.addAction(
            R.drawable.ic_skip_next,
            getString(R.string.next),
            servicePendingIntent<VideoPlayService>(IntentAction.next)
        )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            servicePendingIntent<VideoPlayService>(IntentAction.stop)
        )
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSessionCompat.sessionToken)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        //小窗尺寸必须以"设备短边"为基准，不能用 displayMetrics.widthPixels：
        //从全屏（横屏）切小窗时 displayMetrics 已经是横屏尺寸（宽=长边），
        //直接乘 3/4 会得到 810px 这类比竖屏 450px 大得多的窗口，
        //窗口一大，右上角"返回播放页/关闭"按钮就被推出可视范围点不到。
        //取长短边的较小值后，横屏进小窗与竖屏进小窗的窗口大小完全一致。
        val dm = resources.displayMetrics
        val shortSide = min(dm.widthPixels, dm.heightPixels)
        val videoWidth = playerView.currentVideoWidth
        val videoHeight = playerView.currentVideoHeight
        val windowWidth = if (videoHeight > videoWidth * 1.2) {
            shortSide / 2 //竖屏视频：更窄，避免占满屏
        } else {
            shortSide * 3 / 4 //常规视频：短边的 3/4
        }
        val windowHeight = if (videoWidth > 0 && videoHeight > 0) (windowWidth * videoHeight / videoWidth) else (windowWidth * 9 / 16) // 默认16:9比例
        // 设置窗口参数
        params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 30
            y = shortSide / 10
        }
        floatingView.setOnTouchListener(FloatingTouchListener())
        windowManager.addView(floatingView, params)

    }

    inner class FloatingTouchListener : OnTouchListener {
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var initialX = 0
        private var initialY = 0
        private var isClick = true

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isClick = true
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    cancelAnimator()
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (abs(deltaX) > 20 || abs(deltaY) > 20) {
                        isClick = false
                        params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        updateViewPosition()
                        cancelAnimator()
                    } else {
                        isClick = true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        playerView.showControlUi()
                    } else {
                        startEdgeAnimation()
                    }

                }
            }
            return false
        }
    }


    private fun setupPlayerView() {
        playerView.fullscreenB.setOnClickListener {
            toggleFullScreen()
        }
        playerView.backButton.setOnClickListener { stop() }
        if (playerView.isInPlayingState) {
            upMediaMetadata()
            upPlayProgress()
        }
        playerView.setVideoAllCallBack(object : GSYSampleCallBack() {
            override fun onPrepared(url: String?, vararg objects: Any?) {
                upMediaMetadata()
                upPlayProgress()
                upVideoPlayNotification()
                //按实际视频比例重算小窗高度。宽度同样以设备短边为基准：
                //从横屏全屏切过来时 displayMetrics.widthPixels 是长边，
                //用它算会得到超宽窗口，右上角返回按钮被推出屏幕（点不到）。
                val videoWidth = playerView.currentVideoWidth
                val videoHeight = playerView.currentVideoHeight
                val dm = resources.displayMetrics
                val shortSide = min(dm.widthPixels, dm.heightPixels)
                if (videoWidth > 0 && videoHeight > 0) {
                    val parentWidth = if (videoHeight > videoWidth * 1.2) {
                        VideoPlay.isPortraitVideo = true
                        shortSide / 2 //竖屏时为短边的1/2
                    } else {
                        VideoPlay.isPortraitVideo = false
                        params.width //沿用 createFloatingWindow 已按短边算好的宽度
                    }
                    val aspectRatio = videoHeight.toFloat() / videoWidth.toFloat()
                    val height = (parentWidth * aspectRatio).toInt()
                    params.width = parentWidth
                    params.height = height
                    windowManager.updateViewLayout(floatingView, params)
                }
            }
            override fun onAutoComplete(url: String?, vararg objects: Any?) {
                if (!VideoPlay.upDurIndex(1, playerView)) {
                    stop()
                }
            }
            override fun onClickStartIcon(url: String?, vararg objects: Any?) {
                resume(true)
            }
            override fun onClickResume(url: String?, vararg objects: Any?) {
                resume(true)
            }
            override fun onClickStop(url: String?, vararg objects: Any?) {
                pause(true)
            }
        })
    }

    private fun stop() {
        stopSelf()
        pause = true
    }

    private fun toggleFullScreen() {
        VideoPlay.savePlayState(playerView)
        val fullscreenIntent = Intent(this, VideoPlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("isNew", false)
        }
        startActivity(fullscreenIntent)
        playerView.needDestroy = false
    }


    private fun upMediaMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, VideoPlay.videoTitle ?: "null")
            .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, VideoPlay.book?.name ?: "视频播放")
            .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, VideoPlay.book?.author ?: "null")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playerView.getDuration())
            .build()
        mediaSessionCompat.setMetadata(metadata)
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoPlay.saveRead()
        try {
            if (::windowManager.isInitialized && floatingView.parent != null) {
                windowManager.removeView(floatingView)
            }
            mediaSessionCompat.release()
            unregisterReceiver(broadcastReceiver)
            upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED)
            upNotificationJob?.invokeOnCompletion {
                notificationManager.cancel(NotificationId.VideoPlayService)
            }
            application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
            playerView.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 小窗播放器是通过 gsy cloneParams() 从小窗实例复制过来的，连
        // mBackFromFullScreenListener 那个 lambda（捕获了已销毁的 VideoPlayerActivity）也一起复制了过来。
        // 不显式清掉，LeakCanary 会持续报
        // FloatingPlayer → mBackFromFullScreenListener → VideoPlayerActivity$$ExternalSyntheticLambda。
        try {
            playerView.setBackFromFullScreenListener(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}
