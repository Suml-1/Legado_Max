package io.legado.app.ui.autoTask

import android.app.Application
import com.google.gson.annotations.SerializedName
import io.legado.app.base.BaseViewModel
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.outputStream
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AutoTaskViewModel(application: Application) : BaseViewModel(application) {

    private data class AutoTaskExport(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("enable")
        val enable: Boolean,
        @SerializedName("cron")
        val cron: String?,
        @SerializedName("loginUrl")
        val loginUrl: String?,
        @SerializedName("loginUi")
        val loginUi: String?,
        @SerializedName("loginCheckJs")
        val loginCheckJs: String?,
        @SerializedName("comment")
        val comment: String?,
        @SerializedName("script")
        val script: String,
        @SerializedName("header")
        val header: String?,
        @SerializedName("jsLib")
        val jsLib: String?,
        @SerializedName("concurrentRate")
        val concurrentRate: String?,
        @SerializedName("enabledCookieJar")
        val enabledCookieJar: Boolean
    )

    private fun AutoTaskRule.toExport(): AutoTaskExport {
        return AutoTaskExport(
            id = id,
            name = name,
            enable = enable,
            cron = cron,
            loginUrl = loginUrl,
            loginUi = loginUi,
            loginCheckJs = loginCheckJs,
            comment = comment,
            script = script,
            header = header,
            jsLib = jsLib,
            concurrentRate = concurrentRate,
            enabledCookieJar = enabledCookieJar
        )
    }

    private val _rulesFlow = MutableStateFlow<List<AutoTaskRule>>(emptyList())
    val rulesFlow = _rulesFlow.asStateFlow()

    fun refresh() {
        execute {
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun save(rule: AutoTaskRule) {
        execute {
            AutoTask.upsert(rule)
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun delete(rule: AutoTaskRule) {
        execute {
            AutoTask.delete(rule.id)
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        execute {
            AutoTask.delete(*ids.toTypedArray())
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun saveOrder(items: List<AutoTaskRule>) {
        if (items.isEmpty()) return
        execute {
            AutoTask.reorder(items)
        }
    }

    fun updateEnabled(ids: List<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        execute {
            ids.forEach { id -> AutoTask.update(id) { it.copy(enable = enabled) } }
            AutoTask.refreshSchedule()
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun updateCron(ids: List<String>, cron: String) {
        if (ids.isEmpty()) return
        execute {
            ids.forEach { id -> AutoTask.update(id) { it.copy(cron = cron) } }
            AutoTask.refreshSchedule()
            AutoTask.getRules()
        }.onSuccess {
            _rulesFlow.value = it
        }
    }

    fun exportToFile(success: (File) -> Unit) {
        execute {
            val path = "${context.filesDir}/exportAutoTask.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use {
                val tasks = AutoTask.getRules().map { rule -> rule.toExport() }
                GSON.writeToOutputStream(it, tasks)
            }
            file
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun exportSelection(ids: List<String>, success: (File) -> Unit) {
        if (ids.isEmpty()) return
        execute {
            val idSet = ids.toHashSet()
            val tasks = AutoTask.getRules()
                .filter { idSet.contains(it.id) }
                .map { rule -> rule.toExport() }
            val path = "${context.filesDir}/exportAutoTaskSelection.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use {
                GSON.writeToOutputStream(it, tasks)
            }
            file
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }
}
