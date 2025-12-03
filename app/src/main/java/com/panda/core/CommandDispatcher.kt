package com.panda.core

import android.net.LocalSocket
import com.panda.modules.*
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.InputStream

/**
 * 命令分发器
 * 负责接收客户端命令并调用相应的功能模块处理
 */
class CommandDispatcher(
    private val client: LocalSocket,
    private val output: BufferedOutputStream
) {
    private val input: InputStream = client.inputStream
    
    // 各功能模块
    private val appModule = AppModule()
    private val wifiModule = WiFiModule()
    private val clipboardModule = ClipboardModule()
    private val notificationModule = NotificationModule()
    private val storageModule = StorageModule()
    private val audioModule = AudioModule()
    private val systemModule = SystemModule()
    private val autoClickModule = AutoClickModule.getInstance()
    private val cpuModule = CpuModule()
    private val gpuModule = GpuModule()
    private val fpsModule = FpsModule()
    private val memoryModule = MemoryModule()
    private val batteryModule = BatteryModule()
    private val networkStatsModule = NetworkStatsModule()
    
    /**
     * 分发命令到相应模块
     */
    fun dispatch(command: Int) {
        try {
            when (command) {
                // 基础操作 (0-1)
                0 -> appModule.createVirtualDisplay(output)
                1 -> appModule.initialize()
                
                // 应用管理 (10-14)
                10 -> appModule.getAppList(input, output)
                11 -> appModule.getApkPath(input, output)
                12 -> appModule.getCameraStatus(input, output)
                13 -> appModule.getCameraList(input, output)
                14 -> appModule.launchApp(input, output)
                
                // 系统信息 (20-21)
                20 -> storageModule.getStorageList(output)
                21 -> appModule.getCameraServiceInfo(output)
                
                // 音频捕获 (30-31) - Android 11+
                30 -> audioModule.captureSystemAudio(client)
                31 -> audioModule.captureMicAudio(input, client)
                
                // 文件传输 (40)
                40 -> systemModule.fileTransfer(input, client.outputStream)
                
                // WiFi 管理 (50-58)
                50 -> wifiModule.getWifiState(output)
                51 -> wifiModule.setWifiEnabled(input)
                52 -> wifiModule.scanWifi(output)
                53 -> wifiModule.getWifiInfo(output)
                54 -> wifiModule.getConfiguredNetworks(output)
                55 -> wifiModule.connectToNetwork(input)
                56 -> wifiModule.addNetwork(input)
                57 -> wifiModule.setAutoJoin(input)
                58 -> wifiModule.removeNetwork(input)
                
                // 系统操作 (60-65)
                60 -> systemModule.getSystemProperties(output)
                61 -> systemModule.fileOperation(input, client.outputStream)
                62 -> systemModule.systemOperationA(output)
                63 -> systemModule.systemOperationB(output)
                64 -> systemModule.systemOperationC(client.outputStream)
                65 -> systemModule.systemOperationD(output)
                
                // 剪贴板 (70-73)
                70 -> clipboardModule.getClipboard(output)
                71 -> clipboardModule.setClipboard(input, output)
                72 -> clipboardModule.watchClipboard(output)
                73 -> clipboardModule.clipboardOperation(input, output)
                
                // 通知管理 (80-83)
                80 -> notificationModule.getNotifications(input, output)
                81 -> notificationModule.cancelNotification(input)
                82 -> notificationModule.openNotification(input)
                83 -> notificationModule.clearAllNotifications()
                
                // 截图 (90, 120)
                90 -> systemModule.screenshotWallpaper(output)
                120 -> systemModule.screenshot(output)
                
                // Shell 命令 (100)
                100 -> systemModule.executeCommand(input, client)
                
                // 性能数据采集 (200-209)
                200 -> cpuModule.getCpuUsage(output)
                201 -> cpuModule.getCpuCoreUsage(output)
                202 -> cpuModule.getCpuFreq(output)
                203 -> gpuModule.getGpuUsage(output)
                204 -> fpsModule.getFps(output)
                205 -> memoryModule.getMemoryUsage(input, output)
                206 -> cpuModule.getCpuTemperature(output)
                207 -> cpuModule.getThreadCpuUsage(input, output)
                208 -> fpsModule.startProfiling(input, output)
                209 -> fpsModule.stopProfiling(output)
                
                // 电池信息 (220-222)
                220 -> batteryModule.getBatteryInfo(output)
                221 -> batteryModule.getBatteryLevel(output)
                222 -> batteryModule.isBatteryMonitoringSupported(output)
                
                // 网络流量统计 (230-232)
                230 -> networkStatsModule.getNetworkUsage(input, output)
                231 -> networkStatsModule.getTotalNetworkUsage(output)
                232 -> networkStatsModule.getNetworkUsageByPackage(input, output)
                
                // 自动点击 (110-119)
                110 -> autoClickModule.clickByText(input, output)
                111 -> autoClickModule.clickByExactText(input, output)
                112 -> autoClickModule.clickAtCoordinate(input, output)
                113 -> autoClickModule.getClickableTexts(output)
                114 -> autoClickModule.startAutoClickMonitor(input, output)
                115 -> autoClickModule.stopAutoClickMonitor(output)
                116 -> autoClickModule.getMonitorStatus(output)
                117 -> autoClickModule.pressBack(output)
                118 -> autoClickModule.pressHome(output)
                119 -> autoClickModule.hasText(input, output)
                
                else -> {
                    Logger.log("Unknown command: $command")
                    IOUtils.writeError(output, -1, "Unknown command: $command")
                }
            }
        } catch (e: Exception) {
            Logger.error("Error dispatching command $command", e)
            try {
                IOUtils.writeError(output, -1, IOUtils.getStackTrace(e))
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
}

