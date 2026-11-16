package io.legado.app.help.config

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Draft images are disposable; saved images are shared only while a theme references them. */
internal object NgThemeDrawerAssets {
    private const val DRAFT_DIR = "ng_drawer_image_drafts"
    private const val IMAGE_DIR = "ng_drawer_images"
    private const val CURRENT_IMAGE_DIR = "ng_current_drawer_images"
    private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
    private const val MAX_IMAGE_PIXELS = 64L * 1024 * 1024

    suspend fun copyDraft(context: Context, uri: Uri): String {
        var draft: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "jpg"
                }
                val root = File(context.cacheDir, DRAFT_DIR).apply { mkdirs() }
                val target = File(root, "${UUID.randomUUID()}.$extension").also { draft = it }
                val input = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
                input.use { copyBounded(it, target) }
                validateImage(target)
                target.absolutePath
            }
        } catch (error: Throwable) {
            // Also covers cancellation while dispatching the completed copy back to the editor.
            draft?.delete()
            throw error
        }
    }

    fun discardDraft(context: Context, path: String?) {
        deleteDirectChild(File(context.cacheDir, DRAFT_DIR), path)
    }

    fun discardDrafts(context: Context, profile: NgThemeDrawerProfile?) {
        profile.imagePaths().forEach { discardDraft(context, it) }
    }

    /** Caller persists [NgPreparedDrawerImages.theme] before releasing its draft. */
    fun prepare(context: Context, theme: NgManagedTheme): NgPreparedDrawerImages {
        val profile = theme.drawerProfile ?: return NgPreparedDrawerImages(theme)
        val prepared = prepareProfile(context, profile, currentSettings = false)
        return NgPreparedDrawerImages(theme.copy(drawerProfile = prepared.profile), prepared.created)
    }

    fun prepareCurrent(context: Context, profile: NgThemeDrawerProfile): NgPreparedDrawerProfile =
        prepareProfile(context, profile, currentSettings = true)

    fun removeReplacedCurrent(
        context: Context,
        previous: NgThemeDrawerProfile?,
        retained: NgThemeDrawerProfile,
    ) {
        val retainedPaths = retained.imagePaths().toSet()
        previous.imagePaths().filterNot { it in retainedPaths }.forEach {
            deleteDirectChild(File(context.filesDir, CURRENT_IMAGE_DIR), it)
        }
    }

    private fun prepareProfile(
        context: Context,
        profile: NgThemeDrawerProfile,
        currentSettings: Boolean,
    ): NgPreparedDrawerProfile {
        val root = File(context.filesDir, if (currentSettings) CURRENT_IMAGE_DIR else IMAGE_DIR).canonicalFile
        val created = mutableListOf<File>()
        val paths = mutableMapOf<String, String>()
        fun materialize(style: NgThemeDrawerStyle): NgThemeDrawerStyle {
            val source = style.imagePath ?: return style
            paths[source]?.let { return style.copy(imagePath = it) }
            val sourceFile = if (source.startsWith("asset://")) null else File(source).canonicalFile
            if (sourceFile?.parentFile == root && sourceFile.isFile) {
                return style.copy(imagePath = sourceFile.absolutePath)
            }
            root.mkdirs()
            val extension = source.substringAfterLast('.', "jpg")
                .lowercase().takeIf { it in setOf("png", "webp", "gif", "jpg", "jpeg") } ?: "jpg"
            val target = File(root, "${UUID.randomUUID()}.$extension")
            created += target
            val input = if (source.startsWith("asset://")) {
                context.assets.open(source.removePrefix("asset://"))
            } else {
                requireNotNull(sourceFile).inputStream()
            }
            input.use { copyBounded(it, target) }
            validateImage(target)
            paths[source] = target.absolutePath
            return style.copy(imagePath = target.absolutePath)
        }
        return try {
            NgPreparedDrawerProfile(
                profile.copy(
                    light = materialize(profile.light),
                    dark = materialize(profile.dark),
                ),
                created,
                sourceProfile = profile,
            )
        } catch (error: Throwable) {
            created.forEach(File::delete)
            throw error
        }
    }

    fun removeUnreferenced(
        context: Context,
        candidates: Collection<NgManagedTheme>,
        retained: Collection<NgManagedTheme>,
    ) {
        val retainedPaths = retained.flatMapTo(hashSetOf()) { it.drawerProfile.imagePaths() }
        val root = File(context.filesDir, IMAGE_DIR)
        candidates.flatMap { it.drawerProfile.imagePaths() }
            .filterNot { it in retainedPaths }
            .forEach { deleteDirectChild(root, it) }
    }

    internal fun validateImage(file: File) {
        require(file.length() in 1..MAX_IMAGE_BYTES) { "图片为空或超过 32 MB" }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "无法读取图片格式" }
        require(options.outWidth.toLong() * options.outHeight <= MAX_IMAGE_PIXELS) {
            "图片尺寸过大，请缩小后重试"
        }
    }

    private fun copyBounded(input: InputStream, target: File) {
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytes = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                require(bytes <= MAX_IMAGE_BYTES) { "图片超过 32 MB" }
                output.write(buffer, 0, count)
            }
        }
    }

    private fun deleteDirectChild(root: File, path: String?) {
        path ?: return
        runCatching {
            val file = File(path).canonicalFile
            if (file.parentFile == root.canonicalFile && file.isFile) file.delete()
        }
    }
}

internal class NgPreparedDrawerProfile(
    val profile: NgThemeDrawerProfile,
    val created: List<File> = emptyList(),
    val sourceProfile: NgThemeDrawerProfile = profile,
) {
    private var committed = false

    fun markCommitted() { committed = true }

    fun discard() {
        if (!committed) created.forEach(File::delete)
    }
}

internal data class NgPreparedDrawerImages(
    val theme: NgManagedTheme,
    val created: List<File> = emptyList(),
) {
    fun discard() = created.forEach(File::delete)
}

private fun NgThemeDrawerProfile?.imagePaths(): List<String> =
    this?.let { listOfNotNull(it.light.imagePath, it.dark.imagePath) }.orEmpty()
