package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import com.google.gson.JsonParser
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.RegexCache
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import java.io.File

/**
 * 高亮规则的数据存储。
 *
 * 保留历史调用入口，负责规则持久化、缓存、备份数据组装和旧数据清洗；
 * 默认规则、背景图文件和 UI 访问入口分别下沉到专门对象。
 */
object HighlightRuleStore {

    const val backupFileName = "highlightRule.json"
    const val backupBgDirName = "highlightRuleBg"

    /** 九宫格分割比例的默认值，与实体字段默认值保持一致 */
    const val DEFAULT_NP_RATIO = 0.1f

    /** 背景图左右间距（em）的合法区间，超出视为未设置。
     *  水平方向文字左右有邻字可让位，放宽到 ±1em，能抵消"强制"模式的外扩 */
    const val MIN_BG_SPACING_H = -1f
    const val MAX_BG_SPACING_H = 1f

    /** 背景图上下间距（em）的合法区间，超出视为未设置。
     *  垂直方向向内收受行距限制，收得太狠会把背景连同文字一起收没，故下限只给 -0.5em；
     *  向外撑大不受行距限制（只是可能压到上下行），正向给到 1em */
    const val MIN_BG_SPACING_V = -0.5f
    const val MAX_BG_SPACING_V = 1f

    /** 命中字距（px）的合法区间：只允许向外留白，上限与编辑页滑块一致 */
    const val MIN_MATCH_LETTER_SPACING = 0f
    const val MAX_MATCH_LETTER_SPACING = 120f

    /**
     * 高亮规则备份文件的完整数据结构。
     */
    data class BackupData(
        val rules: List<HighlightRule> = emptyList(),
        val groups: List<String> = emptyList(),
        val currentGroup: String = "",
        val dialogEnabled: Boolean = true,
        val bookTitleEnabled: Boolean = true,
        val bracketNoteEnabled: Boolean = true,
    )

    @Volatile
    private var cachedRules: List<HighlightRule>? = null

    /**
     * 清除内存缓存，强制下次 load 从 SharedPreferences 重新读取。
     *
     * 同时清除正则表达式缓存，避免旧规则残留。
     * 用于备份恢复后确保缓存与持久化数据一致。
     */
    fun clearCache() {
        cachedRules = null
        RegexCache.clear()
    }

    fun defaultPresetRules(context: Context): List<HighlightRule> = createDefaultRules(context)

    fun load(context: Context): MutableList<HighlightRule> {
        cachedRules?.let { return it.toMutableList() }
        val stored = context.getPrefString(PreferKey.highlightRuleItems)
        if (stored.isNullOrBlank()) {
            return mutableListOf()
        }
        // 混淆版本遗留数据：键名是 a/b 等混淆名，反序列化后正则等字段全为空，
        // 表现为规则列表还在但怎么开关都不生效；数据不可恢复，重置为默认规则
        if (isObfuscatedLegacyJson(stored)) {
            return reset(context)
        }
        val rules = GSON.fromJsonArray<HighlightRule>(stored).getOrNull()?.toMutableList()
        if (rules != null) {
            val normalized = normalizeRules(rules, context)
            if (normalized != rules) {
                save(context, normalized)
            } else {
                HighlightRuleGroupStore.ensureFromRules(context, normalized)
            }
            cachedRules = normalized
            return normalized.toMutableList()
        }
        return mutableListOf()
    }

    fun loadEnabled(context: Context): List<HighlightRule> = load(context).filter { it.enabled && it.pattern.isNotBlank() }

    fun save(context: Context, rules: List<HighlightRule>) {
        val json = GSON.toJson(rules)
        context.putPrefString(PreferKey.highlightRuleItems, json)
        // 防御性拷贝：避免缓存与调用方持有同一个列表引用，
        // 当调用方 clear/修改其列表时不会污染缓存（修复新建/删除分组后规则消失的 bug）
        cachedRules = ArrayList(rules)
        // 规则变更后清除正则缓存，避免旧 pattern 残留
        RegexCache.clear()
        HighlightRuleGroupStore.ensureFromRules(context, rules)
    }

    fun reset(context: Context): MutableList<HighlightRule> {
        val defaultRules = createDefaultRules(context)
        save(context, defaultRules)
        return defaultRules.toMutableList()
    }

    fun sanitizeRule(rule: HighlightRule, fallbackGroup: String = HighlightRuleGroupStore.DEFAULT_GROUP): HighlightRule {
        val sanitized = migrateLegacySpacing(rule)
        return sanitized.copy(
            name = sanitized.name.trim(),
            pattern = sanitized.pattern.trim(),
            sampleText = sanitized.sampleText.trim(),
            group = sanitized.group.takeIf { it.isNotBlank() } ?: fallbackGroup,
            scope = sanitized.scope?.trim()?.takeIf { it.isNotBlank() },
            excludeScope = sanitized.excludeScope?.trim()?.takeIf { it.isNotBlank() },
            layoutScope = sanitized.layoutScope?.trim()?.takeIf { it.isNotBlank() },
            // GSON 用 Unsafe 实例化 data class 时不调用构造函数，
            // 老规则 JSON 缺失 themeScope 字段时反序列化得到 0，应视为全部生效而非 coerceIn(1,3)=1（仅亮色）
            themeScope = if (sanitized.themeScope in 1..3) sanitized.themeScope else HighlightRule.THEME_ALL,
            // 九宫格分割比例只在 0-1 内有意义，越界视为未设置过该字段，回落到默认比例
            npLeft = sanitized.npLeft.takeIf { it in 0f..1f } ?: DEFAULT_NP_RATIO,
            npTop = sanitized.npTop.takeIf { it in 0f..1f } ?: DEFAULT_NP_RATIO,
            npRight = sanitized.npRight.takeIf { it in 0f..1f } ?: DEFAULT_NP_RATIO,
            npBottom = sanitized.npBottom.takeIf { it in 0f..1f } ?: DEFAULT_NP_RATIO,
            // 四边间距越界视为未设置，回落到 0（紧贴文字）；左右与上下的区间不同。
            // 迁移后旧字段固定清零，不再参与后续读写
            bgSpacingH = 0f,
            bgSpacingV = 0f,
            bgSpacingLeft = sanitized.bgSpacingLeft.takeIf { it in MIN_BG_SPACING_H..MAX_BG_SPACING_H } ?: 0f,
            bgSpacingRight = sanitized.bgSpacingRight.takeIf { it in MIN_BG_SPACING_H..MAX_BG_SPACING_H } ?: 0f,
            bgSpacingTop = sanitized.bgSpacingTop.takeIf { it in MIN_BG_SPACING_V..MAX_BG_SPACING_V } ?: 0f,
            bgSpacingBottom = sanitized.bgSpacingBottom.takeIf { it in MIN_BG_SPACING_V..MAX_BG_SPACING_V } ?: 0f,
            // 命中字距越界（含 GSON 反序列化出的 NaN）视为未设置，回落到 0
            letterSpacingBefore = sanitized.letterSpacingBefore
                .takeIf { it in MIN_MATCH_LETTER_SPACING..MAX_MATCH_LETTER_SPACING } ?: 0f,
            letterSpacingAfter = sanitized.letterSpacingAfter
                .takeIf { it in MIN_MATCH_LETTER_SPACING..MAX_MATCH_LETTER_SPACING } ?: 0f,
            // 外扩策略只认三个枚举值，其余（含老规则缺字段得到的 0 以外的值）回落到 null 表示智能
            bgBleedMode = sanitized.bgBleedMode?.takeIf {
                it == HighlightRule.BLEED_STRICT ||
                    it == HighlightRule.BLEED_SMART ||
                    it == HighlightRule.BLEED_FORCE
            },
        )
    }

    /**
     * 把旧版的"左右间距/上下间距"（bgSpacingH/bgSpacingV）迁移到四边独立参数。
     *
     * 只有四边都还是默认值 0 时才迁移：编辑过间距的规则四边一定有非 0 值，不会被旧值覆盖；
     * 迁移与读取都幂等，重复调用不会叠加。
     */
    private fun migrateLegacySpacing(rule: HighlightRule): HighlightRule {
        val allSidesUnset = rule.bgSpacingLeft == 0f && rule.bgSpacingRight == 0f &&
            rule.bgSpacingTop == 0f && rule.bgSpacingBottom == 0f
        if (!allSidesUnset || (rule.bgSpacingH == 0f && rule.bgSpacingV == 0f)) return rule
        return rule.copy(
            bgSpacingLeft = rule.bgSpacingH,
            bgSpacingRight = rule.bgSpacingH,
            bgSpacingTop = rule.bgSpacingV,
            bgSpacingBottom = rule.bgSpacingV,
        )
    }

    fun backupData(context: Context): BackupData {
        val rules = load(context)
        val groups = HighlightRuleGroupStore.load(context)
        val currentGroup = context.getPrefString(PreferKey.highlightRuleCurrentGroup) ?: ""
        val dialogEnabled = context.getPrefBoolean(PreferKey.highlightRuleDialog, true)
        val bookTitleEnabled = context.getPrefBoolean(PreferKey.highlightRuleBookTitle, true)
        val bracketNoteEnabled = context.getPrefBoolean(PreferKey.highlightRuleBracketNote, true)

        return BackupData(
            rules = rules,
            groups = groups,
            currentGroup = currentGroup,
            dialogEnabled = dialogEnabled,
            bookTitleEnabled = bookTitleEnabled,
            bracketNoteEnabled = bracketNoteEnabled,
        )
    }

    fun restoreBackupData(
        context: Context,
        backupData: BackupData,
        backupRootPath: String? = null,
    ) {
        // GSON 通过反射反序列化，混淆版本写出的旧备份键名对不上时，
        // 非空类型字段实际会为 null，这里统一取可空值兜底，
        // 否则恢复过程 NPE 被 Restore 的 runCatching 吞掉，高亮规则段静默跳过
        val backupRules: List<HighlightRule>? = backupData.rules
        val backupGroups: List<String>? = backupData.groups
        val backupCurrentGroup: String? = backupData.currentGroup
        if (backupRules != null) {
            // 从备份目录恢复背景图文件，并更新规则中的 bgImage 路径
            val restoredRules = if (backupRootPath != null) {
                backupRules.map { rule ->
                    val restoredPath = HighlightRuleBackgroundManager.restoreFromBackup(
                        context,
                        backupRootPath,
                        rule.bgImage,
                    )
                    if (restoredPath != null && restoredPath != rule.bgImage) {
                        rule.copy(bgImage = restoredPath)
                    } else {
                        rule
                    }
                }
            } else {
                backupRules
            }
            // 恢复前走一遍清洗：老备份只有"左右/上下间距"，需迁移到四边参数渲染才读得到
            save(context, restoredRules.map { sanitizeRule(it) })
            // 三个旧开关跟随规则数据恢复：规则键缺失的损坏备份不误关开关
            context.putPrefBoolean(PreferKey.highlightRuleDialog, backupData.dialogEnabled)
            context.putPrefBoolean(PreferKey.highlightRuleBookTitle, backupData.bookTitleEnabled)
            context.putPrefBoolean(PreferKey.highlightRuleBracketNote, backupData.bracketNoteEnabled)
        }
        if (backupGroups != null) {
            HighlightRuleGroupStore.save(context, backupGroups)
        }
        val groups = HighlightRuleGroupStore.load(context)
        context.putPrefString(
            PreferKey.highlightRuleCurrentGroup,
            backupCurrentGroup?.takeIf { groups.contains(it) }.orEmpty(),
        )
    }

    fun getUsedBgImageFiles(context: Context): List<File> = HighlightRuleBackgroundManager.getUsedFiles(context, load(context))

    private fun createDefaultRules(context: Context): List<HighlightRule> = HighlightRuleDefaultRules.create(context)

    private fun normalizeRules(
        rules: List<HighlightRule>,
        context: Context,
    ): List<HighlightRule> {
        val builtins = createDefaultRules(context).associateBy { it.id }
        return rules.map { rule ->
            val safeRule = sanitizeRule(rule)
            val normalizedGroup = safeRule.group
            val builtin = builtins[safeRule.id]
            val base = if (builtin != null && shouldRefreshBuiltin(safeRule)) {
                builtin.copy(
                    enabled = safeRule.enabled,
                    group = normalizedGroup,
                    targetScope = normalizeTargetScope(safeRule.targetScope, builtin.targetScope),
                    textColor = safeRule.textColor ?: builtin.textColor,
                    underlineMode = safeRule.underlineMode.takeIf { it != 0 } ?: builtin.underlineMode,
                    underlineColor = safeRule.underlineColor ?: builtin.underlineColor,
                    underlineWidth = safeRule.underlineWidth.takeIf { it != 1f } ?: builtin.underlineWidth,
                    underlineSvgPath = safeRule.underlineSvgPath ?: builtin.underlineSvgPath,
                    bgImage = safeRule.bgImage ?: builtin.bgImage,
                    bgColor = safeRule.bgColor ?: builtin.bgColor,
                    bgImageFit = safeRule.bgImageFit.takeIf { it != 0 } ?: builtin.bgImageFit,
                    bgImageScale = safeRule.bgImageScale.takeIf { it != 1f } ?: builtin.bgImageScale,
                    scope = safeRule.scope,
                    excludeScope = safeRule.excludeScope,
                )
            } else {
                safeRule
            }
            base
        }
    }

    private fun shouldRefreshBuiltin(rule: HighlightRule): Boolean {
        // 仅对内置规则 ID 执行检查，用户自定义规则不受影响
        if (rule.id !in builtinIds) return false
        // 检查条件：
        // 1. name 或 pattern 为空（数据丢失）
        // 2. 文本包含乱码标记（编码问题）
        // 3. pattern 匹配旧版遗留正则（需升级到当前版本）
        // 4. sampleText 匹配旧版遗留样本文本（需升级到当前版本）
        // 不再因"无样式"而刷新——用户可能故意清除内置规则的样式
        val inspectText = rule.name + rule.pattern + rule.sampleText
        return rule.name.isBlank() ||
            rule.pattern.isBlank() ||
            garbledMarkers.any { inspectText.contains(it) } ||
            legacyBuiltinPatterns[rule.id] == rule.pattern ||
            legacyBuiltinSampleTexts[rule.id] == rule.sampleText
    }

    /** 内置规则 ID 集合 */
    private val builtinIds = setOf(
        "dialog_default",
        "book_title_default",
        "bracket_note_default",
        "title_emphasis_default",
        "thought_default",
        "narrator_default",
        "emphasis_default",
        "poetry_default",
        "ellipsis_default",
        "number_default",
        "english_default",
        "date_time_default",
    )

    /**
     * 旧版遗留正则表达式映射。
     *
     * 当用户 SharedPreferences 中存储的内置规则 pattern 与此映射中的旧版 pattern
     * 完全匹配时，说明该规则尚未升级到当前版本，需要刷新为最新默认值。
     */
    private val legacyBuiltinPatterns = mapOf(
        "dialog_default" to "[“\"]([^”\"\\n]{1,120})[”\"]|「[^」\\n]{1,120}」|『[^』\\n]{1,120}』",
        "book_title_default" to "《[^》\\n]{1,80}》",
        "bracket_note_default" to "（[^）\\n]{1,80}）|\\([^\\)\\n]{1,80}\\)|【[^】\\n]{1,80}】",
        "title_emphasis_default" to "(?m)^(第[0-9零一二三四五六七八九十百千两0123456789IVXLCDMivxlcdm]{1,12}[章节回卷部篇集幕]|序章|楔子|引子|终章|尾声|后记|番外)[^\\n]{0,40}$",
        "thought_default" to "（[^）]*?(想道|暗道|心道|心里|想着|思量|思忖|盘算|盘算着)[^）]*?）",
        "narrator_default" to "（以下\\S{0,20}省略|省略\\S{0,20}内容|[^\\n]{0,20}的情景不再赘述|[^\\n]{0,20}的情况不再多说）",
        "emphasis_default" to "[*！]{1,2}[^*\\n]{1,50}[*！]{1,2}",
        "poetry_default" to "[\\n]([七五言绝句律诗词牌曲牌][^\\n]{0,60}[^\\n]{10,50}[^\\n]{0,20}[，。！？])\\n",
        "ellipsis_default" to "x{2,}|\\*{2,}|\\.{2,}",
        "number_default" to "[0-9零一二三四五六七八九十百千万亿]+[元块美元英镑]|[0-9]+[%％]",
        "english_default" to "[a-zA-Z]{2,}[a-zA-Z0-9'-]*",
        "date_time_default" to "[0-9零一二三四五六七八九十]+年[0-9零一二三四五六七八九十]+月[0-9零一二三四五六七八九十]*日?|[0-9]+点[0-9零一二三四五六七八九十]*分?",
    )

    /** 乱码标记，用于检测旧数据编码问题 */
    private val garbledMarkers = listOf("锛", "銆", "鈥", "瀵", "涔", "鏍", "鐪", "鏈", "绗")

    /**
     * 旧版遗留样本文本映射。
     *
     * 当用户 SharedPreferences 中存储的内置规则 sampleText 与此映射中的旧版值
     * 完全匹配时，说明该规则尚未升级到当前版本，需要刷新为最新默认值。
     * 主要用于修复重构时 \\n 被错误地当作字面字符而非换行符的问题。
     */
    private val legacyBuiltinSampleTexts = mapOf(
        "poetry_default" to "床前明月光，\\n疑是地上霜。",
    )

    private fun normalizeTargetScope(ruleScope: Int, builtinScope: Int): Int = if (ruleScope in 0..2) ruleScope else builtinScope

    /** HighlightRule 的规范字段名，用于识别混淆版本写出的损坏 JSON */
    private val canonicalFieldNames = setOf(
        "id", "name", "pattern", "isRegex", "sampleText", "group", "targetScope", "enabled",
        "textColor", "underlineMode", "underlineColor", "underlineWidth", "underlineOffset",
        "underlineSvgPath", "font", "bgColor", "bgImage", "bgImageFit", "bgImageScale",
        "scope", "excludeScope", "layoutScope", "themeScope",
    )

    /**
     * 检测是否为混淆版本遗留的损坏规则数据。
     *
     * 历史版本未 keep HighlightRule 字段名，release 包里 GSON 以 a/b 等混淆名写出到
     * SharedPreferences；升级后这些键无法映射到当前字段。只要所有条目都不含任何
     * 规范字段名，即判定为不可恢复的损坏数据（正常数据至少会有 id/name/pattern 键）。
     */
    private fun isObfuscatedLegacyJson(stored: String): Boolean {
        return runCatching {
            val element = JsonParser.parseString(stored)
            if (!element.isJsonArray) return@runCatching false
            val entries = element.asJsonArray
            if (entries.isEmpty) return@runCatching false
            entries.all { entry ->
                !entry.isJsonObject || entry.asJsonObject.keySet().none { it in canonicalFieldNames }
            }
        }.getOrDefault(false)
    }
}
