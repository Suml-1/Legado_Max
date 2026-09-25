# 高亮规则说明

本项目包含两种高亮规则系统：**替换规则 (ReplaceRule)** 和 **样式高亮规则 (HighlightRule)**。两者功能定位不同，机制各异。

---

## 一、替换规则 (ReplaceRule)

### 1.1 功能定位

替换规则用于**在加载书籍内容时进行文本替换/净化**，是一种内容预处理机制。它会在书籍内容加载到阅读界面之前，对原始文本进行修改。

典型应用场景：
- 去除广告文字
- 修正错别字
- 替换特定格式文本
- 净化不良内容

### 1.2 数据结构

定义位置：[ReplaceRule.kt](app/src/main/java/io/legado/app/data/entities/ReplaceRule.kt)

```kotlin
@Entity(tableName = "replace_rules")
data class ReplaceRule(
    var id: Long,                    // 主键（即按它来识别是否重复添加）
    var name: String,                // 规则名称
    var group: String?,              // 分组
    var pattern: String,             // 匹配模式（要替换的内容）
    var replacement: String,         // 替换为的内容
    var scope: String?,              // 作用范围（书籍名或源名）
    var scopeTitle: Boolean,         // 是否作用于标题
    var scopeContent: Boolean,       // 是否作用于正文（默认 true）
    var excludeScope: String?,       // 排除范围
    var isEnabled: Boolean,          // 是否启用
    var isRegex: Boolean,            // 是否为正则表达式（默认 true）
    var timeoutMillisecond: Long,    // 超时时间（默认 3000ms）
    var order: Int                   // 排序
)
```

### 1.3 语法说明

#### 匹配模式 (pattern)

- **正则模式** (`isRegex = true`)：使用 Kotlin 正则表达式语法
  - 示例：`广告.*?结束` 匹配 "广告" 到 "结束" 之间的内容
  - 示例：`\\[.*?\\]` 匹配方括号内的内容

- **普通文本模式** (`isRegex = false`)：直接匹配文本
  - 示例：`错别字` 精确匹配该文本

#### 替换内容 (replacement)

- 可以是任意文本
- 正则模式下支持捕获组引用：`$1`、`$2` 等
- 留空表示删除匹配内容

#### 作用范围 (scope)

- 格式：`书籍名,书源名` 或单独指定
- 为空表示全局生效
- `excludeScope` 用于排除特定书籍/源

### 1.4 执行机制

执行位置：[ContentProcessor.kt](app/src/main/java/io/legado/app/help/book/ContentProcessor.kt)

执行流程：

```
书籍内容加载
    ↓
去除重复标题
    ↓
重新分段（可选）
    ↓
简繁转换（可选）
    ↓
【替换规则执行】← 按顺序遍历所有启用的规则
    ↓
返回处理后的内容
```

关键代码逻辑：

```kotlin
getContentReplaceRules().forEach { item ->
    val tmp = if (item.isRegex) {
        mContent.replace(item.regex, item.replacement, timeout)
    } else {
        mContent.replace(item.pattern, item.replacement)
    }
    if (mContent != tmp) {
        effectiveReplaceRules.add(item)  // 记录生效的规则
        mContent = tmp
    }
}
```

### 1.5 使用 usehtml 实现富文本高亮

替换规则不仅能替换纯文本，还能通过 `<usehtml>` 标签实现**富文本高亮**，这是普通替换规则的高级用法。

#### 1.5.1 机制说明

`<usehtml>` 是一种特殊的标记，内容会被解析为 HTML 并渲染：

1. **识别阶段**：`ContentProcessor` 在替换规则执行前，先提取 `<usehtml>...</usehtml>` 内容
2. **占位替换**：用占位符临时替换，避免被其他替换规则修改
3. **排版阶段**：`TextChapterLayout` 检测到 `<usehtml>` 后，调用 `setTypeHtml()` 方法
4. **HTML 解析**：使用 `HtmlCompat.parseAsHtml()` 解析 HTML 标签
5. **样式应用**：应用 Span 样式后渲染到页面

#### 1.5.2 支持的 HTML 标签

| 标签 | 说明 | 示例 |
|-----|------|------|
| `<b>` / `<strong>` | 粗体 | `<b>重点内容</b>` |
| `<i>` / `<em>` | 斜体 | `<i>斜体文字</i>` |
| `<u>` | 下划线 | `<u>下划线文字</u>` |
| `<s>` / `<del>` | 删除线 | `<s>删除内容</s>` |
| `<big>` | 大号字体 | `<big>大字</big>` |
| `<small>` | 小号字体 | `<small>小字</small>` |
| `<sub>` | 下标 | `H<sub>2</sub>O` |
| `<sup>` | 上标 | `x<sup>2</sup>` |
| `<font color="">` | 指定颜色 | `<font color="#FF0000">红色</font>` |
| `<a href="">` | 超链接 | `<a href="url">链接</a>` |
| `<img src="">` | 图片 | `<img src="图片地址">` |
| `<p>` | 段落 | `<p>段落内容</p>` |
| `<div>` | 块级容器 | `<div>内容</div>` |
| `<br>` | 换行 | `第一行<br>第二行` |
| `<hr>` | 分隔线 | `<hr>` |
| `<button>` | 按钮 | `<button>按钮名@onclick:js代码</button>` |

#### 1.5.3 使用示例

**示例 1：文字颜色高亮**

将对话内容替换为带颜色的 HTML：

```
规则名称：对话颜色高亮
匹配模式：(")([^"]+)(")
替换为：<usehtml><font color="#FF8C00">$1$2$3</font></usehtml>
```

**示例 2：重点内容加粗+颜色**

```
规则名称：重点强调
匹配模式：【([^】]+)】
替换为：<usehtml><b><font color="#DC143C">【$1】</font></b></usehtml>
```

**示例 3：书名号绿色+斜体**

```
规则名称：书名号样式
匹配模式：《([^》]+)》
替换为：<usehtml><i><font color="#228B22">《$1》</font></i></usehtml>
```

**示例 4：章节标题居中+加粗**

```
规则名称：章节标题样式
匹配模式：^第([0-9]+)章\s+(.+)$
替换为：<usehtml><p style="text-align:center"><b>第$1章 $2</b></p></usehtml>
```

**示例 5：分隔线替代**

```
规则名称：分隔符替换
匹配模式：^---+$
替换为：<usehtml><hr></usehtml>
```

**示例 6：可点击按钮**

```
规则名称：跳转按钮
匹配模式：\[跳转\]
替换为：<usehtml><button>点击跳转@onclick:java.toast("按钮被点击")</button></usehtml>
```

#### 1.5.4 注意事项

1. **必须启用特殊样式**：需要在设置中开启 `adaptSpecialStyle`（适配特殊样式）
2. **嵌套限制**：`<usehtml>` 标签不能嵌套
3. **性能影响**：HTML 解析比纯文本慢，不宜大量使用
4. **样式优先级**：HTML 样式会与高亮规则叠加，可能产生冲突
5. **按钮交互**：`<button>` 标签需要配合 `@onclick:` 指定点击执行的 JS 代码

### 1.6 存储方式

- 存储在 Room 数据库 `replace_rules` 表中
- 通过 `ReplaceRuleDao` 进行 CRUD 操作
- 支持导入/导出 JSON 格式

---

## 二、样式高亮规则 (HighlightRule)

### 2.1 功能定位

样式高亮规则用于**在阅读页面渲染时对文本进行样式高亮**，是一种视觉增强机制。它不修改原始内容，仅改变显示样式。

典型应用场景：
- 对话内容高亮（如引号内的文字）
- 书名号下划线（《书名》）
- 括号注释灰色显示
- 章节标题强调

### 2.2 数据结构

定义位置：[HighlightRule.kt](app/src/main/java/io/legado/app/ui/book/read/config/HighlightRule.kt)

```kotlin
data class HighlightRule(
    var id: String,                  // 唯一标识
    var name: String,                // 规则名称
    var pattern: String,             // 正则表达式模式
    var sampleText: String,          // 示例文本（用于预览）
    var group: String,               // 分组
    var targetScope: Int,            // 作用范围：0=全部, 1=标题, 2=正文
    var enabled: Boolean,            // 是否启用
    var textColor: Int?,             // 文字颜色（ARGB）
    var underlineMode: Int,          // 下划线模式
    var underlineColor: Int?,        // 下划线颜色
    var underlineWidth: Float,       // 下划线宽度
    var underlineOffset: Float,      // 下划线偏移
    var underlineSvgPath: String?,   // 自定义 SVG 路径
    var font: String?,               // 高亮字体路径，为空跟随阅读字体
    var bgImage: String?,            // 背景图片路径
    var bgImageFit: Int,             // 背景图适配方式
    var bgImageScale: Float,         // 背景图缩放
    var npLeft: Float,               // 九宫格左分割比例（0-1，默认 0.1，左右相加、上下相加不超过 1）
    var npTop: Float,                // 九宫格上分割比例
    var npRight: Float,              // 九宫格右分割比例
    var npBottom: Float,             // 九宫格下分割比例
    var bgBleedMode: Int?,           // 九宫格外扩策略：0=严格, 1=智能(默认), 2=强制
    var bgSpacingLeft: Float,        // 背景图左间距（em，默认 0；正数向外扩、负数向内收，范围 -1~1）
    var bgSpacingRight: Float,       // 背景图右间距（em，范围 -1~1）
    var bgSpacingTop: Float,         // 背景图上间距（em，范围 -0.5~1）
    var bgSpacingBottom: Float,      // 背景图下间距（em，范围 -0.5~1）
    var letterSpacingBefore: Float,  // 命中字距-命中前（px，0~120，默认 0：不留白）
    var letterSpacingAfter: Float    // 命中字距-命中后（px，0~120，默认 0：不留白）
)
```

### 2.3 语法说明

#### 匹配模式 (pattern)

使用 Kotlin 正则表达式语法，常用模式示例：

| 规则名称 | 正则模式 | 匹配内容 |
|---------|---------|---------|
| 对话高亮 | `"[^"\n]{1,120}"|「[^」\n]{1,120}」` | 中英文引号内的对话 |
| 书名号 | `《[^》\n]{1,80}》` | 书名号内的内容 |
| 括号注释 | `（[^）\n]{1,80}）|\\([^()\\n]{1,80}\\)` | 中英文括号内的注释 |
| 章节标题 | `(?m)^\\s{0,2}第[0-9零一二三四五六七八九十]{1,12}[章节卷].*$` | 章节标题行 |
| 英文单词 | `\\b[A-Za-z]{2,}[A-Za-z0-9'-]*\\b` | 英文单词 |

#### 下划线模式 (underlineMode)

| 值 | 模式 | 说明 |
|---|------|------|
| 0 | 无 | 默认值 |
| 1 | 实线下划线 | 普通实线 |
| 2 | 虚线下划线 | 虚线样式 |
| 3 | 波浪下划线 | 波浪线样式 |
| 4 | 双下划线 | 双实线 |
| 5 | 自定义 SVG | 使用 `underlineSvgPath` 绘制 |
| 6 | 删除线 | 文字中间绘制横线 |
| 7 | 斜体 | 文字倾斜显示 |
| 8 | 方框 | 用矩形框住文字，整行匹配时变为长方形 |

#### 作用范围 (targetScope)

| 值 | 范围 | 说明 |
|---|------|------|
| 0 | TARGET_ALL | 作用于标题和正文 |
| 1 | TARGET_TITLE | 仅作用于标题 |
| 2 | TARGET_BODY | 仅作用于正文 |

#### 高亮字体 (font)

- 默认为空，跟随阅读界面设置的字体（默认不选择字体）。
- 在规则编辑页点击"字体 → 选择"，可从字体目录或内置字体目录选择字体文件（支持 .ttf / .otf），选择逻辑与阅读界面"正文/标题字体"一致。
- 在字体选择对话框菜单中选"系统字体"可恢复默认（清空）。

#### 背景图适配 (bgImageFit)

| 值 | 模式 | 说明 |
|---|------|------|
| 0 | 平铺 | 默认平铺 |
| 1 | 拉伸 | 拉伸填充 |
| 2 | 裁剪 | 居中裁剪 |
| 3 | 九宫格 | 按 `npLeft` / `npTop` / `npRight` / `npBottom` 四个分割比例（0-1，占图片宽/高的百分比，左右相加、上下相加不超过 100%）把图切成 3×3：四角保持原始尺寸，四条边与中心分别拉伸，向外扩多少由"外扩策略"决定 |

> **九宫格调整**：适配方式选"九宫格"且已设置背景图时，编辑页出现"九宫格调整"入口。弹窗内预览图叠加四条红色分割线，下方滑条按百分比微调分割线位置，实时预览、确定后生效。原先对 .9.png 的自动适配（自动检测透明留白并外扩）已移除，改由用户在此手动调整。弹窗按钮行（取消左边）另有「重置」，点一下把弹窗内所有取值（分割比例、外扩策略、四边间距）恢复默认，仍需点「确定」才会写入规则。

#### 外扩策略与间距（bgBleedMode / bgSpacingLeft / bgSpacingRight / bgSpacingTop / bgSpacingBottom）

同在"九宫格调整"弹窗内调整，用于控制背景图要在匹配文字之外扩多少。间距按四个方向**独立可调**，
弹窗左下角的「重置」可把全部取值（含四边分割比例与外扩策略）一键恢复默认。

| 参数 | 默认 | 含义 |
|---|------|------|
| `bgBleedMode` | 智能 | 自动外扩策略：**严格** = 不外扩，背景只覆盖匹配到的文字；**智能** = 只占用邻接的空白；**强制** = 按四边分割比例向外扩展（即原来的"包裹文字"效果），同时**自动把左右邻字推开一个正文字距**，邻字不会被背景盖住 |
| `bgSpacingLeft` | 0 | 左间距（em，随字号缩放）：**正数**把背景向外撑大、离文字更远，**负数**向内收（范围 −1.00 ~ +1.00em）。**强制**模式下它决定左邻字被推多远 |
| `bgSpacingRight` | 0 | 右间距（em，范围 −1.00 ~ +1.00em）；**强制**模式下它决定右邻字被推多远 |
| `bgSpacingTop` | 0 | 上间距（em）：正数向外撑大、负数向内收。向外不受行距限制，向内收得太狠会把背景连同文字一起收没，故范围 −0.50 ~ +1.00em |
| `bgSpacingBottom` | 0 | 下间距（em，范围 −0.50 ~ +1.00em） |

> 旧版本只有"左右间距"`bgSpacingH` 与"上下间距"`bgSpacingV` 两个值，升级后会自动迁移到四边：
> 左/右取原左右间距，上/下取原上下间距。

三种策略的自动外扩量：

| 策略 | 左右 | 上下 |
|---|---|---|
| 严格 | 0，绝不外扩 | 0 |
| 智能 | 邻接字符是空白（空格/制表符）时，借用该空白宽度的 80%；匹配段排到行末（段末）时，借右侧到正文边界之间的空白（最多一个字符宽）；都借不到时保留 **0.1em 的最小外扩**，保证背景外框落在匹配文字外侧 | 吃掉一半行距（行距越大背景越高），最少 0.1em，不会压到上下行的字形 |
| 强制 | 四角/边条厚度，但**最多一个字宽（1em）**，并把左右邻字各向外推开"外扩量 + 一个正文字距 − 邻字原有空隙"；推开量由背景元素自身决定（随外扩量与「背景图间距」伸缩，不再固定为一个字宽） | 四角/边条厚度，但**最多吃掉一半行距** |

> **强制模式下邻字会被推开**：推开的距离作为**真实排版宽度**参与断行与两端对齐，所以剩下的文字仍然整齐（不会溢出右边距，也不会参差不齐）。让出的距离来自背景元素自身（外扩量 + 一个正文字距）：把「背景图间距」调大，邻字会被推得更远，背景始终完整地包住匹配文字。"一个正文字距"取自阅读设置里的「字间距」，不需要额外配置。
>
> **段首缩进不受影响**：匹配从段首开始时，首行缩进不会被当成"可借用的空隙"——背景的左侧边缘会落在**缩进后的文字起始位置**上，缩进后的文字按外扩量整体右移，与匹配文字的距离保持不变。所以带背景的段落不会被背景压掉半格缩进，与其他段落的缩进看起来一致。

> 间距与策略的外扩量**叠加**，所以"严格 + 正间距"仍然是往外撑（那是用户主动的选择），而"智能 + 间距 0"在中文密排下等于恰好贴住文字。
>
> **边框装饰的粗细受"让出来的空隙"限制**：四角/边条只会画在"自动外扩 + 间距"让出的范围内，超出部分会被压到匹配文字本身上（表现为背景包不住文字，或被压成看不清的横条）。想让边框更粗，把它对应的间距调大即可；强制策略让出的空隙最大，边框也最完整。
>
> 上下范围始终以**文字上下界**（字体 ascent/descent）为基准，不用行盒——文字基线贴着行盒底部，按行盒内缩会把字身上下各切掉一段，表现为背景包不住文字。
>
> **图片自带的透明留白不会被自动裁掉**：九宫格四角按原始像素绘制，若图片四周有透明边，可见图案会比预期小一圈（背景显得比文字矮）。此时把间距调成正数、让背景整体向外撑即可补偿。

#### 命中字距（letterSpacingBefore / letterSpacingAfter）

编辑页「命中字距（px）」卡片，`命中前` / `命中后` 分别控制命中段与**左侧/右侧相邻文字**之间留出的空白，单位 px（滑块一格 0.1px，范围 0~120）。默认 0 表示不留白。

| 参数 | 默认 | 含义 |
|---|------|------|
| `letterSpacingBefore` | 0 | 命中段左侧留白（px） |
| `letterSpacingAfter` | 0 | 命中段右侧留白（px） |

- 留白**落在命中段外侧**：命中段内部字符间距不变，只有段首字符拿到左侧留白、段尾字符拿到右侧留白，所以整段文字不会被从内部撑开。
- 留白是**真实排版宽度**：会参与断行与两端对齐，剩下的文字仍然整齐；命中段换行时，每个可视行都会按段首/段尾重新拿到留白。
- 留白**不撑大背景**：背景（背景图/背景色）仍然只覆盖命中的文字，留白区域保持干净。想让背景跟着外扩，用「背景图间距」（见上一节）。
- 只设了命中字距、没有其他样式的规则同样生效。
- 重叠规则在同一侧取较大值。

### 2.4 执行机制

执行位置：[TextChapterLayout.kt](app/src/main/java/io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt)

执行流程：

```
章节内容排版
    ↓
编译正则表达式（启动时预编译）
    ↓
【高亮规则匹配】← 遍历所有启用的规则
    ↓
应用 Span 样式（ForegroundColorSpan、HighlightStyleSpan）
    ↓
渲染到页面
```

关键代码逻辑：

```kotlin
private fun applyHighlightRules(
    spannable: SpannableStringBuilder,
    isTitle: Boolean = false
): SpannableStringBuilder {
    compiledHighlightRules.forEach { compiled ->
        if (!compiled.rule.appliesTo(isTitle)) return@forEach
        applyRuleSpans(spannable, compiled.rule, compiled.regex)
    }
    return spannable
}

private fun applyRuleSpans(
    spannable: SpannableStringBuilder,
    rule: HighlightRule,
    regex: Regex
) {
    regex.findAll(spannable).forEach { match ->
        // 应用文字颜色
        rule.textColor?.let { color ->
            spannable.setSpan(ForegroundColorSpan(color), ...)
        }
        // 应用下划线/背景图样式
        if (rule.underlineMode != 0 || !rule.bgImage.isNullOrBlank()) {
            spannable.setSpan(HighlightStyleSpan(...), ...)
        }
    }
}
```

### 2.5 内置默认规则

系统预设了以下高亮规则：

| ID | 名称 | 默认启用 | 样式 |
|----|------|---------|------|
| dialog_default | 对话高亮 | ✓ | 橙色文字 |
| book_title_default | 书名号高亮 | ✓ | 绿色波浪下划线 |
| bracket_note_default | 括号标注高亮 | ✓ | 灰色文字 + 蓝色虚线下划线 |
| title_emphasis_default | 标题强调 | ✓ | 深灰文字 + 棕色双下划线 |
| thought_default | 心理活动 | ✗ | 紫色文字 + 紫色实线下划线 |
| narrator_default | 旁白说明 | ✗ | 灰色文字 |
| emphasis_default | 重点强调 | ✗ | 红色文字 + 红色实线下划线 |
| poetry_default | 诗词引用 | ✗ | 深青文字 + 深青波浪下划线 |
| ellipsis_default | 省略停顿 | ✗ | 灰色文字 |
| number_default | 数字金额 | ✗ | 蓝色文字 |
| english_default | 英文单词 | ✗ | 蓝色文字 |
| date_time_default | 时间日期 | ✗ | 青色文字 |

### 2.6 存储方式

- 存储在 SharedPreferences 中
- Key: `highlightRuleItems`
- 值为 JSON 数组格式
- 通过 `HighlightRuleStore` 进行读写操作

---

## 三、两种规则对比

| 特性 | 替换规则 (ReplaceRule) | 高亮规则 (HighlightRule) |
|------|----------------------|------------------------|
| **目的** | 修改文本内容 | 改变显示样式 |
| **执行时机** | 内容加载时 | 页面渲染时 |
| **是否修改原文** | 是 | 否 |
| **存储位置** | Room 数据库 | SharedPreferences |
| **正则支持** | 可选（可关闭） | 必须 |
| **作用范围** | 按书籍/源过滤 | 按标题/正文过滤 |
| **样式能力** | 无 | 颜色、下划线、背景图 |
| **可逆性** | 不可逆（已修改内容） | 可逆（仅样式） |
| **配置入口** | 替换净化设置 | 阅读设置 → 高亮规则 |

---

## 四、扩展开发指南

### 4.1 添加新的替换规则

1. 在 `ReplaceRuleActivity` 中添加规则
2. 规则会自动保存到数据库
3. 下次加载书籍时自动生效

### 4.2 添加新的高亮规则

1. 在 `HighlightRuleStore.createDefaultRules()` 中添加预设规则
2. 或在阅读界面通过高亮规则配置对话框添加
3. 规则会立即应用到当前阅读页面

### 4.3 自定义下划线样式

通过 `underlineSvgPath` 可以定义自定义 SVG 路径：

```kotlin
HighlightRule(
    name = "自定义下划线",
    pattern = "重点",
    underlineMode = 5,  // 自定义 SVG 模式
    underlineSvgPath = "M0,0 L10,0 L5,5 Z"  // 三角形路径
)
```

---

## 五、注意事项

1. **替换规则性能**：正则表达式可能超时，默认 3 秒超时限制
2. **高亮规则性能**：启动时预编译正则，避免运行时编译开销
3. **规则优先级**：替换规则按 `order` 排序执行；高亮规则按加载顺序应用
4. **作用范围冲突**：替换规则的 `scope` 和高亮规则的 `targetScope` 是独立的过滤条件
5. **时间顺序**：高亮规则是实时应用，是最晚的。比如用替换规则把小明替换成了小张，高亮规则只能找到小张，因为高亮规则是最后应用的，所以只能匹配替换后的文本。
6. **样式冲突**：如果高亮规则和替换规则同时匹配到同一文本，高亮规则的样式会覆盖替换规则的样式。
7. **推荐使用高亮规则**：高亮规则在阅读时更方便，因为它们是实时应用的，并且可以预览，而替换规则需要先加载完所有内容后再应用。替换规则就用替换规则来写，高亮规不在此处，在阅读界面的设置里。