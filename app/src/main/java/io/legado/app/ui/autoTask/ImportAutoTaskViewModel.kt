package io.legado.app.ui.autoTask

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.readText
import splitties.init.appCtx

class ImportAutoTaskViewModel(app: Application) : BaseViewModel(app) {

    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<Int>()

    val allTasks = arrayListOf<AutoTaskRule>()
    val checkTasks = arrayListOf<AutoTaskRule?>()
    val selectStatus = arrayListOf<Boolean>()

    val isSelectAll: Boolean
        get() {
            selectStatus.forEach {
                if (!it) {
                    return false
                }
            }
            return true
        }

    val selectCount: Int
        get() {
            var count = 0
            selectStatus.forEach {
                if (it) {
                    count++
                }
            }
            return count
        }

    fun importSelect(finally: () -> Unit) {
        execute {
            val selectTasks = arrayListOf<AutoTaskRule>()
            selectStatus.forEachIndexed { index, b ->
                if (b) {
                    selectTasks.add(allTasks[index])
                }
            }
            AutoTask.upsert(selectTasks)
        }.onFinally {
            finally.invoke()
        }
    }

    fun importSource(text: String?) {
        val sourceText = text?.trim().orEmpty()
        if (sourceText.isBlank()) {
            errorLiveData.postValue("ImportError:${context.getString(R.string.wrong_format)}")
            return
        }
        allTasks.clear()
        checkTasks.clear()
        selectStatus.clear()
        execute {
            importSourceAwait(sourceText)
        }.onError {
            errorLiveData.postValue("ImportError:${it.localizedMessage}")
            AppLog.put("ImportError:${it.localizedMessage}", it)
        }.onSuccess {
            comparisonSource()
        }
    }

    fun updateTaskAt(index: Int, task: AutoTaskRule) {
        if (index < 0 || index >= allTasks.size) return
        allTasks[index] = task
        val localTask = AutoTask.getRules().firstOrNull { it.id == task.id }
        if (index < checkTasks.size) {
            checkTasks[index] = localTask
        }
        if (index < selectStatus.size) {
            selectStatus[index] = localTask == null || task != localTask
        }
    }

    private suspend fun importSourceAwait(text: String) {
        when {
            text.isJsonObject() -> {
                GSON.fromJsonObject<AutoTaskRule>(text).getOrThrow().let {
                    allTasks.add(it)
                }
            }

            text.isJsonArray() -> GSON.fromJsonArray<AutoTaskRule>(text).getOrThrow()
                .let { items ->
                    allTasks.addAll(items)
                }

            text.isAbsUrl() -> {
                importSourceUrl(text)
            }

            text.isUri() -> {
                importSourceAwait(text.toUri().readText(appCtx))
            }

            else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
        }
    }

    private suspend fun importSourceUrl(url: String) {
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().text().let {
            importSourceAwait(it)
        }
    }

    private fun comparisonSource() {
        execute {
            val localMap = AutoTask.getRules().associateBy { it.id }
            allTasks.forEach {
                val local = localMap[it.id]
                checkTasks.add(local)
                selectStatus.add(local == null || it != local)
            }
            successLiveData.postValue(allTasks.size)
        }
    }
}
