package io.legado.app.ui.book.source.edit

import android.content.Context
import android.content.Intent
import io.legado.app.data.appDb
import io.legado.app.ui.code.CodeEditActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书源编辑的统一入口。
 *
 * JS 单文件书源（`mainJs` 非空）没有规则字段可填，任何界面进入编辑都应直接进代码编辑页；
 * 其余书源继续进规则编辑页（[BookSourceEditActivity]）。
 *
 * 除本入口外，[BookSourceEditActivity] 自身也有一道兜底重定向，防止新增入口漏改。
 */
object BookSourceEditLauncher {

    const val EXTRA_SOURCE_URL = "sourceUrl"

    /**
     * 该地址是否是 JS 单文件书源。
     *
     * 注意：主线程可调用（单条主键查询，只取一个 int）。书源不存在时按普通书源处理。
     */
    fun isJsSource(sourceUrl: String): Boolean =
        appDb.bookSourceDao.hasMainJs(sourceUrl) == 1

    fun jsEditIntent(context: Context, sourceUrl: String): Intent =
        Intent(context, CodeEditActivity::class.java).apply {
            putExtra(CodeEditActivity.EXTRA_JS_SOURCE_URL, sourceUrl)
        }

    fun sourceEditIntent(context: Context, sourceUrl: String): Intent =
        Intent(context, BookSourceEditActivity::class.java).apply {
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
        }

    /** 给不依赖 ActivityResult 的入口用：异步判类型后打开对应编辑页 */
    suspend fun open(context: Context, sourceUrl: String) {
        val js = withContext(Dispatchers.IO) { isJsSource(sourceUrl) }
        context.startActivity(
            if (js) jsEditIntent(context, sourceUrl) else sourceEditIntent(context, sourceUrl)
        )
    }
}
