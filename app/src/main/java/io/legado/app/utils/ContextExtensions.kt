@file:Suppress("unused", "UnusedReceiverParameter")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getActivity
import android.app.PendingIntent.getBroadcast
import android.app.PendingIntent.getService
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.Book
import io.legado.app.help.IntentHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.shared.SharedReceiverActivity
import splitties.systemservices.clipboardManager
import splitties.systemservices.connectivityManager
import splitties.systemservices.uiModeManager
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

inline fun <reified A : Activity> Context.startActivity(configIntent: Intent.() -> Unit = {}) {
    val intent = Intent(this, A::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.apply(configIntent)
    startActivity(intent)
}

fun Context.startActivityForBook(
    book: Book,
    configIntent: Intent.() -> Unit = {},
) {
    val cls = when {
        book.isVideo -> VideoPlayerActivity::class.java
        book.isAudio -> AudioPlayActivity::class.java
        !book.isLocal && book.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
        else -> ReadBookActivity::class.java
    }
    val intent = Intent(this, cls)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.putExtra("bookUrl", book.bookUrl)
    intent.apply(configIntent)
    startActivity(intent)
}

inline fun <reified T : Service> Context.startService(configIntent: Intent.() -> Unit = {}) {
    startService(Intent(this, T::class.java).apply(configIntent))
}

inline fun <reified T : Service> Context.stopService() {
    stopService(Intent(this, T::class.java))
}

@SuppressLint("UnspecifiedImmutableFlag")
inline fun <reified T : Service> Context.servicePendingIntent(
    action: String,
    requestCode: Int = 0,
    configIntent: Intent.() -> Unit = {},
): PendingIntent? {
    val intent = Intent(this, T::class.java)
    intent.action = action
    configIntent.invoke(intent)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getService(this, requestCode, intent, flags)
}

@SuppressLint("UnspecifiedImmutableFlag")
fun Context.activityPendingIntent(
    intent: Intent,
    action: String,
): PendingIntent? {
    intent.action = action
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getActivity(this, 0, intent, flags)
}

@SuppressLint("UnspecifiedImmutableFlag")
inline fun <reified T : Activity> Context.activityPendingIntent(
    action: String,
    configIntent: Intent.() -> Unit = {},
): PendingIntent? {
    val intent = Intent(this, T::class.java)
    intent.action = action
    configIntent.invoke(intent)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getActivity(this, 0, intent, flags)
}

@SuppressLint("UnspecifiedImmutableFlag")
inline fun <reified T : BroadcastReceiver> Context.broadcastPendingIntent(
    action: String,
    configIntent: Intent.() -> Unit = {},
): PendingIntent? {
    val intent = Intent(this, T::class.java)
    intent.action = action
    configIntent.invoke(intent)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
    } else {
        FLAG_UPDATE_CURRENT
    }
    return getBroadcast(this, 0, intent, flags)
}

fun Context.startForegroundServiceCompat(intent: Intent) {
    try {
        startService(intent)
    } catch (e: IllegalStateException) {
        ContextCompat.startForegroundService(this, intent)
    }
}

val Context.defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.getPrefBoolean(key: String, defValue: Boolean = false): Boolean = try {
    defaultSharedPreferences.getBoolean(key, defValue)
} catch (e: ClassCastException) {
    // 值可能以其他类型（如 String）存储，尝试兼容读取
    val strValue = defaultSharedPreferences.getString(key, null)
    strValue?.toBooleanStrictOrNull() ?: defValue
}

fun Context.putPrefBoolean(key: String, value: Boolean = false) = defaultSharedPreferences.edit { putBoolean(key, value) }

fun Context.getPrefInt(key: String, defValue: Int = 0): Int = try {
    defaultSharedPreferences.getInt(key, defValue)
} catch (e: ClassCastException) {
    // 值可能以 String 类型存储（如从其他 Legado 变体恢复备份），尝试兼容读取
    val strValue = defaultSharedPreferences.getString(key, null)
    strValue?.toIntOrNull() ?: defValue
}

fun Context.putPrefInt(key: String, value: Int) = defaultSharedPreferences.edit { putInt(key, value) }

fun Context.getPrefLong(key: String, defValue: Long = 0L): Long = try {
    defaultSharedPreferences.getLong(key, defValue)
} catch (e: ClassCastException) {
    // 值可能以 String 类型存储，尝试兼容读取
    val strValue = defaultSharedPreferences.getString(key, null)
    strValue?.toLongOrNull() ?: defValue
}

fun Context.putPrefLong(key: String, value: Long) = defaultSharedPreferences.edit { putLong(key, value) }

fun Context.getPrefString(key: String, defValue: String? = null) = defaultSharedPreferences.getString(key, defValue)

fun Context.putPrefString(key: String, value: String?) = defaultSharedPreferences.edit { putString(key, value) }

fun Context.getPrefStringSet(
    key: String,
    defValue: MutableSet<String>? = null,
): MutableSet<String>? = defaultSharedPreferences.getStringSet(key, defValue)

fun Context.putPrefStringSet(key: String, value: MutableSet<String>) = defaultSharedPreferences.edit { putStringSet(key, value) }

fun Context.removePref(key: String) = defaultSharedPreferences.edit { remove(key) }

fun Context.getCompatColor(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Context.getCompatDrawable(@DrawableRes id: Int): Drawable? = ContextCompat.getDrawable(this, id)

fun Context.getCompatColorStateList(@ColorRes id: Int): ColorStateList? = ContextCompat.getColorStateList(this, id)

fun Context.checkSelfUriPermission(uri: Uri, modeFlags: Int): Int = checkUriPermission(uri, Process.myPid(), Process.myUid(), modeFlags)

fun Context.restart() {
    val intent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_CLEAR_TASK
                or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        startActivity(intent)
        // 杀掉以前进程
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}

/**
 * 系统息屏时间
 */
val Context.sysScreenOffTime: Int
    get() {
        return kotlin.runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.onFailure {
            it.printOnDebug()
        }.getOrDefault(0)
    }

val Context.statusBarHeight: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        if (Build.BOARD == "windows") {
            return 0
        }
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

val Context.navigationBarHeight: Int
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    get() {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

/**
 * 建一个「不包含自己」的分享选择器。
 *
 * 本 App 的 [SharedReceiverActivity] 注册了 `ACTION_SEND` + `text/plain` 接收器，
 * 分享纯文本（如主题/顶栏/底栏的配置 JSON）时会被自己接住，落地成「搜索」页——
 * 用户看到的就是"点分享怎么跳到搜索去了"。排除自身后才是真正的对外分享。
 */
private fun Context.hasExternalShareTarget(intent: Intent): Boolean {
    val self = ComponentName(this, SharedReceiverActivity::class.java)
    return packageManager.queryIntentActivities(intent, 0).any { info ->
        ComponentName(info.activityInfo.packageName, info.activityInfo.name) != self
    }
}

private fun Context.createShareChooser(intent: Intent, title: String): Intent {
    val chooser = Intent.createChooser(intent, title)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        chooser.putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(this, SharedReceiverActivity::class.java)),
        )
    }
    return chooser
}

fun Context.share(text: String, title: String = getString(R.string.share)) {
    kotlin.runCatching {
        val intent = Intent(Intent.ACTION_SEND)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(Intent.EXTRA_SUBJECT, title)
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.type = "text/plain"
        if (!hasExternalShareTarget(intent)) {
            // 设备上没有任何第三方分享目标（纯净 ROM / 模拟器）：选择器只剩一句
            // 「没有应用可执行此操作」，用户拿不到内容——退回剪贴板，
            // 正好对接配置页的「从剪贴板导入」
            sendToClip(text)
            return
        }
        startActivity(createShareChooser(intent, title))
    }
}

fun Context.share(file: File, type: String = "text/*") {
    val fileUri = FileProvider.getUriForFile(this, AppConst.authority, file)
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = type
    intent.putExtra(Intent.EXTRA_STREAM, fileUri)
    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(createShareChooser(intent, getString(R.string.share_selected_source)))
}

@SuppressLint("SetWorldReadable")
fun Context.shareWithQr(
    text: String,
    title: String = getString(R.string.share),
    errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.H,
) {
    val bitmap = QRCodeUtils.createQRCode(text, errorCorrectionLevel = errorCorrectionLevel)
    if (bitmap == null) {
        toastOnUi(R.string.text_too_long_qr_error)
    } else {
        try {
            val file = File(externalCacheDir, "qr.png")
            val fOut = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
            fOut.flush()
            fOut.close()
            file.setReadable(true, false)
            val contentUri = FileProvider.getUriForFile(this, AppConst.authority, file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(Intent.EXTRA_STREAM, contentUri)
            intent.type = "image/png"
            startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            toastOnUi(e.localizedMessage ?: "ERROR")
        }
    }
}

fun Context.sendToClip(text: String) {
    val clipData = ClipData.newPlainText(null, text)
    clipboardManager.setPrimaryClip(clipData)
    longToastOnUi(R.string.copy_complete)
}

fun Context.getClipText(): String? {
    clipboardManager.primaryClip?.let {
        if (it.itemCount > 0) {
            return it.getItemAt(0).text?.toString()?.trim()
        }
    }
    return null
}

fun Context.clearClip() {
    val clipData = ClipData.newPlainText(null, null)
    clipboardManager.setPrimaryClip(clipData)
}

fun Context.sendMail(mail: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:$mail")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: Exception) {
        toastOnUi(e.localizedMessage ?: "Error")
    }
}

/**
 * 获取电量
 */
val Context.sysBattery: Int
    get() {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, iFilter)
        return batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

val Context.externalFiles: File
    get() = this.getExternalFilesDir(null) ?: this.filesDir

val Context.externalCache: File
    get() = this.externalCacheDir ?: this.cacheDir

fun Context.openUrl(url: String) {
    try {
        startActivity(IntentHelp.getBrowserIntent(url))
    } catch (e: Exception) {
        toastOnUi(e.localizedMessage ?: "open url error")
        e.printOnDebug()
    }
}

fun Context.openUrl(uri: Uri) {
    try {
        startActivity(IntentHelp.getBrowserIntent(uri))
    } catch (e: Exception) {
        toastOnUi(e.localizedMessage ?: "open url error")
        e.printOnDebug()
    }
}

/**
 * 使用内置浏览器打开链接
 * http/https 协议打开 WebViewActivity，legado/yuedu 协议打开在线导入页，其他协议交给系统处理
 */
fun Context.openInInnerBrowser(url: String) {
    openInInnerBrowser(Uri.parse(url))
}

/**
 * 使用内置浏览器打开链接
 * http/https 协议打开 WebViewActivity，legado/yuedu 协议打开在线导入页，其他协议交给系统处理
 */
fun Context.openInInnerBrowser(uri: Uri) {
    when (uri.scheme) {
        "http", "https" -> {
            startActivity<io.legado.app.ui.browser.WebViewActivity> {
                putExtra("url", uri.toString())
            }
        }
        "legado", "yuedu" -> {
            startActivity<io.legado.app.ui.association.OnLineImportActivity> {
                data = uri
            }
        }
        else -> {
            openUrl(uri)
        }
    }
}

// 打开文件uri 弹出式菜单显示其他软件
// 7.0版本以上需要添加读取权限
@SuppressLint("ObsoleteSdkInt")
fun Context.openFileUri(uri: Uri, type: String? = null) {
    val intent = Intent()
    intent.action = Intent.ACTION_VIEW
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // 7.0版本以上
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val uri = if (uri.isContentScheme()) {
        uri
    } else {
        FileProvider.getUriForFile(this, AppConst.authority, File(uri.path!!))
    }
    intent.setDataAndType(uri, type ?: IntentType.from(uri))
    try {
        startActivity(intent)
    } catch (e: Exception) {
        toastOnUi(e.stackTraceStr)
        e.printOnDebug()
    }
}

@Suppress("DEPRECATION")
val Context.isWifiConnect: Boolean
    @SuppressLint("MissingPermission")
    get() {
        val info = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        return info?.isConnected == true
    }

val Context.isPad: Boolean
    get() {
        return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

val Context.isTv: Boolean
    get() = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

val Context.channel: String
    get() {
        try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            return appInfo.metaData.getString("channel") ?: ""
        } catch (e: Exception) {
            e.printOnDebug()
        }
        return ""
    }

val Context.isDebuggable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
