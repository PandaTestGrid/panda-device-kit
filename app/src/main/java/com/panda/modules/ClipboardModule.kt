package com.panda.modules

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import com.panda.utils.FakeContext
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 剪贴板管理模块
 * 提供剪贴板读写、监听等功能
 */
class ClipboardModule {
    
    private var lastClipboard: ClipboardData? = null
    
    data class ClipboardData(
        val mimeType: String,
        val data: ByteArray
    )
    
    private fun getClipboardManager(): ClipboardManager {
        return FakeContext.get().getSystemService(ClipboardManager::class.java)
    }
    
    /**
     * 命令 70: 读取剪贴板内容
     */
    fun getClipboard(output: BufferedOutputStream) {
        try {
            val clipboard = getCurrentClipboard()
            
            if (clipboard == null) {
                IOUtils.writeInt(output, -1)
                return
            }
            
            // 写入 MIME 类型
            IOUtils.writeString(output, clipboard.mimeType)
            // 写入数据
            IOUtils.writeBytes(output, clipboard.data)
            
            Logger.log("Clipboard read: ${clipboard.mimeType}, ${clipboard.data.size} bytes")
        } catch (e: Exception) {
            Logger.error("Error reading clipboard", e)
            IOUtils.writeInt(output, -2)
            IOUtils.writeString(output, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 71: 设置剪贴板内容
     */
    fun setClipboard(input: InputStream, output: BufferedOutputStream) {
        try {
            val mimeType = IOUtils.readString(input)
            val data = IOUtils.readBytes(input)
            
            Logger.log("Setting clipboard: $mimeType, ${data.size} bytes")
            
            val clipboardManager = getClipboardManager()
            
            when {
                mimeType == "text/plain" -> {
                    // 设置文本
                    val text = String(data, Charsets.UTF_8)
                    val clip = ClipData.newPlainText("panda", text)
                    clipboardManager.setPrimaryClip(clip)
                }
                mimeType.startsWith("image/") -> {
                    // 设置图片 - 保存到临时文件
                    val cacheDir = FakeContext.get().externalCacheDir
                    val clipboardFile = File(cacheDir, "clipboard")
                    
                    FileOutputStream(clipboardFile).use { fos ->
                        fos.write(data)
                    }
                    
                    val uri = Uri.fromFile(clipboardFile)
                    val clip = ClipData.newUri(FakeContext.get().contentResolver, "panda", uri)
                    clipboardManager.setPrimaryClip(clip)
                }
                else -> {
                    // 其他类型作为文本处理
                    val clip = ClipData.newPlainText("panda", String(data, Charsets.UTF_8))
                    clipboardManager.setPrimaryClip(clip)
                }
            }
            
            lastClipboard = ClipboardData(mimeType, data)
            IOUtils.writeSuccess(output)
            
        } catch (e: Exception) {
            Logger.error("Error setting clipboard", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 72: 监听剪贴板变化
     */
    fun watchClipboard(output: BufferedOutputStream) {
        try {
            val clipboardManager = getClipboardManager()
            
            clipboardManager.addPrimaryClipChangedListener {
                try {
                    val clipboard = getCurrentClipboard()
                    if (clipboard != null && clipboard != lastClipboard) {
                        Logger.log("Clipboard changed: ${clipboard.mimeType}, ${clipboard.data.size} bytes")
                        
                        lastClipboard = clipboard
                        
                        // 发送变化通知
                        IOUtils.writeString(output, clipboard.mimeType)
                        IOUtils.writeBytes(output, clipboard.data)
                        output.flush()
                    }
                } catch (e: Exception) {
                    Logger.error("Error in clipboard listener", e)
                }
            }
            
            Logger.log("Clipboard listener registered")
        } catch (e: Exception) {
            Logger.error("Error watching clipboard", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 73: 剪贴板扩展操作
     */
    fun clipboardOperation(input: InputStream, output: BufferedOutputStream) {
        // 可以扩展其他剪贴板操作
        Logger.log("Clipboard operation called")
    }
    
    /**
     * 获取当前剪贴板内容
     */
    private fun getCurrentClipboard(): ClipboardData? {
        val clipboardManager = getClipboardManager()
        
        val clip = clipboardManager.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return null
        }
        
        val item = clip.getItemAt(0)
        
        return when {
            // 文本内容
            item.text != null -> {
                val text = item.text.toString()
                ClipboardData("text/plain", text.toByteArray(Charsets.UTF_8))
            }
            // URI 内容
            item.uri != null -> {
                if (item.uri.scheme == "content") {
                    // 从 content:// URI 读取
                    try {
                        val contentResolver = FakeContext.get().contentResolver
                        contentResolver.openInputStream(item.uri)?.use { stream ->
                            val data = stream.readBytes()
                            
                            // 判断 MIME 类型
                            val mimeType = if (clip.description.hasMimeType("text/*")) {
                                "text/plain"
                            } else if (clip.description.hasMimeType("image/*")) {
                                clip.description.filterMimeTypes("image/*")?.get(0) ?: "image/png"
                            } else {
                                "application/octet-stream"
                            }
                            
                            ClipboardData(mimeType, data)
                        }
                    } catch (e: Exception) {
                        Logger.error("Error reading content URI", e)
                        null
                    }
                } else {
                    // 其他 URI 作为文本
                    val uriString = item.uri.toString()
                    ClipboardData("text/plain", uriString.toByteArray(Charsets.UTF_8))
                }
            }
            else -> null
        }
    }
}

