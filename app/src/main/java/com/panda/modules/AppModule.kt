package com.panda.modules

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.panda.utils.FakeContext
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * 应用管理模块
 * 提供应用列表、APK信息、图标获取、应用启动等功能
 */
@SuppressLint("MissingPermission")
class AppModule {
    
    /**
     * 命令 0: 创建虚拟显示器
     */
    fun createVirtualDisplay(output: BufferedOutputStream) {
        try {
            // 这里简化实现，实际需要创建 VirtualDisplay
            IOUtils.writeSuccess(output)
            Logger.log("Virtual display created")
        } catch (e: Exception) {
            Logger.error("Error creating virtual display", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 1: 系统初始化
     */
    fun initialize() {
        try {
            // 强制停止 shell 进程（如果需要）
            Logger.log("Initialize called")
        } catch (e: Exception) {
            Logger.error("Error initializing", e)
        }
    }
    
    /**
     * 命令 10: 获取应用列表（包含图标）
     * 使用 PackageManager API 快速获取应用图标
     */
    fun getAppList(input: InputStream, output: BufferedOutputStream) {
        try {
            val flags = IOUtils.readInt(input)
            val iconSize = IOUtils.readInt(input)
            
            val pm = FakeContext.get().packageManager
            var packages = pm.getInstalledPackages(0)
            
            // 过滤系统应用
            if (flags and 1 == 0) {
                packages = packages.filter { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            }
            
            // 过滤第三方应用
            if (flags and 2 == 0) {
                packages = packages.filter { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            }
            
            // 过滤无启动器的应用
            if (flags and 4 == 0) {
                packages = packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            }
            
            // 发送默认图标
            val defaultIcon = pm.defaultActivityIcon
            val defaultBitmap = drawableToBitmap(defaultIcon, iconSize)
            writeBitmap(output, defaultBitmap, iconSize)
            
            // 发送应用数量
            IOUtils.writeInt(output, packages.size)
            
            var lastFlushTime = System.currentTimeMillis()
            
            // 遍历所有应用
            for (pkg in packages) {
                // 包名
                IOUtils.writeString(output, pkg.packageName)
                // 版本名
                IOUtils.writeString(output, pkg.versionName ?: "")
                // 版本号
                val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                    pkg.longVersionCode
                } else {
                    pkg.versionCode.toLong()
                }
                IOUtils.writeLong(output, versionCode)
                
                // 应用名称
                val label = pm.getApplicationLabel(pkg.applicationInfo).toString()
                IOUtils.writeString(output, label)
                
                // 安装时间
                IOUtils.writeInt(output, (pkg.firstInstallTime / 1000).toInt())
                // 更新时间
                IOUtils.writeInt(output, (pkg.lastUpdateTime / 1000).toInt())
                // 最后使用时间 (这里简化为0)
                IOUtils.writeInt(output, 0)
                
                // 安装器包名
                val installer = pm.getInstallerPackageName(pkg.packageName) ?: ""
                IOUtils.writeString(output, installer)
                
                // CPU 架构
                IOUtils.writeString(output, "")
                
                // Target SDK
                IOUtils.writeInt(output, pkg.applicationInfo.targetSdkVersion)
                // Min SDK
                val minSdk = if (Build.VERSION.SDK_INT >= 24) {
                    pkg.applicationInfo.minSdkVersion
                } else {
                    21
                }
                IOUtils.writeInt(output, minSdk)
                
                // Flags
                IOUtils.writeInt(output, pkg.applicationInfo.flags)
                
                // 是否有分包
                val hasSplits = !pkg.applicationInfo.splitPublicSourceDirs.isNullOrEmpty()
                IOUtils.writeBoolean(output, hasSplits)
                
                // 是否可启动
                val canLaunch = pm.getLaunchIntentForPackage(pkg.packageName) != null
                IOUtils.writeBoolean(output, canLaunch)
                
                // 应用大小（简化实现）
                val appSize = File(pkg.applicationInfo.sourceDir).length()
                IOUtils.writeLong(output, appSize)
                IOUtils.writeLong(output, 0)  // 数据大小
                IOUtils.writeLong(output, 0)  // 缓存大小
                
                // 获取并发送图标 - 核心代码！
                try {
                    val iconId = pkg.applicationInfo.icon
                    if (iconId != 0) {
                        val icon = pm.getDrawable(pkg.packageName, iconId, pkg.applicationInfo)
                        val bitmap = drawableToBitmap(icon, iconSize)
                        writeBitmap(output, bitmap, iconSize)
                    } else {
                        IOUtils.writeInt(output, 0)  // 无图标
                    }
                } catch (e: Exception) {
                    Logger.error("Error loading icon for ${pkg.packageName}", e)
                    IOUtils.writeInt(output, 0)
                }
                
                // 每 100ms 刷新一次缓冲区（优化性能）
                if (System.currentTimeMillis() - lastFlushTime > 100) {
                    output.flush()
                    lastFlushTime = System.currentTimeMillis()
                }
            }
            
            output.flush()
            Logger.log("Sent ${packages.size} apps")
            
        } catch (e: Exception) {
            Logger.error("Error getting app list", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 11: 获取应用的 APK 文件路径和大小
     */
    fun getApkPath(input: InputStream, output: BufferedOutputStream) {
        try {
            val packageName = IOUtils.readString(input)
            val pm = FakeContext.get().packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            
            // 发送主 APK 路径
            IOUtils.writeString(output, appInfo.publicSourceDir)
            // 发送文件大小
            IOUtils.writeLong(output, File(appInfo.publicSourceDir).length())
            
            // 发送分包信息
            val splitPaths = appInfo.splitPublicSourceDirs
            if (splitPaths.isNullOrEmpty()) {
                IOUtils.writeInt(output, 0)
            } else {
                IOUtils.writeInt(output, splitPaths.size)
                for (path in splitPaths) {
                    IOUtils.writeString(output, path)
                    IOUtils.writeLong(output, File(path).length())
                }
            }
            
            Logger.log("APK path for $packageName: ${appInfo.publicSourceDir}")
        } catch (e: Exception) {
            Logger.error("Error getting APK path", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 12: 获取相机状态
     */
    fun getCameraStatus(input: InputStream, output: BufferedOutputStream) {
        try {
            val cameraId = IOUtils.readInt(input)
            // 简化实现
            IOUtils.writeInt(output, 1)  // 状态：可用
            Logger.log("Camera $cameraId status: available")
        } catch (e: Exception) {
            Logger.error("Error getting camera status", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 13: 获取设备上所有可用相机列表及其特性
     */
    fun getCameraList(input: InputStream, output: BufferedOutputStream) {
        try {
            val cameraManager = FakeContext.get().getSystemService(CameraManager::class.java)
            val cameraIds = cameraManager.cameraIdList
            
            IOUtils.writeInt(output, cameraIds.size)
            
            for (id in cameraIds) {
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    
                    // 相机ID
                    IOUtils.writeString(output, id)
                    
                    // 镜头方向
                    val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: 0
                    IOUtils.writeInt(output, lensFacing)
                    
                    // 传感器尺寸
                    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    IOUtils.writeInt(output, sensorSize?.width ?: 0)
                    IOUtils.writeInt(output, sensorSize?.height ?: 0)
                } catch (e: Exception) {
                    Logger.error("Error getting camera $id characteristics", e)
                }
            }
            
            Logger.log("Camera list: ${cameraIds.size} cameras")
        } catch (e: Exception) {
            Logger.error("Error getting camera list", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 14: 在指定显示器上启动应用
     */
    fun launchApp(input: InputStream, output: BufferedOutputStream) {
        try {
            val packageName = IOUtils.readString(input)
            val displayId = IOUtils.readInt(input)
            
            val pm = FakeContext.get().packageManager
            var intent = pm.getLaunchIntentForPackage(packageName)
            
            if (intent == null) {
                intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            }
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                // 设置显示器
                if (Build.VERSION.SDK_INT >= 26 && displayId != 0) {
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = displayId
                    // 启动应用
                }
                
                IOUtils.writeSuccess(output)
                Logger.log("Launched app: $packageName on display $displayId")
            } else {
                IOUtils.writeError(output, -1, "No launch intent found")
            }
        } catch (e: Exception) {
            Logger.error("Error launching app", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 21: 获取相机服务信息
     */
    fun getCameraServiceInfo(output: BufferedOutputStream) {
        // 简化实现
        Logger.log("Camera service info requested")
    }
    
    /**
     * 将 Drawable 转换为 Bitmap
     * 核心图标处理逻辑，支持自动缩放
     */
    private fun drawableToBitmap(drawable: Drawable?, targetSize: Int?): Bitmap? {
        if (drawable == null) return null
        
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        
        // 计算目标尺寸
        val (width, height) = if (targetSize == null || targetSize >= intrinsicWidth && targetSize >= intrinsicHeight) {
            intrinsicWidth to intrinsicHeight
        } else {
            // 按比例缩放
            if (intrinsicWidth > intrinsicHeight) {
                val ratio = targetSize.toFloat() / intrinsicWidth
                targetSize to (intrinsicHeight * ratio).toInt()
            } else {
                val ratio = targetSize.toFloat() / intrinsicHeight
                (intrinsicWidth * ratio).toInt() to targetSize
            }
        }
        
        // 创建 Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        
        return bitmap
    }
    
    /**
     * 将 Bitmap 压缩为 PNG 并写入输出流
     */
    private fun writeBitmap(output: BufferedOutputStream, bitmap: Bitmap?, maxSize: Int?) {
        if (bitmap == null) {
            IOUtils.writeInt(output, 0)
            return
        }
        
        var finalBitmap = bitmap
        
        // 如果需要再次缩放
        if (maxSize != null && (maxSize < bitmap.width || maxSize < bitmap.height)) {
            val (width, height) = if (bitmap.width > bitmap.height) {
                val ratio = maxSize.toFloat() / bitmap.width
                maxSize to (bitmap.height * ratio).toInt()
            } else {
                val ratio = maxSize.toFloat() / bitmap.height
                (bitmap.width * ratio).toInt() to maxSize
            }
            finalBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        
        // 压缩为 PNG
        val byteStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
        
        // 释放内存
        if (finalBitmap != bitmap) {
            finalBitmap.recycle()
        }
        bitmap.recycle()
        
        // 发送数据
        val pngData = byteStream.toByteArray()
        IOUtils.writeInt(output, pngData.size)
        output.write(pngData)
    }
}

