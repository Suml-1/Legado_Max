package io.legado.app.ui.book.source.edit

import android.app.Application
import android.content.Intent
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.RuleComplete
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.storage.ImportOldData
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.jsonPath
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers


class BookSourceEditViewModel(application: Application) : BaseViewModel(application) {
    var autoComplete = false
    var bookSource: BookSource? = null

    /**
     * 加载待编辑的书源并回调界面。
     *
     * 同样用 executeLazy + start：onFinally 一旦漏执行，界面不会绑定数据（空白页），
     * 且调用方用于区分"是否保存过"的引用会停留在 null，退出时会误报 RESULT_OK。
     */
    fun initData(intent: Intent, onFinally: () -> Unit) {
        executeLazy {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            var source: BookSource? = null
            if (sourceUrl != null) {
                source = appDb.bookSourceDao.getBookSource(sourceUrl)
            }
            source?.let {
                bookSource = it
            }
        }.onFinally {
            onFinally()
        }.start()
    }

    /**
     * 保存书源。
     *
     * 用 executeLazy（LAZY 启动）挂完回调再 start：链式协程的回调依赖"任务还没完成时已挂上回调"
     * 这一时序，回调一旦漏掉，调用方（书源列表/详情页/换源/阅读页）就拿不到保存结果，
     * 表现为"保存了但没生效、要再保存一次"。
     *
     * 书源 URL 变更时的书籍迁移也放在本协程内：原先在 Activity 的 lifecycleScope 里做，
     * Activity 一旦销毁该协程被取消，迁移与结果回传会一起丢失。
     */
    fun save(
        source: BookSource,
        finally: (() -> Unit)? = null,
        success: ((BookSource) -> Unit)? = null
    ) {
        var migrated = false
        executeLazy {
            if (source.bookSourceUrl.isBlank() || source.bookSourceName.isBlank()) {
                throw NoStackTraceException(context.getString(R.string.non_null_name_url))
            }
            val oldSource = bookSource ?: BookSource()
            if (!source.equal(oldSource)) {
                source.lastUpdateTime = System.currentTimeMillis()
                if (oldSource.exploreUrl != source.exploreUrl) {
                    oldSource.clearExploreKindsCache()
                }
                if (oldSource.jsLib != source.jsLib) {
                    SharedJsScope.remove(oldSource.jsLib)
                }
            }
            bookSource?.let {
                if (it.bookSourceUrl != source.bookSourceUrl) {
                    SourceHelp.deleteBookSource(it.bookSourceUrl)
                } else {
                    appDb.bookSourceDao.delete(it)
                    SourceConfig.removeSource(it.bookSourceUrl)
                }
            }
            appDb.bookSourceDao.insert(source)
            bookSource = source
            concurrentRecordMap.remove(source.bookSourceUrl) // 删除并发限制缓存
            // 源地址变了：书架里关联这本书源的书籍一起迁移，避免换地址后书籍丢源。
            // 迁移是附带动作，单独兜底：它失败不能连累"书源已保存"这个结果，
            // 否则书源已入库、调用方却收不到成功回调，又变成"保存了但没生效"
            val oldUrl = oldSource.bookSourceUrl
            if (oldUrl.isNotBlank() && oldUrl != source.bookSourceUrl) {
                runCatching {
                    if (appDb.bookDao.hasBookByOrigin(oldUrl)) {
                        appDb.bookDao.updateOrigin(oldUrl, source.bookSourceUrl)
                        migrated = true
                    }
                }.onFailure { it.printOnDebug() }
            }
            source
        }.onSuccess {
            if (migrated) {
                context.toastOnUi(R.string.migrate_book_origin_done)
            }
            success?.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }.onFinally {
            finally?.invoke()
        }.start()
    }

    fun pasteSource(onSuccess: (source: BookSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为空")
            } else {
                importSource(text, onSuccess)
            }
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    fun importSource(text: String, finally: (source: BookSource) -> Unit) {
        execute {
            importSource(text)
        }.onSuccess {
            finally.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    suspend fun importSource(text: String): BookSource {
        return when {
            text.isAbsUrl() -> {
                val text1 = okHttpClient.newCallStrResponse { url(text) }.body
                importSource(text1!!)
            }

            text.isJsonArray() -> {
                if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
                    val items: List<Map<String, Any>> = jsonPath.parse(text).read("$")
                    val jsonItem = jsonPath.parse(items[0])
                    ImportOldData.fromOldBookSource(jsonItem)
                } else {
                    GSON.fromJsonArray<BookSource>(text).getOrThrow()[0]
                }
            }

            text.isJsonObject() -> {
                if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
                    val jsonItem = jsonPath.parse(text)
                    ImportOldData.fromOldBookSource(jsonItem)
                } else {
                    GSON.fromJsonObject<BookSource>(text).getOrThrow()
                }
            }

            else -> throw NoStackTraceException("格式不对")
        }
    }

    fun clearCookie(url: String) {
        execute {
            CookieStore.removeCookie(url)
        }
    }

    fun ruleComplete(rule: String?, preRule: String? = null, type: Int = 1): String? {
        if (autoComplete) {
            return RuleComplete.autoComplete(rule, preRule, type)
        }
        return rule
    }

}