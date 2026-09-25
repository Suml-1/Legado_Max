# Compose UI 规范 — 结构、命名与 API 契约

> 原 `UI-ARCHITECTURE.md`（2026-08-19）拆分产物：§1、§2、§3、§11、§12，章节编号沿用原编号，跨文件引用按「文件名 §编号」格式书写。生效范围、执行方式、老代码策略等通用约定见 [README.md](./README.md)。
> **最后更新**：2026-08-19

---

## 1. 目录结构规范

```
ui/
├── components/                     # 全局纯展示积木（跨 Feature 复用，无状态/无手势/无三方依赖，对外 API 稳定）〔目标态，首次落地时建〕
│   ├── AppTopBar.kt
│   ├── AppScaffold.kt
│   ├── AppListItem.kt
│   ├── AppButton.kt
│   ├── AppCard.kt
│   ├── AppEmptyState.kt
│   └── AppLoadingState.kt
├── widget/                         # 全局交互组件 & 第三方封装（带状态/手势/三方库依赖，区别于 components 的纯展示积木）
│   ├── components/                 # 新 Compose 通用组件一律进这里；可按业务域建子包归集（现有 card/ list/ dialog/ settings/ swipe/）
│   │   ├── AppImage.kt             # 图片加载统一入口（内部 Glide bitmap 链路，见 theme-styles.md §7.3）〔目标态，尚未建立〕
│   │   ├── BookBottomSheet.kt
│   │   └── ...
│   ├── TitleBar.kt                 # ┐
│   ├── SearchView.kt               # │ XML 时代自定义 View 存量混存区：迁移期保留原位不动，
│   ├── ...                         # ┘ 禁止继续往这里新增（见下方硬规则）
│   ├── image/                      # 同上，存量子包（CoverImageView、PhotoView 等）
│   ├── dialog/
│   └── recycler/
├── theme/                          # 主题层：颜色、尺寸、圆角、动效参数的唯一定义处
│   ├── LegadoTheme.kt
│   ├── CommonPageColors.kt
│   ├── ComposeActivitySupport.kt
│   ├── Dimensions.kt               # 尺寸令牌 AppDimens（已建立，见 theme-styles.md §7.2）
│   ├── ComposeShapes.kt            # 圆角令牌，取自 UiCorner（已建立，见 theme-styles.md §7.2）
│   └── ...
├── config/                         # Feature 示例：配置域（book/、about/ 等其余 Feature 均按此模式组织）
│   ├── theme/
│   │   ├── manage/
│   │   │   ├── ThemeManageScreen.kt
│   │   │   ├── ThemeManageViewModel.kt
│   │   │   ├── ThemeManageUiState.kt
│   │   │   ├── ThemeRepository.kt
│   │   │   └── components/         # Feature 私有 UI 组件，多文件组件归集于此
│   │   │       ├── ThemeCard.kt
│   │   │       └── ThemeEditDialog.kt
│   │   └── ...                     # 其他子域（legacy 等）
│   ├── widget/                     # Config 模块级通用组件（跨子域复用，不出 config 包）
│   │   ├── ConfigManageScaffold.kt
│   │   └── ConfigMultiSelectBar.kt
│   └── ...
└── README.md
```

> **现状对照**：`Dimensions.kt`（`AppDimens`）与 `ComposeShapes.kt` 已建立，前者吸收了原 `PageDimens`（精准管理/下载管理就近定义的过渡对象已删除），后者从 `lib/theme/UiCorner` 取圆角值，与 XML 侧同源。仍标〔目标态〕未建立的组件是 `components/` 下的 `AppTopBar` / `AppListItem` 等与 `AppImage.kt`；`ui/widget/components/` 下实际已落地的是 `AppPageTopBar` / `AppSearchBar` / `AppScaffold` / `AppBookCover`（书籍封面，Glide bitmap 链路）/ `BookBottomSheet` / `VerticalScrollbar` / `settings/`（设置面板与行）等。Feature 内部的 Compose 组件按需归入 `ui/[feature]/[子域]/compose/`（如 `ui/main/bookshelf/compose/`）。目标态组件落地时如沿用与树不同的命名，请回改本树。

> 注：每个 Feature 内部还允许 `[Feature]/widget/`（模块级通用组件，如 `config/widget/`），以及更深层子域的 `components/`（Feature 私有），规则见下方硬规则。

### 硬规则

- **禁止**在 Screen 文件内定义 `private fun` 形式的可复用 UI 组件。Screen 只管编排，不造积木。
- Feature 内部**允许**存在 `components/` 子目录（如 `config/theme/manage/components/`），用于归集该 Feature 专属的多文件 UI 组件。
- **禁止**跨 Feature 引用 `ui/[feature]/*/components/` 下的组件，这些组件对外不保证 API 稳定。跨 Feature 复用的组件必须提升到 `ui/widget/components/` 或 `ui/[模块]/widget/`（如 `ui/config/widget/`）。
- **禁止**跨层引用：`ui/widget/`（含 `ui/widget/components/`）和 `ui/theme/` 不准引用任何 Feature 包（`ui/[feature]/*`）的类；Feature 包可以单向引用 `ui/widget/` 和 `ui/theme/`。
- `ui/widget/` 根目录是**存量混存区**（XML 时代自定义 View）。新 Compose 通用组件一律进 `ui/widget/components/`，禁止继续往根目录堆放；旧 View 迁移完成前保留原位置不动。

---

## 2. 文件命名规范

| 类型                        | 命名模式                                        | 示例                                            |
| --------------------------- | ----------------------------------------------- | ----------------------------------------------- |
| Screen（页面级 Composable） | `*Screen.kt`                                    | `ReadRecordScreen.kt`、`ThemeManageScreen.kt`   |
| ViewModel                   | `*ViewModel.kt`                                 | `ThemeManageViewModel.kt`                       |
| Repository / DataSource     | `*Repository.kt` / `*DataSource.kt`             | `BookRepository.kt`、`ThemeRepository.kt`       |
| Feature 私有组件            | `*Card.kt` / `*Dialog.kt` / `*Menu.kt` 语义命名 | `ThemeCard.kt` 而非 `ThemeComponents.kt`        |
| 通用组件                    | `*Sheet.kt` / `*Scaffold.kt` / 语义命名         | `BookBottomSheet.kt`、`ConfigManageScaffold.kt` |
| State 定义                  | `*State.kt` / `*UiState.kt`                     | `ThemeManageUiState.kt`、`ConfigManageState.kt` |
| 工具函数扩展                | `*Utils.kt` / `*Extensions.kt`                  | `CodeViewExtensions.kt`                         |

### 硬规则

- **禁止** `*Components.kt` 大杂烩文件。如果文件里超过 3 个 `@Composable fun` 且职责不同，必须拆分。
- **禁止** `*View.kt` 命名用于 Compose 时代码（那是 XML 时代的遗毒，看到直接改掉）。

---

## 3. Composable API 契约

### 3.1 参数顺序（强制）

> 示例中的 `AppListItem` 为目标态设计，**尚未落地**；现行通用组件见 `ui/widget/components/`（`AppPageTopBar` / `AppSearchBar` / `AppScaffold` 等）。

```kotlin
@Composable
fun AppListItem(
    modifier: Modifier = Modifier,          // 1. Modifier 永远是第一个参数
    icon: ImageVector,                      // 2. 样式属性
    title: String,                          // 3. 内容属性
    subtitle: String? = null,               // 4. 可选内容
    onClick: () -> Unit = {},               // 5. 回调
    trailing: @Composable (() -> Unit)? = null // 6. 尾部插槽
)
```

### 3.2 命名规则

- **必须**用 DSL 风格命名回调：`onXxx`、`onXxxChange`、`onXxxClick`。
- **禁止** `mOnClick`、`clickListener`、`listener`。
- **禁止** 单个参数命名 `data`、`item`、`config`——必须带领域语义：`theme` → `themeItem` 或 `themeConfig`；`book` → `bookItem` 或 `bookEntity`。

### 3.3 可见性

- **必须**加 `@Composable` 注解。
- **推荐** `internal` 可见性（禁止无意义 `public`）。
- **禁止** 在 `*Screen.kt` 里暴露 `private` 可复用组件给其他文件引用（物理上不可能，但别搞 `internal` 套 Screen 文件）。

---

## 11. 组件拆分标准

### 11.1 何时拆分文件

满足下列任一条件即拆分：

1. 单个文件超过 **400 行**。
2. 逻辑过于复杂
3. 单个 `@Composable fun` 超过 **130 行**。
4. 文件里超过 **3 个** `@Composable fun` 且语义不相关（同为 `*Screen` 内部私有组件不算）。

### 11.2 何时抽取通用组件

- 同一种 UI 模式在 **2 个及以上** Feature 出现 → 抽到 `ui/widget/components/` 或 `ui/[模块]/widget/`。
- 同 Feature 内部的 `*Card`/`*Dialog`/`*Row` 只要**语义清晰**，即使只在本 Feature 内复用，也抽成独立文件（如 `ThemeCard.kt`），禁止堆在 `ThemeManageScreen.kt` 里用 `private fun` 实现。

---

## 12. 注释规范

### 12.1 原则与必须注释场景

与全项目统一，见 CLAUDE.md「Comments」一节（只写 Why 不写 What；业务决策 / 边界 / 魔法数字 / 暂时性 Hack 必须注释），本文件不重复维护。

### 12.3 KDoc 规范（公开 API 强制）

- `ui/widget/components/` 及 `ui/[模块]/widget/` 下跨 Feature 暴露的 `public` Composable **必须**编写 KDoc（下方示例的 `AppListItem` 为目标态、尚未落地，同 §3.1 说明）。
- 必须对所有非默认值参数进行说明。
- 示例：

```kotlin
/**
 * 列表页通用条目：图标 + 标题 + 可选副标题 + 尾部插槽。
 *
 * @param icon 条目主图标，未走 Material Icons 体系的资源请自行转 `ImageVector`。
 * @param title 主文案，超长由内部 ellipsis 处理，调用方无需截断。
 * @param subtitle 副标题，传 `null` 时条目自动收缩为单行高度。
 * @param onClick 整行点击回调，默认空实现表示纯展示条目。
 */
@Composable
fun AppListItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null
)
```

---
