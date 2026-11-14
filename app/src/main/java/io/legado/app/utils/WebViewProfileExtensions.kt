package io.legado.app.utils

import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import io.legado.app.constant.AppLog
import io.legado.app.model.AutoTask

private const val TASK_WEBVIEW_PROFILE_PREFIX = "legado_task_"

/**
 * 是否是定时任务的虚拟源（key 形如 `auto_task:{任务id}`）
 */
fun isTaskSourceKey(key: String?): Boolean {
    return key?.startsWith("${AutoTask.SOURCE_KEY}:") == true
}

fun taskWebViewProfileName(sourceKey: String): String {
    return "$TASK_WEBVIEW_PROFILE_PREFIX${sourceKey.substringAfter(':')}"
}

/**
 * 删除定时任务的专属 WebView profile（任务删除时调用）。
 * 任务 loginUrl 网页登录使用独立 profile，其 cookie 与默认浏览器配置完全隔离。
 */
fun deleteTaskWebViewProfile(sourceKey: String) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
    val profileName = taskWebViewProfileName(sourceKey)
    runOnUI {
        runCatching {
            ProfileStore.getInstance().deleteProfile(profileName)
        }.onFailure {
            AppLog.put("删除任务 WebView profile 失败: $profileName", it)
        }
    }
}
