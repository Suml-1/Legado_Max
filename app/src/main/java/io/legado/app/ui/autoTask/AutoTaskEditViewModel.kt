package io.legado.app.ui.autoTask

import android.app.Application
import android.content.Intent
import io.legado.app.base.BaseViewModel
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi

class AutoTaskEditViewModel(app: Application) : BaseViewModel(app) {

    var task: AutoTaskRule? = null

    fun initData(intent: Intent, finally: (AutoTaskRule) -> Unit) {
        execute {
            val id = intent.getStringExtra("id")
            task = AutoTask.getRules().firstOrNull { it.id == id } ?: AutoTaskRule()
        }.onFinally {
            task?.let { finally(it) }
        }
    }

    fun save(rule: AutoTaskRule, success: () -> Unit) {
        execute {
            AutoTask.upsert(rule)
        }.onSuccess {
            success()
        }.onError {
            context.toastOnUi("save error, ${it.localizedMessage}")
        }
    }

    fun pasteSource(onSuccess: (AutoTaskRule) -> Unit) {
        execute {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为空")
            }
            when {
                text.isJsonObject() -> {
                    GSON.fromJsonObject<AutoTaskRule>(text).getOrThrow()
                }
                text.isJsonArray() -> {
                    throw NoStackTraceException("剪贴板包含多个任务，请使用导入功能")
                }
                else -> throw NoStackTraceException("剪贴板内容格式不正确")
            }
        }.onSuccess {
            onSuccess(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }
}
