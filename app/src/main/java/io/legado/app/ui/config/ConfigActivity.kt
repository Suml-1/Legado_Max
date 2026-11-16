package io.legado.app.ui.config

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityConfigBinding
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

interface ConfigBackHandler {
    fun onConfigBackPressed(): Boolean
}

class ConfigActivity : VMBaseActivity<ActivityConfigBinding, ConfigViewModel>() {

    override val binding by viewBinding(ActivityConfigBinding::inflate)
    override val viewModel by viewModels<ConfigViewModel>()

    /** 后台收到 RECREATE 时置位，回到前台 onResume 再重建，避免与前台页面并发重建窗口 */
    private var recreateOnResume = false

    override fun onResume() {
        super.onResume()
        if (recreateOnResume) {
            recreateOnResume = false
            recreate()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        when (val configTag = intent.getStringExtra("configTag")) {
            ConfigTag.PRECISE_MANAGE -> replaceFragment<PreciseManageFragment>(configTag)
            ConfigTag.OTHER_CONFIG -> replaceFragment<OtherConfigFragment>(configTag)
            ConfigTag.THEME_CONFIG -> replaceFragment<ThemeConfigFragment>(configTag)
            ConfigTag.BACKUP_CONFIG -> replaceFragment<BackupConfigFragment>(configTag)
            ConfigTag.COVER_CONFIG -> replaceFragment<CoverConfigFragment>(configTag)
            ConfigTag.WELCOME_CONFIG -> replaceFragment<WelcomeConfigFragment>(configTag)
            else -> finish()
        }
        onBackPressedDispatcher.addCallback(this) {
            handleConfigBackPressed()
        }
    }

    override fun setTitle(resId: Int) {
        super.setTitle(resId)
        binding.titleBar.setTitle(resId)
    }

    private fun handleConfigBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.configFrameLayout)
        if ((fragment as? ConfigBackHandler)?.onConfigBackPressed() == true) {
            return
        }
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return
        }
        finish()
    }

    fun openThemeColorConfigPage() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.configFrameLayout,
                ThemeColorConfigFragment(),
                ConfigTag.THEME_COLOR_CONFIG
            )
            .addToBackStack(ConfigTag.THEME_COLOR_CONFIG)
            .commit()
    }

    fun openThemeManagerPage() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.configFrameLayout,
                ThemeManagerFragment(),
                ConfigTag.THEME_MANAGER
            )
            .addToBackStack(ConfigTag.THEME_MANAGER)
            .commit()
    }

    inline fun <reified T : Fragment> replaceFragment(configTag: String) {
        intent.putExtra("configTag", configTag)
        @Suppress("DEPRECATION")
        val existing = supportFragmentManager.findFragmentByTag(configTag)
        // Activity 经 RECREATE 重建后，Fragment 已由系统恢复并处于 added 状态，
        // 再把它传入 replace 会触发 "Fragment already added"，这里直接跳过事务。
        if (existing != null && existing.isAdded) {
            return
        }
        val configFragment = existing ?: T::class.java.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.configFrameLayout, configFragment, configTag)
            .commit()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.RECREATE) {
            if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                recreate()
            } else {
                recreateOnResume = true
            }
        }
    }
}
