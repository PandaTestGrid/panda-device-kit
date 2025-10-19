package com.panda.modules

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.LocalSocket
import com.panda.mirror.IWindowManagerMirror
import com.panda.mirror.ServiceManagerMirror
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 系统操作模块
 * 提供文件传输、截图、Shell命令执行等系统级功能
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class SystemModule {
    
    /**
     * 命令 40: 文件传输
     */
    fun fileTransfer(input: InputStream, output: OutputStream) {
        try {
            // 双向文件传输逻辑
            Logger.log("File transfer started")
            // 实现文件传输协议
        } catch (e: Exception) {
            Logger.error("Error in file transfer", e)
        }
    }
    
    /**
     * 命令 60: 获取系统属性
     */
    fun getSystemProperties(output: BufferedOutputStream) {
        try {
            Logger.log("Get system properties")
            // 简化实现
        } catch (e: Exception) {
            Logger.error("Error getting system properties", e)
        }
    }
    
    /**
     * 命令 61: 文件操作
     */
    fun fileOperation(input: InputStream, output: OutputStream) {
        Logger.log("File operation")
    }
    
    /**
     * 命令 62-65: 系统操作
     */
    fun systemOperationA(output: BufferedOutputStream) {
        Logger.log("System operation A")
    }
    
    fun systemOperationB(output: BufferedOutputStream) {
        Logger.log("System operation B")
    }
    
    fun systemOperationC(output: OutputStream) {
        Logger.log("System operation C")
    }
    
    fun systemOperationD(output: BufferedOutputStream) {
        Logger.log("System operation D")
    }
    
    /**
     * 命令 90: 截取当前壁纸（锁屏或桌面壁纸）
     */
    fun screenshotWallpaper(output: BufferedOutputStream) {
        try {
            // 获取 WindowManager 服务
            val binder = ServiceManagerMirror.getService.call("window")
            val wm = IWindowManagerMirror.asInterface.call(binder)
            val bitmap = IWindowManagerMirror.screenshotWallpaper.call(wm)
            
            if (bitmap != null) {
                // 缩放到 1080p
                val scaledBitmap = if (bitmap.width > 1080) {
                    val ratio = 1080f / bitmap.width
                    Bitmap.createScaledBitmap(
                        bitmap,
                        1080,
                        (bitmap.height * ratio).toInt(),
                        true
                    )
                } else {
                    bitmap
                }
                
                // 压缩为 PNG
                val byteStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
                
                // 释放内存
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()
                
                // 发送数据
                val pngData = byteStream.toByteArray()
                output.write(pngData)
                output.flush()
                
                Logger.log("Wallpaper screenshot sent: ${pngData.size} bytes")
            } else {
                Logger.log("No wallpaper to screenshot")
            }
        } catch (e: Exception) {
            Logger.error("Error screenshot wallpaper", e)
        }
    }
    
    /**
     * 命令 100: 执行 Shell 命令并返回输出
     */
    fun executeCommand(input: InputStream, client: LocalSocket) {
        try {
            val command = IOUtils.readString(input)
            Logger.log("Executing command: $command")
            
            // 执行命令
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val pid = getPid(process)
            
            Logger.log("Process $pid started: $command")
            
            // 将输出重定向到 socket
            val outputThread = Thread {
                try {
                    process.inputStream.copyTo(client.outputStream)
                } catch (e: Exception) {
                    Logger.error("Error copying stdout", e)
                }
            }
            
            val errorThread = Thread {
                try {
                    process.errorStream.copyTo(client.outputStream)
                } catch (e: Exception) {
                    Logger.error("Error copying stderr", e)
                }
            }
            
            outputThread.start()
            errorThread.start()
            
            // 等待进程结束
            process.waitFor()
            
            outputThread.join()
            errorThread.join()
            
            Logger.log("Process $pid finished")
            
        } catch (e: Exception) {
            Logger.error("Error executing command", e)
        }
    }
    
    private fun getPid(process: Process): Int {
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (e: Exception) {
            -1
        }
    }
}

