package io.legado.app.model

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.CacheManager
import io.legado.app.service.AutoTaskService
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.mergeFilteredOrder
import io.legado.app.utils.stackTraceStr
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoTask {

    const val SOURCE_KEY = "auto_task"
    private const val BOOK_TASK_PREFIX = "book_update:"
    private const val KEY_RULES = "autoTaskRules"
    private const val MAX_LOG_LENGTH = 4000

    const val DEFAULT_CRON = "*/30 * * * *"

    fun bookTaskId(bookUrl: String): String {
        return BOOK_TASK_PREFIX + io.legado.app.utils.MD5Utils.md5Encode16(bookUrl)
    }

    fun buildBookUpdateScript(
        bookUrl: String,
        notifyEnabled: Boolean = true,
        cacheEnabled: Boolean = false
    ): String {
        val notify = linkedMapOf<String, Any?>(
            "enable" to notifyEnabled,
            "minCount" to 1
        )
        val cache = linkedMapOf<String, Any?>(
            "enable" to cacheEnabled
        )
        val action = linkedMapOf<String, Any?>(
            "type" to "refreshToc",
            "bookUrl" to bookUrl,
            "notify" to notify,
            "cache" to cache
        )
        val root = linkedMapOf<String, Any?>(
            "actions" to listOf(action)
        )
        val json = io.legado.app.utils.GSON.toJson(root)
        return "var __autoTask = $json;\n__autoTask"
    }

    fun normalizeScript(script: String): String {
        val trimmed = script.trim()
        return when {
            trimmed.startsWith("@js:", true) -> trimmed.substring(4).trim()
            trimmed.startsWith("<js>", true) && trimmed.contains("</") ->
                trimmed.substring(4, trimmed.lastIndexOf("<")).trim()
            else -> trimmed
        }
    }

    /** lastLog 格式对 cron 调度与手动运行(含 MCP run_auto_task)统一。 */
    fun buildLastLog(
        lines: List<String>,
        detail: String?,
        cost: Long,
        runAt: Long,
    ): String {
        val sb = StringBuilder()
        sb.append("[OK] ").append(formatLogTime(runAt)).append('\n')
        sb.append("耗时: ").append(cost).append("ms")
        if (lines.isNotEmpty()) {
            sb.append('\n').append("动作:")
            lines.forEach { sb.append('\n').append("- ").append(it) }
        }
        if (!detail.isNullOrBlank()) {
            sb.append('\n').append("返回: ").append(detail)
        }
        return sb.toString().ifBlank { "执行完成" }.take(MAX_LOG_LENGTH)
    }

    fun buildErrorLog(msg: String, error: Throwable?, runAt: Long): String {
        val sb = StringBuilder()
        sb.append("[FAIL] ").append(formatLogTime(runAt)).append('\n')
        sb.append("错误: ").append(msg)
        error?.stackTraceStr?.takeIf { it.isNotBlank() }?.let {
            sb.append('\n').append("堆栈:").append('\n').append(it)
        }
        return sb.toString().take(MAX_LOG_LENGTH)
    }

    private fun formatLogTime(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))

    fun start(context: Context) {
        AutoTaskService.start(context)
    }

    fun stop(context: Context) {
        AutoTaskService.stop(context)
    }

    fun refreshSchedule(context: Context = appCtx) {
        if (!context.getPrefBoolean(PreferKey.autoTaskService)) return
        AutoTaskService.refresh(context)
    }

    fun buildSource(task: AutoTaskRule): BookSource {
        return BookSource(
            bookSourceUrl = "${SOURCE_KEY}:${task.id}",
            bookSourceName = task.name
        ).apply {
            jsLib = task.jsLib
            header = task.header
            concurrentRate = task.concurrentRate
            enabledCookieJar = task.enabledCookieJar
            loginUrl = task.loginUrl
            loginUi = task.loginUi
            loginCheckJs = task.loginCheckJs
        }
    }

    @Synchronized
    fun getRules(): MutableList<AutoTaskRule> {
        val rules = appDb.autoTaskRuleDao.all().toMutableList()
        if (rules.isEmpty()) {
            // 尝试从旧 CacheManager 迁移数据
            migrateFromCache()?.let { rules.addAll(it) }
        }
        return rules
    }

    @Synchronized
    fun upsert(rule: AutoTaskRule) {
        upsertRule(rule)
        refreshSchedule()
    }

    @Synchronized
    fun reorder(orderedRules: List<AutoTaskRule>) {
        if (orderedRules.isEmpty()) return
        val allRules = getRules()
        val reordered = mergeFilteredOrder(allRules, orderedRules) { it.id }
        appDb.autoTaskRuleDao.resetOrder(reordered)
    }

    @Synchronized
    fun upsert(rules: List<AutoTaskRule>) {
        if (rules.isEmpty()) return
        rules.forEach(::upsertRule)
        refreshSchedule()
    }

    private fun upsertRule(rule: AutoTaskRule) {
        val existing = appDb.autoTaskRuleDao.getById(rule.id)
        if (existing == null) {
            val sortOrder = appDb.autoTaskRuleDao.maxOrder + 1
            appDb.autoTaskRuleDao.insert(rule.copy(sortOrder = sortOrder))
        } else {
            appDb.autoTaskRuleDao.update(rule.copy(sortOrder = existing.sortOrder))
        }
    }

    @Synchronized
    fun delete(vararg ids: String) {
        ids.forEach { appDb.autoTaskRuleDao.delete(it) }
        refreshSchedule()
    }

    @Synchronized
    fun update(id: String, updater: (AutoTaskRule) -> AutoTaskRule): AutoTaskRule? {
        val existing = appDb.autoTaskRuleDao.getById(id) ?: return null
        val updated = updater(existing)
        appDb.autoTaskRuleDao.update(updated)
        return updated
    }

    /** 迁移旧 CacheManager 中的数据到 Room */
    private fun migrateFromCache(): MutableList<AutoTaskRule>? {
        val json = CacheManager.get(KEY_RULES) ?: return null
        val rules = GSON.fromJsonArray<AutoTaskRule>(json).getOrNull()
            ?.mapIndexed { index, rule -> rule.copy(sortOrder = index) }
            ?.toMutableList()
            ?: return null
        appDb.autoTaskRuleDao.insert(*rules.toTypedArray())
        CacheManager.delete(KEY_RULES)
        return rules
    }
}
