package com.panda.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Process
import android.os.Looper
import com.panda.mirror.ActivityThreadMirror

/**
 * FakeContext
 * 在非应用环境中获取 Android Context，用于访问系统服务
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object FakeContext {
    
    private var context: Context? = null
    private var displayId: Int = 0
    
    /**
     * 获取 Context 对象
     */
    fun get(): Context {
        if (context == null) {
            context = createContext()
        }
        return context!!
    }
    
    /**
     * 创建 Context
     * 使用反射获取系统 Context
     */
    private fun createContext(): Context {
        try {
            // 准备主 Looper
            try {
                Looper.prepareMainLooper()
            } catch (e: Exception) {
                // Looper 可能已经准备好
            }
            
            // 通过反射获取 ActivityThread 和 Context
            val activityThread = ActivityThreadMirror.systemMain.call()
            val systemContext = ActivityThreadMirror.getSystemContext.call(activityThread)
            
            val shellBase = createShellContext(systemContext)
            
            return ShellContextWrapper(shellBase)
        } catch (e: Exception) {
            Logger.error("Error creating FakeContext", e)
            throw RuntimeException("Failed to create context", e)
        }
    }
    
    private fun createShellContext(systemContext: Context): Context {
        val flagCandidates = intArrayOf(
            Context.CONTEXT_IGNORE_SECURITY,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            0
        )
        for (flags in flagCandidates) {
            try {
                return systemContext.createPackageContext(SHELL_PACKAGE, flags)
            } catch (_: Exception) {
                // Try next option
            }
        }
        Logger.error("Failed to create shell package context, fallback to system context", null)
        return systemContext
    }
    
    /**
     * 设置显示器 ID
     */
    fun setDisplayId(id: Int) {
        displayId = id
        Logger.log("Display ID set to: $id")
    }
    
    /**
     * 获取显示器 ID
     */
    fun getDisplayId(): Int {
        return displayId
    }
    
    /**
     * 包装系统 Context，重写包名相关方法以匹配 shell UID
     */
    private class ShellContextWrapper(base: Context) : ContextWrapper(base) {
        private val shellPackage = SHELL_PACKAGE
        
        init {
            spoofBaseContext()
        }
        
        private fun spoofBaseContext() {
            try {
                val baseField = ContextWrapper::class.java.getDeclaredField("mBase")
                baseField.isAccessible = true
                val baseContext = baseField.get(this) ?: return
                val contextImplClass = Class.forName("android.app.ContextImpl")
                if (contextImplClass.isInstance(baseContext)) {
                    fun setField(name: String) {
                        try {
                            val field = contextImplClass.getDeclaredField(name)
                            field.isAccessible = true
                            field.set(baseContext, shellPackage)
                        } catch (_: Exception) {
                            // 某些 Android 版本可能不存在该字段
                        }
                    }
                    setField("mBasePackageName")
                    setField("mPackageName")
                    setField("mOpPackageName")
                    
                    if (Build.VERSION.SDK_INT >= 31) {
                        try {
                            val attributionSourceClass = Class.forName("android.content.AttributionSource")
                            val builderClass = Class.forName("android.content.AttributionSource\$Builder")
                            val builder = builderClass
                                .getConstructor(Int::class.javaPrimitiveType)
                                .newInstance(Process.SHELL_UID)
                            builderClass.getMethod("setPackageName", String::class.java)
                                .invoke(builder, shellPackage)
                            try {
                                builderClass.getMethod("setAttributionTag", String::class.java)
                                    .invoke(builder, shellPackage)
                            } catch (_: Exception) {
                                // Older builds might not expose attribution tags
                            }
                            val attributionSource = builderClass.getMethod("build").invoke(builder)
                            val field = contextImplClass.getDeclaredField("mAttributionSource")
                            field.isAccessible = true
                            field.set(baseContext, attributionSource)
                        } catch (e: Exception) {
                            Logger.error("Failed to spoof attribution source", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.error("Failed to spoof base package", e)
            }
        }
        
        override fun getOpPackageName(): String {
            return shellPackage
        }
        
        override fun getPackageName(): String {
            return shellPackage
        }
        
        override fun getAttributionTag(): String? {
            return if (Build.VERSION.SDK_INT >= 31) {
                shellPackage
            } else {
                super.getAttributionTag()
            }
        }
    }
}

private const val SHELL_PACKAGE = "com.android.shell"

