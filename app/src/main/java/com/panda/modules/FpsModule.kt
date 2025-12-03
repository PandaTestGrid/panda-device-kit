package com.panda.modules

import android.annotation.SuppressLint
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import android.os.IBinder
import com.panda.mirror.ServiceManagerMirror

/**
 * FPS 数据采集模块
 * 提供帧率监控功能
 * 参考 PerfDog Console 实现
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class FpsModule {
    
    // FPS 监控
    private val isCollecting = AtomicBoolean(false)
    
    // FPS 缓存（用于 SurfaceFlinger 方法）
    private var surfaceFlingerFps = AtomicLong(0)
    private var lastSurfaceFlingerCheck = AtomicLong(0)
    private val SURFACE_FLINGER_CACHE_DURATION = 2000L // 缓存 2 秒
    
    // FPS 获取方法标记
    private var fpsMethod = "none"  // "choreographer", "reflection"
    
    private var fpsValue = 0
    private var frameCount = AtomicLong(0)
    private var lastFpsTime = System.currentTimeMillis()
    private var frameCallback: android.view.Choreographer.FrameCallback? = null
    
    /**
     * 命令 204: 获取 FPS（帧率）
     * 响应: FPS(int)
     */
    fun getFps(output: BufferedOutputStream) {
        try {
            val fps = getCurrentFps()
            IOUtils.writeInt(output, fps)
            output.flush()
            // 只在调试模式下输出详细日志，减少日志量
            // Logger.log("FPS: $fps")  // 已移除，避免日志过多
        } catch (e: Exception) {
            Logger.error("Error getting FPS", e)
            IOUtils.writeInt(output, 0)
            output.flush()
        }
    }
    
    /**
     * 命令 208: 开始性能分析（启动 FPS 监控等）
     * 请求: 监控间隔(int ms)
     */
    fun startProfiling(input: InputStream, output: BufferedOutputStream) {
        try {
            val interval = IOUtils.readInt(input)
            isCollecting.set(true)
            startFpsMonitoring(interval)
            IOUtils.writeInt(output, 1)
            Logger.log("Profiling started with interval: ${interval}ms")
        } catch (e: Exception) {
            Logger.error("Error starting profiling", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 209: 停止性能分析
     */
    fun stopProfiling(output: BufferedOutputStream) {
        try {
            isCollecting.set(false)
            stopFpsMonitoring()
            IOUtils.writeInt(output, 1)
            Logger.log("Profiling stopped")
        } catch (e: Exception) {
            Logger.error("Error stopping profiling", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    // ========== 内部实现方法 ==========
    
    /**
     * 获取当前 FPS
     * 优先使用 Choreographer，如果失败则通过反射 SurfaceFlinger API 获取系统级 FPS
     * 
     * 获取顺序：
     * 1. Choreographer（如果监控已启动且有帧回调，适用于应用内监控）
     * 2. 反射 SurfaceFlinger API（系统级 FPS，性能最好，需要系统权限，带缓存）
     */
    private fun getCurrentFps(): Int {
        // 如果 Choreographer 有数据，优先使用
        if (fpsValue > 0) {
            if (fpsMethod != "choreographer") {
                fpsMethod = "choreographer"
                Logger.log("Using Choreographer for FPS monitoring")
            }
            return fpsValue
        }
        
        // 如果监控已启动但还没有收到任何帧回调，使用反射 SurfaceFlinger API 获取系统级 FPS
        if (isCollecting.get() && fpsValue == 0 && frameCount.get() == 0L) {
            val currentTime = System.currentTimeMillis()
            val cachedFps = surfaceFlingerFps.get()
            val lastCheck = lastSurfaceFlingerCheck.get()
            
            // 如果缓存有效，直接返回
            if (cachedFps > 0 && (currentTime - lastCheck) < SURFACE_FLINGER_CACHE_DURATION) {
                return cachedFps.toInt()
            }
            
            // 通过反射 SurfaceFlinger API 获取系统级 FPS
            val reflectionFps = getFpsFromSurfaceFlingerReflection()
            if (reflectionFps > 0) {
                if (fpsMethod != "reflection") {
                    fpsMethod = "reflection"
                    Logger.log("Using SurfaceFlinger reflection for FPS (FPS: $reflectionFps)")
                }
                surfaceFlingerFps.set(reflectionFps.toLong())
                lastSurfaceFlingerCheck.set(currentTime)
                return reflectionFps
            }
        }
        
        return fpsValue
    }
    
    /**
     * 启动 FPS 监控
     * 注意: Choreographer 的 FrameCallback 只有在有 UI 渲染时才会被调用
     * 在后台服务中，如果没有活动的窗口，FPS 将始终为 0
     */
    private fun startFpsMonitoring(interval: Int) {
        stopFpsMonitoring()
        
        try {
            val choreographer = android.view.Choreographer.getInstance()
            lastFpsTime = System.currentTimeMillis()
            frameCount.set(0)
            fpsValue = 0
            
            frameCallback = object : android.view.Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    frameCount.incrementAndGet()
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFpsTime >= interval) {
                        val elapsed = currentTime - lastFpsTime
                        fpsValue = if (elapsed > 0) {
                            (frameCount.get() * 1000 / elapsed).toInt()
                        } else {
                            0
                        }
                        Logger.log("FPS calculated: ${fpsValue} (frames: ${frameCount.get()}, elapsed: ${elapsed}ms)")
                        frameCount.set(0)
                        lastFpsTime = currentTime
                    }
                    if (isCollecting.get()) {
                        choreographer.postFrameCallback(this)
                    }
                }
            }
            choreographer.postFrameCallback(frameCallback!!)
            Logger.log("FPS monitoring started (interval: ${interval}ms). Note: FPS will use SurfaceFlinger if no UI is rendering.")
            
            // 启动一个线程来检查是否有帧回调被触发
            Thread {
                Thread.sleep(interval.toLong() + 500) // 等待一个间隔 + 500ms
                if (frameCount.get() == 0L && fpsValue == 0) {
                    Logger.log("Choreographer not receiving callbacks, will use SurfaceFlinger reflection for FPS")
                }
            }.start()
        } catch (e: Exception) {
            Logger.error("Error starting FPS monitoring", e)
        }
    }
    
    /**
     * 停止 FPS 监控
     */
    private fun stopFpsMonitoring() {
        try {
            frameCallback?.let {
                val choreographer = android.view.Choreographer.getInstance()
                choreographer.removeFrameCallback(it)
            }
            frameCallback = null
            frameCount.set(0)
            fpsValue = 0
            Logger.log("FPS monitoring stopped")
        } catch (e: Exception) {
            Logger.error("Error stopping FPS monitoring", e)
        }
    }
    
    /**
     * 通过反射 SurfaceFlinger API 获取系统级 FPS
     * 需要系统权限
     * 
     * 方法：通过 ServiceManager 获取 SurfaceFlinger 服务，调用相关方法获取刷新率
     * 
     * 优势：
     * - 性能最好（直接 API 调用，无需启动进程）
     * - 实时性好（直接获取系统服务数据）
     * - 资源占用少（不需要解析文本输出）
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun getFpsFromSurfaceFlingerReflection(): Int {
        try {
            // 获取 SurfaceFlinger 服务
            val service = ServiceManagerMirror.getService.call("SurfaceFlinger") as? IBinder
            if (service == null) {
                Logger.log("SurfaceFlinger service not available")
                return 0
            }
            
            // 尝试通过 dump 方法获取信息
            val pipe = android.os.ParcelFileDescriptor.createPipe()
            try {
                // 调用 dump 方法
                try {
                    val dumpMethod = service.javaClass.getMethod(
                        "dump", 
                        java.io.FileDescriptor::class.java, 
                        Array<String>::class.java
                    )
                    dumpMethod.invoke(service, pipe[1].fileDescriptor, arrayOf<String>())
                } catch (e: Exception) {
                    // 尝试另一种方法签名
                    try {
                        val dumpMethod = service.javaClass.getMethod(
                            "dump", 
                            java.io.FileDescriptor::class.java
                        )
                        dumpMethod.invoke(service, pipe[1].fileDescriptor)
                    } catch (e2: Exception) {
                        Logger.log("SurfaceFlinger dump method not available: ${e2.message}")
                        pipe[0].close()
                        pipe[1].close()
                        return 0
                    }
                }
                
                // 关闭写入端，这样读取端在读取完所有数据后会立即返回 EOF，避免阻塞
                try {
                    pipe[1].close()
                } catch (e: Exception) {
                    // Ignore
                }
                
                // 读取输出（限制读取行数和时间，避免阻塞）
                val reader = BufferedReader(
                    InputStreamReader(
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
                    )
                )
                
                // 解析输出，查找刷新率信息
                var line: String?
                var foundFps = 0
                var lineCount = 0
                val maxLines = 1000  // 限制最大读取行数，避免阻塞
                val startTime = System.currentTimeMillis()
                val timeout = 2000L  // 2秒超时
                
                while (lineCount < maxLines && (System.currentTimeMillis() - startTime) < timeout) {
                    try {
                        line = reader.readLine()
                        if (line == null) {
                            break  // EOF
                        }
                    } catch (e: Exception) {
                        Logger.log("Error reading SurfaceFlinger output: ${e.message}")
                        break
                    }
                    
                    lineCount++
                    val trimmed = line!!.trim()
                    
                    // 查找刷新率相关信息
                    if (trimmed.contains("refresh-rate", ignoreCase = true) || 
                        trimmed.contains("Refresh rate", ignoreCase = true)) {
                        val regex = Regex("(?:refresh-rate|Refresh rate)[:\\s]+(\\d+(?:\\.\\d+)?)")
                        val match = regex.find(trimmed)
                        if (match != null) {
                            val fps = match.groupValues[1].toFloatOrNull()?.toInt() ?: 0
                            if (fps > 0 && fps <= 240) {
                                foundFps = fps
                                break
                            }
                        }
                    }
                    
                    // 查找 fps 信息
                    if (foundFps == 0 && trimmed.contains("fps", ignoreCase = true)) {
                        val regex = Regex("(\\d+(?:\\.\\d+)?)\\s*fps", RegexOption.IGNORE_CASE)
                        val match = regex.find(trimmed)
                        if (match != null) {
                            val fps = match.groupValues[1].toFloatOrNull()?.toInt() ?: 0
                            if (fps > 0 && fps <= 240) {
                                foundFps = fps
                                break
                            }
                        }
                    }
                }
                
                try {
                    reader.close()
                } catch (e: Exception) {
                    // Ignore
                }
                
                if (foundFps > 0) {
                    return foundFps
                } else {
                    Logger.log("FPS not found in SurfaceFlinger dump output")
                }
            } finally {
                try {
                    pipe[0].close()
                    pipe[1].close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting FPS from SurfaceFlinger reflection", e)
        }
        return 0
    }
}

