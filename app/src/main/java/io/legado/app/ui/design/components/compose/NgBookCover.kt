package io.legado.app.ui.design.components.compose

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.model.localBook.localBookCoverUpdates
import io.legado.app.ui.widget.image.CoverImageView

/**
 * Compose 书籍卡片共用的封面加载器。
 *
 * 继续复用既有 [CoverImageView] 的占位图、缓存与本地封面逻辑，只统一 Compose 宿主，
 * 避免书架与管理页各自维护一套加载键。
 */
@Composable
fun NgBookCover(
    book: Book,
    modifier: Modifier = Modifier,
    coverRadius: Int? = null,
    coverAspectRatio: Float? = null,
    contentDescription: String? = null,
    revision: Int = 0,
    loadOnlyWifi: Boolean = false,
    fragment: Fragment? = null,
    lifecycle: Lifecycle? = null,
) {
    val localRevision = if (book.isLocal) key(book.bookUrl) {
        val updates = remember(book.bookUrl) { localBookCoverUpdates.observe(book.bookUrl) }
        val currentRevision by updates.collectAsStateWithLifecycle(
            initialValue = localBookCoverUpdates.revisionOf(book.bookUrl),
        )
        currentRevision
    } else 0
    val loadKey = NgBookCoverLoadKey(
        bookUrl = book.bookUrl,
        displayCover = book.getDisplayCover(),
        name = book.name,
        author = book.author,
        origin = book.origin,
        revision = revision,
        localRevision = localRevision,
        loadOnlyWifi = loadOnlyWifi,
        fragment = fragment,
        lifecycle = lifecycle,
    )
    AndroidView(
        factory = { context ->
            CoverImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { cover ->
            cover.contentDescription = contentDescription
            coverRadius?.let(cover::setCoverRadiusDp)
            coverAspectRatio?.let(cover::setCoverAspectRatio)
            if (cover.getTag(R.id.bookshelf_cover_load_key) != loadKey) {
                cover.setTag(R.id.bookshelf_cover_load_key, loadKey)
                cover.load(
                    book = book,
                    loadOnlyWifi = loadOnlyWifi,
                    fragment = fragment,
                    lifecycle = lifecycle,
                )
            }
        },
        modifier = modifier,
    )
}

private data class NgBookCoverLoadKey(
    val bookUrl: String,
    val displayCover: String?,
    val name: String,
    val author: String,
    val origin: String,
    val revision: Int,
    val localRevision: Int,
    val loadOnlyWifi: Boolean,
    val fragment: Fragment?,
    val lifecycle: Lifecycle?,
)
