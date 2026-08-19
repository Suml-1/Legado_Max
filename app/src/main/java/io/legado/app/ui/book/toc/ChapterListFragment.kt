package io.legado.app.ui.book.toc

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.ViewConfiguration
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.FragmentChapterListBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.gone
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChapterListFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_chapter_list),
    ChapterListAdapter.Callback,
    TocViewModel.ChapterListCallBack {
    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentChapterListBinding::bind)
    private val mLayoutManager by lazy { UpLinearLayoutManager(requireContext()) }
    private val adapter by lazy { ChapterListAdapter(requireContext(), this) }
    private var durChapterIndex = 0
    private var shouldAutoScrollToCurrent = false
    private val viewScope get() = viewLifecycleOwner.lifecycleScope

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        viewModel.chapterListCallBack = this@ChapterListFragment
        val bbg = bottomBackground
        val btc = requireContext().getPrimaryTextColor(ColorUtils.isColorLight(bbg))
        llChapterBaseInfo.setBackgroundColor(bbg)
        tvCurrentChapterInfo.setTextColor(btc)
        ivChapterTop.setColorFilter(btc, PorterDuff.Mode.SRC_IN)
        ivChapterBottom.setColorFilter(btc, PorterDuff.Mode.SRC_IN)
        initRecyclerView()
        initView()
        viewModel.bookData.observe(this@ChapterListFragment) {
            initBook(it)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.scrollBarSize =
            ViewConfiguration.get(requireContext()).scaledScrollBarSize
        binding.recyclerView.setHideScrollbar(false)
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
    }

    private fun initView() = binding.run {
        ivChapterTop.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        ivChapterBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        tvCurrentChapterInfo.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(durChapterIndex, 0)
        }
        binding.llChapterBaseInfo.applyNavigationBarPadding()
    }

    @SuppressLint("SetTextI18n")
    private fun initBook(book: Book) {
        AppLog.putReaderDebug("[TOC-Frag] initBook: bookUrl=${book.bookUrl}, totalChapterNum=${book.totalChapterNum}, isLocal=${book.isLocal}")
        viewScope.launch {
            shouldAutoScrollToCurrent = true
            durChapterIndex = book.durChapterIndex
            upChapterList(null)
            AppLog.putReaderDebug("[TOC-Frag] initBook after upChapterList: adapter.itemCount=${adapter.itemCount}")
            // 如果数据库为空且不是本地书，可能正在渐进加载中，延迟重试
            if (adapter.itemCount == 0 && !book.isLocal) {
                delay(2000)
                AppLog.putReaderDebug("[TOC-Frag] 延迟重试: adapter.itemCount=${adapter.itemCount}")
                if (adapter.itemCount == 0) {
                    upChapterList(null)
                    AppLog.putReaderDebug("[TOC-Frag] 重试后: adapter.itemCount=${adapter.itemCount}")
                }
            }
        }
    }

    private fun updateCurrentChapterInfo(book: Book) {
        val chapters = chapterList
        if (chapters.isNullOrEmpty()) {
            binding.tvCurrentChapterInfo.text =
                "${book.durChapterTitle}(${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()})"
            return
        }
        val chapterPosition = chapters.indexOfFirst { it.index == book.durChapterIndex }
        if (chapterPosition < 0) {
            binding.tvCurrentChapterInfo.text =
                "${book.durChapterTitle}(${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()})"
            return
        }
        val volumes = chapters.withIndex().filter { it.value.isVolume }
        val currentVolume = volumes.lastOrNull { it.index <= chapterPosition }
        if (currentVolume == null) {
            binding.tvCurrentChapterInfo.text =
                "${book.durChapterTitle}(${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()})"
            return
        }
        val nextVolumePosition = volumes.firstOrNull { it.index > currentVolume.index }?.index ?: chapters.size
        val chapterInVolumeIndex = chapters
            .subList(currentVolume.index + 1, chapterPosition + 1)
            .count { !it.isVolume }
            .coerceAtLeast(1)
        val chapterCountInVolume = chapters
            .subList(currentVolume.index + 1, nextVolumePosition)
            .count { !it.isVolume }
        val volumeTitle = currentVolume.value.title
        val totalProgress = "${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()}"
        val chapterTitle = book.durChapterTitle
        binding.tvCurrentChapterInfo.text =
            if (chapterTitle == currentVolume.value.title) {
                "$volumeTitle·$totalProgress"
            } else {
                "$volumeTitle·$chapterTitle·$totalProgress"
            }
    }

    private fun initCacheFileNames(book: Book) {
        viewScope.launch(IO) {
            val fileNames = BookHelp.getChapterFiles(book)
            withContext(Main) {
                adapter.cacheFileNames.clear()
                adapter.cacheFileNames.addAll(fileNames)
                adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.bookData.value?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == bookUrl) {
                    adapter.cacheFileNames.add(chapter.getFileName())
                    adapter.notifyChapterChanged(chapter.index)
                }
            }
        }
        observeEventSticky<String>(EventBus.TOC_PARTIAL_LOADED) { bookUrl ->
            AppLog.putReaderDebug("[TOC-Frag] 收到TOC_PARTIAL_LOADED事件: bookUrl=$bookUrl")
            if (viewModel.bookUrl == bookUrl) {
                binding.tvTocLoading.visible()
                upChapterList(null)
            }
        }
        observeEventSticky<String>(EventBus.TOC_LOAD_COMPLETE) { bookUrl ->
            if (viewModel.bookUrl == bookUrl) {
                binding.tvTocLoading.gone()
            }
        }
    }

    override fun upChapterList(searchKey: String?) {
        viewScope.launch {
            withContext(IO) {
                val currentBook = book
                val isPartialLoadingBook = AppConfig.isTocPartialLoad &&
                        currentBook?.isLocal == false &&
                        !currentBook.readSimulating()
                val end = if (isPartialLoadingBook) {
                    Int.MAX_VALUE
                } else {
                    (currentBook?.simulatedTotalChapterNum() ?: Int.MAX_VALUE) - 1
                }
                AppLog.putReaderDebug("[TOC-Frag] upChapterList: bookUrl=${viewModel.bookUrl}, totalChapterNum=${book?.totalChapterNum}, end=$end")
                when {
                    searchKey.isNullOrBlank() ->
                        appDb.bookChapterDao.getChapterList(viewModel.bookUrl, 0, end).also {
                            chapterList = it
                            AppLog.putReaderDebug("[TOC-Frag] DB查询结果: count=${it.size}")
                        }

                    else -> appDb.bookChapterDao.search(viewModel.bookUrl, searchKey, 0, end)
                }
            }.let {
                adapter.setChapterItems(it, applyCollapse = searchKey.isNullOrBlank())
                if (searchKey.isNullOrBlank()) {
                    book?.let(::updateCurrentChapterInfo)
                    // 全量刷新时重新扫描缓存文件, 确保缓存状态图标与实际文件同步
                    book?.let(::initCacheFileNames)
                } else {
                    upCurrentChapterInfo(it)
                }
            }
        }
    }

    private fun upCurrentChapterInfo(chapters: List<BookChapter>) {
        val book = book ?: return
        val title = book.durChapterTitle
            ?.takeIf { it.isNotBlank() }
            ?: chapters.getOrNull(book.durChapterIndex)?.title
            ?: chapters.firstOrNull()?.title
            ?: getString(R.string.loading)
        val total = if (AppConfig.isTocPartialLoad && !book.isLocal && !book.readSimulating()) {
            chapters.size.takeIf { it > 0 } ?: book.simulatedTotalChapterNum()
        } else {
            book.simulatedTotalChapterNum()
        }
        binding.tvCurrentChapterInfo.text = "$title(${book.durChapterIndex + 1}/$total)"
    }

    override fun onListChanged() {
        viewScope.launch {
            val shouldAutoScroll = shouldAutoScrollToCurrent
            var targetPos = mLayoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
            if (shouldAutoScroll) {
                withContext(Default) {
                    adapter.getItems().forEachIndexed { index, bookChapter ->
                        if (bookChapter.index >= durChapterIndex) {
                            return@withContext
                        }
                        targetPos = index
                    }
                }
            }
            if (shouldAutoScroll) {
                mLayoutManager.scrollToPositionWithOffset(targetPos, 0)
                shouldAutoScrollToCurrent = false
            }
            adapter.upDisplayTitles(targetPos)
        }
    }

    override fun clearDisplayTitle() {
        adapter.clearDisplayTitle()
        adapter.upDisplayTitles(mLayoutManager.findFirstVisibleItemPosition())
    }

    override fun upAdapter() {
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    override val scope: CoroutineScope
        get() = viewScope

    override val book: Book?
        get() = viewModel.bookData.value

    override val isLocalBook: Boolean
        get() = viewModel.bookData.value?.isLocal == true

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }
    private var chapterList: List<BookChapter>? = null

    override fun openChapter(bookChapter: BookChapter) {
        activity?.run {
            if (book?.isVideo == true) {
                val volumes = arrayListOf<BookChapter>()
                chapterList?.forEach { chapter ->
                    if (chapter.isVolume) {
                        volumes.add(chapter)
                    }
                }
                var chapterInVolumeIndex = 0
                var durVolumeIndex = 0
                val chapters = chapterList
                val chapterPosition = chapters?.indexOfFirst { it.index == bookChapter.index } ?: -1
                if (volumes.isNotEmpty() && chapters != null && chapterPosition >= 0) {
                    for ((index, volume) in volumes.withIndex()) {
                        val volumePosition =
                            chapters.indexOfFirst { it.isVolume && it.index == volume.index }
                        if (volumePosition < 0 || volumePosition > chapterPosition) {
                            continue
                        }
                        val nextVolumePosition = volumes.getOrNull(index + 1)
                            ?.let { nextVolume ->
                                chapters.indexOfFirst {
                                    it.isVolume && it.index == nextVolume.index
                                }
                            }
                            ?.takeIf { it > volumePosition }
                            ?: chapters.size
                        if (chapterPosition < nextVolumePosition) {
                            durVolumeIndex = index
                            chapterInVolumeIndex = chapters
                                .subList(volumePosition + 1, chapterPosition + 1)
                                .count { !it.isVolume } - 1
                            if (chapterInVolumeIndex < 0) {
                                chapterInVolumeIndex = 0
                            }
                            break
                        }
                    }
                } else {
                    chapterInVolumeIndex = bookChapter.index
                }
                setResult(
                    RESULT_OK, Intent()
                        .putExtra("index", bookChapter.index)
                        .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
                        .putExtra("durVolumeIndex", durVolumeIndex)
                        .putExtra("chapterInVolumeIndex", chapterInVolumeIndex)
                )
                finish()
                return@run
            }
            setResult(
                RESULT_OK, Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
            )
            finish()
        }
    }

}
