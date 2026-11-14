package io.legado.app.ui.autoTask

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.model.AutoTaskProtocol
import io.legado.app.model.Debug
import io.legado.app.utils.stackTraceStr

class AutoTaskDebugViewModel(application: Application) : BaseViewModel(application),
    Debug.Callback {

    var task: AutoTaskRule? = null
    private var callback: ((Int, String) -> Unit)? = null

    fun init(id: String?, finally: () -> Unit) {
        execute {
            task = AutoTask.getRules().firstOrNull { it.id == id }
        }.onFinally {
            finally.invoke()
        }
    }

    fun observe(callback: (Int, String) -> Unit) {
        this.callback = callback
    }

    fun startDebug(start: (() -> Unit)? = null, error: (() -> Unit)? = null) {
        if (task == null) {
            error?.invoke()
            return
        }
        execute {
            val rule = task ?: return@execute
            val source = AutoTask.buildSource(rule)
            Debug.callback = this@AutoTaskDebugViewModel
            Debug.startSimpleDebug(source.getKey())
            Debug.log(source.getKey(), "︾开始执行")
            val script = normalizeScript(rule.script)
            if (script.isBlank()) {
                Debug.log(source.getKey(), "脚本为空", state = -1)
                return@execute
            }
            runCatching {
                val result = source.evalJS(script)
                AutoTaskProtocol.handle(result, context, rule.name) { msg ->
                    Debug.log(source.getKey(), msg, showTime = false)
                }
                result
            }.onSuccess { result ->
                val detail = result?.toString()?.take(200)
                if (!detail.isNullOrBlank()) {
                    Debug.log(source.getKey(), detail, showTime = false)
                }
                Debug.log(source.getKey(), "︽执行完成", state = 1000)
            }.onFailure { error ->
                Debug.log(source.getKey(), error.stackTraceStr, state = -1)
            }
        }.onStart {
            start?.invoke()
        }.onError {
            error?.invoke()
        }
    }

    override fun printLog(state: Int, msg: String) {
        callback?.invoke(state, msg)
    }

    override fun onCleared() {
        super.onCleared()
        Debug.cancelDebug(true)
    }

    private fun normalizeScript(script: String): String {
        return AutoTask.normalizeScript(script)
    }
}
