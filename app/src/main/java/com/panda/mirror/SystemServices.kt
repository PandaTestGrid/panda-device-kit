package com.panda.mirror

import android.os.IBinder

/**
 * 系统服务反射包装
 */

object ServiceManagerMirror {
    private val cls = Class.forName("android.os.ServiceManager")
    val getService = RefStaticMethod<IBinder>(cls, "getService", String::class.java)
}

object INotificationManagerMirror {
    private val stubClass = Class.forName("android.app.INotificationManager\$Stub")
    val asInterface = RefStaticMethod<Any>(stubClass, "asInterface", IBinder::class.java)
    
    private val cls = Class.forName("android.app.INotificationManager")
    private val intClass = Int::class.javaPrimitiveType!!
    
    val registerListener = RefMethod<Unit>(cls, "registerListener", 
        Class.forName("android.service.notification.INotificationListener"),
        Class.forName("android.content.ComponentName"),
        intClass)
    
    val unregisterListener = RefMethod<Unit>(cls, "unregisterListener",
        Class.forName("android.service.notification.INotificationListener"),
        intClass)
    
    val getActiveNotificationsFromListener = RefMethod<Any>(cls, "getActiveNotificationsFromListener",
        Class.forName("android.service.notification.INotificationListener"),
        Array<String>::class.java,
        intClass)
    
    val cancelNotificationsFromListener = RefMethod<Unit>(cls, "cancelNotificationsFromListener",
        Class.forName("android.service.notification.INotificationListener"),
        Array<String>::class.java)
}

object IWindowManagerMirror {
    private val stubClass = Class.forName("android.view.IWindowManager\$Stub")
    val asInterface = RefStaticMethod<Any>(stubClass, "asInterface", IBinder::class.java)
    
    private val cls = Class.forName("android.view.IWindowManager")
    val screenshotWallpaper = RefMethod<android.graphics.Bitmap>(cls, "screenshotWallpaper")
}

