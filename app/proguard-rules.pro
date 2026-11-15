# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 混合时不使用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames

# 这句话能够使我们的项目混淆后产生映射文件
# 包含有类名->混淆后类名的映射关系
-verbose

# 保留Annotation不混淆
-keepattributes *Annotation*,InnerClasses

# 避免混淆泛型
-keepattributes Signature

# 指定混淆是采用的算法，后面的参数是一个过滤器
# 这个过滤器是谷歌推荐的算法，一般不做更改
-optimizations !code/simplification/cast,!field/*,!class/merging/*

-flattenpackagehierarchy

#############################################
#
# Android开发中一些需要保留的公共部分
#
#############################################
# 屏蔽错误Unresolved class name
#noinspection ShrinkerUnresolvedReference

# 移除Log类打印各个等级日志的代码，打正式包的时候可以做为禁log使用，这里可以作为禁止log打印的功能使用
# 记得proguard-android.txt中一定不要加-dontoptimize才起作用
# 另外的一种实现方案是通过BuildConfig.DEBUG的变量来控制
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 保持js引擎调用的java类
-keep class * extends io.legado.app.help.JsExtensions{*;}
# JS 书源沙箱：Rhino 按方法名反射调用 JsSourceSandbox.evalString/evalStringWithData，
# 该类不在 JsExtensions 继承链内、上方的 keep 覆盖不到，release 混淆后
# 脚本调用 java.getQuickJsSandbox().evalString(...) 会报「TypeError: 找不到函数 evalString」
-keep class io.legado.app.model.jsSource.JsSourceSandbox{*;}
# 定时任务 cookie 门面：绑定成脚本作用域的 `cookie`，方法（getCookie/setCookie/getKey 等）
# 只被 Rhino 按方法名反射调用、无 Kotlin 调用点，不保名会被 R8 改名/裁剪，
# release 包任务脚本调 cookie.* 会报「找不到函数」。注意 `**.help.http.CookieStore`
# 是精确类名匹配，覆盖不到 TaskCookieStore。
-keep class io.legado.app.help.http.TaskCookieStore{*;}
# 数据类
-keep class **.data.entities.**{*;}
# 高亮规则数据类：通过 GSON 持久化到 SharedPreferences 和 highlightRule.json 备份文件，
# 字段名被混淆会导致升级后规则失效/丢失、备份恢复失败、导出键名变成 a/b 等乱码键
-keep class io.legado.app.ui.book.read.config.highlight.HighlightRule{*;}
-keep class io.legado.app.ui.book.read.config.highlight.HighlightRuleStore$BackupData{*;}
# 屏蔽规则数据类：通过 GSON 持久化到 SharedPreferences（exploreBlockRuleItems），
# 并随 config.xml 参与备份恢复，字段名被混淆会导致升级后规则失效、剪贴板导出键名乱码
-keep class io.legado.app.model.blockrule.BlockRule{*;}
# 书籍缓存索引数据类：GSON 写入 bookCacheIndex.json（常规备份与「书籍缓存选择」单独导出 ZIP 共用），
# 不在 data.entities 包内，字段名被混淆会导致正式包恢复缓存时索引解析失败、.nb 文件无法复制
-keep class io.legado.app.help.storage.BookCacheIndex{*;}
-keep class io.legado.app.help.storage.ChapterCacheInfo{*;}
# 缓存清单数据类：GSON 反射读写 book_cache/<书>/cache_manifest.json，
# 字段名被混淆后 GSON 按名字匹配不到字段（chapters 退化成 LinkedTreeMap），
# 缓存管理页会因 ClassCastException 崩溃、已缓存计数恒为 0
-keep class io.legado.app.help.book.CacheBookManifest{*;}
-keep class io.legado.app.help.book.CacheChapterManifest{*;}

# Room 数据库（防止 R8 在 release 构建中剥离生成的 _Impl 类）
-keep class io.legado.app.data.AppDatabase {*;}
-keep class io.legado.app.data.AppDatabase_Impl {*;}
-keep,allowobfuscation @androidx.room.Dao interface * {*;}
-keep,allowobfuscation @androidx.room.DatabaseView class * {*;}
-keep class * extends androidx.room.migration.Migration {*;}
-keep class io.legado.app.data.DatabaseMigrations {*;}
# 保留 Room 生成的 DAO 实现（BookDao_Impl 等）
-keep class io.legado.app.data.dao.**_Impl {*;}
# 保留 DatabaseView 的 ViewInfo 内部类（BookSourcePart）
-keep class io.legado.app.data.entities.BookSourcePart {*;}
# 确保 Room 能通过反射找到生成的实现类
-keepnames class io.legado.app.data.AppDatabase_Impl
-keepnames class io.legado.app.data.dao.**_Impl

# 状态栏 / 导航栏间距处理（防止 R8 剥离 WindowInsets 监听逻辑，
# 导致 release 版本顶栏紧贴系统状态栏）
-keep class io.legado.app.utils.ViewExtensionsKt {*;}
-keep class io.legado.app.utils.WindowInsetsExtensionsKt {*;}
-keep class io.legado.app.utils.ContextExtensionsKt {
    *** getStatusBarHeight(...);
    *** getNavigationBarHeight(...);
}
-keep class io.legado.app.utils.ActivityExtensionsKt {
    *** fullScreen(...);
    *** setStatusBarColorAuto(...);
}
# TitleBar 整体保留（init 中 WindowInsets 回调的 lambda 编译为合成方法）
-keep class io.legado.app.ui.widget.TitleBar {*;}

# 确保 AndroidX WindowInsets 相关类不被 R8 优化移除
-keep class androidx.core.view.WindowInsetsCompat {*;}
-keep class androidx.core.view.WindowInsetsCompat$* {*;}
-keep class androidx.core.graphics.Insets {*;}
-keep interface androidx.core.view.OnApplyWindowInsetsListener {*;}
-keep class androidx.core.view.ViewCompat {
    *** setOnApplyWindowInsetsListener(...);
    *** requestApplyInsets(...);
}
-dontwarn androidx.core.view.WindowInsetsCompat$Impl*
# hutool-core hutool-crypto
-keep class
!cn.hutool.core.util.RuntimeUtil,
!cn.hutool.core.util.ClassLoaderUtil,
!cn.hutool.core.util.ReflectUtil,
!cn.hutool.core.util.SerializeUtil,
!cn.hutool.core.util.ClassUtil,
cn.hutool.core.codec.**,
cn.hutool.core.util.**{*;}
-keep class cn.hutool.crypto.**{*;}
-dontwarn cn.hutool.**
# 缓存 Cookie
-keep class **.help.http.CookieStore{*;}
-keep class **.help.CacheManager{*;}
# StrResponse
-keep class **.help.http.StrResponse{*;}

# markwon
-dontwarn org.commonmark.ext.gfm.**

-keep class okhttp3.*{*;}
-keep class okio.*{*;}
-keep class com.jayway.jsonpath.*{*;}

# LiveEventBus
-keepclassmembers class androidx.lifecycle.LiveData {
    *** mObservers;
    *** mActiveCount;
}
-keepclassmembers class androidx.arch.core.internal.SafeIterableMap {
    *** size();
    *** putIfAbsent(...);
}

## ChangeBookSourceDialog initNavigationView
-keepclassmembers class androidx.appcompat.widget.Toolbar {
    *** mNavButtonView;
}

# MenuExtensions applyOpenTint
-keepnames class androidx.appcompat.view.menu.SubMenuBuilder
-keep class androidx.appcompat.view.menu.MenuBuilder {
    *** setOptionalIconsVisible(...);
    *** getNonActionItems();
}

# FileDocExtensions.kt treeDocumentFileConstructor
-keep class androidx.documentfile.provider.TreeDocumentFile {
    <init>(...);
}

# JsoupXpath
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.AxisSelector{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.NodeTest{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.Function{*;}

## JSOUP
-keep class org.jsoup.**{*;}
-dontwarn org.jspecify.annotations.NullMarked

## ExoPlayer 反射设置ua 保证该私有变量不被混淆
-keepclassmembers class androidx.media3.datasource.cache.CacheDataSource$Factory {
    *** upstreamDataSourceFactory;
}
## ExoPlayer 如果还不能播放就取消注释这个
# -keep class com.google.android.exoplayer2.** {*;}

## 对外提供api
-keep class io.legado.app.api.ReturnData{*;}

# Cronet
-keepclassmembers class org.chromium.net.X509Util {
    *** sDefaultTrustManager;
    *** sTestTrustManager;
}

# Throwable
-keepnames class * extends java.lang.Throwable
-keepclassmembernames,allowobfuscation class * extends java.lang.Throwable{*;}

# Sora Editor
-keep class org.eclipse.tm4e.** { *; }
-keep class org.joni.** { *; }

# GSYVideoPlayer
-keep class com.shuyu.gsyvideoplayer.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.**
#-keep class com.shuyu.gsyvideoplayer.video.** { *; }
#-dontwarn com.shuyu.gsyvideoplayer.video.**
#-keep class com.shuyu.gsyvideoplayer.video.base.** { *; }
#-dontwarn com.shuyu.gsyvideoplayer.video.base.**
#-keep class com.shuyu.gsyvideoplayer.utils.** { *; }
#-dontwarn com.shuyu.gsyvideoplayer.utils.**
#-keep class com.shuyu.gsyvideoplayer.player.** {*;}
#-dontwarn com.shuyu.gsyvideoplayer.player.**
#-keep class tv.danmaku.ijk.** { *; }
#-dontwarn tv.danmaku.ijk.**
#-keep class androidx.media3.** {*;}
#-keep interface androidx.media3.**
#-keep class com.shuyu.alipay.** {*;}
#-keep interface com.shuyu.alipay.**
-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, java.lang.Boolean);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
