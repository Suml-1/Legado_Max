package io.legado.app.ui.association

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.RuleUpdate
import io.legado.app.utils.inputStream
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isUri
import io.legado.app.utils.splitNotBlank
import java.io.StringReader

class ImportBookSourceViewModel(app: Application) : BaseViewModel(app) {
    var isAddGroup = false
    var groupName: String? = null
    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<Int>()

    val allSources = arrayListOf<BookSource>()
    val checkSources = arrayListOf<BookSourcePart?>()
    val selectStatus = arrayListOf<Boolean>()
    val newSourceStatus = arrayListOf<Boolean>()
    val updateSourceStatus = arrayListOf<Boolean>()

    val isSelectAll: Boolean
        get() {
            selectStatus.forEach {
                if (!it) {
                    return false
                }
            }
            return true
        }

    val isSelectAllNew: Boolean
        get() {
            newSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val isSelectAllUpdate: Boolean
        get() {
            updateSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
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
            val group = groupName?.trim()
            val keepName = AppConfig.importKeepName
            val keepGroup = AppConfig.importKeepGroup
            val keepEnable = AppConfig.importKeepEnable
            val selectSource = arrayListOf<BookSource>()
            selectStatus.forEachIndexed { index, b ->
                if (b) {
                    val source = allSources[index]
                    checkSources[index]?.let {
                        if (keepName) {
                            source.bookSourceName = it.bookSourceName
                        }
                        if (keepGroup) {
                            source.bookSourceGroup = it.bookSourceGroup
                        }
                        if (keepEnable) {
                            source.enabled = it.enabled
                            source.enabledExplore = it.enabledExplore
                        }
                        source.customOrder = it.customOrder
                    }
                    if (!group.isNullOrEmpty()) {
                        if (isAddGroup) {
                            val groups = linkedSetOf<String>()
                            source.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.let {
                                groups.addAll(it)
                            }
                            groups.add(group)
                            source.bookSourceGroup = groups.joinToString(",")
                        } else {
                            source.bookSourceGroup = group
                        }
                    }
                    selectSource.add(source)
                }
            }
            SourceHelp.insertBookSource(*selectSource.toTypedArray())
            ContentProcessor.upReplaceRules()
        }.onFinally {
            finally.invoke()
        }
    }

    fun importSource(text: String) {
        execute {
            val mText = text.trim()
            // 首字符是 [ / { 的交给统一解析器（里面区分 JSON 合集与 sourceUrls 包装）
            val isJsonLike = mText.startsWith("[") || mText.startsWith("{")
            when {
                !isJsonLike && mText.isAbsUrl() -> {
                    importSourceUrl(mText)
                }

                !isJsonLike && mText.isUri() -> {
                    Uri.parse(mText).inputStream(context).getOrThrow().bufferedReader().use {
                        readBookSourceImport(it, false, ::addSource) { }
                    }
                }

                // 粘贴内容：JSON、纯 JS 文本或 sourceUrls 包装都走同一个解析器
                else -> StringReader(mText).use {
                    readBookSourceImport(it, true, ::addSource, ::importSourceUrl)
                }
            }
        }.onError {
            errorLiveData.postValue("ImportError:${it.localizedMessage}")
            AppLog.put("ImportError:${it.localizedMessage}", it, dialogName = "导入书源")
        }.onSuccess {
            comparisonSource()
        }
    }

    private suspend fun importSourceUrl(url: String) {
        RuleUpdate.cacheBookSourceMap[url]?.also {
            allSources.addAll(it)
            RuleUpdate.cacheBookSourceMap.remove(url)
            return
        }
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().byteStream().use { inputS ->
            // 网址返回的可能是 JSON 合集，也可能是一份 .js 单文件书源
            readBookSourceImport(inputS.reader().buffered(), false, ::addSource) { }
        }
    }

    private fun addSource(source: BookSource) {
        if (source.bookSourceUrl.isEmpty()) {
            throw NoStackTraceException("不是书源")
        }
        allSources.add(source)
    }

    private fun comparisonSource() {
        execute {
            allSources.forEach {
                val source = appDb.bookSourceDao.getBookSourcePart(it.bookSourceUrl)
                checkSources.add(source)
                selectStatus.add(source == null || source.lastUpdateTime < it.lastUpdateTime)
                newSourceStatus.add(source == null)
                updateSourceStatus.add(source != null && source.lastUpdateTime < it.lastUpdateTime)
            }
            successLiveData.postValue(allSources.size)
        }
    }
}
