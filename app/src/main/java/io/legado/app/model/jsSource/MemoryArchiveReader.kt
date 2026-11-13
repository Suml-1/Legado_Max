package io.legado.app.model.jsSource

import android.system.OsConstants.S_ISREG
import cn.hutool.core.codec.Base64
import io.legado.app.R
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * 只在内存里解压书源自带的（Base64）压缩包，供 `java.getArchiveByteArrayContent` 使用。
 *
 * 之所以不落盘：书源可以携带任意数据，条目名不可信（可能含路径穿越），
 * 这里只按名字查找并返回字节；[entryName] 也不参与任何文件路径拼接。
 * 解压前会限制输入大小、展开后大小与条目数量，避免压缩炸弹。
 */
internal object MemoryArchiveReader {
    private const val MAX_BASE64_CHARS = 2_000_000
    private const val MAX_EXPANDED_BYTES = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 128

    fun read(base64: String, entryName: String): ByteArray? {
        require(base64.length in 1..MAX_BASE64_CHARS) {
            appCtx.getString(R.string.js_source_archive_too_large)
        }
        require(entryName.length in 1..1024 && entryName.none { it.code < 32 }) {
            appCtx.getString(R.string.js_source_archive_entry_invalid)
        }
        val bytes = Base64.decode(base64)
        require(bytes.isNotEmpty() && bytes.size <= MAX_BASE64_CHARS * 3 / 4) {
            appCtx.getString(R.string.js_source_archive_too_large)
        }
        val input = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            flip()
        }
        val archive = Archive.readNew()
        val started = System.nanoTime()
        fun checkRunning() {
            check(!Thread.currentThread().isInterrupted) { "Archive extraction interrupted" }
            check(System.nanoTime() - started < 15_000_000_000L) { "Archive extraction timed out" }
        }
        try {
            Archive.setCharset(archive, "UTF-8".toByteArray())
            Archive.readSupportFormatTar(archive)
            Archive.readSupportFormatZip(archive)
            Archive.readSupportFormat7zip(archive)
            Archive.readSupportFilterGzip(archive)
            Archive.readSupportFilterBzip2(archive)
            Archive.readSupportFilterXz(archive)
            Archive.readSupportFilterZstd(archive)
            Archive.readOpenMemory(archive, input)
            val chunk = ByteBuffer.allocateDirect(64 * 1024)
            val copy = ByteArray(chunk.capacity())
            var expanded = 0L
            var entries = 0
            while (true) {
                checkRunning()
                val entry = Archive.readNextHeader(archive)
                if (entry == 0L) return null
                check(++entries <= MAX_ENTRIES) { "Archive entry count exceeds limit" }
                val size = ArchiveEntry.size(entry)
                check(size <= MAX_EXPANDED_BYTES - expanded) { "Archive expanded data exceeds limit" }
                val name = ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.toString(Charsets.UTF_8)
                val output = if (name == entryName && S_ISREG(ArchiveEntry.stat(entry).stMode)) ByteArrayOutputStream() else null
                // Read skipped entries too, so compressed skipped data cannot evade the quota.
                while (true) {
                    checkRunning()
                    chunk.clear()
                    Archive.readData(archive, chunk)
                    chunk.flip()
                    val count = chunk.remaining()
                    if (count == 0) break
                    expanded += count
                    check(expanded <= MAX_EXPANDED_BYTES) { "Archive expanded data exceeds limit" }
                    if (output != null) {
                        chunk.get(copy, 0, count)
                        output.write(copy, 0, count)
                    }
                }
                if (output != null) return output.toByteArray()
            }
        } finally {
            Archive.free(archive)
            // Keep the backing memory alive through native archive disposal.
            input.clear()
        }
    }
}
