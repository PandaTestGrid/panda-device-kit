package com.panda.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
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
            
            return ContextWrapper(systemContext)
        } catch (e: Exception) {
            Logger.error("Error creating FakeContext", e)
            throw RuntimeException("Failed to create context", e)
        }
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
}

