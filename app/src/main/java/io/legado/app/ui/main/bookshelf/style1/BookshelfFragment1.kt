@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.style1

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.book.BookTagManagement
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.loadBookshelfTagBarData
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.ui.widget.RoundedTagBarView
import io.legado.app.utils.isCreated
import io.legado.app.utils.MenuExtensions
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.collections.set

/**
 * 书架界面
 * 支持两种分组切换模式：
 * 1. TabLayout 模式（下拉选择分组开关未勾选）：显示所有分组标签，可滑动点击切换
 * 2. 下拉选择模式（下拉选择分组开关勾选）：点击标题栏弹出下拉选择分组菜单
 */
class BookshelfFragment1() :
    BaseBookshelfFragment(R.layout.fragment_bookshelf1),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager) }

    // 下拉选择模式相关控件
    private var titleSelect: LinearLayout? = null
    private var tvGroupName: TextView? = null
    private var ivArrow: ImageView? = null

    // TabLayout 模式相关控件
    private var tabLayout: TabLayout? = null

    // 二级标签栏
    private var tagBar: RoundedTagBarView? = null
    private var tagSelectedIndex = -1
    private var currentTagList: List<String> = emptyList()
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    private var currentPosition = 0
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override var onlyUpdateRead = false
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 清理已销毁子页面的引用，避免 fragmentMap 持有导致内存泄漏
        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(fm: FragmentManager, fragment: Fragment) {
                    fragmentMap.entries.removeIf { it.value === fragment }
                }
            },
            true,
        )
        setSupportToolbar(binding.titleBar.toolbar)
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(currentPosition)

    private fun initView() {
        binding.viewPagerBookshelf.setEdgeEffectColor(primaryColor)
        binding.viewPagerBookshelf.offscreenPageLimit = 2
        binding.viewPagerBookshelf.adapter = adapter
        tagBar = binding.tagBar
        tagBar?.setOnTagClickListener { index ->
            tagSelectedIndex = index
            tagBar?.setSelectedIndex(index)
            refreshBooksByTag()
        }
        // 根据"下拉选择分组"开关动态添加布局到 TitleBar
        if (AppConfig.dropdownSelectGroup) {
            // 下拉选择模式：清除 toolbar 默认标题，避免与分组名同时显示
            binding.titleBar.title = ""
            // 下拉选择模式：添加 view_group_selector 布局
            val groupSelectorView = LayoutInflater.from(requireContext())
                .inflate(R.layout.view_group_selector, binding.titleBar.toolbar, false)
            binding.titleBar.toolbar.addView(groupSelectorView)
            titleSelect = groupSelectorView.findViewById(R.id.title_select)
            tvGroupName = groupSelectorView.findViewById(R.id.tv_group_name)
            ivArrow = groupSelectorView.findViewById(R.id.iv_arrow)
            // 监听 ViewPager 页面切换，更新当前分组名称显示
            binding.viewPagerBookshelf.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageSelected(position: Int) {
                    currentPosition = position
                    AppConfig.saveTabPosition = position
                    tvGroupName?.text = bookGroups.getOrNull(position)?.groupName ?: ""
                    loadTagBar()
                }
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
                override fun onPageScrollStateChanged(state: Int) {}
            })
            initTitleSelect()
            updateTitleColor()
        } else {
            // TabLayout 模式：添加 view_tab_layout_min 布局
            val tabLayoutView = LayoutInflater.from(requireContext())
                .inflate(R.layout.view_tab_layout_min, binding.titleBar.toolbar, false)
            binding.titleBar.toolbar.addView(tabLayoutView)
            tabLayout = tabLayoutView.findViewById(R.id.tab_layout)
            tabLayout?.let { tab ->
                tab.isTabIndicatorFullWidth = false
                tab.tabMode = TabLayout.MODE_SCROLLABLE
                tab.setSelectedTabIndicatorColor(requireContext().accentColor)
                tab.setupWithViewPager(binding.viewPagerBookshelf)
            }
        }
    }

    private fun initTitleSelect() {
        // 下拉选择模式：点击标题栏弹出下拉选择分组菜单
        titleSelect?.setOnClickListener {
            if (bookGroups.isEmpty()) return@setOnClickListener
            val groupNames = bookGroups.map { it.groupName }
            val popup = ListPopupWindow(requireContext())
            popup.anchorView = titleSelect
            // 使用自定义适配器显示勾号
            popup.setAdapter(GroupSelectorAdapter(requireContext(), groupNames, currentPosition))
            // 手动测量最宽分组名的宽度
            val maxWidth = measureMaxTextWidth(groupNames)
            popup.width = maxWidth + 72 // 加上padding和勾号宽度
            popup.setOnItemClickListener { _, _, position, _ ->
                currentPosition = position
                AppConfig.saveTabPosition = position
                tvGroupName?.text = bookGroups[position].groupName
                binding.viewPagerBookshelf.setCurrentItem(position, false)
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun measureMaxTextWidth(items: List<String>): Int {
        val paint = tvGroupName?.paint ?: return 0
        var maxWidth = 0
        for (item in items) {
            val width = paint.measureText(item).toInt()
            if (width > maxWidth) maxWidth = width
        }
        return maxWidth
    }

    // 自定义适配器，显示勾号
    private class GroupSelectorAdapter(
        context: android.content.Context,
        items: List<String>,
        private val selectedPosition: Int,
    ) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, items) {

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val view = super.getView(position, convertView, parent)
            if (view is TextView) {
                if (position == selectedPosition) {
                    view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
                } else {
                    view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            }
            return view
        }
    }

    private fun updateTitleColor() {
        // 与右上角搜索/三点菜单图标同一取色逻辑（MenuExtensions.getMenuColor），
        // 保证透明导航栏/顶栏明暗模式下分组名与图标颜色一致
        val textColor = MenuExtensions.getMenuColor(requireContext(), binding.titleBar.topBarTheme)
        tvGroupName?.setTextColor(textColor)
        ivArrow?.setColorFilter(textColor)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean = false

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                val lastPosition = AppConfig.saveTabPosition
                    .coerceIn(0, bookGroups.size - 1)
                // 先设置 currentPosition，这样 onPageSelected / onTabSelected
                // 回调能正确使用目标位置，避免回调将位置覆盖为 0
                currentPosition = lastPosition
                // 先调用 notifyDataSetChanged 让 ViewPager 知道新的数据量，
                // 然后立即设置 currentItem 到目标位置（无动画），
                // 这样 ViewPager 不会先显示 position 0 再切换，避免闪烁
                adapter.notifyDataSetChanged()
                // notifyDataSetChanged 之后 ViewPager 已知道新的 item count，
                // 此时 setCurrentItem 不会崩溃，且能在同一帧内完成位置切换
                binding.viewPagerBookshelf.setCurrentItem(lastPosition, false)
                if (AppConfig.dropdownSelectGroup) {
                    AppConfig.saveTabPosition = lastPosition
                    updateTitleSelect()
                    loadTagBar()
                } else {
                    selectLastTab(lastPosition)
                    // 设置长按分组标签编辑分组
                    for (i in 0 until adapter.count) {
                        tabLayout?.getTabAt(i)?.view?.setOnLongClickListener {
                            showDialogFragment(GroupEditDialog(bookGroups[i]))
                            true
                        }
                    }
                    loadTagBar()
                }
            }
        }
    }

    override fun upSort() {
        adapter.notifyDataSetChanged()
    }

    private fun updateTitleSelect() {
        if (bookGroups.isNotEmpty()) {
            val position = currentPosition.coerceIn(0, bookGroups.size - 1)
            tvGroupName?.text = bookGroups[position].groupName
        }
    }

    // TabLayout 模式：选择上次保存的分组
    // ViewPager 的 currentItem 已在 upGroup 中通过 setCurrentItem 同步设置，
    // TabLayout 通过 setupWithViewPager 自动跟随 ViewPager 的位置，
    // 但 TabLayout 的视觉选中可能不同步，需要显式 select。
    private fun selectLastTab(lastPosition: Int) {
        val position = lastPosition.coerceIn(0, bookGroups.size - 1)
        AppConfig.saveTabPosition = position
        // 移除监听器避免 select 触发 onTabSelected 覆盖已设置的位置
        tabLayout?.removeOnTabSelectedListener(this)
        tabLayout?.getTabAt(position)?.select()
        tabLayout?.addOnTabSelectedListener(this)
    }

    // TabLayout 模式：Tab 选中回调
    override fun onTabSelected(tab: TabLayout.Tab) {
        currentPosition = tab.position
        AppConfig.saveTabPosition = tab.position
        loadTagBar()
    }

    // TabLayout 模式：Tab 未选中回调
    override fun onTabUnselected(tab: TabLayout.Tab) = Unit

    // TabLayout 模式：Tab 再次选中回调（显示分组书籍数量）
    override fun onTabReselected(tab: TabLayout.Tab) {
        selectedGroup?.let { group ->
            fragmentMap[group.groupId]?.let {
                toastOnUi("${group.groupName}(${it.getBooksCount()})")
            }
        }
    }

    /**
     * 加载当前分组的二级标签栏数据。
     * 标签来源：当前分组中实际有书籍使用的标签 + 用户手动配置的标签，
     * 减去被隐藏的标签。只显示当前分组中有对应书籍的标签。
     */
    private fun loadTagBar() {
        if (!AppConfig.showBookshelfTagBar) {
            tagBar?.visibility = View.GONE
            tagSelectedIndex = -1
            currentTagList = emptyList()
            fragmentMap[groupId]?.filterByTag(null)
            return
        }
        val currentGroupId = groupId
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val allText = getString(R.string.bookshelf_tag_all)
            val (tags, tagCounts) = loadBookshelfTagBarData(context, currentGroupId)
            // 在标签列表前插入空字符串作为“全部”标签，显示时转为 allText
            currentTagList = listOf("") + tags
            tagSelectedIndex = 0
            tagBar?.visibility = View.VISIBLE
            tagBar?.applyTopBarStyle(force = true)
            tagBar?.submitItems(
                currentTagList.map { tag ->
                    RoundedTagBarView.Item(
                        BookTagManagement.tagBarLabel(tag, allText, tagCounts[tag] ?: 0),
                    )
                },
                0,
            )
            tagBar?.setSelectedIndex(0, false)
            refreshBooksByTag()
        }
    }

    /**
     * 根据选中的标签筛选当前分组的书籍。
     * “全部”标签（索引0）传 null 表示不筛选。
     */
    private fun refreshBooksByTag() {
        val fragment = fragmentMap[groupId] ?: return
        val selectedIndex = tagSelectedIndex
        if (selectedIndex <= 0 || selectedIndex >= currentTagList.size) {
            fragment.filterByTag(null)
        } else {
            fragment.filterByTag(currentTagList[selectedIndex])
        }
    }

    override fun gotoTop() {
        fragmentMap[groupId]?.gotoTop()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            loadTagBar()
        }
        // 顶栏配置变更时，同步刷新二级标签栏样式
        observeEvent<Boolean>(EventBus.TOP_BAR_CHANGED) { isNightMode ->
            if (isNightMode == AppConfig.isNightTheme) {
                tagBar?.applyTopBarStyle(force = true)
            }
        }
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        if (view == null) return
        fragmentMap.values.forEach {
            if (it.view != null) {
                it.updateMainBottomPadding(bottomPadding)
            }
        }
    }

    private inner class TabFragmentPageAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        /**
         * 确定视图位置是否更改时调用
         * @return POSITION_NONE 已更改,刷新视图. POSITION_UNCHANGED 未更改,不刷新视图
         */
        override fun getItemPosition(any: Any): Int {
            val fragment = any as BooksFragment
            val position = fragment.position
            val group = bookGroups.getOrNull(position)
            if (fragment.groupId != group?.groupId) {
                return POSITION_NONE
            }
            val bookSort = group.getRealBookSort()
            fragment.setEnableRefresh(group.enableRefresh)
            if (fragment.bookSort != bookSort) {
                fragment.upBookSort(bookSort)
            }
            return POSITION_UNCHANGED
        }

        override fun getItem(position: Int): Fragment {
            val group = bookGroups[position]
            onlyUpdateRead = group.onlyUpdateRead
            return BooksFragment(position, group)
        }

        override fun getCount(): Int = bookGroups.size

        // TabLayout 模式：返回分组名称作为 Tab 标题
        override fun getPageTitle(position: Int): CharSequence = bookGroups[position].groupName

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as BooksFragment
            val group = bookGroups[position]
            /**
             * Activity recreate 会复用之前的 Fragment，不正确的需要重新创建
             */
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as BooksFragment
            }
            fragmentMap[group.groupId] = fragment
            return fragment
        }
    }
}
