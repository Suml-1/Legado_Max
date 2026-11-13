package io.legado.app.help.source

import java.security.MessageDigest

/**
 * 书源 URL → 稳定命名空间。
 *
 * 用于把「同一份 jsLib 在不同书源之间」隔离成不同的共享作用域；
 * 用 SHA-256 而不是 URL 原文，避免把源码地址带进缓存 key 与日志。
 */
internal object BookSourceStorageScope {

    private const val identityPrefix = "book\u0000"

    fun namespace(sourceUrl: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest((identityPrefix + sourceUrl).toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
