package com.panda.modules

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Debug
import com.panda.utils.FakeContext
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.InputStream

/**
 * 内存数据采集模块
 * 提供进程内存使用信息
 * 参考 PerfDog Console 实现
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class MemoryModule {
    
    /**
     * 命令 205: 获取进程内存使用
     * 请求: PID(int)
     * 响应: PSS(long KB), PrivateDirty(long KB), SharedDirty(long KB)
     */
    fun getMemoryUsage(input: InputStream, output: BufferedOutputStream) {
        try {
            val pid = IOUtils.readInt(input)
            val memoryInfo = getProcessMemoryInfo(pid)
            
            IOUtils.writeLong(output, memoryInfo.pss)
            IOUtils.writeLong(output, memoryInfo.privateDirty)
            IOUtils.writeLong(output, memoryInfo.sharedDirty)
            output.flush()
            
            Logger.log("Memory for PID $pid: PSS=${memoryInfo.pss}KB, Private=${memoryInfo.privateDirty}KB, Shared=${memoryInfo.sharedDirty}KB")
        } catch (e: Exception) {
            Logger.error("Error getting memory usage", e)
            IOUtils.writeLong(output, 0)
            IOUtils.writeLong(output, 0)
            IOUtils.writeLong(output, 0)
            output.flush()
        }
    }
    
    // ========== 内部实现方法 ==========
    
    /**
     * 获取进程内存信息
     * 参考 PerfDog Console 实现，使用 ActivityManager 和 Debug.MemoryInfo
     * 
     * 官方实现特点：
     * 1. 使用 ActivityManager.getMemoryInfo() 获取系统内存信息
     * 2. 使用 ActivityManager.getProcessMemoryInfo() 获取进程内存信息
     * 3. 通过反射获取高级内存信息（交换内存等）
     */
    private fun getProcessMemoryInfo(pid: Int): MemoryInfo {
        val context = FakeContext.get()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        
        try {
            // 1. 获取系统内存信息（参考官方实现，用于日志和调试）
            val systemMemInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(systemMemInfo)
            
            // 2. 获取进程详细内存信息（官方实现的核心方法）
            val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(pid))
            if (memoryInfoArray.isNotEmpty()) {
                val memInfo = memoryInfoArray[0]
                
                // 3. 尝试通过反射获取高级内存信息（参考官方实现）
                val advancedInfo = getAdvancedMemoryInfo(memInfo)
                
                // 记录系统内存信息（用于调试）
                Logger.log("System memory - Total: ${systemMemInfo.totalMem / 1024 / 1024}MB, " +
                          "Available: ${systemMemInfo.availMem / 1024 / 1024}MB, " +
                          "Threshold: ${systemMemInfo.threshold / 1024 / 1024}MB, " +
                          "LowMemory: ${systemMemInfo.lowMemory}")
                
                // 记录高级内存信息（如果可用）
                if (advancedInfo != null) {
                    Logger.log("Advanced memory info - SwappedOut: ${advancedInfo.totalSwappedOut}KB, " +
                              "SwappedOutPss: ${advancedInfo.totalSwappedOutPss}KB, " +
                              "HasSwappedOutPss: ${advancedInfo.hasSwappedOutPss}")
                }
                
                return MemoryInfo(
                    pss = memInfo.totalPss.toLong(),
                    privateDirty = memInfo.totalPrivateDirty.toLong(),
                    sharedDirty = memInfo.totalSharedDirty.toLong()
                )
            }
        } catch (e: Exception) {
            Logger.error("Error getting process memory info", e)
        }
        
        return MemoryInfo(0, 0, 0)
    }
    
    /**
     * 通过反射获取高级内存信息
     * 参考官方实现：使用反射调用 Debug.MemoryInfo 的隐藏方法
     * 
     * 官方反射的方法：
     * - getTotalSwappedOut() - 总交换内存
     * - getOtherLabel(int) - 其他内存标签
     * - getOtherPss(int) - 其他 PSS 内存
     * - hasSwappedOutPss() - 是否有交换 PSS
     * - getTotalSwappedOutPss() - 总交换 PSS
     */
    @SuppressLint("PrivateApi")
    private fun getAdvancedMemoryInfo(memInfo: Debug.MemoryInfo): AdvancedMemoryInfo? {
        return try {
            var totalSwappedOut = 0L
            var totalSwappedOutPss = 0L
            var hasSwappedOutPss = false
            
            // 尝试获取 getTotalSwappedOut()
            try {
                val method = Debug.MemoryInfo::class.java.getMethod("getTotalSwappedOut")
                totalSwappedOut = (method.invoke(memInfo) as? Int)?.toLong() ?: 0L
            } catch (e: Exception) {
                // 方法不存在或调用失败，忽略
            }
            
            // 尝试获取 hasSwappedOutPss()
            try {
                val hasMethod = Debug.MemoryInfo::class.java.getMethod("hasSwappedOutPss")
                hasSwappedOutPss = (hasMethod.invoke(memInfo) as? Boolean) ?: false
            } catch (e: Exception) {
                // 方法不存在或调用失败，忽略
            }
            
            // 尝试获取 getTotalSwappedOutPss()
            try {
                val pssMethod = Debug.MemoryInfo::class.java.getMethod("getTotalSwappedOutPss")
                totalSwappedOutPss = (pssMethod.invoke(memInfo) as? Int)?.toLong() ?: 0L
            } catch (e: Exception) {
                // 方法不存在或调用失败，忽略
            }
            
            AdvancedMemoryInfo(
                totalSwappedOut = totalSwappedOut,
                totalSwappedOutPss = totalSwappedOutPss,
                hasSwappedOutPss = hasSwappedOutPss
            )
        } catch (e: Exception) {
            // 反射失败，返回 null（某些 Android 版本可能不支持这些方法）
            null
        }
    }
    
    /**
     * 内存信息数据类
     * 基本内存信息（用于 API 响应）
     */
    private data class MemoryInfo(
        val pss: Long,
        val privateDirty: Long,
        val sharedDirty: Long
    )
    
    /**
     * 高级内存信息数据类
     * 通过反射获取的额外内存信息（用于日志和调试）
     * 参考官方实现
     */
    private data class AdvancedMemoryInfo(
        val totalSwappedOut: Long,        // 总交换内存 (KB)
        val totalSwappedOutPss: Long,     // 总交换 PSS (KB)
        val hasSwappedOutPss: Boolean     // 是否有交换 PSS
    )
}

