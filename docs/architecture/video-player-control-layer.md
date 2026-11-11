# 视频播放器控制层设计

> 适用范围：`ui/video/player/` 下的控制层样式、手势与面板。**有效**（代码已落地）。
> 相关文件：`VideoPlayer.kt`、`ChoiceEpisodeDialog.kt`、`ChoiceSpeedDialog.kt`、`VideoPanelWindow.kt`、`res/layout/video_layout_controller*.xml`。
> 播放内核沿用 GSYVideoPlayer 的 `StandardGSYVideoPlayer`，本文只讲本项目在其之上的定制。

## 一、布局与两套控制层

播放器有两份控制层布局，按是否全屏切换：

| 布局 | 场景 | 结构特点 |
| --- | --- | --- |
| `video_layout_controller.xml` | 竖屏 / 小窗 | `RelativeLayout`，底栏 `layout_bottom` 为水平 `LinearLayout` |
| `video_layout_controller_full.xml` | 横屏全屏 | `RelativeLayout` 外层 + 底栏 `ConstraintLayout` |

两者共享同一批 id（`start` / `next` / `progress` / `current` / `total` / `tiplay`…），`VideoPlayer.getLayoutId()` 依据 `mIfCurrentIsFullscreen` 选择。**新增控件必须在两份布局里都定义**，否则全屏切换时 `findViewById` 会拿到 null 而静默失效。

### 1.1 竖屏与横屏的差异

横屏底栏多出竖屏没有的控件：`tv_clock`（右上角时刻）、`btn_mute`（静音切换）、`quick_jump_container`（快捷跳转）、`lock_screen`（锁定）。小窗播放时这些控件拿不到，代码里统一用 `findViewById(...) ?: return` 或 `?.` 处理。

### 1.2 底栏对齐口径

底栏所有控件约束到 `@id/progress`，形成一条水平线：

- `current` 贴左（`constraintStart_toStartOf=parent`），`total` 贴右
- `progress` 宽度 `0dp` 夹在两者之间，天然居中
- `start` / `next` / `btn_mute` / `quick_jump_container` 统一 `constraintTop_toBottomOf=@id/progress`，且高度都是 `24dp`

**高度必须一致**：`quick_jump_container` 用过 `wrap_content` 时，12sp 文本实际高约 16dp，比 24dp 的按钮中心线低 4dp，肉眼可见错位。改成 `24dp` + `gravity=center_vertical` 后才对齐。

## 二、手势

### 2.1 双击分区

屏幕按宽度三等分（`VideoPlayer.kt` 的 `onDoubleTap`）：

| 区域 | 行为 |
| --- | --- |
| `x < width/3` | 快退 `VideoPlay.doubleTapSeekSeconds` 秒 |
| `x > width*2/3` | 快进同秒数 |
| 中间 | 播放 / 暂停 |

宽度优先取视图实宽（`this@VideoPlayer.width`），取不到时退回 `displayMetrics.widthPixels`，避免旋转瞬间 `width=0` 导致全屏都判成"左侧"。

### 2.2 纵向滑动：亮度 / 音量

`touchSurfaceMoveFullLogic` 判定左右半屏与方向，`touchSurfaceMove` 执行。**亮度与音量完全由本项目接管，不调 `super.touchSurfaceMove`**：

```kotlin
override fun touchSurfaceMove(deltaX: Float, deltaY: Float, y: Float) {
    when {
        mBrightness   -> showGestureIndicator(getBrightnessRatio(deltaY), ...)
        mChangeVolume -> showGestureIndicator(getVolumeRatio(deltaY), ...)
        else          -> super.touchSurfaceMove(deltaX, deltaY, y)  // 进度拖动必须交给基类
    }
}
```

两个易踩的点：

1. **进度拖动必须走 super**。`mSeekTimePosition` 在基类的 `touchSurfaceMove` 里计算，绕过它会导致横向拖进度条完全失效。
2. **亮度 / 音量不能走 super**。基类在同一方法里也会 `setStreamVolume` 和 `onBrightnessSlide`，两边同时写互相覆盖，表现为数值来回跳。

### 2.3 deltaY 的语义

`deltaY` 是**相对按下点的累计位移**，不是每帧增量。因此计算必须从按下时的起点值重算，不能逐帧累加：

```kotlin
val raw = (brightnessDown + (-deltaY / screenHeight) * BRIGHTNESS_SENSITIVITY)
```

逐帧累加会让亮度越滑越快（每帧都把总位移再加一遍）。

## 三、亮度与音量的应用

### 3.1 连续值与量化值分离

`getBrightnessRatio` / `getVolumeRatio` 都返回**连续值**，同时内部按系统档位量化后写入系统：

| 用途 | 取值 |
| --- | --- |
| 写入系统 | 量化值（亮度 100 级、音量 `STREAM_MUSIC` 的 max，通常 15） |
| 喂指示器 | 未量化的连续值 |

分离的原因：系统音量只有 15 档，直接把量化值给指示器会看到填充条一格一格跳。指示器用连续值才平滑，而系统仍按整数档对齐（与按音量键一致）。

### 3.2 只在档位变化时写入

两个函数都维护 `lastAppliedVolume` / `lastAppliedBrightnessLevel`，**只有档位真正变化才写系统**：

- 音量：每帧调 `setStreamVolume` 会导致音频流反复重置，出现爆音和卡顿
- 亮度：每帧写 `window.attributes` 会让 WindowManager 反复走测量/布局流程，出现可见顿挫

滑动开始时把起点档位写进 `lastApplied*`，避免第一帧重复写。

### 3.3 亮度起点

`systemBrightnessRatio()` 优先读 `window.attributes.screenBrightness`；该值在窗口未单独设置过亮度时为 -1，此时回落到 `Settings.System.SCREEN_BRIGHTNESS / 255`。每次开始滑动都重新读取，保证从画面当前亮度接着调。

亮度下限 `BRIGHTNESS_MAX_LEVEL` 量化后最小为 0，实际写入时不做下限限制会导致全黑看不见画面，调暗到 0 时需注意。

## 四、指示器

`video_gesture_indicator.xml` + `VideoPanelWindow.kt` 之外的辅助资源：

| 资源 | 作用 |
| --- | --- |
| `video_gesture_indicator.xml` | 容器 + 竖条（`FrameLayout`）+ 图标 |
| `video_gesture_indicator_bg.xml` | 半透明圆角底 `#99000000` |
| `video_gesture_indicator_track.xml` | 轨道 `#33FFFFFF` |
| `video_gesture_indicator_fill.xml` | 填充 `#FFFFFFFF` |

### 4.1 用 scaleY 而不是改高度

填充块是 `match_parent` 固定高，靠 `scaleY` 表达档位，`pivotY` 设在底边：

```kotlin
fill.pivotY = fill.height.toFloat()
fill.scaleY = ratio.coerceAtLeast(0.002f)
```

改 `layoutParams.height` 会触发父容器重新测量，高度取整后出现上下抖动；`scaleY` 只走渲染层。**不要用 `ViewPropertyAnimator` 做补间**——连续滑动时每帧 `cancel()` + `start()`，`cancel` 会把属性回滚到动画起点，动画永远到不了目标值，表现为"数值变了、条不动"。

### 4.2 左右分置

音量指示器靠左、亮度靠右，通过改 `gesture_indicator_host` 的 `RelativeLayout.LayoutParams` 实现（`alignIndicator()`）。位置未变时直接 return（`indicatorAlignedRight`），避免每次滑动都触发重新布局。

### 4.3 自动收起

`hideGestureRunnable` 在每次更新时重置 `postDelayed(600ms)`，松手 600ms 后淡出。

### 4.4 include 的坑

`<include>` 会用自身属性**覆盖**被包含布局根节点的 `layout_*` 属性。把 `layout_alignParentStart` 写在被包含布局里、`<include>` 只给宽高，会导致定位失效。正确做法是用外层 `RelativeLayout` 包裹 `<include>`，把定位属性写在包裹层上。

## 五、选集与倍速面板

两个面板都是挂载在播放 Activity 上的独立 `Dialog`，共用 `VideoPanelWindow.applyVideoPanelWindow()`：

```kotlin
lp.width  = (metrics.widthPixels * widthFraction).toInt()
lp.height = metrics.heightPixels
lp.gravity = Gravity.END or Gravity.TOP
```

宽度比例：选集 0.40、倍速 0.30。

### 5.1 必须贴顶铺满，不能加内边距

面板是**独立 Dialog 窗口**，不继承播放器的沉浸式全屏状态。系统会给它一条状态栏高的 top inset，若再给内容加上内边距或垂直居中，标题行（"选集（33）"）会被推出面板背景绘制区，屏幕上只剩列表——表现就是"顶部少一块"。

因此需要三件事配合：

1. `FLAG_LAYOUT_NO_LIMITS | FLAG_LAYOUT_IN_SCREEN`：窗口铺到物理屏幕边界
2. decorView 打沉浸式 `systemUiVisibility`（API 30+ 走 `windowInsetsController.hide`）
3. 清掉 `Theme.Dialog` 自带窗口背景

三者缺一，紧凑的标题行就会被裁掉。

### 5.2 高度不要扣系统栏

`heightPixels` 在沉浸式全屏下就是整屏高度。扣掉 insets 会让窗口比可用区矮，靠底部对齐的内容（倍速列表）整块落到屏幕外，看起来就是"倍速面板内容看不见"。

### 5.3 列表可滚动

- `switch_speed_video_dialog.xml`：不能有 `weight=1` 的占位 View 把列表挤到底部，`ListView` 用 `match_parent`
- `switch_episode_video_dialog.xml`：`ListView` 用 `0dp + weight=1`，让标题固定、列表占满剩余并可滚动

### 5.4 onStart 里要再应用一次

`Dialog.show()` 内部会按 decor 重新下发一次 `LayoutParams`，覆盖掉 `initList` 里设好的尺寸。因此 `onStart()` 里必须再调一次 `applyVideoPanelWindow()`。

## 六、播放键在加载中的可见性

基类 `changeUiToPreparingShow` / `changeUiToPlayingBufferingShow` 会把 `mStartButton` 置为 `INVISIBLE`，观感上像"按钮没了"。本项目重写六个状态方法，在父类改动后重新拉回来：

```kotlin
mStartButton?.apply {
    alpha = if (loading) START_BUTTON_DISABLED_ALPHA else 1f
    visibility = VISIBLE
    isEnabled = !loading
    isClickable = !loading
}
```

同时重写 `startDismissControlViewTimer()`，加载中直接 `cancelDismissControlViewTimer()`——否则倒计时到点 `hideAllWidget` 会把整层控制（含播放键）一起藏掉。

## 七、提示系统

`showOverlayTip()` 是统一提示入口，无底板纯文字 + 阴影。三个参数：

| 参数 | 含义 |
| --- | --- |
| `message` | 主行文字；传 null 即隐藏 |
| `delay` | 停留时长；<= 0 表示一直显示 |
| `delta` | 副行文字（相对调整量） |
| `pinned` | 钉住标记 |

### 7.1 pinned 的作用

基类 `touchSurfaceUp()` 每次抬手都会调 `dismissProgressDialog()`。双击快进 / 底栏快进快退的抬手紧跟在同一串事件里，若不区分，提示刚显示就被清掉（"闪一秒就没"）。

因此一次性进度提示传 `pinned = true`，`dismissProgressDialog()` 遇到 pinned 直接 return，交给 `TIP_HIDE_DELAY_MS` 自己收尾；拖动产生的提示跟手，抬手即收。

### 7.2 拖动浮层的参数语义

`showProgressDialog(delta, currentTime, currentDiff, totalTime, totalDiff)` 的 `currentDiff` 传的是 **`mSeekTimePosition`（目标位置）而非差值**。实际调整量要用 `mDownPosition`（按下时位置）相减：

```kotlin
val seekDelta = currentDiff - mDownPosition
```

把它当差值用，副行就会显示成"当前播放进度"而不是"调整了多少"。

### 7.3 调整量文案

`formatDelta()` 按秒 → 分 → 时分秒逐级进位：`+10秒` / `+1分30秒` / `+1时50分30秒`。

## 八、图标

| 资源 | 说明 |
| --- | --- |
| `ic_lock_closed.xml` / `ic_lock_open.xml` | 锁定按钮两态，**描边式**矢量（`strokeWidth=2.4`） |
| `ic_volume_off.xml` | 静音态 |

锁定图标原本来自 `gsyvideoplayer-java` 的 `res/drawable-xxhdpi-v4/lock.png`（96×128 实心粗体），与播放器其它线性图标风格不一。现在项目内用同名矢量覆盖，并在 `LockClickListener` 里显式切换两态。

**注意**：想覆盖库里的 PNG，必须放在 `drawable-xxhdpi/` 目录放同名 PNG；只在 `drawable/` 放矢量 XML **不会生效**——密度目录优先级高于无密度目录。

## 九、涉及文件清单

| 文件 | 职责 |
| --- | --- |
| `VideoPlayer.kt` | 控制层全部逻辑：手势、提示、面板、图标状态 |
| `ChoiceEpisodeDialog.kt` / `ChoiceSpeedDialog.kt` | 选集 / 倍速面板 |
| `VideoPanelWindow.kt` | 面板窗口尺寸与沉浸式设置（共用） |
| `video_layout_controller.xml` | 竖屏控制层 |
| `video_layout_controller_full.xml` | 横屏控制层 |
| `video_gesture_indicator.xml` | 亮度 / 音量指示器 |
| `switch_episode_video_dialog.xml` / `switch_speed_video_dialog.xml` | 面板内容布局 |
