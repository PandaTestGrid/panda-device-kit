# PerfDog Console vs Panda 功能对比分析

## 📊 功能对比总览

| 功能模块 | PerfDog Console | Panda 项目 | 状态 |
|---------|----------------|-----------|------|
| **网络通信** | TCP + LocalSocket | LocalSocket 仅 | ⚠️ 部分缺失 |
| **性能数据采集** | ✅ 完整 | ❌ 缺失 | ❌ 缺失 |
| **电池信息** | ✅ 完整 | ❌ 缺失 | ❌ 缺失 |
| **网络流量统计** | ✅ 完整 | ❌ 缺失 | ❌ 缺失 |
| **屏幕截图** | ✅ 完整屏幕 | ⚠️ 仅壁纸 | ⚠️ 部分缺失 |
| **应用管理** | ✅ 完整 | ✅ 完整 | ✅ 已有 |
| **日志系统** | ✅ 实时传输 | ❌ 缺失 | ❌ 缺失 |
| **进程监控** | ✅ 完整 | ❌ 缺失 | ❌ 缺失 |
| **系统交互** | ✅ Shell/dumpsys/反射 | ⚠️ 仅 Shell | ⚠️ 部分缺失 |

---

## 🔴 核心缺失功能（高优先级）

### 1. 性能数据采集模块 ⭐⭐⭐⭐⭐

**PerfDog 支持的数据类型**：
- CPU 使用率（整体 + 核心级别）
- CPU 频率
- GPU 使用率和频率
- FPS（帧率）
- 内存使用（PSS、Private Dirty、Shared Dirty）
- 功耗（Power）
- CPU 温度
- 帧详情（Frame Detail）
- 内存详情（Memory Detail）
- 线程 CPU 使用率
- 屏幕亮度
- 电池电量
- 热状态（Thermal Status）

**当前 Panda 项目**：
- ❌ 完全没有性能监控功能

**建议实现**：
```kotlin
// 命令 200-219: 性能数据采集
200 -> performanceModule.getCpuUsage(input, output)      // CPU 使用率
201 -> performanceModule.getCpuFreq(output)              // CPU 频率
202 -> performanceModule.getGpuUsage(output)             // GPU 使用率
203 -> performanceModule.getFps(output)                  // FPS
204 -> performanceModule.getMemoryUsage(input, output)    // 内存使用
205 -> performanceModule.getBatteryInfo(output)          // 电池信息
206 -> performanceModule.getNetworkUsage(input, output)   // 网络流量
207 -> performanceModule.getCpuTemperature(output)      // CPU 温度
208 -> performanceModule.startProfiling(input, output)    // 开始性能分析
209 -> performanceModule.stopProfiling(output)            // 停止性能分析
```

**实现参考**（PerfDog 文档）：
- 内存数据：使用 `ActivityManager.getProcessMemoryInfo()` 和 `Debug.MemoryInfo`
- CPU 数据：读取 `/proc/stat` 和 `/proc/[pid]/stat`
- GPU 数据：通过系统服务或 `/sys/class/kgsl/` 读取
- FPS：通过 `Choreographer` 或反射 `SurfaceFlinger` API

---

### 2. 电池信息采集 ⭐⭐⭐⭐

**PerfDog 功能**：
- 电池电流（毫安）
- 电池电压（毫伏）
- 充电状态
- 电池电量百分比
- 时间戳

**当前 Panda 项目**：
- ❌ 完全没有电池监控功能

**建议实现**：
```kotlin
// 命令 220-221: 电池信息
220 -> systemModule.getBatteryInfo(output)              // 获取电池信息
221 -> systemModule.getBatteryLevel(output)             // 获取电池电量
```

**实现参考**（PerfDog 文档）：
```kotlin
// 使用 BatteryManager API (Android 5.0+)
val batteryManager = context.getSystemService(BatteryManager::class.java)
val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

// 通过 ServiceManager 获取电压
val service = ServiceManager.getService("battery")
// 调用 dump 方法获取详细信息
```

---

### 3. 网络流量统计 ⭐⭐⭐⭐

**PerfDog 功能**：
- 按 UID 统计网络流量
- 接收字节数（RxBytes）
- 发送字节数（TxBytes）
- 支持移动网络和 WiFi
- 实时流量监控

**当前 Panda 项目**：
- ❌ 完全没有网络流量统计功能

**建议实现**：
```kotlin
// 命令 230-232: 网络流量统计
230 -> networkModule.getNetworkUsage(input, output)      // 获取应用网络流量
231 -> networkModule.getTotalNetworkUsage(output)        // 获取总网络流量
232 -> networkModule.startNetworkMonitoring(input, output) // 开始监控
```

**实现参考**（PerfDog 文档）：
```kotlin
// 使用 NetworkStatsManager (Android 6.0+)
val networkStatsManager = context.getSystemService(NetworkStatsManager::class.java)
val bucket = NetworkStats.Bucket()

// 查询指定 UID 的流量
networkStatsManager.querySummaryForUid(
    TYPE_WIFI, null, startTime, endTime, uid
)
```

**注意事项**：
- 需要 `READ_NETWORK_USAGE_HISTORY` 权限
- 需要 Android 6.0+ (API 23+)
- 某些版本需要通过反射设置 Context

---

### 4. 完整屏幕截图 ⭐⭐⭐⭐

**PerfDog 功能**：
- 完整屏幕截图（不是壁纸）
- 支持屏幕旋转
- 使用 HardwareBuffer 和 ScreenCapture API
- 通过 JNI 转换为字节数组

**当前 Panda 项目**：
- ⚠️ 只有壁纸截图（命令 90）
- ❌ 缺少完整屏幕截图

**建议实现**：
```kotlin
// 命令 120-122: 屏幕截图（补充现有功能）
120 -> systemModule.screenshot(output)                  // 完整屏幕截图
121 -> systemModule.screenshotRegion(input, output)     // 区域截图
122 -> systemModule.screenshotApp(input, output)         // 应用窗口截图
```

**实现参考**（PerfDog 文档）：
```kotlin
// Android 12+ 使用公开 API
val builder = ScreenCapture.DisplayCaptureArgs.Builder(displayToken)
builder.setSize(width, height)
val screenshot = ScreenCapture.captureDisplay(builder.build())
val hardwareBuffer = screenshot.getHardwareBuffer()

// Android 11- 使用内部 API（反射）
// 参考 PerfDog 的降级策略
```

---

### 5. 实时日志系统 ⭐⭐⭐

**PerfDog 功能**：
- 实时日志收集和传输
- 支持多级别日志（DEBUG、INFO、WARN、ERROR）
- 使用有界队列缓存（容量 50）
- 线程安全的日志传输
- 自动处理连接断开

**当前 Panda 项目**：
- ⚠️ 只有本地日志（Logger）
- ❌ 缺少实时日志传输功能

**建议实现**：
```kotlin
// 命令 240-242: 日志系统
240 -> logModule.startLogStream(output)                  // 开始日志流
241 -> logModule.stopLogStream()                         // 停止日志流
242 -> logModule.getLogLevel(output)                     // 获取日志级别
```

**实现参考**（PerfDog 文档）：
```kotlin
// 使用有界队列和锁机制
class LogModule {
    private val logQueue = ArrayBlockingQueue<LogEntry>(50)
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var logSocket: LocalSocket? = null
    
    fun addLog(level: Int, tag: String, message: String) {
        lock.lock()
        try {
            logQueue.offer(LogEntry(System.currentTimeMillis(), level, tag, message))
            condition.signal()
        } finally {
            lock.unlock()
        }
    }
}
```

---

### 6. 进程监控 ⭐⭐⭐

**PerfDog 功能**：
- 获取运行进程列表
- 进程 PID、名称、命令行
- 进程重要性级别
- 判断前台进程
- 进程标志信息

**当前 Panda 项目**：
- ❌ 完全没有进程监控功能

**建议实现**：
```kotlin
// 命令 250-253: 进程监控
250 -> processModule.getRunningProcesses(output)         // 获取运行进程列表
251 -> processModule.getProcessInfo(input, output)       // 获取进程详细信息
252 -> processModule.getProcessMemory(input, output)     // 获取进程内存
253 -> processModule.getProcessCpu(input, output)        // 获取进程 CPU
```

**实现参考**（PerfDog 文档）：
```kotlin
// 使用 ActivityManager
val activityManager = context.getSystemService(ActivityManager::class.java)
val processes = activityManager.runningAppProcesses

// 读取进程命令行
val cmdline = File("/proc/$pid/cmdline").readText()
```

---

## 🟡 部分缺失功能（中优先级）

### 7. TCP 服务器支持 ⭐⭐⭐

**PerfDog 功能**：
- TCP 服务器监听端口 43305
- 支持多客户端连接
- 每个连接独立线程处理
- Protocol Buffers 序列化

**当前 Panda 项目**：
- ✅ 只有 LocalSocket 通信
- ❌ 缺少 TCP 服务器支持

**建议实现**：
```kotlin
// 在 Main.kt 中添加 TCP 服务器
private fun startTcpServer(port: Int = 43305) {
    Thread {
        val serverSocket = ServerSocket(port, 128)
        while (true) {
            val clientSocket = serverSocket.accept()
            Thread {
                handleTcpClient(clientSocket)
            }.start()
        }
    }.start()
}
```

**优势**：
- 支持远程连接（不只是本地）
- 兼容性更好（不需要 ADB）
- 可以跨网络使用

---

### 8. 系统设置读取 ⭐⭐

**PerfDog 功能**：
- 读取系统设置（如屏幕亮度）
- 通过 ContentProvider 访问 Settings
- 支持 System、Secure、Global 命名空间

**当前 Panda 项目**：
- ❌ 缺少系统设置读取功能

**建议实现**：
```kotlin
// 命令 260-262: 系统设置
260 -> systemModule.getSystemSetting(input, output)     // 读取系统设置
261 -> systemModule.setSystemSetting(input, output)      // 设置系统设置
262 -> systemModule.getScreenBrightness(output)          // 获取屏幕亮度
```

**实现参考**（PerfDog 文档）：
```kotlin
// 通过反射获取 Settings ContentProvider
val service = ServiceManager.getService("settings")
val provider = IContentProvider.Stub.asInterface(service)
val value = provider.call(null, "system", "screen_brightness", null, null)
```

---

### 9. Java 类反射功能 ⭐⭐

**PerfDog 功能**：
- 反射获取类的字段信息
- 遍历类层次结构
- 返回字段修饰符、类型、名称

**当前 Panda 项目**：
- ❌ 缺少反射功能

**建议实现**：
```kotlin
// 命令 270: Java 类反射
270 -> systemModule.reflectJavaClass(input, output)      // 反射 Java 类
```

**实现参考**（PerfDog 文档）：
```kotlin
val clazz = Class.forName(className)
for (field in clazz.declaredFields) {
    if (!Modifier.isStatic(field.modifiers)) {
        // 返回字段信息
    }
}
```

---

### 10. dumpsys 调用 ⭐⭐

**PerfDog 功能**：
- 调用 Android 系统的 `dumpsys` 命令
- 获取系统服务信息
- 支持各种服务类型

**当前 Panda 项目**：
- ⚠️ 可以通过 Shell 命令执行（命令 100）
- ❌ 缺少专门的 dumpsys 接口

**建议实现**：
```kotlin
// 命令 280: dumpsys
280 -> systemModule.dumpsys(input, output)              // 调用 dumpsys
```

**实现**：
```kotlin
val serviceName = IOUtils.readString(input)
val process = Runtime.getRuntime().exec("dumpsys $serviceName")
// 读取输出并返回
```

---

## 📋 实现优先级建议

### 🔥 第一优先级（性能监控核心）
1. **性能数据采集模块** - CPU、内存、FPS
2. **电池信息采集** - 电量、电压、电流
3. **网络流量统计** - 应用级别流量监控

### ⭐ 第二优先级（增强功能）
4. **完整屏幕截图** - 补充现有功能
5. **实时日志系统** - 便于调试和监控
6. **进程监控** - 系统状态了解

### 💡 第三优先级（扩展功能）
7. **TCP 服务器支持** - 远程连接
8. **系统设置读取** - 系统配置
9. **Java 类反射** - 调试工具
10. **dumpsys 调用** - 系统诊断

---

## 🛠️ 实现建议

### 架构设计

1. **创建 PerformanceModule**：
   - 统一管理所有性能数据采集
   - 支持缓存机制（参考 PerfDog）
   - 使用线程池处理耗时操作

2. **数据采集策略**：
   - 使用 `AtomicBoolean` 控制采集状态
   - 实现数据缓存避免重复采集
   - 支持批量数据返回

3. **通信协议**：
   - 保持现有的简单协议（命令码 + 数据）
   - 或考虑引入 Protocol Buffers（如 PerfDog）

### 代码组织

```
app/src/main/java/com/panda/modules/
├── PerformanceModule.kt      # 性能数据采集（新增）
├── BatteryModule.kt           # 电池信息（新增）
├── NetworkStatsModule.kt     # 网络流量统计（新增）
├── ProcessModule.kt           # 进程监控（新增）
├── LogModule.kt               # 日志系统（新增）
└── ... (现有模块)
```

---

## 📚 参考资源

1. **PerfDog Console 开发文档** - 已提供详细实现参考
2. **Android 官方文档**：
   - [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)
   - [NetworkStatsManager](https://developer.android.com/reference/android/app/usage/NetworkStatsManager)
   - [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager)
3. **系统源码**：
   - `/proc/stat` - CPU 统计
   - `/proc/[pid]/stat` - 进程统计
   - `/sys/class/power_supply/` - 电池信息

---

## ✅ 总结

**核心缺失**：
- ❌ 性能数据采集（CPU、GPU、内存、FPS）
- ❌ 电池信息采集
- ❌ 网络流量统计
- ⚠️ 完整屏幕截图（只有壁纸截图）

**建议优先实现**：
1. 性能数据采集模块（最重要）
2. 电池信息采集
3. 网络流量统计
4. 完整屏幕截图

这些功能是性能监控工具的核心，参考 PerfDog 的实现可以快速开发。

