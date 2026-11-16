package io.legado.app.ui.book.manga.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Point
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.base.BasePrefDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.MangaReadMode
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.prefs.NameListPreference
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setEdgeEffectColor

class MangaQuickSettingDialog : BasePrefDialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (size.y * 0.55).toInt())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(context).apply {
            setBackgroundColor(requireContext().bottomBackground)
            id = R.id.tag1
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tag = "mangaQuickPreferenceFragment"
        val existing = childFragmentManager.findFragmentByTag(tag)
        if (existing != null && existing.isAdded) return
        val fragment = existing ?: MangaPreferenceFragment()
        childFragmentManager.beginTransaction()
            .replace(view.id, fragment, tag)
            .commit()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? Callback)?.onMangaSettingDismiss()
    }

    class MangaPreferenceFragment : PreferenceFragment(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val callback get() = activity as? Callback
        private val footerConfig: MangaFooterConfig
            get() = GSON.fromJsonObject<MangaFooterConfig>(AppConfig.mangaFooterConfig).getOrNull()
                ?: MangaFooterConfig()

        @SuppressLint("RestrictedApi")
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            if (getPrefString(PreferKey.mangaReadMode).isNullOrBlank()) {
                AppConfig.mangaReadMode =
                    if (AppConfig.enableMangaHorizontalScroll) MODE_NORMAL else MODE_SCROLL
            }
            addPreferencesFromResource(R.xml.pref_manga_quick_setting)
            findPreference<SwitchPreference>(KEY_HIDE_FOOTER)?.isChecked = footerConfig.hideFooter
            updatePreloadSummary()
            updateReadModeSummary()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            listView.setEdgeEffectColor(primaryColor)
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            preferenceManager.sharedPreferences
                ?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                PreferKey.mangaReadMode -> {
                    applyReadMode(AppConfig.mangaReadMode)
                    updateReadModeSummary()
                    callback?.onMangaQuickSettingChanged()
                }

                PreferKey.disableMangaPageAnim,
                PreferKey.disableMangaScale,
                PreferKey.disableClickScroll -> callback?.onMangaQuickSettingChanged()

                PreferKey.hideMangaTitle -> callback?.onMangaQuickSettingChanged(reloadContent = true)
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                PreferKey.mangaPreDownloadNum -> {
                    NumberPickerDialog(requireContext())
                        .setTitle(getString(R.string.pre_download))
                        .setMaxValue(9999)
                        .setMinValue(0)
                        .setValue(AppConfig.mangaPreDownloadNum)
                        .show {
                            AppConfig.mangaPreDownloadNum = it
                            updatePreloadSummary()
                            callback?.onMangaPreloadChanged()
                        }
                    return true
                }

                KEY_HIDE_FOOTER -> {
                    val switchPreference = preference as? SwitchPreference ?: return true
                    val config = footerConfig.apply {
                        hideFooter = switchPreference.isChecked
                    }
                    AppConfig.mangaFooterConfig = GSON.toJson(config)
                    postEvent(EventBus.UP_MANGA_CONFIG, config)
                    return true
                }
            }
            return super.onPreferenceTreeClick(preference)
        }

        private fun applyReadMode(mode: String) {
            AppConfig.mangaReadMode = mode
            if (MangaReadMode.isHorizontal(mode)) {
                AppConfig.disableHorizontalPageSnap = false
            }
        }

        private fun updatePreloadSummary() {
            findPreference<Preference>(PreferKey.mangaPreDownloadNum)?.summary =
                getString(R.string.pre_download_m, AppConfig.mangaPreDownloadNum)
        }

        private fun updateReadModeSummary() {
            findPreference<NameListPreference>(PreferKey.mangaReadMode)?.value = AppConfig.mangaReadMode
        }
    }

    interface Callback {
        fun onMangaQuickSettingChanged(reloadContent: Boolean = false)
        fun onMangaPreloadChanged()
        fun onMangaSettingDismiss() = Unit
    }

    companion object {
        private const val KEY_HIDE_FOOTER = "mangaHideFooterInfo"
        private const val MODE_SCROLL = MangaReadMode.SCROLL
        private const val MODE_NORMAL = MangaReadMode.NORMAL
    }
}
