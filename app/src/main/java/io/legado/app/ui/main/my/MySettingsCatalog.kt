package io.legado.app.ui.main.my

import io.legado.app.R

/**
 * 「我的」页的静态结构定义。
 *
 * 行顺序、分组、图标与文案资源集中在此。`key` 是稳定的动作标识，沿用旧版偏好页的偏好键，
 * 保证"哪一行点什么"不随文案改动而漂移。分组的可见性随 `debugMode` 变化，
 * 因此入口是 [visibleSections] 而不是一个常量列表。
 */
internal object MySettingsCatalog {

    private val sections: List<MySettingsSection> = listOf(
        MySettingsSection(
            titleRes = null,
            rows = listOf(
                MySettingsRow(
                    key = "bookSourceManage",
                    iconRes = R.drawable.ic_cfg_source,
                    titleRes = R.string.book_source_manage,
                    summaryRes = R.string.book_source_manage_desc
                ),
                MySettingsRow(
                    key = "txtTocRuleManage",
                    iconRes = R.drawable.ic_cfg_source,
                    titleRes = R.string.txt_toc_rule,
                    summaryRes = R.string.config_txt_toc_rule
                ),
                MySettingsRow(
                    key = "replaceManage",
                    iconRes = R.drawable.ic_cfg_replace,
                    titleRes = R.string.replace_purify,
                    summaryRes = R.string.replace_purify_desc
                ),
                MySettingsRow(
                    key = "dictRuleManage",
                    iconRes = R.drawable.ic_translate,
                    titleRes = R.string.dict_rule,
                    summaryRes = R.string.config_dict_rule
                ),
                MySettingsRow(
                    key = "debugTools",
                    iconRes = R.drawable.ic_code,
                    titleRes = R.string.debug_tools,
                    summaryRes = R.string.debug_tools_desc,
                    debugOnly = true
                ),
                MySettingsRow(
                    key = "themeMode",
                    iconRes = R.drawable.ic_cfg_theme,
                    titleRes = R.string.theme_mode,
                    summaryRes = R.string.theme_mode_desc,
                    kind = MySettingsRowKind.ThemeMode
                ),
                MySettingsRow(
                    key = "webService",
                    iconRes = R.drawable.ic_cfg_web,
                    titleRes = R.string.web_service,
                    summaryRes = R.string.web_service_desc,
                    kind = MySettingsRowKind.WebService
                )
            )
        ),
        MySettingsSection(
            titleRes = R.string.setting,
            rows = listOf(
                MySettingsRow(
                    key = "web_dav_setting",
                    iconRes = R.drawable.ic_cfg_backup,
                    titleRes = R.string.backup_restore,
                    summaryRes = R.string.web_dav_set_import_old
                ),
                MySettingsRow(
                    key = "theme_setting",
                    iconRes = R.drawable.ic_cfg_theme,
                    titleRes = R.string.theme_setting,
                    summaryRes = R.string.theme_setting_s
                ),
                MySettingsRow(
                    key = "setting",
                    iconRes = R.drawable.ic_cfg_other,
                    titleRes = R.string.other_setting,
                    summaryRes = R.string.other_setting_s
                )
            )
        ),
        MySettingsSection(
            titleRes = R.string.other,
            rows = listOf(
                MySettingsRow(
                    key = "bookmark",
                    iconRes = R.drawable.ic_bookmark,
                    titleRes = R.string.bookmark,
                    summaryRes = R.string.all_bookmark
                ),
                MySettingsRow(
                    key = "readRecord",
                    iconRes = R.drawable.ic_history,
                    titleRes = R.string.read_record,
                    summaryRes = R.string.read_record_summary
                ),
                MySettingsRow(
                    key = "preciseManage",
                    iconRes = R.drawable.ic_storage_black_24dp,
                    titleRes = R.string.precise_manage,
                    summaryRes = R.string.precise_manage_summary
                )
            )
        ),
        MySettingsSection(
            titleRes = null,
            rows = listOf(
                MySettingsRow(
                    key = "about",
                    iconRes = R.drawable.ic_cfg_about,
                    titleRes = R.string.about
                ),
                MySettingsRow(
                    key = "exit",
                    iconRes = R.drawable.ic_exit,
                    titleRes = R.string.exit
                )
            )
        )
    )

    /**
     * 当前应展示的分组。
     *
     * 组内只按可见性过滤，整组不可见时连标题一起去掉——否则会出现只有标题没有内容的空面板。
     */
    fun visibleSections(showDebugTools: Boolean): List<MySettingsSection> =
        sections.mapNotNull { section ->
            val rows = section.rows.filter { !it.debugOnly || showDebugTools }
            rows.takeIf { it.isNotEmpty() }?.let { MySettingsSection(section.titleRes, it) }
        }
}
