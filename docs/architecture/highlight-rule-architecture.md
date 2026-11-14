# 高亮规则文件分布与架构

> 适用范围：阅读内容「高亮规则」功能。**有效**（2026-09-27 按当前代码复核：路径已随 `highlight/` 子包迁移更新，入口在 `ReadBookActivity`，正文走 `CharStyle` 数组、HTML 正文走 Span）。

本文档梳理的是阅读内容里的“高亮规则”功能。代码编辑器的 TextMate 语法高亮是另一套体系，见文末“与代码编辑器高亮的区别”。

## 核心文件

### 规则模型与存储

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRule.kt`
  - 高亮规则数据模型。
  - 字段包括：`pattern` 正则、`enabled`、`group`、`targetScope`、文字色、下划线样式、背景色/背景图、书籍作用范围 `scope`、排除范围 `excludeScope`。
  - `matchesScope(bookName, bookOrigin)` 用于判断规则是否适用于当前书籍。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleStore.kt`
  - 规则加载、保存、规则清洗、备份恢复的兼容门面。
  - 规则不是数据库表，主要以 JSON 存在 SharedPreferences。
  - 备份文件名为 `highlightRule.json`，背景图备份目录为 `highlightRuleBg`。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleRepository.kt`
  - UI/ViewModel 面向的规则仓库入口。
  - 封装规则、分组、当前分组、导入编码等操作，减少 UI 直接依赖 Store。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleDefaultRules.kt`
  - 默认预置规则生成。
  - 当前内置默认规则已经从 `HighlightRuleStore` 拆到这里。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleBackgroundManager.kt`
  - 高亮规则背景图迁移、恢复、使用文件收集和未使用文件清理。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleGroupStore.kt`
  - 高亮规则分组加载、保存、与规则列表同步。

- `app/src/main/java/io/legado/app/constant/PreferKey.kt`
  - 高亮规则相关偏好 key：
    - `highlightRuleDialog`
    - `highlightRuleBookTitle`
    - `highlightRuleBracketNote`
    - `highlightRuleItems`
    - `highlightRuleGroups`
    - `highlightRuleCurrentGroup`

### 配置入口与配置 UI

- `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
  - 两处入口，都直接 `showDialogFragment(HighlightRuleConfigDialog())`：阅读页菜单 `menu_highlight_rule_config`（`res/menu/book_read.xml`）与正文选中菜单 `menu_highlight_rule`（`res/menu/content_select_action.xml`，条目与顺序受 `TextMenuConfig` 管控）。
  - 早期版本走阅读设置里的 `highlightRuleConfig` preference（`pref_config_read.xml` + `MoreConfigDialog` 拦截），该入口已移除，不要再按 preference 去找。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleConfigDialog.kt`
  - 高亮规则列表页。
  - 支持启用/禁用、编辑、删除、导入、导出、分享、重置、选择规则、分组管理。
  - 只负责 UI 渲染、弹窗和用户交互，规则状态交给 `HighlightRuleConfigViewModel`。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleConfigViewModel.kt`
  - 高亮规则列表页 ViewModel。
  - 管理规则集合、当前分组、重置、增删改、导入、启用状态和同步保存。
  - 保存后通过 `EventBus.UP_CONFIG` 通知阅读配置刷新。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleEditDialog.kt`
  - 新增/编辑单条规则。
  - 处理规则名称、正则、分组、目标范围、书籍 scope、文字色、下划线、SVG 下划线、背景色、背景图、预览文本。
  - 背景图选择后会复制到内部目录。
  - 当前编辑规则、分组列表和编辑页临时状态由 `HighlightRuleEditViewModel` 保存。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleEditViewModel.kt`
  - 单条高亮规则编辑页 ViewModel。
  - 保存正在编辑的 `HighlightRule`、可选分组列表和正则切换状态。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightPresetRuleDialog.kt`
  - 预置规则选择弹窗。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleGroupManageDialog.kt`
  - 分组管理弹窗。
  - 支持新增、重命名、删除分组，以及导出分组规则。
  - 分组列表和规则列表由 `HighlightRuleGroupManageViewModel` 管理。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleGroupManageViewModel.kt`
  - 分组管理页 ViewModel。
  - 维护分组列表和规则列表，负责新增、重命名、删除分组时的数据同步。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleBottomSheet.kt`
  - 底部弹窗拖拽关闭的公共 helper。

### 配置页预览

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRulePreview.kt`
  - 根据规则和预览文本生成配置页的预览 `CharSequence`。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightPreviewTextView.kt`
  - 承载预览内容的控件，负责预览的实时刷新。

- `app/src/main/java/io/legado/app/ui/book/read/config/highlight/HighlightRuleStyle.kt`
  - 统一的高亮样式模型。
  - 配置页预览与阅读页都从 `HighlightRuleStyle.from(rule)` 取字段：预览直接打 Span，阅读页正文把它折成 `CharStyle` 写进每字符样式数组。

- 以下 Span 只服务配置页预览（除 `SvgPathParser.kt` 外都在 `config/highlight/` 下）：
  - `BgColorSpan.kt`
  - `BgImageSpan.kt`
  - `BoxTextSpan.kt`
  - `ItalicTextSpan.kt`
  - `SolidUnderlineSpan.kt`
  - `DashUnderlineSpan.kt`
  - `WaveUnderlineSpan.kt`
  - `DoubleUnderlineSpan.kt`
  - `SvgUnderlineSpan.kt`
  - `StrikeThroughSpan.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/config/SvgPathParser.kt`

## 阅读页实际渲染链路

阅读页最终渲染不直接复用配置页预览 Span。正文与标题走「规则匹配成 `CharStyle` 数组 → 排版期写进 `TextColumn` → Canvas 绘制」，只有 `<usehtml>` HTML 正文仍走 Span 标记。

主链路如下：

1. `TextChapterLayout` 初始化时通过 `HighlightRuleRepository.loadEnabledRules(appCtx)` 加载启用且正则不为空的规则；`compiledHighlightRules` 把 `pattern` 预编译成 `Regex`，命中时用的 `CharStyle` 由 `CompiledHighlightRule.charStyle`（内部调 `HighlightRuleStyle.from(rule)`）懒加载生成。
2. 规则是否生效统一由 `HighlightRule.appliesTo(isTitle, bookName, bookOrigin)` 判断：书籍 scope（`scope` / `excludeScope`）、排版作用域、主题作用域、标题/正文目标范围。
3. 正文：`buildBodyHighlightStyles(contents)` 把整章段落拼成全文跑一次规则，产出每字符 `Array<CharStyle?>` 与每段在全文中的起始偏移；多条规则命中同一字符时按字段级合并。
   - 拼接前会把每个 `<img>` 标签压成单个占位字符、`srcReplaceChar` 换成 `srcReplacementChar`，保证数组下标与排版文本逐字符对齐（见下节）。
   - 没有任何正文规则时直接跳过拼接与匹配。
4. `layoutBodyContents(...)` 排版每段正文时，`setTypeText(...)` 用 `bodyHighlightStyles.stylesAt(startAt(contentIndex), text.length)` 切出本段样式；标题、被图片拆出的残段等没有整章上下文的文本，就地用 `createHighlightStyles(text, isTitle)` 扫描。
5. 排版期逐字符消费样式数组：命中字体的字符按高亮字体重测宽度（`adjustWidthsForHighlightFont`），命中字距与命中行行距进入断行、两端对齐与翻页计算，样式字段随 `TextColumn` 落到行上。
6. `TextLine` 绘制时读取列上的样式字段：
   - `drawStyledBackgrounds(...)` 绘制背景色和背景图。
   - `drawStyledUnderlines(...)` 绘制实线、虚线、波浪线、双线、自定义 SVG 下划线。
7. 如果当前行有高亮样式，`checkFastDraw()` 会返回 false，避免走快速绘制路径。
8. HTML 正文是例外：`setTypeHtml(...)` 对解析出的 HTML 调 `applyHighlightRules(...)` → `applyRuleSpans(...)` 打 `ForegroundColorSpan` / `HighlightStyleSpan` / `HighlightTypefaceSpan`，再随 `TextHtmlColumn` 进入同一套绘制。

### 正文高亮的偏移对齐约束

整章匹配是为了让允许换行的规则能命中跨段内容（跨段对话、书名等），代价是**样式数组下标必须就是全文下标**，必须与排版文本逐字符对齐；一旦错位，含段评气泡/插图的段落会把它后面的段落整体推偏，跨段规则的高亮会落到气泡或错误字符上。约定：

- 一段正文在全文里的长度 = `content` 先做 `srcReplaceChar → srcReplacementChar`，再把每个 `<img>` 标签压成 **1 个占位字符**（段评气泡与文字内嵌小图在排版文本里本来就只占 1 个字符）。
- 大图不进排版文本，排版游标 `layoutTextOffset` 要额外 `+1` 跳过它在全文里的占位。
- 被大图拆成「图前 / 图后」两段时，后一段的 `bodyHighlightStart` 要跟着游标走，不能沿用段落起始偏移。
- 段落之间的 `'\n'` 在全文里占 1 个下标，而排版文本不含它（切片只取本段长度，不取分隔符）。
- 占位必须是单字符：`srcReplaceStr` 虽然声明为 `String`，取值就是单字符；哪天改成多字符占位不会报错，只会静默错位。

相关文件：

- `app/src/main/java/io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt`
  - 加载、编译、匹配高亮规则，产出正文用的 `CharStyle` 数组。
  - HTML 正文路径仍会转换出 `ForegroundColorSpan` / `HighlightStyleSpan` / `HighlightTypefaceSpan`。

- `app/src/main/java/io/legado/app/ui/book/read/page/provider/HighlightStyleSpan.kt`
  - 阅读排版阶段传递高亮样式的轻量 Span，只用于 HTML 正文路径。
  - 本身不绘制，只携带样式参数。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/column/TextBaseColumn.kt`
  - 文本列基础接口，包含高亮相关字段。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/column/TextColumn.kt`
  - 普通文本列，保存文字色、下划线、背景色、背景图等字段。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/column/TextHtmlColumn.kt`
  - HTML 文本列，同样保存高亮样式字段。

- `app/src/main/java/io/legado/app/ui/book/read/page/entities/TextLine.kt`
  - 最终 Canvas 绘制层。
  - 负责连续区间合并、背景色/背景图绘制、下划线绘制、背景图缓存、背景图清理。
  - 九宫格背景（`bgImageFit == 3`）由 `drawNineSlice(...)` 绘制：目标矩形 = 匹配区 + 自动外扩（严格/智能/强制三档）+ 手动四边间距，四角保持原始比例、边条与中段拉伸。切分比例（`npLeft/npTop/npRight/npBottom`）与四角厚度的换算口径同时供排版期 `computeNeighborPush`（强制策略推开邻字）使用，两处必须同源。
  - **行末/行首气泡角预留**（2026-11-14，参考 MD3 分支）：匹配段顶到行末（延续到下一行）时，行末没有邻字可推（`computeNeighborPush` 的 widthAdd 会落到下一行首字上，对本行无效）、也没有段末压缩可借，角装饰会溢出正文列右边界被裁掉。排版期在 `setTypeText` 行循环里对这类行计算 `wrapEndBleed`（= `nineSliceSideWidth` 按字号夹过的右角厚 + 手动右间距，与绘制层外扩口径同源；严格策略不外扩不预留），两端对齐按负 residual 压缩行宽、自然排列由 `exceed(extraRightMargin)` 压回，行尾落在正文列右边界内，行末照常画出完整角装饰。段末行走 `endBleed` 分支、标题走 `titleEndExtra`，互不重复。
  - **行首镜像预留**：续行行首（`lineStart > 0`）落在气泡段里时同理计算 `wrapStartBleed`（左角厚 + 手动左间距），行内容整体右移让出左角，两端对齐把右移量同步补进 desiredWidth（行尾仍落在正文列右边界）、自然排列超出时由 exceed 压回。只对强制策略预留（智能策略行首只外扩 0.1em 固定兜底量、严格不外扩）；段首行（`lineStart == 0`）的行首匹配由 `lineStartAdd` / `indentAdd` 机制负责，不在这里重复算。

## 默认预置规则

默认预置规则在 `HighlightRuleDefaultRules.create(context)` 中生成，不是独立 JSON 文件。

当前内置 id 包括：

- `dialog_default`
- `book_title_default`
- `bracket_note_default`
- `title_emphasis_default`
- `thought_default`
- `narrator_default`
- `emphasis_default`
- `poetry_default`
- `ellipsis_default`
- `number_default`
- `english_default`
- `date_time_default`

其中前三个还保留了旧版独立开关：

- `highlightRuleDialog`
- `highlightRuleBookTitle`
- `highlightRuleBracketNote`

## 备份与恢复

高亮规则已接入备份恢复。

相关文件：

- `app/src/main/java/io/legado/app/help/storage/Backup.kt`
  - 写出 `highlightRule.json`。
  - `stageHighlightRuleBackgroundFiles(...)` 会把规则引用的背景图一起放入 `highlightRuleBg`。

- `app/src/main/java/io/legado/app/help/storage/Restore.kt`
  - 读取 `highlightRule.json`。
  - 调用 `HighlightRuleStore.restoreBackupData(...)` 恢复规则、分组、当前分组和背景图。

- `app/src/main/java/io/legado/app/api/controller/BackupController.kt`
  - Web 备份接口里也统计和导出高亮规则及背景图。

- `app/src/main/java/io/legado/app/help/storage/BackupInfoHelper.kt`
  - 备份信息展示中统计高亮规则数据大小。

- `app/src/main/java/io/legado/app/help/storage/BackupSelectorConfig.kt`
  - 备份选择项中注册 `highlightRule.json`。

### 字段名稳定性约束

`HighlightRule` 与 `HighlightRuleStore.BackupData` 是 GSON 反射序列化的数据类，
但不在 `data.entities` 包内，release 构建必须在 `app/proguard-rules.pro` 中
保留这两个类的字段名。历史上曾因字段被 R8 混淆成 `a`/`b` 导致：升级后规则失效或丢失、
备份恢复静默失败、导出键名变成单字母。新增需要持久化的高亮规则数据类时必须同步加 keep 规则。

## 需要注意的遗留点

- 早期文档提到的 `applyBuiltInHighlightRules(...)` / `applyHighlightRulesFromStore(...)` 已从 `TextChapterLayout.kt` 中删除，不要再按这两个方法名找入口。
- 高亮结果有两套表达，改动时别只看一套：

```text
正文/标题：compiledHighlightRules -> buildBodyHighlightStyles / createHighlightStyles -> Array<CharStyle?>
          -> TextColumn -> TextLine.drawStyledBackgrounds / drawStyledUnderlines
HTML 正文：compiledHighlightRules -> applyHighlightRules -> applyRuleSpans -> HighlightStyleSpan
          -> TextHtmlColumn -> 同上
```

- 「高亮规则相关 Kotlin 文件里的中文注释是乱码」是终端按 GBK 解码 UTF-8 文件造成的观感（Windows PowerShell 不指定编码读文件时会出现），不是文件坏了。2026-09-27 复核 `ui/book/read/config/highlight/` 下源码：无 `U+FFFD`、无典型乱码串。

## 与代码编辑器高亮的区别

代码编辑器语法高亮是另一套 TextMate 架构，和阅读内容高亮规则无关。

相关文件：

- `app/src/main/java/io/legado/app/ui/code/CodeEditViewModel.kt`
  - 加载 TextMate grammar 和 theme。

- `app/src/main/java/io/legado/app/ui/code/CodeEditActivity.kt`
  - 设置编辑器语言和主题。

- `app/src/main/java/io/legado/app/ui/code/TextMateColorScheme2.kt`
  - TextMate 配色方案扩展。

- `app/src/main/assets/textmate/languages.json`
  - TextMate 语言注册表。

- `app/src/main/assets/textmate/javascript/syntaxes/javascript.tmLanguage.json`
  - JavaScript 语法规则。

- `app/src/main/assets/textmate/html/syntaxes/html.tmLanguage.json`
  - HTML 语法规则。

- `app/src/main/assets/textmate/markdown/syntaxes/markdown.tmLanguage.json`
  - Markdown 语法规则。

- `app/src/main/assets/textmate/d_*.json`、`app/src/main/assets/textmate/l_*.json`
  - 深色/浅色主题配置。
