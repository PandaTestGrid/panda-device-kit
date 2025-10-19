package com.panda.mirror

import android.content.Context
import android.os.Looper

/**
 * Android 系统类反射包装
 */

object ActivityThreadMirror {
    private val cls = Class.forName("android.app.ActivityThread")
    
    val systemMain = RefStaticMethod<Any>(cls, "systemMain")
    val getSystemContext = RefMethod<Context>(cls, "getSystemContext")
}

object UiAutomationMirror {
    private val cls = Class.forName("android.app.UiAutomation")
    private val connClass = try {
        Class.forName("android.app.IUiAutomationConnection")
    } catch (e: ClassNotFoundException) {
        Class.forName("android.app.UiAutomationConnection")
    }
    
    val ctor: RefConstructor<Any> = try {
        // 尝试旧版本构造函数: UiAutomation(Looper, Connection)
        RefConstructor(cls, Looper::class.java, connClass)
    } catch (e: Exception) {
        // 新版本: UiAutomation(Context, Looper, Connection)
        RefConstructor(cls, Context::class.java, Looper::class.java, connClass)
    }
    
    val connect = RefMethod<Unit>(cls, "connect")
}

object UiAutomationConnectionMirror {
    private val cls = Class.forName("android.app.UiAutomationConnection")
    val ctor = RefConstructor<Any>(cls)
}

