package com.panda.modules

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.panda.mirror.ServiceManagerMirror
import com.panda.utils.FakeContext
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.InputStreamReader

/**
 * 电池信息采集模块
 * 提供电池电流、电压、电量、充电状态等信息
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class BatteryModule {
    
    private var batteryManager: BatteryManager? = null
    
    /**
     * 命令 220: 获取完整电池信息
     * 响应: 电流(int 毫安), 电压(int 毫伏), 电量(int 0-100), 充电状态(int 0=未充电, 1=充电中), 时间戳(long)
     */
    fun getBatteryInfo(output: BufferedOutputStream) {
        try {
            if (Build.VERSION.SDK_INT < 21) {
                // Android 5.0 以下不支持
                IOUtils.writeInt(output, 0)
                IOUtils.writeInt(output, 0)
                IOUtils.writeInt(output, 0)
                IOUtils.writeInt(output, 0)
                IOUtils.writeLong(output, 0)
                return
            }
            
            val context = FakeContext.get()
            
            // 获取 BatteryManager
            if (batteryManager == null) {
                batteryManager = context.getSystemService(BatteryManager::class.java)
            }
            
            // 获取电池电流（微安）
            var currentNow = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            // 转换为毫安（如果值大于 10000，说明单位已经是微安，需要除以 1000）
            if (Math.abs(currentNow) > 10000) {
                currentNow /= 1000
            }
            
            // 获取电池电压（毫伏）
            val voltage = getBatteryVoltage()
            
            // 获取电池电量（0-100）
            val level = getBatteryLevel()
            
            // 获取充电状态
            val isCharging = isCharging()
            
            // 时间戳
            val timestamp = System.currentTimeMillis()
            
            // 发送数据
            IOUtils.writeInt(output, currentNow)  // 电流（毫安）
            IOUtils.writeInt(output, voltage)      // 电压（毫伏）
            IOUtils.writeInt(output, level)        // 电量（0-100）
            IOUtils.writeInt(output, if (isCharging) 1 else 0)  // 充电状态
            IOUtils.writeLong(output, timestamp)   // 时间戳
            output.flush()  // 确保数据发送
            
            Logger.log("Battery info: current=${currentNow}mA, voltage=${voltage}mV, level=$level%, charging=$isCharging")
            
        } catch (e: Exception) {
            Logger.error("Error getting battery info", e)
            IOUtils.writeInt(output, 0)
            IOUtils.writeInt(output, 0)
            IOUtils.writeInt(output, 0)
            IOUtils.writeInt(output, 0)
            IOUtils.writeLong(output, 0)
            output.flush()
        }
    }
    
    /**
     * 命令 221: 获取电池电量
     * 响应: 电量(int 0-100)
     */
    fun getBatteryLevel(output: BufferedOutputStream) {
        try {
            val level = getBatteryLevel()
            IOUtils.writeInt(output, level)
            Logger.log("Battery level: $level%")
        } catch (e: Exception) {
            Logger.error("Error getting battery level", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 222: 检查是否支持电池监控
     * 响应: 支持(int 1=支持, 0=不支持)
     */
    fun isBatteryMonitoringSupported(output: BufferedOutputStream) {
        try {
            val supported = checkBatteryMonitoringSupport()
            IOUtils.writeInt(output, if (supported) 1 else 0)
            Logger.log("Battery monitoring supported: $supported")
        } catch (e: Exception) {
            Logger.error("Error checking battery monitoring support", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    // ========== 内部实现方法 ==========
    
    /**
     * 获取电池电压（毫伏）
     * 通过 ServiceManager 获取电池服务，调用 dump 方法获取电压信息
     */
    private fun getBatteryVoltage(): Int {
        try {
            val service = ServiceManagerMirror.getService.call("battery") as? IBinder
            if (service != null) {
                // 创建管道
                val pipe = ParcelFileDescriptor.createPipe()
                try {
                    // 调用 dump 方法获取电池信息
                    try {
                        val dumpMethod = service.javaClass.getMethod("dump", java.io.FileDescriptor::class.java, Array<String>::class.java)
                        dumpMethod.invoke(service, pipe[1].fileDescriptor, emptyArray<String>())
                    } catch (e: Exception) {
                        // 尝试另一种方法签名
                        val dumpMethod = service.javaClass.getMethod("dump", java.io.FileDescriptor::class.java)
                        dumpMethod.invoke(service, pipe[1].fileDescriptor)
                    }
                    
                    // 读取输出
                    val reader = BufferedReader(
                        InputStreamReader(
                            ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
                        )
                    )
                    
                    // 解析输出，查找 voltage 字段
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.startsWith("voltage:")) {
                            val voltage = trimmed.substring(8).trim().toIntOrNull() ?: 0
                            reader.close()
                            return voltage
                        }
                    }
                    reader.close()
                } finally {
                    pipe[0].close()
                    pipe[1].close()
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting battery voltage from service", e)
        }
        
        // 降级方案：通过 Intent 获取
        try {
            val context = FakeContext.get()
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                if (voltage > 0) {
                    return voltage
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting battery voltage from Intent", e)
        }
        
        return 0
    }
    
    /**
     * 获取电池电量（0-100）
     */
    private fun getBatteryLevel(): Int {
        // 首先尝试从 battery service dump 获取
        try {
            val level = getBatteryLevelFromService()
            if (level >= 0) {
                return level
            }
        } catch (e: Exception) {
            Logger.error("Error getting battery level from service", e)
        }
        
        // 降级方案：通过 Intent 获取
        try {
            val context = FakeContext.get()
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    return (level * 100 / scale)
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting battery level from Intent", e)
        }
        return 0
    }
    
    /**
     * 检查是否正在充电
     */
    private fun isCharging(): Boolean {
        // 首先尝试从 battery service dump 获取
        try {
            val status = getChargingStatusFromService()
            if (status != null) {
                return status
            }
        } catch (e: Exception) {
            Logger.error("Error getting charging status from service", e)
        }
        
        // 降级方案：通过 Intent 获取
        try {
            val context = FakeContext.get()
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {
            Logger.error("Error checking charging status from Intent", e)
        }
        return false
    }
    
    /**
     * 从 battery service dump 获取电池电量
     */
    private fun getBatteryLevelFromService(): Int {
        try {
            val service = ServiceManagerMirror.getService.call("battery") as? IBinder
            if (service != null) {
                val pipe = ParcelFileDescriptor.createPipe()
                try {
                    // 调用 dump 方法获取电池信息
                    try {
                        val dumpMethod = service.javaClass.getMethod("dump", java.io.FileDescriptor::class.java, Array<String>::class.java)
                        dumpMethod.invoke(service, pipe[1].fileDescriptor, emptyArray<String>())
                    } catch (e: Exception) {
                        val dumpMethod = service.javaClass.getMethod("dump", java.io.FileDescriptor::class.java)
                        dumpMethod.invoke(service, pipe[1].fileDescriptor)
                    }
                    
                    // 读取输出
                    val reader = BufferedReader(
                        InputStreamReader(
                            ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
                        )
                    )
                    
                    // 解析输出，查找 level 字段
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        // 查找 "level: 100" 或 "level:100" 格式
                        if (trimmed.startsWith("level:")) {
                            val levelStr = trimmed.substring(6).trim()
                            val level = levelStr.toIntOrNull()
                            if (level != null && level >= 0 && level <= 100) {
                                reader.close()
                                return level
                            }
                        }
                    }
                    reader.close()
                } finally {
                    pipe[0].close()
                    pipe[1].close()
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting battery level from service", e)
        }
        return -1
    }
    
    /**
     * 从 battery service dump 获取充电状态
     */
    private fun getChargingStatusFromService(): Boolean? {
        try {
            val service = ServiceManagerMirror.getService.call("battery") as? IBinder
            if (service != null) {
                val pipe = ParcelFileDescriptor.createPipe()
                try {
                    // 调用 dump 方法获取电池信息
                    try {
                        val dumpMethod = service.javaClass.getMethod("dump", java.io.FileDescriptor::class.java, Array<String>::class.java)
                        dumpMethod.invoke(service, pipe[1].fileDescriptor, emptyArray<String>())
                    } catch (e: Exception) {
                        val dumpMethod = service.javaClass.getMethod("dump", java.io.FileDescriptor::class.java)
                        dumpMethod.invoke(service, pipe[1].fileDescriptor)
                    }
                    
                    // 读取输出
                    val reader = BufferedReader(
                        InputStreamReader(
                            ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
                        )
                    )
                    
                    // 解析输出，查找 status 字段
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        // 查找 "status: 2" 或 "status:2" 格式
                        // 2 = BATTERY_STATUS_CHARGING, 5 = BATTERY_STATUS_FULL
                        if (trimmed.startsWith("status:")) {
                            val statusStr = trimmed.substring(7).trim()
                            val status = statusStr.toIntOrNull()
                            if (status != null) {
                                reader.close()
                                return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                       status == BatteryManager.BATTERY_STATUS_FULL
                            }
                        }
                    }
                    reader.close()
                } finally {
                    pipe[0].close()
                    pipe[1].close()
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting charging status from service", e)
        }
        return null
    }
    
    /**
     * 检查是否支持电池监控
     */
    private fun checkBatteryMonitoringSupport(): Boolean {
        if (Build.VERSION.SDK_INT < 21) {
            return false
        }
        
        try {
            val context = FakeContext.get()
            val bm = context.getSystemService(BatteryManager::class.java)
            if (bm != null) {
                val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                return current != 0
            }
        } catch (e: Exception) {
            Logger.error("Error checking battery monitoring support", e)
        }
        return false
    }
}

