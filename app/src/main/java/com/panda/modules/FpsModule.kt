package com.panda.modules

import android.annotation.SuppressLint
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    private val REFRESH_RATE_REGEX = Regex("(?:refresh-rate|Refresh rate)[:\\s]+(\\d+(?:\\.\\d+)?)")
    private val FPS_VALUE_REGEX = Regex("(\\d+(?:\\.\\d+)?)\\s*fps", RegexOption.IGNORE_CASE)
    
    // FPS 获取方法标记
    private var fpsMethod = "none"  // "choreographer", "reflection", "dumpsys"
    
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
     * 获取当前 FPS，按照新的优先级顺序：
     * 1. Choreographer
     * 2. SurfaceFlinger Reflection
     * 3. dumpsys SurfaceFlinger
     */
    private fun getCurrentFps(): Int {
        // 1) Choreographer
        if (fpsValue > 0) {
            return notifyFpsMethod("choreographer", fpsValue)
        }

        // 2) SurfaceFlinger Reflection（带缓存 + 超时保护）
        val reflectionFps = getFpsFromSurfaceFlingerReflectionSafe()
        if (reflectionFps > 0) {
            surfaceFlingerFps.set(reflectionFps.toLong())
            lastSurfaceFlingerCheck.set(System.currentTimeMillis())
            return notifyFpsMethod("reflection", reflectionFps)
        }

        // 3) dumpsys SurfaceFlinger 作为降级方案
        val dumpsysFps = getFpsFromSurfaceFlingerDumpsys()
        if (dumpsysFps > 0) {
            surfaceFlingerFps.set(dumpsysFps.toLong())
            lastSurfaceFlingerCheck.set(System.currentTimeMillis())
            return notifyFpsMethod("dumpsys", dumpsysFps)
        }

        return notifyFpsMethod("none", 0)
    }

    /**
     * 记录当前使用的方法，仅在方法切换时输出日志
     */
    private fun notifyFpsMethod(method: String, fps: Int): Int {
        if (fpsMethod != method) {
            fpsMethod = method
            val readable = when (method) {
                "choreographer" -> "Choreographer callback"
                "reflection" -> "SurfaceFlinger reflection"
                "dumpsys" -> "dumpsys SurfaceFlinger"
                else -> "no available method"
            }
            Logger.log("Using $readable for FPS (method=$method, FPS=$fps)")
        }
        return fps
    }

    /**
     * 带缓存 + 超时保护的 SurfaceFlinger 反射方法
     */
    private fun getFpsFromSurfaceFlingerReflectionSafe(): Int {
        val currentTime = System.currentTimeMillis()
        val cachedFps = surfaceFlingerFps.get()
        val lastCheck = lastSurfaceFlingerCheck.get()

        if (cachedFps > 0 && (currentTime - lastCheck) < SURFACE_FLINGER_CACHE_DURATION) {
            return cachedFps.toInt()
        }

        val future = FutureTask {
            getFpsFromSurfaceFlingerReflection()
        }
        val thread = Thread(future, "fps-reflection")
        thread.start()

        return try {
            future.get(1500, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Logger.log("SurfaceFlinger reflection timeout (1.5s)")
            future.cancel(true)
            thread.interrupt()
            0
        } catch (e: Exception) {
            Logger.error("Error executing SurfaceFlinger reflection", e)
            future.cancel(true)
            thread.interrupt()
            0
        }
    }

    /**
     * dumpsys SurfaceFlinger 作为降级方案
     */
    private fun getFpsFromSurfaceFlingerDumpsys(): Int {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys SurfaceFlinger --display-id 0"))
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                Logger.log("dumpsys SurfaceFlinger timeout (1.5s)")
                process.destroy()
                return 0
            }

            var foundFps = 0
            val reader = process.inputStream.bufferedReader()
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    val parsed = parseFpsFromLine(trimmed)
                    if (parsed != null) {
                        foundFps = parsed
                        break
                    }
                }
            } finally {
                reader.close()
            }
            process.errorStream.close()
            process.destroy()

            if (foundFps > 0) {
                Logger.log("Using dumpsys SurfaceFlinger for FPS (method=dumpsys, FPS=$foundFps)")
            } else {
                Logger.log("dumpsys SurfaceFlinger output did not contain FPS")
            }
            foundFps
        } catch (e: Exception) {
            Logger.error("Error getting FPS via dumpsys SurfaceFlinger", e)
            0
        } finally {
            process?.destroy()
        }
    }

    /**
     * 从文本行中解析 FPS 或 refresh-rate
     */
    private fun parseFpsFromLine(line: String): Int? {
        REFRESH_RATE_REGEX.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.toInt()?.let {
            if (it in 1..240) return it
        }
        FPS_VALUE_REGEX.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.toInt()?.let {
            if (it in 1..240) return it
        }
        return null
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
            val readFd = pipe[0]
            val writeFd = pipe[1]
            val foundFps = AtomicInteger(0)
            val readerThread = Thread({
                var reader: BufferedReader? = null
                try {
                    reader = BufferedReader(
                        InputStreamReader(
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(readFd)
                        )
                    )
                    var line: String?
                    var lineCount = 0
                    val maxLines = 1000
                    val startTime = System.currentTimeMillis()
                    val timeout = 2000L
                    while (lineCount < maxLines && (System.currentTimeMillis() - startTime) < timeout) {
                        line = reader.readLine() ?: break
                        lineCount++
                        val parsed = parseFpsFromLine(line!!.trim())
                        if (parsed != null) {
                            foundFps.set(parsed)
                            break
                        }
                    }
                } catch (e: Exception) {
                    Logger.log("Error reading SurfaceFlinger output: ${e.message}")
                } finally {
                    try {
                        reader?.close()
                    } catch (_: Exception) {
                    }
                }
            }, "fps-sf-reader")

            try {
                readerThread.start()
                try {
                    val dumpMethod = service.javaClass.getMethod(
                        "dump",
                        java.io.FileDescriptor::class.java,
                        Array<String>::class.java
                    )
                    dumpMethod.invoke(service, writeFd.fileDescriptor, arrayOf<String>())
                } catch (e: Exception) {
                    try {
                        val dumpMethod = service.javaClass.getMethod(
                            "dump",
                            java.io.FileDescriptor::class.java
                        )
                        dumpMethod.invoke(service, writeFd.fileDescriptor)
                    } catch (e2: Exception) {
                        Logger.log("SurfaceFlinger dump method not available: ${e2.message}")
                        readerThread.interrupt()
                        try {
                            readerThread.join(100)
                        } catch (_: InterruptedException) {
                        }
                        return 0
                    }
                } finally {
                    try {
                        writeFd.close()
                    } catch (_: Exception) {
                    }
                }

                readerThread.join(2200)
                if (readerThread.isAlive) {
                    readerThread.interrupt()
                }

                val fps = foundFps.get()
                if (fps > 0) {
                    Logger.log("SurfaceFlinger reflection FPS=$fps")
                    return fps
                } else {
                    Logger.log("FPS not found in SurfaceFlinger dump output")
                }
            } finally {
                try {
                    readFd.close()
                } catch (_: Exception) {
                }
                try {
                    writeFd.close()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Logger.error("Error getting FPS from SurfaceFlinger reflection", e)
        }
        return 0
    }
}

