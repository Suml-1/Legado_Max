package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.NgEffectThemeSelection
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.postEvent

class ThemeColorConfigFragment : BaseFragment(R.layout.fragment_theme_color_config),
    ConfigBackHandler {

    private var hasChanges = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.ng_custom_colors)
        hasChanges = savedInstanceState?.getBoolean("themeColorsChanged") ?: hasChanges
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = requireContext()
                if (
                    NgThemeModeStore.current(context) ==
                    NgThemePresentationMode.EFFECT_THEME
                ) {
                    var colors by remember { mutableStateOf(NgEffectThemeSelection.colors(context)) }
                    NgAppTheme(
                        snapshot = NgThemeResolver.resolve(
                            context = context,
                            colors = colors,
                            isDark = ThemeConfig.isDarkTheme(context),
                        ),
                    ) {
                        ThemeColorConfigScreen(
                            colors = colors,
                            onColorsChanged = { updated ->
                                if (updated != colors) {
                                    hasChanges = true
                                    colors = updated
                                    NgEffectThemeSelection.updateColors(context, updated)
                                }
                            },
                        )
                    }
                } else {
                    val flow = NgColorConfigStore.observe(context)
                    val observed by flow.collectAsState()
                    val colors = observed ?: NgColorConfigStore.current(context)
                    NgAppTheme {
                        ThemeColorConfigScreen(
                            colors = colors,
                            onColorsChanged = ::updateColors
                        )
                    }
                }
            }
        }
    }

    override fun onConfigBackPressed(): Boolean {
        if (!hasChanges) return false
        hasChanges = false
        val context = requireContext()
        val wasFoundation = NgThemeModeStore.isCustomFoundation(context)
        parentFragmentManager.popBackStack()
        requireActivity().window.decorView.post {
            if (wasFoundation) NgThemeModeStore.activateManualAppearance(context)
            else postEvent(EventBus.RECREATE, "")
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("themeColorsChanged", hasChanges)
        super.onSaveInstanceState(outState)
    }

    private fun updateColors(colors: NgColorSystem) {
        if (colors == NgColorConfigStore.current(requireContext())) return
        hasChanges = true
        NgColorConfigStore.update(requireContext(), colors)
    }
}
