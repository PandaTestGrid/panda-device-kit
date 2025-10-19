package com.panda.modules

import android.annotation.SuppressLint
import android.os.Build
import com.panda.utils.FakeContext
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream

/**
 * 存储设备管理模块
 * 提供存储卷信息查询功能（SD卡、USB存储等）
 */
@SuppressLint("PrivateApi")
class StorageModule {
    
    /**
     * 命令 20: 获取所有挂载的存储设备列表
     */
    fun getStorageList(output: BufferedOutputStream) {
        if (Build.VERSION.SDK_INT < 24) {
            IOUtils.writeInt(output, 0)
            return
        }
        
        try {
            val context = FakeContext.get()
            val storageManager = context.getSystemService(android.os.storage.StorageManager::class.java)
            
            val volumes = storageManager?.storageVolumes ?: emptyList()
            val mountedVolumes = volumes.filter { it.state == "mounted" }
            
            IOUtils.writeInt(output, mountedVolumes.size)
            
            for (volume in mountedVolumes) {
                // 类型判断
                val type = when {
                    volume.isPrimary -> 0  // 内部存储
                    volume.isRemovable -> if (volume.getDescription(context).contains("SD", ignoreCase = true)) 1 else 2
                    else -> 0
                }
                
                // 写入类型
                IOUtils.writeInt(output, type)
                
                // 写入标签
                val label = volume.getDescription(context)
                IOUtils.writeString(output, label)
                
                // 写入路径
                val path = if (Build.VERSION.SDK_INT >= 30) {
                    volume.directory?.path ?: ""
                } else {
                    // 使用反射获取 path
                    try {
                        val method = volume.javaClass.getMethod("getPath")
                        method.invoke(volume) as? String ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                }
                IOUtils.writeString(output, path)
            }
            
            Logger.log("Storage list: ${mountedVolumes.size} volumes")
            
        } catch (e: Exception) {
            Logger.error("Error getting storage list", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
}

