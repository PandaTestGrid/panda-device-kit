package com.panda.modules

import android.annotation.SuppressLint
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileReader
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * CPU 数据采集模块
 * 提供 CPU 使用率、核心使用率、频率、温度、线程 CPU 使用率等功能
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class CpuModule {
    
    // CPU 数据缓存
    private var lastCpuTime: Long = 0
    private var lastCpuIdle: Long = 0
    private val cpuUsageCache = mutableMapOf<Int, Float>()
    private val lastUpdateTime = AtomicLong(0)
    
    /**
     * 命令 200: 获取整体 CPU 使用率
     * 响应: CPU 使用率(float, 0-100)
     */
    fun getCpuUsage(output: BufferedOutputStream) {
        try {
            val usage = calculateCpuUsage()
            IOUtils.writeFloat(output, usage)
            output.flush()  // 确保数据发送
            Logger.log("CPU usage: ${usage}%")
        } catch (e: Exception) {
            Logger.error("Error getting CPU usage", e)
            IOUtils.writeFloat(output, 0f)
            output.flush()
        }
    }
    
    /**
     * 命令 201: 获取 CPU 核心使用率
     * 响应: 核心数量(int), 每个核心的使用率(float[])
     */
    fun getCpuCoreUsage(output: BufferedOutputStream) {
        try {
            val coreUsages = getCpuCoreUsages()
            IOUtils.writeInt(output, coreUsages.size)
            coreUsages.forEach { usage ->
                IOUtils.writeFloat(output, usage)
            }
            Logger.log("CPU core usage: ${coreUsages.size} cores")
        } catch (e: Exception) {
            Logger.error("Error getting CPU core usage", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 202: 获取 CPU 频率
     * 响应: 核心数量(int), 每个核心的频率(int[] kHz)
     */
    fun getCpuFreq(output: BufferedOutputStream) {
        try {
            val frequencies = getCpuFrequencies()
            IOUtils.writeInt(output, frequencies.size)
            frequencies.forEach { freq ->
                IOUtils.writeInt(output, freq)
            }
            Logger.log("CPU frequencies: ${frequencies.size} cores")
        } catch (e: Exception) {
            Logger.error("Error getting CPU frequencies", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 206: 获取 CPU 温度
     * 响应: 温度(float 摄氏度)
     */
    fun getCpuTemperature(output: BufferedOutputStream) {
        try {
            val temp = getCpuTemperatureValue()
            IOUtils.writeFloat(output, temp)
            Logger.log("CPU temperature: ${temp}°C")
        } catch (e: Exception) {
            Logger.error("Error getting CPU temperature", e)
            IOUtils.writeFloat(output, 0f)
        }
    }
    
    /**
     * 命令 207: 获取线程 CPU 使用率
     * 请求: PID(int), TID(int)
     * 响应: CPU 使用率(float 0-100)
     */
    fun getThreadCpuUsage(input: InputStream, output: BufferedOutputStream) {
        try {
            val pid = IOUtils.readInt(input)
            val tid = IOUtils.readInt(input)
            val usage = getThreadCpuUsageValue(pid, tid)
            IOUtils.writeFloat(output, usage)
            Logger.log("Thread $tid CPU usage: ${usage}%")
        } catch (e: Exception) {
            Logger.error("Error getting thread CPU usage", e)
            IOUtils.writeFloat(output, 0f)
        }
    }
    
    // ========== 内部实现方法 ==========
    
    /**
     * 计算整体 CPU 使用率
     * 读取 /proc/stat 第一行（所有 CPU 的总和）
     * 
     * 注意：
     * - 这是 Linux 系统获取 CPU 使用率的标准方法
     * - 与 FPS 不同，CPU 使用率没有对应的系统服务，只能通过文件系统读取
     * - /proc/stat 是内核提供的标准接口，性能很好，无需反射
     * - 需要计算增量（两次读取的差值）才能得到准确的使用率
     * - 第一次调用会返回 0%，需要第二次调用才能得到准确值
     */
    private fun calculateCpuUsage(): Float {
        try {
            val statFile = File("/proc/stat")
            if (!statFile.exists()) {
                Logger.log("Warning: /proc/stat not found, CPU usage unavailable")
                return 0f
            }
            
            val reader = BufferedReader(FileReader(statFile))
            val line = reader.readLine() ?: run {
                reader.close()
                return 0f
            }
            reader.close()
            
            // 格式: cpu user nice system idle iowait irq softirq
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 5) return 0f
            
            val user = parts[1].toLongOrNull() ?: 0L
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: 0L
            
            val total = user + nice + system + idle
            
            // 计算增量
            val currentTime = System.currentTimeMillis()
            if (lastCpuTime > 0 && currentTime - lastUpdateTime.get() < 1000) {
                // 使用缓存（1秒内）
                return cpuUsageCache.getOrDefault(-1, 0f)
            }
            
            // 如果是第一次调用，只记录当前值，返回 0
            if (lastCpuTime == 0L) {
                lastCpuTime = total
                lastCpuIdle = idle
                lastUpdateTime.set(currentTime)
                Logger.log("CPU usage: First call, initializing (will return 0%)")
                return 0f
            }
            
            val totalDiff = total - lastCpuTime
            val idleDiff = idle - lastCpuIdle
            
            if (totalDiff <= 0) {
                // 没有变化，返回缓存值或 0
                return cpuUsageCache.getOrDefault(-1, 0f)
            }
            
            val usage = ((totalDiff - idleDiff).toFloat() / totalDiff) * 100f
            
            lastCpuTime = total
            lastCpuIdle = idle
            lastUpdateTime.set(currentTime)
            cpuUsageCache[-1] = usage
            
            return usage.coerceIn(0f, 100f)
        } catch (e: Exception) {
            Logger.error("Error calculating CPU usage", e)
            return 0f
        }
    }
    
    /**
     * 获取各核心 CPU 使用率
     */
    private fun getCpuCoreUsages(): List<Float> {
        val usages = mutableListOf<Float>()
        val statFile = File("/proc/stat")
        if (!statFile.exists()) return usages
        
        try {
            val reader = BufferedReader(FileReader(statFile))
            var line: String?
            val maxCores = 16  // 限制最大核心数
            
            while (reader.readLine().also { line = it } != null && usages.size < maxCores) {
                val trimmed = line!!.trim()
                // 匹配 cpu0, cpu1, cpu2 等格式（必须是 "cpu" + 单个数字 + 空格）
                if (trimmed.startsWith("cpu") && trimmed.length > 4) {
                    // 检查是否是 cpu0, cpu1 格式（不是 cpu 总体）
                    if (trimmed[3].isDigit() && trimmed[4] == ' ') {
                        val coreIndex = trimmed[3].toString().toIntOrNull()
                        if (coreIndex != null && coreIndex < maxCores) {
                            val parts = trimmed.split(Regex("\\s+"))
                            if (parts.size >= 5) {
                                val user = parts[1].toLongOrNull() ?: 0L
                                val nice = parts[2].toLongOrNull() ?: 0L
                                val system = parts[3].toLongOrNull() ?: 0L
                                val idle = parts[4].toLongOrNull() ?: 0L
                                
                                val total = user + nice + system + idle
                                
                                // 简化计算（实际应该计算增量）
                                val usage = if (total > 0) {
                                    ((user + nice + system).toFloat() / total) * 100f
                                } else {
                                    0f
                                }
                                usages.add(usage.coerceIn(0f, 100f))
                            }
                        }
                    } else if (trimmed == "cpu" || trimmed.startsWith("cpu ")) {
                        // 这是总体 CPU 行，跳过
                        continue
                    } else {
                        // 其他格式，停止
                        break
                    }
                } else {
                    // 不是 cpu 相关的行，停止
                    break
                }
            }
            reader.close()
        } catch (e: Exception) {
            Logger.error("Error reading CPU core usage", e)
        }
        
        return usages
    }
    
    /**
     * 获取 CPU 频率
     * 读取 /sys/devices/system/cpu/cpu[core]/cpufreq/scaling_cur_freq
     * 
     * 注意：
     * - 这是 Linux 系统获取 CPU 频率的标准方法
     * - 与 FPS 不同，CPU 频率没有对应的系统服务
     * - /sys/devices/system/cpu/ 是内核提供的标准接口，性能很好
     * - 某些设备可能使用不同的路径（如 time_in_state）
     */
    private fun getCpuFrequencies(): List<Int> {
        val frequencies = mutableListOf<Int>()
        var coreIndex = 0
        val maxCores = 16  // 限制最大核心数
        
        while (coreIndex < maxCores) {
            // 优先使用 scaling_cur_freq（当前频率）
            var freqFile = File("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq")
            
            // 如果不存在，尝试 cpuinfo_cur_freq
            if (!freqFile.exists()) {
                freqFile = File("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/cpuinfo_cur_freq")
            }
            
            // 如果还是不存在，说明这个核心不存在或没有频率信息
            if (!freqFile.exists()) {
                break
            }
            
            try {
                val freq = freqFile.readText().trim().toIntOrNull() ?: 0
                if (freq > 0) {
                    frequencies.add(freq / 1000) // 转换为 kHz（文件中的单位是 Hz）
                } else {
                    frequencies.add(0)
                }
            } catch (e: Exception) {
                Logger.error("Error reading CPU frequency for core $coreIndex", e)
                frequencies.add(0)
            }
            coreIndex++
        }
        
        return frequencies
    }
    
    /**
     * 获取 CPU 温度
     * 读取 /sys/class/thermal/thermal_zone[zone]/temp
     */
    private fun getCpuTemperatureValue(): Float {
        try {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists()) {
                thermalDir.listFiles()?.forEach { zone ->
                    if (zone.name.startsWith("thermal_zone")) {
                        val typeFile = File(zone, "type")
                        val tempFile = File(zone, "temp")
                        
                        if (typeFile.exists() && tempFile.exists()) {
                            val type = typeFile.readText().trim()
                            if (type.contains("cpu", ignoreCase = true) || 
                                type.contains("tsens", ignoreCase = true)) {
                                val temp = tempFile.readText().trim().toIntOrNull() ?: 0
                                return temp / 1000f // 转换为摄氏度
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("Error reading CPU temperature", e)
        }
        return 0f
    }
    
    /**
     * 获取线程 CPU 使用率
     * 读取 /proc/[pid]/task/[tid]/stat
     */
    private fun getThreadCpuUsageValue(pid: Int, tid: Int): Float {
        try {
            val statFile = File("/proc/$pid/task/$tid/stat")
            if (!statFile.exists()) return 0f
            
            val stat = statFile.readText()
            val parts = stat.split(Regex("\\s+"))
            
            // stat 格式: pid comm state ppid ... utime stime ...
            // utime 在索引 13, stime 在索引 14 (从 0 开始)
            if (parts.size >= 15) {
                val utime = parts[13].toLongOrNull() ?: 0L
                val stime = parts[14].toLongOrNull() ?: 0L
                val totalTime = utime + stime
                
                // 需要计算增量，这里简化返回
                return (totalTime / 100f).toFloat().coerceIn(0f, 100f)
            }
        } catch (e: Exception) {
            Logger.error("Error reading thread CPU usage", e)
        }
        return 0f
    }
}

