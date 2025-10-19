package com.panda.modules

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import com.panda.utils.FakeContext
import com.panda.utils.IOUtils
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.InputStream
import java.util.concurrent.Executors

/**
 * WiFi 管理模块
 * 提供 WiFi 状态查询、网络扫描、配置管理等功能
 */
@SuppressLint("MissingPermission")
class WiFiModule {
    
    private fun getWifiManager(): WifiManager {
        return FakeContext.get().getSystemService(WifiManager::class.java)
    }
    
    /**
     * 命令 50: 获取 WiFi 状态
     */
    fun getWifiState(output: BufferedOutputStream) {
        try {
            val wifiManager = getWifiManager()
            val state = wifiManager.wifiState
            IOUtils.writeInt(output, state)
            Logger.log("WiFi state: $state")
        } catch (e: Exception) {
            Logger.error("Error getting WiFi state", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 51: 设置 WiFi 开关
     */
    fun setWifiEnabled(input: InputStream) {
        try {
            val enabled = IOUtils.readInt(input) != 0
            val wifiManager = getWifiManager()
            wifiManager.isWifiEnabled = enabled
            Logger.log("WiFi enabled: $enabled")
        } catch (e: Exception) {
            Logger.error("Error setting WiFi state", e)
        }
    }
    
    /**
     * 命令 52: 扫描 WiFi 网络
     */
    fun scanWifi(output: BufferedOutputStream) {
        try {
            val wifiManager = getWifiManager()
            
            if (Build.VERSION.SDK_INT >= 30) {
                // Android 11+ 使用回调
                wifiManager.registerScanResultsCallback(
                    Executors.newSingleThreadExecutor(),
                    object : WifiManager.ScanResultsCallback() {
                        override fun onScanResultsAvailable() {
                            writeScanResults(wifiManager, output)
                        }
                    }
                )
            }
            
            wifiManager.startScan()
            
            if (Build.VERSION.SDK_INT < 30) {
                // Android 10 及以下，等待扫描完成
                Thread.sleep(2000)
                writeScanResults(wifiManager, output)
            }
        } catch (e: Exception) {
            Logger.error("Error scanning WiFi", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    private fun writeScanResults(wifiManager: WifiManager, output: BufferedOutputStream) {
        val results = wifiManager.scanResults.distinctBy { it.SSID }
        IOUtils.writeInt(output, results.size)
        
        for (result in results) {
            // 写入 SSID (去掉引号)
            IOUtils.writeString(output, result.SSID.trim('"'))
            // 写入 BSSID
            IOUtils.writeString(output, result.BSSID)
            // 写入频率
            IOUtils.writeInt(output, result.frequency)
            // 写入 WiFi 标准
            val standard = if (Build.VERSION.SDK_INT >= 30) {
                result.wifiStandard
            } else {
                0
            }
            IOUtils.writeInt(output, standard)
            // 写入信号等级 (0-4)
            val level = WifiManager.calculateSignalLevel(result.level, 5)
            IOUtils.writeInt(output, level)
        }
        
        output.flush()
        Logger.log("Scan results: ${results.size} networks")
    }
    
    /**
     * 命令 53: 获取当前连接的 WiFi 详细信息
     */
    fun getWifiInfo(output: BufferedOutputStream) {
        try {
            val wifiManager = getWifiManager()
            val connectionInfo = wifiManager.connectionInfo
            
            // 写入连接信息
            IOUtils.writeString(output, connectionInfo.ssid.trim('"'))
            IOUtils.writeString(output, connectionInfo.bssid ?: "")
            IOUtils.writeInt(output, connectionInfo.networkId)
            IOUtils.writeInt(output, connectionInfo.linkSpeed)
            IOUtils.writeInt(output, connectionInfo.rssi)
            
            output.flush()
        } catch (e: Exception) {
            Logger.error("Error getting WiFi info", e)
            IOUtils.writeError(output, -1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * 命令 54: 获取已配置的网络列表
     */
    fun getConfiguredNetworks(output: BufferedOutputStream) {
        try {
            val wifiManager = getWifiManager()
            val networks = wifiManager.configuredNetworks
            
            IOUtils.writeInt(output, networks.size)
            for (network in networks) {
                IOUtils.writeInt(output, network.networkId)
                IOUtils.writeString(output, network.SSID.trim('"'))
            }
            
            Logger.log("Configured networks: ${networks.size}")
        } catch (e: Exception) {
            Logger.error("Error getting configured networks", e)
            IOUtils.writeInt(output, 0)
        }
    }
    
    /**
     * 命令 55: 连接到指定网络
     */
    fun connectToNetwork(input: InputStream) {
        try {
            val networkId = IOUtils.readInt(input)
            val wifiManager = getWifiManager()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()
            Logger.log("Connecting to network: $networkId")
        } catch (e: Exception) {
            Logger.error("Error connecting to network", e)
        }
    }
    
    /**
     * 命令 56: 添加新的 WiFi 网络配置
     */
    fun addNetwork(input: InputStream) {
        try {
            val ssid = IOUtils.readString(input)
            val password = IOUtils.readString(input)
            val autoJoin = IOUtils.readInt(input) != 0
            
            val wifiManager = getWifiManager()
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
            }
            
            val networkId = wifiManager.addNetwork(config)
            wifiManager.enableNetwork(networkId, true)
            
            // allowAutojoin 使用反射（可能是隐藏API）
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    val method = wifiManager.javaClass.getMethod("allowAutojoin", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    method.invoke(wifiManager, networkId, autoJoin)
                } catch (e: Exception) {
                    Logger.log("allowAutojoin not available: ${e.message}")
                }
            }
            wifiManager.reconnect()
            
            Logger.log("Network added: $ssid (ID: $networkId)")
        } catch (e: Exception) {
            Logger.error("Error adding network", e)
        }
    }
    
    /**
     * 命令 57: 设置网络自动重连选项
     */
    fun setAutoJoin(input: InputStream) {
        try {
            val networkId = IOUtils.readInt(input)
            val autoJoin = IOUtils.readInt(input) != 0
            
            if (Build.VERSION.SDK_INT >= 29) {
                val wifiManager = getWifiManager()
                try {
                    val method = wifiManager.javaClass.getMethod("allowAutojoin", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    method.invoke(wifiManager, networkId, autoJoin)
                    Logger.log("Auto join set for network $networkId: $autoJoin")
                } catch (e: Exception) {
                    Logger.error("allowAutojoin not available", e)
                }
            }
        } catch (e: Exception) {
            Logger.error("Error setting auto join", e)
        }
    }
    
    /**
     * 命令 58: 删除已保存的网络配置
     */
    fun removeNetwork(input: InputStream) {
        try {
            val networkId = IOUtils.readInt(input)
            val wifiManager = getWifiManager()
            wifiManager.removeNetwork(networkId)
            wifiManager.saveConfiguration()
            Logger.log("Network removed: $networkId")
        } catch (e: Exception) {
            Logger.error("Error removing network", e)
        }
    }
}

