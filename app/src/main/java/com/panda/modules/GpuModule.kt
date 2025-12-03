package com.panda.modules

import android.annotation.SuppressLint
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.File

/**
 * GPU 数据采集模块
 * 提供 GPU 使用率和频率功能
 * 参考 PerfDog Console 实现
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class GpuModule {
    
    /**
     * 命令 203: 获取 GPU 使用率和频率
     * 响应: 使用率(float 0-100), 频率(int kHz)
     */
    fun getGpuUsage(output: BufferedOutputStream) {
        try {
            val usage = getGpuUsageValue()
            val freq = getGpuFrequency()
            IOUtils.writeFloat(output, usage)
            IOUtils.writeInt(output, freq)
            Logger.log("GPU usage: ${usage}%, freq: ${freq} kHz")
        } catch (e: Exception) {
            Logger.error("Error getting GPU usage", e)
            IOUtils.writeFloat(output, 0f)
            IOUtils.writeInt(output, 0)
        }
    }
    
    // ========== 内部实现方法 ==========
    
    /**
     * 获取 GPU 使用率
     * 支持多种 GPU 厂商：
     * - Qualcomm Adreno: /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
     * - ARM Mali: /sys/class/misc/mali0/device/utilization
     * - PowerVR: /sys/devices/platform/pvrsrvkm.0/sgx_dvfs_utilization
     * 
     * 注意：不同设备的路径可能不同，这里实现常见路径
     */
    private fun getGpuUsageValue(): Float {
        try {
            // 1. Qualcomm Adreno (kgsl)
            val kgslDir = File("/sys/class/kgsl/kgsl-3d0")
            if (kgslDir.exists()) {
                val usageFile = File(kgslDir, "gpu_busy_percentage")
                if (usageFile.exists()) {
                    val usage = usageFile.readText().trim().toFloatOrNull() ?: 0f
                    if (usage > 0) {
                        Logger.log("GPU usage from kgsl: $usage%")
                        return usage
                    }
                }
            }
            
            // 2. ARM Mali
            val maliFile = File("/sys/class/misc/mali0/device/utilization")
            if (maliFile.exists()) {
                val usage = maliFile.readText().trim().toFloatOrNull() ?: 0f
                if (usage > 0) {
                    Logger.log("GPU usage from Mali: $usage%")
                    return usage
                }
            }
            
            // 3. PowerVR
            val pvrFile = File("/sys/devices/platform/pvrsrvkm.0/sgx_dvfs_utilization")
            if (pvrFile.exists()) {
                val usage = pvrFile.readText().trim().toFloatOrNull() ?: 0f
                if (usage > 0) {
                    Logger.log("GPU usage from PowerVR: $usage%")
                    return usage
                }
            }
            
            // 4. 尝试其他可能的路径
            val otherPaths = listOf(
                "/sys/class/kgsl/kgsl-3d0/gpu_busy",
                "/sys/devices/platform/*/gpu/utilization",
            )
            
            // 如果都不存在，返回 0（某些设备可能不支持）
            Logger.log("GPU usage not available (device may not support)")
        } catch (e: Exception) {
            Logger.error("Error getting GPU usage", e)
        }
        return 0f
    }
    
    /**
     * 获取 GPU 频率
     * 支持多种 GPU 厂商：
     * - Qualcomm Adreno: /sys/class/kgsl/kgsl-3d0/gpuclk
     * - ARM Mali: /sys/class/misc/mali0/device/clock
     * - PowerVR: /sys/devices/platform/pvrsrvkm.0/sgx_dvfs_clock
     * 
     * 注意：返回值为 kHz
     */
    private fun getGpuFrequency(): Int {
        try {
            // 1. Qualcomm Adreno (kgsl) - 单位已经是 Hz，需要转换为 kHz
            val kgslFreqFile = File("/sys/class/kgsl/kgsl-3d0/gpuclk")
            if (kgslFreqFile.exists()) {
                val freqHz = kgslFreqFile.readText().trim().toLongOrNull() ?: 0L
                if (freqHz > 0) {
                    val freqKHz = (freqHz / 1000).toInt()
                    Logger.log("GPU frequency from kgsl: $freqKHz kHz")
                    return freqKHz
                }
            }
            
            // 2. ARM Mali - 单位可能是 MHz，需要转换为 kHz
            val maliFreqFile = File("/sys/class/misc/mali0/device/clock")
            if (maliFreqFile.exists()) {
                val freq = maliFreqFile.readText().trim().toLongOrNull() ?: 0L
                if (freq > 0) {
                    // Mali 通常以 MHz 为单位，转换为 kHz
                    val freqKHz = if (freq < 10000) freq * 1000 else freq
                    Logger.log("GPU frequency from Mali: $freqKHz kHz")
                    return freqKHz.toInt()
                }
            }
            
            // 3. PowerVR
            val pvrFreqFile = File("/sys/devices/platform/pvrsrvkm.0/sgx_dvfs_clock")
            if (pvrFreqFile.exists()) {
                val freqHz = pvrFreqFile.readText().trim().toLongOrNull() ?: 0L
                if (freqHz > 0) {
                    val freqKHz = (freqHz / 1000).toInt()
                    Logger.log("GPU frequency from PowerVR: $freqKHz kHz")
                    return freqKHz
                }
            }
            
            // 4. 尝试其他可能的路径
            val otherPaths = listOf(
                "/sys/class/kgsl/kgsl-3d0/devfreq/kgsl-3d0/cur_freq",
                "/sys/devices/platform/*/gpu/clock",
            )
            
            // 如果都不存在，返回 0（某些设备可能不支持）
            Logger.log("GPU frequency not available (device may not support)")
        } catch (e: Exception) {
            Logger.error("Error getting GPU frequency", e)
        }
        return 0
    }
}

