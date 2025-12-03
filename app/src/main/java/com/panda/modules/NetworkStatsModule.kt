package com.panda.modules

import android.annotation.SuppressLint
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.os.Build
import com.panda.utils.FakeContext
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.InputStream
import java.lang.reflect.Field

/**
 * 网络流量统计模块
 * 提供按 UID 统计应用网络流量功能
 * 支持移动网络和 WiFi
 * 参考 PerfDog Console 实现
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class NetworkStatsModule {
    
    private var networkStatsManager: NetworkStatsManager? = null
    
    /**
     * 命令 230: 获取指定 UID 的网络流量
     * 请求: UID(int)
     * 响应: 接收字节数(long), 发送字节数(long), WiFi接收(long), WiFi发送(long), 移动接收(long), 移动发送(long)
     */
    fun getNetworkUsage(input: InputStream, output: BufferedOutputStream) {
        try {
            if (Build.VERSION.SDK_INT < 23) {
                // 需要 Android 6.0+
                Logger.log("Network stats requires Android 6.0+")
                writeEmptyNetworkStats(output)
                return
            }
            
            val uid = IOUtils.readInt(input)
            val context = FakeContext.get()
            
            // 获取 NetworkStatsManager
            if (networkStatsManager == null) {
                networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            }
            
            val statsManager = networkStatsManager ?: run {
                Logger.error("NetworkStatsManager is null", null)
                writeEmptyNetworkStats(output)
                return
            }
            
            // 通过反射设置 Context（某些版本需要）
            try {
                val field: Field = statsManager.javaClass.getDeclaredField("mContext")
                field.isAccessible = true
                field.set(statsManager, context)
            } catch (e: Exception) {
                // 某些版本可能没有这个字段，忽略
                Logger.log("Could not set mContext field: ${e.message}")
            }
            
            // 获取 WiFi 流量 (TYPE_WIFI = 1)
            val wifiStats = getNetworkStatsForUid(statsManager, 1, uid)
            
            // 获取移动网络流量 (TYPE_MOBILE = 0)
            val mobileStats = getNetworkStatsForUid(statsManager, 0, uid)
            
            // 发送数据
            IOUtils.writeLong(output, wifiStats.rxBytes + mobileStats.rxBytes)  // 总接收
            IOUtils.writeLong(output, wifiStats.txBytes + mobileStats.txBytes)  // 总发送
            IOUtils.writeLong(output, wifiStats.rxBytes)  // WiFi 接收
            IOUtils.writeLong(output, wifiStats.txBytes)  // WiFi 发送
            IOUtils.writeLong(output, mobileStats.rxBytes)  // 移动接收
            IOUtils.writeLong(output, mobileStats.txBytes)  // 移动发送
            output.flush()
            
            Logger.log("Network stats for UID $uid: Total RX=${wifiStats.rxBytes + mobileStats.rxBytes}, TX=${wifiStats.txBytes + mobileStats.txBytes}")
            
        } catch (e: Exception) {
            Logger.error("Error getting network usage", e)
            writeEmptyNetworkStats(output)
        }
    }
    
    /**
     * 命令 231: 获取总网络流量（所有 UID）
     * 响应: 接收字节数(long), 发送字节数(long)
     */
    fun getTotalNetworkUsage(output: BufferedOutputStream) {
        try {
            if (Build.VERSION.SDK_INT < 23) {
                IOUtils.writeLong(output, 0)
                IOUtils.writeLong(output, 0)
                return
            }
            
            val context = FakeContext.get()
            val statsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: run {
                    IOUtils.writeLong(output, 0)
                    IOUtils.writeLong(output, 0)
                    return
                }
            
            // 获取所有 UID 的流量
            val wifiStats = getAllNetworkStats(statsManager, 1)  // TYPE_WIFI = 1
            val mobileStats = getAllNetworkStats(statsManager, 0)  // TYPE_MOBILE = 0
            
            val totalRx = wifiStats.rxBytes + mobileStats.rxBytes
            val totalTx = wifiStats.txBytes + mobileStats.txBytes
            
            IOUtils.writeLong(output, totalRx)
            IOUtils.writeLong(output, totalTx)
            
            Logger.log("Total network usage: RX=$totalRx, TX=$totalTx")
            
        } catch (e: Exception) {
            Logger.error("Error getting total network usage", e)
            IOUtils.writeLong(output, 0)
            IOUtils.writeLong(output, 0)
        }
    }
    
    /**
     * 命令 232: 获取指定包名的网络流量
     * 请求: 包名(string)
     * 响应: UID(int), 接收字节数(long), 发送字节数(long)
     */
    fun getNetworkUsageByPackage(input: InputStream, output: BufferedOutputStream) {
        try {
            if (Build.VERSION.SDK_INT < 23) {
                IOUtils.writeInt(output, 0)
                IOUtils.writeLong(output, 0)
                IOUtils.writeLong(output, 0)
                return
            }
            
            val packageName = IOUtils.readString(input)
            val context = FakeContext.get()
            val pm = context.packageManager
            
            // 获取 UID
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                Logger.error("Package not found: $packageName", e)
                IOUtils.writeInt(output, 0)
                IOUtils.writeLong(output, 0)
                IOUtils.writeLong(output, 0)
                return
            }
            
            val uid = appInfo.uid
            
            // 获取流量
            val statsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: run {
                    IOUtils.writeInt(output, uid)
                    IOUtils.writeLong(output, 0)
                    IOUtils.writeLong(output, 0)
                    return
                }
            
            val wifiStats = getNetworkStatsForUid(statsManager, 1, uid)  // TYPE_WIFI = 1
            val mobileStats = getNetworkStatsForUid(statsManager, 0, uid)  // TYPE_MOBILE = 0
            
            IOUtils.writeInt(output, uid)
            IOUtils.writeLong(output, wifiStats.rxBytes + mobileStats.rxBytes)
            IOUtils.writeLong(output, wifiStats.txBytes + mobileStats.txBytes)
            
            Logger.log("Network stats for $packageName (UID $uid): RX=${wifiStats.rxBytes + mobileStats.rxBytes}, TX=${wifiStats.txBytes + mobileStats.txBytes}")
            
        } catch (e: Exception) {
            Logger.error("Error getting network usage by package", e)
            IOUtils.writeInt(output, 0)
            IOUtils.writeLong(output, 0)
            IOUtils.writeLong(output, 0)
        }
    }
    
    // ========== 内部实现方法 ==========
    
    /**
     * 获取指定 UID 的网络流量
     * 参考 PerfDog Console 实现：
     * 1. 强制刷新统计数据 (setPollForce(true))
     * 2. 查询并立即关闭以触发刷新
     * 3. 恢复正常轮询 (setPollForce(false))
     * 4. 再次查询用于统计指定 UID 的流量
     * 
     * @param networkType 网络类型：0=TYPE_MOBILE, 1=TYPE_WIFI
     * @param uid 应用 UID
     */
    private fun getNetworkStatsForUid(
        statsManager: NetworkStatsManager,
        networkType: Int,
        uid: Int
    ): NetworkStatsData {
        var rxBytes = 0L
        var txBytes = 0L
        
        try {
            // 强制刷新统计数据（参考 PerfDog Console）
            try {
                val setPollForceMethod = statsManager.javaClass.getMethod("setPollForce", Boolean::class.javaPrimitiveType)
                setPollForceMethod.invoke(statsManager, true)
            } catch (e: Exception) {
                // 某些版本可能没有这个方法，忽略
            }
            
            // 先查询并关闭以触发刷新（参考 PerfDog Console 流程）
            val refreshStats = statsManager.querySummary(
                networkType,
                null,  // subscriberId
                Long.MIN_VALUE,  // startTime
                Long.MAX_VALUE   // endTime
            )
            refreshStats.close()
            
            // 恢复正常轮询
            try {
                val setPollForceMethod = statsManager.javaClass.getMethod("setPollForce", Boolean::class.javaPrimitiveType)
                setPollForceMethod.invoke(statsManager, false)
            } catch (e: Exception) {
                // 忽略
            }
            
            // 再次查询用于统计（参考 PerfDog Console）
            val networkStats = statsManager.querySummary(
                networkType,
                null,
                Long.MIN_VALUE,
                Long.MAX_VALUE
            )
            
            val bucket = NetworkStats.Bucket()
            while (networkStats.getNextBucket(bucket)) {
                if (bucket.uid == uid && bucket.tag == 0) {
                    // tag == 0 表示应用流量（不包括系统标签的流量）
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
            }
            
            networkStats.close()
            
        } catch (e: Exception) {
            Logger.error("Error querying network stats for UID $uid, type $networkType", e)
        }
        
        return NetworkStatsData(rxBytes, txBytes)
    }
    
    /**
     * 获取所有 UID 的网络流量总和
     */
    private fun getAllNetworkStats(
        statsManager: NetworkStatsManager,
        networkType: Int
    ): NetworkStatsData {
        var rxBytes = 0L
        var txBytes = 0L
        
        try {
            val networkStats = statsManager.querySummary(
                networkType,
                null,
                Long.MIN_VALUE,
                Long.MAX_VALUE
            )
            
            val bucket = NetworkStats.Bucket()
            while (networkStats.getNextBucket(bucket)) {
                if (bucket.tag == 0) {
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
            }
            
            networkStats.close()
            
        } catch (e: Exception) {
            Logger.error("Error querying all network stats for type $networkType", e)
        }
        
        return NetworkStatsData(rxBytes, txBytes)
    }
    
    /**
     * 写入空的网络统计数据
     */
    private fun writeEmptyNetworkStats(output: BufferedOutputStream) {
        IOUtils.writeLong(output, 0)
        IOUtils.writeLong(output, 0)
        IOUtils.writeLong(output, 0)
        IOUtils.writeLong(output, 0)
        IOUtils.writeLong(output, 0)
        IOUtils.writeLong(output, 0)
    }
    
    /**
     * 网络统计数据类
     */
    private data class NetworkStatsData(
        val rxBytes: Long,
        val txBytes: Long
    )
}

