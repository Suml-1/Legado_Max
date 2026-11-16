package io.legado.app.help.resource

import android.content.Context
import com.google.gson.annotations.SerializedName
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 远程主题包清单（仅主题外观分类用，字段按 legado-resources 目录 JSON 裁剪）。 */
data class RemoteThemePayload(
    @SerializedName("path") val path: String = "",
    @SerializedName("format") val format: String = "",
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("sha256") val sha256: String? = null,
)

data class RemoteThemeItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("versionCode") val versionCode: Long = 0L,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("description") val description: String = "",
    @SerializedName("payload") val payload: RemoteThemePayload = RemoteThemePayload(),
) {
    val displayVersion: String
        get() = versionName?.takeIf { it.isNotBlank() } ?: versionCode.toString()
}

private data class RemoteCategory(
    @SerializedName("id") val id: String = "",
    @SerializedName("index") val index: String = "",
)

private data class RemoteCatalog(
    @SerializedName("categories") val categories: List<RemoteCategory> = emptyList(),
)

private data class RemoteIndex(
    @SerializedName("resources") val resources: List<RemoteThemeItem> = emptyList(),
)

/**
 * 精简在线主题目录适配器：只负责从 legado-resources 拉取主题分类清单并下载主题包 zip，
 * 不引入 NG 资源中心里与宠物、朗读引擎、组件皮肤、AI 耦合的安装/策略代码。
 * 下载后的 zip 交给现有 NgThemePackageManager 走确认安装流程。
 */
object RemoteThemeCatalog {

    private const val REPOSITORY = "joestar817/legado-resources"
    private const val RAW_BASE = "https://raw.githubusercontent.com/$REPOSITORY"
    private const val CATALOG_BRANCH = "catalog"
    private const val PAYLOAD_BRANCH = "main"
    private const val THEME_CATEGORY = "themes"
    private const val ONLINE_THEME_DIR = "ng_theme_online"

    /** 拉取主题分类下的全部主题条目。 */
    suspend fun fetchThemes(): List<RemoteThemeItem> = withContext(Dispatchers.IO) {
        val catalogBody = okHttpClient.newCallResponse {
            url("$RAW_BASE/$CATALOG_BRANCH/catalog.json")
        }.body?.text() ?: throw NoStackTraceException("目录为空")
        val catalog = GSON.fromJsonObject<RemoteCatalog>(catalogBody)
            .getOrElse { throw NoStackTraceException("目录解析失败") }
        val indexPath = catalog.categories.firstOrNull { it.id == THEME_CATEGORY }?.index
            ?: throw NoStackTraceException("未找到主题分类")
        val indexBody = okHttpClient.newCallResponse {
            url("$RAW_BASE/$CATALOG_BRANCH/$indexPath")
        }.body?.text() ?: throw NoStackTraceException("主题清单为空")
        GSON.fromJsonObject<RemoteIndex>(indexBody)
            .getOrElse { throw NoStackTraceException("主题清单解析失败") }
            .resources
    }

    /** 下载主题包 zip 到缓存目录，返回临时文件；调用方在用完后负责删除。 */
    suspend fun downloadThemeZip(context: Context, item: RemoteThemeItem): File =
        withContext(Dispatchers.IO) {
            if (item.payload.path.isBlank()) throw NoStackTraceException("主题包地址缺失")
            var lastError: Exception? = null
            for (branch in listOf(PAYLOAD_BRANCH, CATALOG_BRANCH)) {
                try {
                    val response = okHttpClient.newCallResponse {
                        url("$RAW_BASE/$branch/${item.payload.path}")
                    }
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                            ?: throw NoStackTraceException("主题包内容为空")
                        val dir = File(context.cacheDir, ONLINE_THEME_DIR).apply { mkdirs() }
                        val file = File(dir, "${UUID.randomUUID()}.zip")
                        file.outputStream().use { it.write(bytes) }
                        return@withContext file
                    }
                    lastError = NoStackTraceException("下载失败(${response.code})")
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: NoStackTraceException("下载失败")
        }
}
