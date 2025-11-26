package com.panda.modules

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.service.notification.StatusBarNotification
import com.panda.mirror.INotificationManagerMirror
import com.panda.mirror.ServiceManagerMirror
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.InputStream

/**
 * 通知管理模块
 * 提供通知读取、交互、清除等功能
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class NotificationModule {
    
    private val notificationListener = NotificationListener()
    private val shellComponent = ComponentName(
        "com.android.shell",
        "com.android.shell.ShellNotificationListener"
    )
    
    private fun getNotificationManager(): Any {
        val binder = ServiceManagerMirror.getService.call("notification")
        return INotificationManagerMirror.asInterface.call(binder)
    }
    
    /**
     * 命令 80: 获取所有活动通知列表
     */
    fun getNotifications(input: InputStream, output: BufferedOutputStream) {
        try {
            val nm = getNotificationManager()
            
            // 注册监听器
            INotificationManagerMirror.registerListener.call(
                nm,
                notificationListener,
                shellComponent,
                -1
            )
            
            // 获取活动通知
            val notifications = getActiveNotifications(nm, notificationListener)
            
            // 发送通知数量
            IOUtils.writeInt(output, notifications.size)
            
            // 发送每个通知的详细信息
            for (notification in notifications) {
                writeNotification(output, notification)
            }
            
            output.flush()
            Logger.log("Sent ${notifications.size} notifications")
            
            // 等待客户端读取完成
            try {
                input.read()
            } finally {
                // 取消注册监听器
                INotificationManagerMirror.unregisterListener.call(nm, notificationListener, -1)
            }
            
        } catch (e: Exception) {
            Logger.error("Error getting notifications", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 81: 取消指定通知
     */
    fun cancelNotification(input: InputStream) {
        try {
            val key = IOUtils.readString(input)
            val nm = getNotificationManager()
            
            INotificationManagerMirror.cancelNotificationsFromListener.call(
                nm,
                notificationListener,
                arrayOf(key)
            )
            
            Logger.log("Cancelled notification: $key")
        } catch (e: Exception) {
            Logger.error("Error cancelling notification", e)
        }
    }
    
    /**
     * 命令 82: 打开或响应通知
     */
    fun openNotification(input: InputStream) {
        try {
            val key = IOUtils.readString(input)
            val actionType = IOUtils.readInt(input)
            
            val nm = getNotificationManager()
            
            // 获取通知
            val notifications = getActiveNotifications(nm, notificationListener, arrayOf(key))
            val notification = notifications.firstOrNull()
            
            if (notification == null) {
                Logger.log("Notification not found: $key")
                return
            }
            
            when (actionType) {
                0 -> {
                    // 打开主内容
                    val contentIntent = notification.notification.contentIntent
                        ?: notification.notification.fullScreenIntent
                    
                    contentIntent?.send()
                    Logger.log("Opened notification: $key")
                }
                1 -> {
                    // 执行动作
                    val actionIndex = IOUtils.readInt(input)
                    val inputText = IOUtils.readString(input)
                    
                    val action = notification.notification.actions?.getOrNull(actionIndex)
                    if (action != null) {
                        action.actionIntent.send()
                        Logger.log("Executed action $actionIndex for notification: $key")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error opening notification", e)
        }
    }
    
    /**
     * 命令 83: 清除所有可清除的通知
     */
    fun clearAllNotifications() {
        try {
            val nm = getNotificationManager()
            val notifications = getActiveNotifications(nm, notificationListener)
            
            val keys = notifications.map { it.key }.toTypedArray()
            INotificationManagerMirror.cancelNotificationsFromListener.call(nm, notificationListener, keys)
            
            Logger.log("Cleared ${keys.size} notifications")
        } catch (e: Exception) {
            Logger.error("Error clearing notifications", e)
        }
    }
    
    /**
     * 获取活动通知列表
     */
    private fun getActiveNotifications(
        nm: Any,
        listener: NotificationListener,
        keys: Array<String>? = null
    ): List<StatusBarNotification> {
        return try {
            val slice = INotificationManagerMirror.getActiveNotificationsFromListener.call(
                nm,
                listener,
                keys,
                0
            )
            
            // 通过反射获取 list 属性
            val list = slice?.javaClass?.getMethod("getList")?.invoke(slice) as? List<*>
            
            list?.filterIsInstance<StatusBarNotification>()?.filter {
                // 过滤掉系统通知
                it.notification != null
            } ?: emptyList()
        } catch (e: Exception) {
            Logger.error("Error getting active notifications", e)
            emptyList()
        }
    }
    
    /**
     * 写入通知信息
     */
    private fun writeNotification(output: BufferedOutputStream, sbn: StatusBarNotification) {
        val notification = sbn.notification
        
        // Key
        IOUtils.writeString(output, sbn.key)
        // 包名
        IOUtils.writeString(output, sbn.packageName)
        // 标题
        val title = notification.extras.getCharSequence("android.title")?.toString() ?: ""
        IOUtils.writeString(output, title)
        // 内容
        val text = notification.extras.getCharSequence("android.text")?.toString() ?: ""
        IOUtils.writeString(output, text)
        // 时间戳
        IOUtils.writeLong(output, sbn.postTime)
        // 是否可清除
        IOUtils.writeBoolean(output, sbn.isClearable)
        
        // 动作数量
        val actions = notification.actions ?: emptyArray()
        IOUtils.writeInt(output, actions.size)
        
        // 每个动作的信息
        for (action in actions) {
            IOUtils.writeString(output, action.title?.toString() ?: "")
            val hasInput = action.remoteInputs != null && action.remoteInputs.isNotEmpty()
            IOUtils.writeBoolean(output, hasInput)
        }
    }
    
    private class NotificationListener : android.service.notification.NotificationListenerService()
}

