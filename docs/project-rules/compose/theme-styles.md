# Compose UI 规范 — 主题与样式

> 原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物：§7，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。生效范围、执行方式、老代码策略等通用约定见 [README.md](./README.md)。
> **最后更新**：2026-09-10

---

## 7. 主题与样式

### 7.1 颜色使用

- **必须**优先使用 `MaterialTheme.colorScheme.xxx` 获取色值。
- **禁止**直接调用 `colorResource(R.color.xxx)` 绕过主题系统。（例外：`Color.Transparent`、`Color.Black`、`Color.White` 等标准色允许直用）
- **禁止** 在 Composable 函数体内用 `Color(0xFFxxxxxx)` 硬编码，色值必须来自 `ThemeEntity` 或 `MaterialTheme`。

### 7.2 魔法数字

- 所有 dimens 必须集中定义在 `ui/theme/Dimensions.kt` 的 `AppDimens`（**已建立**，目录树见 `structure.md` §1；原就地定义的 `PageDimens` 已并入并删除，禁止再另起平行对象）。
- 所有 shapes 必须集中定义在 `ui/theme/ComposeShapes.kt`（**已建立**）。圆角值不得在 Compose 侧另设常量，一律经 `composePanelShape()` / `composeActionShape()` 从 `lib/theme/UiCorner` 取，与 XML 侧保持同源。
- **禁止**在 Composable 体内裸写 `16.dp`、`12.dp`、`0.8f`。
- 动画参数（时长档位、easing、spring、无限动画）**必须**遵循 §7.6，禁止调用点随手写 `tween(200, ...)`、`tween(600, ...)` 这类无档位时长。
- **禁止**自定义 `CubicBezier` / `keyframes` 曲线散落多处，新增自定义曲线必须集中定义在 `ui/theme/AnimationSpecs.kt`（**目标态文件，首次落地时创建**）并注释用途。

```kotlin
// ui/theme/Dimensions.kt（目标态示例，落地时创建）
object AppDimens {
    val cardCornerRadius = 12.dp
    val listItemVerticalPadding = 12.dp
    val listItemHorizontalPadding = 16.dp
    val listItemIconSpacing = 16.dp
}

// ui/theme/Shapes.kt（同上）
val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)
```

### 7.3 图片加载

> **技术选型：本项目图片框架统一为 Glide**（存量技术栈，全项目已依赖）。不引入 Coil / Fresco 等第二套框架；如未来要换，属于架构级决策，必须全局迁移，禁止单个 PR 局部混用。

- **必须**走 `ui/widget/components/` 下的统一封装组件（暂名 `AppImage.kt`，**目标态文件，当前尚未建立**，落地时创建并同步 `structure.md` §1 目录树）加载图片，内部用 Glide 的 bitmap 链路。封装组件落地前，新 Compose 代码按下述链路模式实现，禁止用 View 版 API 过渡：

```kotlin
Glide.with(context)
    .asBitmap()
    .load(source)
    .apply(RequestOptions().override(widthPx, heightPx)) // 显式尺寸，禁止全尺寸解码
    .into(pendingTarget)
```

- **必须**用自持的 `PendingTarget<Bitmap>` 承接结果并交给 `Image(bitmap)` 渲染，`DisposableEffect` 的 `onDispose` 里 `clear()` target 取消 in-flight 请求——页面滑走后 Glide 继续解码就是白烧内存和 CPU。
- **禁止**在 Composable / ViewModel 里手写 `withContext(Dispatchers.IO) { BitmapFactory.decode... }` 自己解码 bitmap 塞 `Image()`——缓存、采样率、请求去重、取消逻辑全要自己维护，纯造轮子。
- **禁止**在 Compose 层使用 View 版 API（`Glide.with(...).into(imageView)`）；老 XML 代码里的存量调用不动，新代码一律走上面的 bitmap 链路。

### 7.4 字体与排版

- **必须**通过 `MaterialTheme.typography.xxx` 拿字体。
- **禁止**在 Composable 内直接调 `FontFamily` 构建。

### 7.5 字符串资源规范

- **必须**所有用户可见文案通过 `stringResource(R.string.xxx)` 获取，禁止在 Composable 内硬编码中文字符串。"用户可见"包括 `contentDescription`——图标无障碍描述同样禁止硬编码中文，机器检测覆盖该参数（见 `migration-review.md` §14.1）。
- **禁止**在 ViewModel 里拼接展示文案（如 `"已复制" + item.name`）。ViewModel 只发数据，文案拼接在 UI 层完成。
- **推荐** string resource 命名保持简洁可读，允许使用缩写或分层前缀（用斜杠表示层级分组），示例：`theme_title`、`theme_card_delete_confirm`、`config/theme/list_empty`。
- **例外**：纯调试用的 `Log` 消息、`TODO` 注释中的文案不需要走 string resource。

```kotlin
// ❌ 违规
Text("已复制 ${theme.name}")

// ✅ 正确
Text(stringResource(R.string.theme_copied, theme.name))
```

### 7.6 动画规范

> 存量动画用法总体合规（`AnimatedVisibility`、`animateColorAsState`、`graphicsLayer` 旋转、`rememberInfiniteTransition` shimmer），本节是**固化现状 + 收口时长档位 + 约束未来代码**，不是补课。

#### 7.6.1 时长与曲线档位（强制）

时长**只允许三档**，禁止 `tween(200, ...)`、`tween(600, ...)` 这类档位外取值：

| 档位     | 时长  | 用途                                        | 曲线                  |
| -------- | ----- | ------------------------------------------- | --------------------- |
| 微交互   | 150ms | 按压缩放、颜色态切换、`animateColorAsState` | `FastOutSlowInEasing` |
| 标准     | 300ms | 展开/收起、`AnimatedVisibility` 进出场      | `FastOutSlowInEasing` |
| 进场过渡 | 450ms | 页面级 `Crossfade`、大图位替换              | `FastOutSlowInEasing` |

- **弹性动画**统一 `SpringSpec(stiffness = Spring.StiffnessMediumLow)`（存量 `CheckSourceScreen` 已在用，固化）；禁止调用点散落 `spring(...)` 随手传 stiffness/dampingRatio。
- **禁止**手写帧循环（`withFrameNanos` / `while` 循环）实现动画——无限动画一律 `rememberInfiniteTransition`，一次性动画一律 `animate*AsState` 或 `Animatable`。
- `Animatable` 手动驱动的场景（手势联动等）必须在 `DisposableEffect` 的 `onDispose` 里 `cancel()`。

#### 7.6.2 动画属性选择（性能相关）

- 位移、缩放、旋转、透明度**必须**走 `graphicsLayer` / `Modifier.alpha` / `Modifier.scale`，禁止对参与 layout 的属性（`padding`、`size`、`offset` modifier 链上的 dp 值）做逐帧动画——layout 脏化 vs 只脏 GPU 图层，同屏 20 个 item 同时动时帧率差一倍以上。
- 高度展开/收起类（必须动 layout 的场景）优先 `AnimatedVisibility` + `expandVertically/fadeIn` 组合，其次 `animateContentSize`；禁止 `animateDpAsState` 逐帧改 `height = x.dp` 的裸写法。

#### 7.6.3 无限动画生命周期（强制）

- `rememberInfiniteTransition` **必须**传 `label` 参数（无障碍：TalkBack 可感知，且 composition 离开时自动取消）。
- 无限动画组件**必须**保证离开 composition 即停（`AnimatedVisibility` 包裹的退出分支要覆盖到）。禁止在 `LaunchedEffect` 里用 `while(true) { delay(16); ... }` 自转——白烧 CPU + 电池，Systrace 上一眼假。

#### 7.6.4 系统"减少动态效果"（强制）

- 读取 `Settings.Global.ANIMATION_SCALE`，`CompositionLocal`（建议 `LocalAnimationScale`，Application 初始化读一次 + `ContentObserver` 监听变化）下发。
- `scale < 0.5f` 时：装饰性动画（shimmer、转圈、背景流动）**时长归零直接 snap 到终态或静态样式**；功能性动画（页面进出场）保留。
- **禁止** Composable 内直接读 `Settings.Global`（读 ContentResolver 有 IPC 成本，不能进 composition 热路径）。

### 7.7 顶栏（TopBar）统一规范

> 本项目的顶栏颜色/背景/阴影统一由 `TopBarConfig`（用户可配置：样式 default/regular、主色 tagBarColor、透明度 tagBarAlpha、常规样式壁纸 wallpaper）驱动。Compose 与 View 两套体系各有其接入入口，**禁止**在调用点直接硬编码 `MaterialTheme.colorScheme.secondary` 或 `ThemeStore.primaryColor` 绕过统一体系。

#### 7.7.1 Compose 页面顶栏（强制）

- **必须**通过 `pageTopBarColors()`（`ui/theme/CommonPageColors.kt`）取色，返回 `PageTopBarColors`（containerColor/contentColor/cornerRadius/shadowElevation/wallpaperFile/wallpaperAlpha）。
- **必须**用 `Modifier.pageTopBarBackground(colors)` 承载顶栏容器（fillMaxWidth + shadow + clip + background + 壁纸），将 `containerColor` 设为 `Color.Transparent` 并把图标/标题色设为 `topBarColors.contentColor`（避免容器色被 TopAppBar 自身重绘覆盖）：

```kotlin
val topBarColors = pageTopBarColors()
TopAppBar(
    modifier = Modifier.pageTopBarBackground(topBarColors),
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = topBarColors.contentColor,
        titleContentColor = topBarColors.contentColor,
        actionIconContentColor = topBarColors.contentColor
    ), ...
)
```

- **禁止**再使用旧 API `pageTopBarContainerColor()`、**禁止**手写 `MaterialTheme.colorScheme.secondary` 作为顶栏容器色。
- 阴影规则（`shadowElevation`）：`transparentNavBar` → `0.dp`；`alphaPercent < 100` → `0.1.dp`；否则用 `context.elevation`。Compose 侧（`CommonPageColors.kt`）与 View 侧（`TitleBarConfig.kt`）**均已**按此规则实现，**禁止**再引入"regular 风格 + 圆角 → 阴影强制置零"的分支（历史 bug，两套体系均已修复）。

#### 7.7.2 Dialog / 浮层顶栏

- 在 `Dialog` / `Surface` 浮层内，**禁止**用 `pageTopBarBackground` modifier（其内部依赖 `context.transparentNavBar`，在浮层语义下可能不符预期）。
- **必须**改用 `pageTopBarColors().containerColor` 方案：取 `val topBarColors = pageTopBarColors()`，`TopAppBar` 的 `containerColor = topBarColors.containerColor`，`titleContentColor` / `navigationIconContentColor` / `actionIconContentColor` 全用 `topBarColors.contentColor`（同 §7.7.1 示例，仅容器色不同：填实色而非 Transparent + pageTopBarBackground）。

#### 7.7.3 View 体系顶栏

- View 体系页面/弹窗顶栏 **必须**统一走 `TitleBar`（`ui/widget/TitleBar.kt`）`applyTopBarConfig()`，由 `TopBarConfig` 计算背景/圆角/壁纸/文字色。
- 裸 `androidx.appcompat.widget.Toolbar` 如需接入统一色，**必须**通过 `TopBarConfig` 计算容器色（`withOpacity(backgroundColor, alphaPercent)`，`STYLE_REGULAR` 用 `resolveBackgroundColor` / `wallpaperAlpha`，否则 `tagBarColor ?: primaryColor` / `tagBarAlpha`），**禁止**直接 `setBackgroundColor(primaryColor)` 写死。
- `TitleBar` 上的 `skipTopBarConfig="true"` 是「有意跳过统一主题」的显式声明，使用前必须确认意图（见 §7.7.4）。

#### 7.7.4 有意例外（改前必读，禁止"修掉"）

以下两处顶栏**有意**不走统一主题，属于设计决策而非 bug，**禁止**在无需求变更时把它们"接入"统一色：

- **书籍详情页**（`ui/book/info/BookInfoActivity.kt`）：顶栏被强制为透明（`binding.titleBar.setBackgroundResource(R.color.transparent)`），使封面图从顶栏后方露出。改成统一色会破坏封面展示。
- **书籍阅读页**（`ui/book/read/ReadMenu.kt`）：`view_read_menu.xml` 中 TitleBar 声明 `skipTopBarConfig="true"`。immersive 菜单顶栏跟随**阅读页背景色**（`ReadBookConfig.durConfig.curBgStr()`），非 immersive 用 `primaryColor`——跟随阅读主题是有意为之。

> 若确有需求要统一这两处（如"阅读页也想用全局顶栏壁纸"），需作为独立需求评审，禁止顺手改。

### 7.8 主题切换与页面重建

> 背景（完整证据链见 `docs/archive/主题列表应用主题后UI卡死根因分析.md` §15，2026-09-10 模拟器实测）：
> 日夜主题切换经 `AppCompatDelegate.setDefaultNightMode` 会触发 AppCompat **系统级再启动**（ActivityThread 直接重建新实例），
> 在此路径及 `Activity.recreate()` 原地重建路径下，重建出的 Compose 实例存在可达缺陷：**首帧组合正常，随后 snapshot 状态写入不再驱动重组/重绘**——
> 输入与 clickable 回调正常（`switchTab` 已执行）、`LaunchedEffect` 不重启、连 pointer 驱动的列表滚动都零帧；`Snapshot.sendApplyNotifications()` + `view.invalidate()` 手动唤醒均无效。
> 而同一进程内全新 `startActivity` 的实例全链路健康。二者差异仅在于窗口的建立路径（原地重建 vs 全新启动）。

本节为 Code Review 人工对照项：[人工] 不达标 PR 打回。

#### 7.8.1 重建路径选择（强制）

- **必须**：主题切换需要重建页面的，统一接管 `recreate()` 为「销毁 + 全新 `startActivity`」——新实例在前，再 `finish()` 旧实例，使新窗口以 **全新启动路径** 建立，规避系统原地重建带来的 Compose 重组冻结。**禁止**直接调用 `super.recreate()` / `Activity.recreate()`。
- **必须**：接管 `recreate()` 时应保留防重入守卫（`recreatePending` + `isFinishing`/`isDestroyed`），并在 `onCreate` 记录实例创建时刻；重建广播（`ThemeConfig.notifyRecreate` 1.5s 防抖后迟到的事件总线 `RECEIVE`）必须被「实例创建时刻 + 2s 宽限」拦截，避免二次重建打断正在建立的窗口。
  参考实现：`ui/config/theme/manage/ThemeManageActivity`（`recreate()` 接管 + `RECREATE_IGNORE_MS = 2000L` 宽限）。

#### 7.8.2 新建代码的优先方向（推荐）

- **强烈推荐**：纯 Compose 页面（不依赖 `values-night` 等 XML 资源的页面）主题切换**优先「状态驱动、不重建」**——把主题色作为可观察状态直接更新，Compose 自身完成重组重绘，从根上避开重建路径。`setDefaultNightMode` 只留给确实需要切换 XML 资源的场景（Dialog 样式、系统资源等）。
- **推荐**：新写主题切换逻辑前先对照本文件 §7 与 `ThemeConfig`/`LegadoTheme` 的现有实现，确认是否必须重建，能省则省。

#### 7.8.3 排查与迁移

- 发现「主题/顶栏/底栏等配置切换后，某些控件点击无反应、整页不刷新」时，先按三个探针定性：① 输入是否到达（clickable 是否执行）② snapshot 状态是否写入 ③ 重组探针是否触发。若①②通而③不触发，即为重建路径问题，按 §7.8.1 改造，不要改内容、不要堆「手动刷新」修复。
- 迁移老页面时，现有 `ThemeManageActivity` 已按本节落地；其它 `BaseComposeActivity` 页面如出现同类冻结，参照改造后再提交。

---
