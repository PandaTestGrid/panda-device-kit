# 🐼 Panda - Android 系统服务工具

> 高性能 Android 远程控制和管理工具  
> 版本: 1.1.0

## 📋 简介

Panda 是一个功能强大的 Android 系统服务工具，提供应用管理、WiFi控制、剪贴板操作、通知管理等丰富功能。通过 LocalSocket 通信，实现零延迟的设备控制。

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

| 类别 | 命令 | 功能 |
|------|------|------|
| **基础** | 0-1 | 虚拟显示器、初始化 |
| **应用管理** | 10-14, 21 | 应用列表、APK信息、图标、启动 |
| **存储** | 20 | 存储设备列表 |
| **音频** | 30-31 | 系统音频、麦克风捕获 |
| **文件** | 40 | 文件传输 |
| **WiFi** | 50-58 | 状态、扫描、连接、配置 |
| **系统** | 60-65 | 系统属性、文件操作 |
| **剪贴板** | 70-73 | 读写、监听 |
| **通知** | 80-83 | 获取、响应、清除 |
| **截图** | 90 | 壁纸截图 |
| **Shell** | 100 | 命令执行 |
| **自动点击** | 110-119 | 智能点击、监控、按键 |

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
│   │   └── SystemModule.kt       # 系统操作
│   └── utils/
│       ├── IOUtils.kt            # IO工具
│       ├── Logger.kt             # 日志
│       └── FakeContext.kt        # Context获取
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
```

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

- [ ] v1.1 - 完善音频捕获功能
- [ ] v1.2 - 添加文件管理功能
- [ ] v1.3 - 性能优化和Bug修复

## 📞 支持

- Issues: https://github.com/yourname/panda
- Documentation: 查看源码注释

---

**Panda** - 强大的 Android 系统服务工具  
**版本**: 1.0.0  
**更新时间**: 2025-10-19
# panda-device-kit
