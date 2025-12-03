package com.panda.utils

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

/**
 * 屏幕截图辅助类
 * 使用 ScreenCapture API 实现高性能截图
 * 参考 PerfDog Console 实现
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object ScreenCaptureHelper {
    
    // 初始化标志
    private var isDisplayControlInitialized = false
    private var isScreenCaptureInitialized = false
    
    // DisplayControl 相关
    private var getPhysicalDisplayTokenMethod: Method? = null
    
    // ScreenCapture API 相关
    private var usePublicApi = false
    private var useInternalApi = false
    
    // 公开 API (Android 12+)
    private var publicBuilderConstructor: java.lang.reflect.Constructor<*>? = null
    private var publicBuilderSetSizeMethod: Method? = null
    private var publicBuilderBuildMethod: Method? = null
    private var publicCaptureDisplayMethod: Method? = null
    private var publicScreenshotGetBufferMethod: Method? = null
    
    // 内部 API (降级方案)
    private var internalBuilderConstructor: java.lang.reflect.Constructor<*>? = null
    private var internalBuilderSetSizeMethod: Method? = null
    private var internalBuilderBuildMethod: Method? = null
    private var internalCaptureDisplayMethod: Method? = null
    private var internalScreenshotGetBufferMethod: Method? = null
    
    /**
     * 初始化 DisplayControl
     */
    private fun initializeDisplayControl() {
        if (isDisplayControlInitialized) return
        
        try {
            // 创建系统类加载器
            val classLoaderFactoryClass = Class.forName("com.android.internal.os.ClassLoaderFactory")
            val createClassLoaderMethod = classLoaderFactoryClass.getDeclaredMethod(
                "createClassLoader",
                String::class.java,
                String::class.java,
                String::class.java,
                ClassLoader::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java
            )
            createClassLoaderMethod.isAccessible = true
            
            val systemServerClassPath = System.getenv("SYSTEMSERVERCLASSPATH") ?: ""
            val classLoader = createClassLoaderMethod.invoke(
                null,
                systemServerClassPath,
                null,
                null,
                ClassLoader.getSystemClassLoader(),
                0,
                true,
                null
            ) as ClassLoader
            
            // 加载 DisplayControl 类
            val displayControlClass = classLoader.loadClass("com.android.server.display.DisplayControl")
            getPhysicalDisplayTokenMethod = displayControlClass.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
            getPhysicalDisplayTokenMethod?.isAccessible = true
            
            // 加载系统库
            val loadLibrary0Method = Runtime::class.java.getDeclaredMethod(
                "loadLibrary0",
                Class::class.java,
                String::class.java
            )
            loadLibrary0Method.isAccessible = true
            loadLibrary0Method.invoke(Runtime.getRuntime(), displayControlClass, "android_servers")
            
            isDisplayControlInitialized = true
            Logger.log("DisplayControl initialized")
        } catch (e: Exception) {
            Logger.error("Failed to initialize DisplayControl", e)
        }
    }
    
    /**
     * 初始化 ScreenCapture API
     */
    private fun initializeScreenCapture() {
        if (isScreenCaptureInitialized) return
        
        try {
            // 尝试使用公开 API (Android 12+)
            try {
                val displayCaptureArgsClass = Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs")
                val builderClass = Class.forName("android.window.ScreenCapture\$DisplayCaptureArgs\$Builder")
                val screenshotClass = Class.forName("android.window.ScreenCapture\$ScreenshotHardwareBuffer")
                val screenCaptureClass = Class.forName("android.window.ScreenCapture")
                
                publicBuilderConstructor = builderClass.getConstructor(IBinder::class.java)
                publicBuilderSetSizeMethod = builderClass.getMethod("setSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                publicBuilderBuildMethod = builderClass.getMethod("build")
                publicCaptureDisplayMethod = screenCaptureClass.getMethod("captureDisplay", displayCaptureArgsClass)
                publicScreenshotGetBufferMethod = screenshotClass.getMethod("getHardwareBuffer")
                
                usePublicApi = true
                isScreenCaptureInitialized = true
                Logger.log("ScreenCapture public API initialized")
            } catch (e: ClassNotFoundException) {
                // 降级到内部 API
                Logger.log("ScreenCapture public API not available, trying ScreenCaptureInternal")
                try {
                    val displayCaptureArgsClass = Class.forName("android.window.ScreenCaptureInternal\$DisplayCaptureArgs")
                    val builderClass = Class.forName("android.window.ScreenCaptureInternal\$DisplayCaptureArgs\$Builder")
                    val screenshotClass = Class.forName("android.window.ScreenCaptureInternal\$ScreenshotHardwareBuffer")
                    val screenCaptureClass = Class.forName("android.window.ScreenCaptureInternal")
                    
                    internalBuilderConstructor = builderClass.getConstructor(IBinder::class.java)
                    internalBuilderSetSizeMethod = builderClass.getMethod("setSize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    internalBuilderBuildMethod = builderClass.getMethod("build")
                    internalCaptureDisplayMethod = screenCaptureClass.getMethod("captureDisplay", displayCaptureArgsClass)
                    internalScreenshotGetBufferMethod = screenshotClass.getMethod("getHardwareBuffer")
                    
                    useInternalApi = true
                    isScreenCaptureInitialized = true
                    Logger.log("ScreenCaptureInternal API initialized")
                } catch (e2: Exception) {
                    Logger.error("ScreenCaptureInternal API not available", e2)
                }
            }
        } catch (e: Exception) {
            Logger.error("Failed to initialize ScreenCapture", e)
        }
    }
    
    /**
     * 获取 HardwareBuffer
     */
    @Suppress("UNCHECKED_CAST")
    private fun getHardwareBuffer(displayToken: IBinder, width: Int, height: Int): Any? {
        if (!isScreenCaptureInitialized) {
            initializeScreenCapture()
        }
        
        if (!usePublicApi && !useInternalApi) {
            Logger.error("ScreenCapture API not available", null)
            return null
        }
        
        try {
            if (usePublicApi) {
                // 使用公开 API
                val builder = publicBuilderConstructor!!.newInstance(displayToken)
                publicBuilderSetSizeMethod!!.invoke(builder, width, height)
                val args = publicBuilderBuildMethod!!.invoke(builder)
                val screenshot = publicCaptureDisplayMethod!!.invoke(null, args)
                if (screenshot == null) {
                    return null
                }
                return publicScreenshotGetBufferMethod!!.invoke(screenshot)
            } else {
                // 使用内部 API
                val builder = internalBuilderConstructor!!.newInstance(displayToken)
                internalBuilderSetSizeMethod!!.invoke(builder, width, height)
                val args = internalBuilderBuildMethod!!.invoke(builder)
                val screenshot = internalCaptureDisplayMethod!!.invoke(null, args)
                if (screenshot == null) {
                    return null
                }
                return internalScreenshotGetBufferMethod!!.invoke(screenshot)
            }
        } catch (e: Exception) {
            Logger.error("Error getting HardwareBuffer", e)
            return null
        }
    }
    
    /**
     * 将 HardwareBuffer 转换为 Bitmap
     */
    private fun hardwareBufferToBitmap(buffer: Any): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 wrapHardwareBuffer
                val wrapMethod = Bitmap::class.java.getMethod("wrapHardwareBuffer", buffer.javaClass, android.graphics.ColorSpace::class.java)
                val bitmap = wrapMethod.invoke(null, buffer, null) as? Bitmap
                if (bitmap != null) {
                    // 创建可变的副本，因为 wrapHardwareBuffer 返回的可能是只读的
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    bitmap.recycle()
                    return mutableBitmap
                }
                null
            } else {
                // Android 9 及以下，HardwareBuffer 可能不可用
                Logger.error("HardwareBuffer not supported on Android 9 and below", null)
                null
            }
        } catch (e: Exception) {
            Logger.error("Error converting HardwareBuffer to Bitmap", e)
            null
        }
    }
    
    /**
     * 计算截图尺寸（考虑旋转）
     */
    private fun calculateScreenshotSize(
        logicalWidth: Int,
        logicalHeight: Int,
        rotation: Int,
        maxSize: Int = 0
    ): Pair<Int, Int> {
        var width = logicalWidth
        var height = logicalHeight
        
        // 根据旋转调整尺寸
        when (rotation) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                width = logicalHeight
                height = logicalWidth
            }
        }
        
        // 如果指定了最大尺寸，进行缩放
        if (maxSize > 0 && (width > maxSize || height > maxSize)) {
            val ratio = if (width > height) {
                maxSize.toFloat() / width
            } else {
                maxSize.toFloat() / height
            }
            width = (width * ratio).toInt()
            height = (height * ratio).toInt()
        }
        
        return Pair(width, height)
    }
    
    /**
     * 获取屏幕截图（PNG 格式字节数组）
     * @param context Android Context
     * @param maxSize 最大尺寸（0 表示不缩放）
     * @return PNG 格式的字节数组，失败返回 null
     */
    fun captureScreen(context: android.content.Context, maxSize: Int = 0): ByteArray? {
        try {
            // 初始化 DisplayControl
            if (!isDisplayControlInitialized) {
                initializeDisplayControl()
            }
            
            if (getPhysicalDisplayTokenMethod == null) {
                Logger.error("DisplayControl not initialized", null)
                return null
            }
            
            // 获取 DisplayManager
            val displayManager = context.getSystemService(android.content.Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(0)
            
            if (display == null) {
                Logger.error("getDisplay returned null", null)
                return null
            }
            
            // 获取 DisplayInfo（使用反射）
            val displayInfoClass = Class.forName("android.view.DisplayInfo")
            val displayInfo = displayInfoClass.newInstance()
            val getDisplayInfoMethod = Display::class.java.getMethod("getDisplayInfo", displayInfoClass)
            val getDisplayInfoResult = getDisplayInfoMethod.invoke(display, displayInfo) as? Boolean
            
            if (getDisplayInfoResult != true) {
                Logger.error("getDisplayInfo returned false", null)
                return null
            }
            
            // 获取 DisplayInfo 字段
            val logicalWidthField = displayInfoClass.getDeclaredField("logicalWidth")
            logicalWidthField.isAccessible = true
            val logicalHeightField = displayInfoClass.getDeclaredField("logicalHeight")
            logicalHeightField.isAccessible = true
            val rotationField = displayInfoClass.getDeclaredField("rotation")
            rotationField.isAccessible = true
            val uniqueIdField = displayInfoClass.getDeclaredField("uniqueId")
            uniqueIdField.isAccessible = true
            
            val logicalWidth = logicalWidthField.getInt(displayInfo)
            val logicalHeight = logicalHeightField.getInt(displayInfo)
            val rotation = rotationField.getInt(displayInfo)
            val uniqueId = uniqueIdField.get(displayInfo) as? String
            
            // 解析 uniqueId 获取物理显示 ID
            if (uniqueId == null || !uniqueId.contains(":")) {
                Logger.error("display uniqueId invalid: $uniqueId", null)
                return null
            }
            
            val physicalDisplayId = try {
                uniqueId.split(":")[1].toLong()
            } catch (e: Exception) {
                Logger.error("Failed to parse physical display ID", e)
                return null
            }
            
            // 获取 Display Token
            val displayToken = getPhysicalDisplayTokenMethod!!.invoke(null, physicalDisplayId) as? IBinder
            if (displayToken == null) {
                Logger.error("getDisplayToken returned null", null)
                return null
            }
            
            // 计算截图尺寸
            val (width, height) = calculateScreenshotSize(
                logicalWidth,
                logicalHeight,
                rotation,
                maxSize
            )
            
            // 获取 HardwareBuffer
            val hardwareBuffer = getHardwareBuffer(displayToken, width, height)
            if (hardwareBuffer == null) {
                Logger.error("getHardwareBuffer returned null", null)
                return null
            }
            
            try {
                // 转换为 Bitmap
                val bitmap = hardwareBufferToBitmap(hardwareBuffer)
                if (bitmap == null) {
                    Logger.error("Failed to convert HardwareBuffer to Bitmap", null)
                    return null
                }
                
                try {
                    // 压缩为 PNG
                    val byteStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
                    return byteStream.toByteArray()
                } finally {
                    bitmap.recycle()
                }
            } finally {
                // 关闭 HardwareBuffer
                try {
                    val closeMethod = hardwareBuffer.javaClass.getMethod("close")
                    closeMethod.invoke(hardwareBuffer)
                } catch (e: Exception) {
                    Logger.error("Error closing HardwareBuffer", e)
                }
            }
        } catch (e: Exception) {
            Logger.error("Error capturing screen", e)
            return null
        }
    }
}

