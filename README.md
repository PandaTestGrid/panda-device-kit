# 🐼 Panda - Android 系统服务工具

> 高性能 Android 远程控制和管理工具  
> 版本: 1.1.0

## 📋 简介

Panda 是一个功能强大的 Android 系统服务工具，提供应用管理、WiFi控制、剪贴板操作、通知管理、性能监控等丰富功能。通过 LocalSocket 通信，实现零延迟的设备控制和实时性能数据采集，提供专业的性能监控能力。

### 核心特性

- ⚡ **高性能图标获取** - 使用 PackageManager API，3秒获取338个应用图标
- 🌐 **完整WiFi管理** - 扫描、连接、配置管理
- 📋 **剪贴板控制** - 读写文本和图片，支持监听
- 🔔 **通知管理** - 获取、响应、清除通知
- 📱 **应用管理** - 列表、信息、启动控制
- 🎵 **音频捕获** - 系统音频和麦克风（Android 11+）
- 💾 **存储管理** - SD卡、USB设备检测
- 🖼️ **截图功能** - 壁纸截取
- ⚙️ **Shell命令** - 远程执行系统命令
- 📊 **性能监控** - CPU、GPU、FPS、内存实时监控
- 🔋 **电池信息** - 电池状态、电量、健康度
- 📡 **网络统计** - 按应用/UID统计网络流量（WiFi/移动网络）
- 🖱️ **自动点击** - 智能点击、坐标点击、按键模拟

## 🚀 快速开始

### 编译

```bash
# 使用 Android Studio（推荐）
1. 打开 Android Studio
2. File → Open → 选择 panda 目录
3. Build → Make Project

# 或使用 Gradle
./gradlew assembleRelease
```

### 部署

```bash
# 上传到设备
adb push app/build/outputs/apk/release/app-release.apk /data/local/tmp/panda.jar

# 启动服务
adb shell "CLASSPATH=/data/local/tmp/panda.jar app_process / com.panda.Main"

# 后台运行
adb shell "nohup sh -c 'CLASSPATH=/data/local/tmp/panda.jar app_process / com.panda.Main > /data/local/tmp/panda.log 2>&1' &"
```

### 连接

```python
import socket

sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
sock.connect('\0panda-1.0.0')

# 发送命令
sock.sendall(struct.pack('>I', command_code))
```

## 📊 API 接口

### 命令列表

| 类别 | 命令 | 功能 | 返回数据类型 |
|------|------|------|-------------|
| **基础** | 0 | 创建虚拟显示器 | int (成功/错误码) |
| | 1 | 系统初始化 | 无返回 |
| **应用管理** | 10 | 获取应用列表 | bitmap (默认图标) + int (数量) + [string (包名), string (版本名), long (版本号), bitmap (图标)] × N |
| | 11 | 获取APK路径 | string (路径) |
| | 12 | 获取相机状态 | int (状态) |
| | 13 | 获取相机列表 | int (数量) + [string (ID), int (方向)] × N |
| | 14 | 启动应用 | int (成功/错误码) |
| | 21 | 获取相机服务信息 | string (信息) |
| **存储** | 20 | 存储设备列表 | int (数量) + [int (类型), string (标签), string (路径)] × N |
| **音频** | 30 | 系统音频捕获 | 流式音频数据 (byte[]) |
| | 31 | 麦克风音频捕获 | 流式音频数据 (byte[]) |
| **文件** | 40 | 文件传输 | 文件数据流 |
| **WiFi** | 50 | 获取WiFi状态 | int (状态) |
| | 51 | 设置WiFi开关 | 无返回 |
| | 52 | 扫描WiFi网络 | int (数量) + [string (SSID), string (BSSID), int (频率), int (标准), int (信号等级)] × N |
| | 53 | 获取WiFi信息 | string (SSID), string (BSSID), int (networkId), int (linkSpeed), int (rssi) |
| | 54 | 获取已配置网络 | int (数量) + [int (networkId), string (SSID)] × N |
| | 55 | 连接网络 | int (成功/错误码) |
| | 56 | 添加网络 | int (成功/错误码) |
| | 57 | 设置自动加入 | 无返回 |
| | 58 | 移除网络 | int (成功/错误码) |
| **系统** | 60 | 获取系统属性 | string (属性值) |
| | 61-65 | 系统操作 | 根据操作类型返回 |
| **剪贴板** | 70 | 获取剪贴板 | int (状态码) + string (MIME类型) + byte[] (数据) 或 int (错误码) + string (错误信息) |
| | 71 | 设置剪贴板 | int (成功/错误码) |
| | 72 | 监听剪贴板 | 流式数据 |
| | 73 | 剪贴板操作 | 根据操作类型返回 |
| **通知** | 80 | 获取通知列表 | int (数量) + [通知详情] × N |
| | 81 | 取消通知 | 无返回 |
| | 82 | 打开/响应通知 | 无返回 |
| | 83 | 清除所有通知 | 无返回 |
| **截图** | 90 | 壁纸截图 | int (图片大小) + byte[] (PNG图片数据) |
| | 120 | 屏幕截图 | int (图片大小) + byte[] (PNG图片数据) |
| **Shell** | 100 | 执行命令 | string (输出) |
| **自动点击** | 110-119 | 智能点击、监控、按键 | 根据操作类型返回 |
| **性能监控** | 200 | 获取CPU使用率 | float (0-100) |
| | 201 | 获取CPU核心使用率 | int (核心数) + float[] (每个核心使用率 0-100) |
| | 202 | 获取CPU频率 | int (核心数) + int[] (每个核心频率, kHz) |
| | 203 | 获取GPU使用率和频率 | float (使用率 0-100), int (频率, kHz) |
| | 204 | 获取FPS | int (帧率) |
| | 205 | 获取进程内存使用 | long (PSS, KB), long (PrivateDirty, KB), long (SharedDirty, KB) |
| | 206 | 获取CPU温度 | float (摄氏度) |
| | 207 | 获取线程CPU使用率 | float (0-100) |
| | 208 | 开始性能分析 | int (成功/错误码) |
| | 209 | 停止性能分析 | int (成功/错误码) |
| **电池信息** | 220 | 获取电池信息 | int (电流, 毫安), int (电压, 毫伏), int (电量 0-100), int (充电状态 0/1), long (时间戳) |
| | 221 | 获取电池电量 | int (0-100) |
| | 222 | 检查电池监控支持 | int (1=支持, 0=不支持) |
| **网络统计** | 230 | 获取指定UID网络流量 | long (总接收), long (总发送), long (WiFi接收), long (WiFi发送), long (移动接收), long (移动发送) |
| | 231 | 获取总网络流量 | long (总接收), long (总发送) |
| | 232 | 获取指定包名网络流量 | int (UID), long (接收), long (发送) |

详细 API 文档请参阅项目 Wiki 或源码注释。

## 🏗️ 项目结构

```
panda/
├── app/src/main/java/com/panda/
│   ├── Main.kt                    # 主入口
│   ├── core/
│   │   └── CommandDispatcher.kt  # 命令分发器
│   ├── modules/
│   │   ├── AppModule.kt          # 应用管理
│   │   ├── WiFiModule.kt         # WiFi管理
│   │   ├── ClipboardModule.kt    # 剪贴板
│   │   ├── NotificationModule.kt # 通知
│   │   ├── StorageModule.kt      # 存储
│   │   ├── AudioModule.kt        # 音频
│   │   ├── SystemModule.kt       # 系统操作
│   │   ├── AutoClickModule.kt    # 自动点击
│   │   ├── CpuModule.kt           # CPU监控
│   │   ├── GpuModule.kt           # GPU监控
│   │   ├── FpsModule.kt           # FPS监控
│   │   ├── MemoryModule.kt        # 内存监控
│   │   ├── BatteryModule.kt       # 电池信息
│   │   └── NetworkStatsModule.kt # 网络统计
│   └── utils/
│       ├── IOUtils.kt            # IO工具
│       ├── Logger.kt             # 日志
│       ├── FakeContext.kt        # Context获取
│       └── ScreenCaptureHelper.kt # 截图辅助
├── build.gradle.kts              # 项目构建配置
└── README.md                     # 本文件
```

## ⚡ 性能特性

### 快速图标获取

使用 Android PackageManager API 直接从系统缓存读取图标：

```kotlin
val icon = packageManager.getDrawable(packageName, iconResourceId, appInfo)
val bitmap = drawableToBitmap(icon, targetSize)
```

**性能数据**:
- 单个应用: ~0.01秒
- 100个应用: ~1秒
- 338个应用: ~3-5秒

### 批量优化

- 512KB 大缓冲区
- 每 100ms 批量刷新
- 即时内存释放

### 性能监控

- **CPU监控**: 整体使用率、核心使用率、频率、温度、线程CPU使用率
- **GPU监控**: 使用率和频率（支持 Qualcomm Adreno、ARM Mali、PowerVR）
- **FPS监控**: 基于 Choreographer 和 SurfaceFlinger 的帧率监控
- **内存监控**: PSS、PrivateDirty、SharedDirty 等详细内存信息
- **网络统计**: 按 UID/包名统计 WiFi 和移动网络流量（需要 `READ_NETWORK_USAGE_HISTORY` 权限）
- **电池信息**: 电池状态、电量、健康度等

## 🔧 使用示例

### Python 客户端

```python
import socket
import struct

class PandaClient:
    def __init__(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.connect('\0panda-1.0.0')
    
    def get_wifi_state(self):
        # 命令 50: 获取 WiFi 状态
        self.sock.sendall(struct.pack('>I', 50))
        state = struct.unpack('>I', self.sock.recv(4))[0]
        return state
    
    def scan_wifi(self):
        # 命令 52: 扫描 WiFi
        self.sock.sendall(struct.pack('>I', 52))
        count = struct.unpack('>I', self.sock.recv(4))[0]
        # 读取网络列表...
        return networks
    
    def get_cpu_usage(self):
        # 命令 200: 获取 CPU 使用率
        self.sock.sendall(struct.pack('>I', 200))
        usage = struct.unpack('>f', self.sock.recv(4))[0]
        return usage
    
    def get_fps(self):
        # 命令 204: 获取 FPS
        self.sock.sendall(struct.pack('>I', 204))
        fps = struct.unpack('>I', self.sock.recv(4))[0]
        return fps
    
    def get_memory_usage(self, pid):
        # 命令 205: 获取进程内存使用
        self.sock.sendall(struct.pack('>I', 205))
        self.sock.sendall(struct.pack('>I', pid))
        pss = struct.unpack('>Q', self.sock.recv(8))[0]
        private_dirty = struct.unpack('>Q', self.sock.recv(8))[0]
        shared_dirty = struct.unpack('>Q', self.sock.recv(8))[0]
        return {'pss': pss, 'private_dirty': private_dirty, 'shared_dirty': shared_dirty}
    
    def get_network_usage(self, uid):
        # 命令 230: 获取指定 UID 的网络流量
        self.sock.sendall(struct.pack('>I', 230))
        self.sock.sendall(struct.pack('>I', uid))
        total_rx = struct.unpack('>Q', self.sock.recv(8))[0]
        total_tx = struct.unpack('>Q', self.sock.recv(8))[0]
        wifi_rx = struct.unpack('>Q', self.sock.recv(8))[0]
        wifi_tx = struct.unpack('>Q', self.sock.recv(8))[0]
        mobile_rx = struct.unpack('>Q', self.sock.recv(8))[0]
        mobile_tx = struct.unpack('>Q', self.sock.recv(8))[0]
        return {
            'total': {'rx': total_rx, 'tx': total_tx},
            'wifi': {'rx': wifi_rx, 'tx': wifi_tx},
            'mobile': {'rx': mobile_rx, 'tx': mobile_tx}
        }
```

### 测试套件

使用 `test_panda.py` 可以对常见模块做冒烟测试，例如：

- `python3 test_panda.py --tests apps` - 拉取应用列表并校验默认图标及样本应用图标
- `python3 test_panda.py --tests wifi storage` - 分别跑 WiFi 与存储检查
- `python3 test_panda.py --tests notifications` - 输出当前通知摘要（含动作信息）

未指定 `--tests` 时，会默认执行除实验性模块以外的所有可用套件。

## 📚 文档

- `README.md` - 本文件
- `CHANGELOG.md` - 版本更新日志
- 源码注释 - 每个方法都有详细说明

## 🛡️ 安全说明

Panda 需要系统级权限运行，请确保：
- 仅在可信设备上使用
- 注意保护敏感数据
- 生产环境请做好安全审计

## 📝 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 开发计划

- [x] v1.1 - 性能监控模块（CPU、GPU、FPS、内存）
- [x] v1.1 - 电池信息模块
- [x] v1.1 - 网络流量统计模块
- [ ] v1.2 - 性能优化和Bug修复
- [ ] v1.3 - 更多系统监控功能

## 📞 支持

- Issues: https://github.com/yourname/panda
- Documentation: 查看源码注释

---

**Panda** - 强大的 Android 系统服务工具  
**版本**: 1.1.0  
**更新时间**: 2025-12-03
# panda-device-kit
