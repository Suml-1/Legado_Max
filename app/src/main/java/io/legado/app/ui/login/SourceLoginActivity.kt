package io.legado.app.ui.login

import android.os.Bundle
import androidx.activity.viewModels
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.BaseSource
import io.legado.app.databinding.ActivitySourceLoginBinding
import io.legado.app.model.login.canRenderLoginUiV2
import io.legado.app.model.login.isLoginUiV2
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding

class SourceLoginActivity : VMBaseActivity<ActivitySourceLoginBinding, SourceLoginViewModel>() {

    override val binding by viewBinding(ActivitySourceLoginBinding::inflate)
    override val viewModel by viewModels<SourceLoginViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.initData(intent, success = { source ->
            initView(source)
        }, error = {
            finish()
        })
    }

    private fun initView(source: BaseSource) {
        // 登录界面 v2（loginUi/loginAction 函数）没有 loginUi 数据，也必须走表单对话框，
        // 否则会退化成「把 loginUrl 当网页打开」——JS 源通常没有 loginUrl，直接 404。
        // 但 v2 标记存在而脚本里没有 loginUi/loginAction 函数时（例如只写了标记的定时任务），
        // 表单对话框必然渲染失败，回退 loginUrl 的 WebView 登录。
        val v2 = source.isLoginUiV2()
        if ((source.loginUi.isNullOrEmpty() && !v2) || (v2 && !source.canRenderLoginUiV2())) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_fragment, WebViewLoginFragment(), "webViewLogin")
                .commit()
        } else {
            showDialogFragment<SourceLoginDialog>()
        }
    }
}
