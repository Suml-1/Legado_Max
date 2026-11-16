package io.legado.app.ui.design.theme

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatViewInflater
import io.legado.app.help.config.NgThemeRuntimeAssets
import java.lang.reflect.Constructor
import java.util.concurrent.ConcurrentHashMap

/** Keep AppCompat's factory, widget classes and theme wrapping; apply fonts at creation. */
@Keep
class NgAppViewInflater : AppCompatViewInflater() {
    private val textConstructors = ConcurrentHashMap<String, Constructor<out TextView>>()

    private fun <T : TextView> T.withInterfaceFont(): T = apply {
        NgThemeRuntimeAssets.applyAppTypeface(context, this)
    }

    override fun createTextView(context: Context, attrs: AttributeSet) =
        super.createTextView(context, attrs).withInterfaceFont()

    override fun createButton(context: Context, attrs: AttributeSet) =
        super.createButton(context, attrs).withInterfaceFont()

    override fun createEditText(context: Context, attrs: AttributeSet) =
        super.createEditText(context, attrs).withInterfaceFont()

    override fun createCheckBox(context: Context, attrs: AttributeSet) =
        super.createCheckBox(context, attrs).withInterfaceFont()

    override fun createRadioButton(context: Context, attrs: AttributeSet) =
        super.createRadioButton(context, attrs).withInterfaceFont()

    override fun createCheckedTextView(context: Context, attrs: AttributeSet) =
        super.createCheckedTextView(context, attrs).withInterfaceFont()

    override fun createAutoCompleteTextView(context: Context, attrs: AttributeSet) =
        super.createAutoCompleteTextView(context, attrs).withInterfaceFont()

    override fun createMultiAutoCompleteTextView(context: Context, attrs: AttributeSet) =
        super.createMultiAutoCompleteTextView(context, attrs).withInterfaceFont()

    override fun createToggleButton(context: Context, attrs: AttributeSet) =
        super.createToggleButton(context, attrs).withInterfaceFont()

    override fun createView(context: Context, name: String, attrs: AttributeSet): View? {
        // Reader-owned canvas/typefaces and font previews are not traversed after inflation.
        val className = if (name == "view") attrs.getAttributeValue(null, "class") else name
        if (className == null || '.' !in className ||
            className == "io.legado.app.ui.widget.BatteryView") {
            return super.createView(context, name, attrs)
        }
        val constructor = textConstructors[className] ?: run {
            val viewClass = runCatching { context.classLoader.loadClass(className) }.getOrNull()
                ?: return super.createView(context, name, attrs)
            if (!TextView::class.java.isAssignableFrom(viewClass)) {
                return super.createView(context, name, attrs)
            }
            val textConstructor = try {
                viewClass.asSubclass(TextView::class.java)
                    .getConstructor(Context::class.java, AttributeSet::class.java)
            } catch (_: NoSuchMethodException) {
                // NumberPicker's internal text field is created by the framework inflater.
                return super.createView(context, name, attrs)
            }
            textConstructor.also { textConstructors[className] = it }
        }
        return constructor.newInstance(context, attrs).withInterfaceFont()
    }
}
