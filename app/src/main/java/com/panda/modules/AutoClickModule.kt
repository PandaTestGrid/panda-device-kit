package com.panda.modules

import android.annotation.SuppressLint
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.panda.core.InstrumentShellWrapper
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.InputStream
import java.util.regex.Pattern

/**
 * 自动点击模块
 * 提供 UI 元素查找、自动点击、弹框监控等功能
 */
@SuppressLint("PrivateApi")
class AutoClickModule {
    
    private var uiDevice: UiDevice? = null
    private var monitorThread: Thread? = null
    @Volatile
    private var isMonitoring = false
    private val monitorKeywords = mutableSetOf<String>()
    
    /**
     * 获取 UiDevice 实例
     */
    private fun getUiDevice(): UiDevice {
        if (uiDevice == null) {
            val instrumentation = InstrumentShellWrapper.getInstance()
            uiDevice = UiDevice.getInstance(instrumentation)
        }
        return uiDevice!!
    }
    
    /**
     * 命令 110: 点击包含指定文本的按钮
     * 请求: 文本(string), 超时(int ms)
     * 响应: 成功(int 1) 或 失败(int 0)
     */
    fun clickByText(input: InputStream, output: BufferedOutputStream) {
        try {
            val text = IOUtils.readString(input)
            val timeout = IOUtils.readInt(input)
            
            Logger.log("Click by text: '$text', timeout: ${timeout}ms")
            
            val device = getUiDevice()
            val elements = device.findObjects(By.textContains(text).clickable(true))
            
            if (elements.isNotEmpty()) {
                // 优先选择短文本（真正的按钮）
                val shortElements = elements.filter { 
                    it.text?.length ?: 0 <= 15 
                }
                
                val target = (if (shortElements.isNotEmpty()) shortElements else elements)
                    .minByOrNull { it.text?.length ?: Int.MAX_VALUE }
                
                target?.let {
                    Logger.log("Clicking: '${it.text}'")
                    it.click()
                    Thread.sleep(300)
                    IOUtils.writeInt(output, 1)
                    return
                }
            }
            
            // 如果没有找到可点击元素，尝试查找父元素
            val allElements = device.findObjects(By.textContains(text))
            for (element in allElements.sortedBy { it.text?.length ?: Int.MAX_VALUE }) {
                if ((element.text?.length ?: 0) > 15) continue
                
                var parent = element.parent
                var depth = 0
                while (parent != null && depth < 3) {
                    if (parent.isClickable) {
                        Logger.log("Clicking parent of: '${element.text}'")
                        parent.click()
                        Thread.sleep(300)
                        IOUtils.writeInt(output, 1)
                        return
                    }
                    parent = parent.parent
                    depth++
                }
            }
            
            Logger.log("Element not found: '$text'")
            IOUtils.writeInt(output, 0)
            
        } catch (e: Exception) {
            Logger.error("Error in clickByText", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 111: 点击精确文本
     * 请求: 文本(string), 超时(int ms)
     * 响应: 成功(int 1) 或 失败(int 0)
     */
    fun clickByExactText(input: InputStream, output: BufferedOutputStream) {
        try {
            val text = IOUtils.readString(input)
            val timeout = IOUtils.readInt(input)
            
            val device = getUiDevice()
            val element = device.findObject(By.text(text))
            
            if (element != null) {
                Logger.log("Exact click: '$text'")
                element.click()
                IOUtils.writeInt(output, 1)
            } else {
                Logger.log("Element not found: '$text'")
                IOUtils.writeInt(output, 0)
            }
        } catch (e: Exception) {
            Logger.error("Error in clickByExactText", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 112: 点击坐标
     * 请求: x(int), y(int)
     * 响应: 成功(int 1) 或 失败(int 0)
     */
    fun clickAtCoordinate(input: InputStream, output: BufferedOutputStream) {
        try {
            val x = IOUtils.readInt(input)
            val y = IOUtils.readInt(input)
            
            val device = getUiDevice()
            val success = device.click(x, y)
            
            Logger.log("Click at ($x, $y): $success")
            IOUtils.writeInt(output, if (success) 1 else 0)
        } catch (e: Exception) {
            Logger.error("Error in clickAtCoordinate", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 113: 获取屏幕上所有可点击元素的文本
     * 响应: 数量(int), 对于每个元素: 文本(string)
     */
    fun getClickableTexts(output: BufferedOutputStream) {
        try {
            val device = getUiDevice()
            val elements = device.findObjects(By.clickable(true))
            
            val texts = elements.mapNotNull {
                try {
                    it.text?.takeIf { text -> text.isNotBlank() }
                } catch (e: Exception) {
                    null
                }
            }
            
            IOUtils.writeInt(output, texts.size)
            texts.forEach { IOUtils.writeString(output, it) }
            
            Logger.log("Found ${texts.size} clickable texts")
        } catch (e: Exception) {
            Logger.error("Error in getClickableTexts", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 114: 启动自动点击监控
     * 请求: 关键词数量(int), 关键词列表(string[])
     * 响应: 成功(int 1) 或 失败(int 0)
     */
    fun startAutoClickMonitor(input: InputStream, output: BufferedOutputStream) {
        try {
            val count = IOUtils.readInt(input)
            monitorKeywords.clear()
            
            repeat(count) {
                monitorKeywords.add(IOUtils.readString(input))
            }
            
            if (isMonitoring) {
                Logger.log("Monitor already running")
                IOUtils.writeInt(output, 1)
                return
            }
            
            startMonitoring()
            IOUtils.writeInt(output, 1)
            Logger.log("Auto-click monitor started with ${monitorKeywords.size} keywords")
            
        } catch (e: Exception) {
            Logger.error("Error starting monitor", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 115: 停止自动点击监控
     * 响应: 成功(int 1)
     */
    fun stopAutoClickMonitor(output: BufferedOutputStream) {
        try {
            stopMonitoring()
            IOUtils.writeInt(output, 1)
            Logger.log("Auto-click monitor stopped")
        } catch (e: Exception) {
            Logger.error("Error stopping monitor", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 116: 获取监控状态
     * 响应: 状态(int 1=运行中, 0=已停止), 关键词数量(int), 关键词列表(string[])
     */
    fun getMonitorStatus(output: BufferedOutputStream) {
        try {
            IOUtils.writeInt(output, if (isMonitoring) 1 else 0)
            IOUtils.writeInt(output, monitorKeywords.size)
            monitorKeywords.forEach { IOUtils.writeString(output, it) }
        } catch (e: Exception) {
            Logger.error("Error getting monitor status", e)
            IOUtils.writeInt(output, 0)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 117: 按返回键
     * 响应: 成功(int 1) 或 失败(int 0)
     */
    fun pressBack(output: BufferedOutputStream) {
        try {
            val device = getUiDevice()
            val success = device.pressBack()
            IOUtils.writeInt(output, if (success) 1 else 0)
            Logger.log("Press back: $success")
        } catch (e: Exception) {
            Logger.error("Error in pressBack", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 118: 按 Home 键
     * 响应: 成功(int 1) 或 失败(int 0)
     */
    fun pressHome(output: BufferedOutputStream) {
        try {
            val device = getUiDevice()
            val success = device.pressHome()
            IOUtils.writeInt(output, if (success) 1 else 0)
            Logger.log("Press home: $success")
        } catch (e: Exception) {
            Logger.error("Error in pressHome", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 119: 检查文本是否存在
     * 请求: 文本(string)
     * 响应: 存在(int 1) 或 不存在(int 0)
     */
    fun hasText(input: InputStream, output: BufferedOutputStream) {
        try {
            val text = IOUtils.readString(input)
            val device = getUiDevice()
            val element = device.findObject(By.textContains(text))
            
            IOUtils.writeInt(output, if (element != null) 1 else 0)
            Logger.log("Has text '$text': ${element != null}")
        } catch (e: Exception) {
            Logger.error("Error in hasText", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 启动监控线程
     */
    private fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitorThread = Thread({
            Logger.log("[Monitor] Started, checking every 2s")
            
            while (isMonitoring) {
                try {
                    checkAndClick()
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Logger.error("[Monitor] Error", e)
                }
            }
            
            Logger.log("[Monitor] Stopped")
        }, "AutoClickMonitor").apply {
            isDaemon = true
            start()
        }
    }
    
    /**
     * 停止监控线程
     */
    private fun stopMonitoring() {
        isMonitoring = false
        monitorThread?.interrupt()
        monitorThread?.join(1000)
        monitorThread = null
    }
    
    /**
     * 检查并点击匹配的按钮（精确匹配 + 优先按钮控件）
     */
    private fun checkAndClick() {
        try {
            val device = getUiDevice()
            
            // 遍历每个关键词
            for (keyword in monitorKeywords) {
                // 策略 1: 优先查找 Button 类型的控件
                val buttons = device.findObjects(
                    By.clazz("android.widget.Button").text(keyword)
                )
                
                if (buttons.isNotEmpty()) {
                    Logger.log("[Monitor] Found Button with exact text: '$keyword'")
                    buttons[0].click()
                    Logger.log("[Monitor] Clicked button, waiting 2s...")
                    Thread.sleep(2000)
                    return
                }
                
                // 策略 2: 查找其他可点击控件，但限制文本长度（<=10字符）
                val elements = device.findObjects(By.text(keyword).clickable(true))
                
                for (element in elements) {
                    try {
                        val text = element.text
                        // 只点击短文本（真正的按钮通常很短）
                        if (text != null && text.length <= 10) {
                            // 检查是否为常见按钮类型
                            val className = element.className
                            val isButton = className.contains("Button") || 
                                          className.contains("TextView") && element.isClickable
                            
                            if (isButton) {
                                Logger.log("[Monitor] Found clickable: '$text' ($className)")
                                element.click()
                                Logger.log("[Monitor] Clicked, waiting 2s...")
                                Thread.sleep(2000)
                                return
                            }
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略错误继续监控
        }
    }
}

