package com.panda.core

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.HandlerThread
import android.os.Looper
import com.panda.mirror.ActivityThreadMirror
import com.panda.mirror.UiAutomationConnectionMirror
import com.panda.mirror.UiAutomationMirror

/**
 * InstrumentShellWrapper - 伪造的 Instrumentation 对象
 * 通过反射创建 UiAutomation，用于初始化 UiDevice
 */
class InstrumentShellWrapper private constructor() : Instrumentation() {
    
    private val handlerThread = HandlerThread("UiAutomatorHandlerThread").apply { start() }
    private val uiAutomationConnection = UiAutomationConnectionMirror.ctor.newInstance()
    private var uiAutomation: UiAutomation? = null
    private var systemContext: Context? = null

    companion object {
        @Volatile
        private var instance: InstrumentShellWrapper? = null

        fun getInstance(): InstrumentShellWrapper {
            return instance ?: synchronized(this) {
                instance ?: InstrumentShellWrapper().also { instance = it }
            }
        }
    }

    override fun getContext(): Context {
        if (systemContext == null) {
            try {
                Looper.prepareMainLooper()
            } catch (e: Exception) {
                // Looper 可能已准备好
            }
            
            val activityThread = ActivityThreadMirror.systemMain.call()
            systemContext = ActivityThreadMirror.getSystemContext.call(activityThread)
        }
        return systemContext!!
    }

    override fun getUiAutomation(): UiAutomation {
        return getUiAutomation(0)
    }

    override fun getUiAutomation(flags: Int): UiAutomation {
        if (uiAutomation != null) {
            return uiAutomation!!
        }

        val automation = try {
            // Android 10+
            UiAutomationMirror.ctor.newInstance(
                context,
                handlerThread.looper,
                uiAutomationConnection
            )
        } catch (e: Exception) {
            // 旧版本
            UiAutomationMirror.ctor.newInstance(
                handlerThread.looper,
                uiAutomationConnection
            )
        }
        
        UiAutomationMirror.connect.call(automation)
        uiAutomation = automation as UiAutomation
        return uiAutomation!!
    }
}

