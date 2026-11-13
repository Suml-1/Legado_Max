/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.script.rhino

import android.os.Build
import org.htmlunit.corejs.javascript.ClassShutter
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.VarScope
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Member
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.Collections

/**
 * This class prevents script access to certain sensitive classes.
 * Note that this class checks over and above SecurityManager. i.e., although
 * a SecurityManager would pass, class shutter may still prevent access.
 *
 * @author A. Sundararajan
 * @since 1.6
 */
object RhinoClassShutter : ClassShutter {

    private const val appClassPrefix = "io.legado.app."

    /**
     * BookSource 脚本必须主动构造且已经过兼容审计的 App 类型。
     *
     * 宿主注入或返回的对象不属于直接类导入，必须通过包装能力暴露，不能因此加入本表。
     * 新增条目必须同时提供现有书源证据和默认拒绝回归测试。
     */
    private val bookSourceDirectClassImports = setOf(
        "io.legado.app.help.http.StrResponse"
    )

    private val bookSourceProtectedClassNames = setOf(
        "android.webkit.CookieManager",
        "android.webkit.CookieSyncManager"
    )

    private val bookSourcePolicyDepth = ThreadLocal<Int>()

    private val bookSourceLabel = ThreadLocal<String>()

    private val hostObjectClassAccess = ThreadLocal<Set<String>>()

    private val protectedClassNamesMatcher by lazy {
        listOf(
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.net.URLClassLoader",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.ProcessImpl",
            "java.lang.UNIXProcess",
            "java.io.File",
            "java.io.FileDescriptor",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.PrintStream",
            "java.io.FileReader",
            "java.io.FileWriter",
            "java.io.PrintWriter",
            "java.io.UnixFileSystem",
            "java.io.RandomAccessFile",
            "java.io.ObjectInputStream",
            "java.io.ObjectOutputStream",
            "java.security.AccessController",
            "java.nio.file.Paths",
            "java.nio.file.Files",
            "java.nio.file.FileSystems",
            "java.util.Formatter",
            "sun.misc.Unsafe",
            "android.content.Intent",
            "android.provider.Settings",
            "android.app.ActivityThread",
            "android.app.AppGlobals",
            "android.os.Looper",
            "android.os.Process",
            "android.os.FileUtils",

            "cn.hutool.core.lang.JarClassLoader",
            "cn.hutool.core.lang.Singleton",
            "cn.hutool.core.util.RuntimeUtil",
            "cn.hutool.core.util.ClassLoaderUtil",
            "cn.hutool.core.util.ReflectUtil",
            "cn.hutool.core.util.SerializeUtil",
            "cn.hutool.core.util.ClassUtil",
            "org.htmlunit.corejs.javascript.DefiningClassLoader",
            "org.htmlunit.corejs.javascript.NestedScriptScope",
            "io.legado.app.data.AppDatabase",
            "io.legado.app.data.AppDatabase_Impl",
            "io.legado.app.data.AppDatabaseKt",
            "io.legado.app.utils.ContextExtensionsKt",
            "androidx.core.content.FileProvider",
            "splitties.init.AppCtxKt",
            "okio.JvmSystemFileSystem",
            "okio.JvmFileHandle",
            "okio.NioSystemFileSystem",
            "okio.NioFileSystemFileHandle",
            "okio.Path",

            "android.system",
            "android.database",
            "androidx.sqlite.db",
            "androidx.room",
            "cn.hutool.core.io",
            "cn.hutool.core.bean",
            "cn.hutool.core.lang.reflect",
            // QuickJS 只能经宿主注入的隔离进程窄门面调用，禁止 Rhino 直接构造运行时。
            "com.dokar.quickjs",
            // System JS execution is available only through the bounded host facade.
            "androidx.javascriptengine",
            "org.chromium.android_webview.js_sandbox",
            "dalvik.system",
            "java.nio.file",
            "java.lang.reflect",
            "java.lang.invoke",
            "io.legado.app.data.dao",
            "com.script",
            "org.htmlunit.corejs",
            "org.mozilla",
            "sun",
            "libcore",
        ).let { ClassNameMatcher(it) }
    }

    private val systemClassProtectedName by lazy {
        Collections.unmodifiableSet(hashSetOf("load", "loadLibrary", "exit"))
    }

    private val protectedClasses by lazy {
        arrayOf(
            ClassLoader::class.java,
            Class::class.java,
            Member::class.java,
            Context::class.java,
            ObjectInputStream::class.java,
            ObjectOutputStream::class.java,
            okio.FileSystem::class.java,
            okio.FileHandle::class.java,
            okio.Path::class.java,
            android.content.Context::class.java,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            arrayOf(FileSystem::class.java, Path::class.java)
        } else {
            emptyArray()
        }
    }

    fun visibleToScripts(obj: Any): Boolean {
        when (obj) {
            is ClassLoader,
            is Class<*>,
            is Member,
            is Context,
            is ObjectInputStream,
            is ObjectOutputStream,
            is okio.FileSystem,
            is okio.FileHandle,
            is okio.Path,
            is android.content.Context -> return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (obj) {
                is FileSystem,
                is Path -> return false
            }
        }
        return !protectedClassNamesMatcher.match(obj.javaClass.name)
    }

    fun visibleToScripts(clazz: Class<*>): Boolean {
        protectedClasses.forEach {
            if (it.isAssignableFrom(clazz)) {
                return false
            }
        }
        return visibleToScripts(clazz.name)
    }

    fun <T> withBookSourceClassPolicy(
        enabled: Boolean,
        sourceLabel: String? = null,
        block: () -> T
    ): T {
        if (!enabled) return block()
        val previousDepth = bookSourcePolicyDepth.get() ?: 0
        val previousLabel = bookSourceLabel.get()
        bookSourcePolicyDepth.set(previousDepth + 1)
        if (!sourceLabel.isNullOrBlank()) {
            bookSourceLabel.set(sourceLabel)
        }
        return try {
            block()
        } finally {
            if (previousDepth == 0) {
                bookSourcePolicyDepth.remove()
            } else {
                bookSourcePolicyDepth.set(previousDepth)
            }
            if (previousLabel == null) {
                bookSourceLabel.remove()
            } else {
                bookSourceLabel.set(previousLabel)
            }
        }
    }

    fun currentBookSourceLabel(): String? = bookSourceLabel.get()

    fun <T> withHostObjectClassAccess(clazz: Class<*>, block: () -> T): T {
        val previous = hostObjectClassAccess.get().orEmpty()
        hostObjectClassAccess.set(previous + clazz.name)
        return try {
            block()
        } finally {
            if (previous.isEmpty()) {
                hostObjectClassAccess.remove()
            } else {
                hostObjectClassAccess.set(previous)
            }
        }
    }

    fun wrapJavaClass(scope: VarScope, javaClass: Class<*>): Scriptable {
        return when (javaClass) {
            System::class.java -> {
                ProtectedNativeJavaClass(scope, javaClass, systemClassProtectedName)
            }

            else -> ProtectedNativeJavaClass(scope, javaClass)
        }
    }

    override fun visibleToScripts(fullClassName: String): Boolean {
        if (protectedClassNamesMatcher.match(fullClassName)) {
            return false
        }
        if (
            (bookSourcePolicyDepth.get() ?: 0) > 0 &&
            fullClassName in bookSourceProtectedClassNames
        ) {
            return false
        }
        if (fullClassName in hostObjectClassAccess.get().orEmpty()) {
            return true
        }
        if (
            (bookSourcePolicyDepth.get() ?: 0) > 0 &&
            fullClassName.startsWith(appClassPrefix)
        ) {
            return fullClassName in bookSourceDirectClassImports
        }
        return true
    }

}
