package io.legado.app.ui.main.explore.compose

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.setHtml

/**
 * html 类分类项（`type: html`）。
 *
 * 与其它分类项不同，html 内容本质上是富文本与网页，Compose 没有等价物：
 * - `<usehtml>` 是带图片/按钮/链接的富文本 → [ScrollTextView] + `Html.fromHtml` 链路；
 * - `<useweb>` 是真实网页 → 池化 WebView。
 *
 * 因此这两类内容仍由 `AndroidView` 承载，但**内容解析、JS 求值、桥接与管理仍在
 * [ExploreKindsController] 上**，与其它分类项共用同一套作用域与 infoMap。
 */
@Composable
internal fun ExploreHtmlContent(
    modifier: Modifier = Modifier,
    kind: ExploreKind,
    sourceUrl: String,
    controller: ExploreKindsController,
    onShowPhoto: (url: String, sourceUrl: String) -> Unit,
) {
    val content by produceState<String?>(
        initialValue = null,
        key1 = sourceUrl,
        key2 = kind,
    ) {
        value = controller.resolveHtmlContent(kind, sourceUrl)
    }
    val payload = content?.trim().orEmpty()
    when {
        payload.startsWith(HTML_KIND_USE_WEB) -> ExploreUseWebContent(
            modifier = modifier,
            payload = payload,
            sourceUrl = sourceUrl,
            controller = controller,
        )

        payload.startsWith(HTML_KIND_USE_HTML) -> ExploreUseHtmlText(
            modifier = modifier,
            payload = payload,
            kindTitle = kind.title,
            sourceUrl = sourceUrl,
            controller = controller,
            onShowPhoto = onShowPhoto,
        )
    }
}

/**
 * `<usehtml>` 富文本。
 *
 * 对齐原实现的渲染链路：`Html.fromHtml` + Glide 图片加载 + 自定义标签处理，
 * 正文里的按钮走书源 `action` 脚本、图片点击走脚本、长按弹图片查看。
 */
@Composable
private fun ExploreUseHtmlText(
    modifier: Modifier,
    payload: String,
    kindTitle: String,
    sourceUrl: String,
    controller: ExploreKindsController,
    onShowPhoto: (url: String, sourceUrl: String) -> Unit,
) {
    val endIndex = payload.lastIndexOf('<')
    if (endIndex < HTML_KIND_USE_HTML.length) return
    val html = payload.substring(HTML_KIND_USE_HTML.length, endIndex)
    val density = LocalDensity.current
    val contentPaddingPx = with(density) { AppDimens.exploreHtmlContentPadding.roundToPx() }
    val minHeightPx = with(density) { AppDimens.exploreHtmlMinHeight.roundToPx() }
    val imageWidthPx = with(density) { AppDimens.exploreHtmlImageMargin.roundToPx() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            buildExploreHtmlContainer(
                context = context,
                html = html,
                lifecycle = lifecycle,
                kindTitle = kindTitle,
                sourceUrl = sourceUrl,
                controller = controller,
                contentPaddingPx = contentPaddingPx,
                minHeightPx = minHeightPx,
                imageWidthPx = imageWidthPx,
                onShowPhoto = onShowPhoto,
            )
        },
    )
}

/**
 * 构建 `<usehtml>` 的宿主容器。
 *
 * 这里刻意复刻原 View 实现的层级（容器 → ScrollTextView），因为它与 `GlideImageGetter`
 * 的图片宽度换算、`setHtml` 的图文混排行为绑定在一起，改成 Compose 文本会丢图与丢按钮。
 */
private fun buildExploreHtmlContainer(
    context: Context,
    html: String,
    lifecycle: Lifecycle,
    kindTitle: String,
    sourceUrl: String,
    controller: ExploreKindsController,
    contentPaddingPx: Int,
    minHeightPx: Int,
    imageWidthPx: Int,
    onShowPhoto: (url: String, sourceUrl: String) -> Unit,
): FrameLayout {
    val textView = ScrollTextView(context, null).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        minHeight = minHeightPx
        setPadding(contentPaddingPx, contentPaddingPx, contentPaddingPx, contentPaddingPx)
        textSize = HTML_TEXT_SIZE_SP
    }
    val imageGetter = GlideImageGetter(
        context,
        textView,
        lifecycle,
        context.resources.displayMetrics.widthPixels - imageWidthPx,
        sourceUrl,
    )
    val tagHandler = TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
        override fun onButtonClick(name: String, click: String) {
            controller.evalAction(sourceUrl, click, "$kindTitle $name")
        }
    })
    textView.setHtml(
        html,
        imageGetter,
        tagHandler,
        imgOnLongClickListener = { onShowPhoto(it, sourceUrl) },
        imgOnClickListener = { click ->
            controller.evalAction(sourceUrl, click, "$kindTitle image")
        },
    )
    return FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(textView)
    }
}

/** 富文本字号，对齐原 `ScrollTextView.textSize = 14F` */
private const val HTML_TEXT_SIZE_SP = 14F
