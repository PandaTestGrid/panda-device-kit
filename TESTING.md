# 测试脚本使用说明

本项目提供了多个 Python 测试脚本，用于测试 Panda 设备工具包的各种功能模块。

## 📋 测试脚本列表

| 脚本 | 功能 | 测试命令 |
|------|------|----------|
| `test_cpu.py` | CPU 性能监控 | 200, 201, 202, 206, 207 |
| `test_gpu.py` | GPU 性能监控 | 203 |
| `test_fps.py` | FPS 性能监控 | 204, 208, 209 |
| `test_memory.py` | 内存监控 | 205 |
| `test_battery.py` | 电池信息 | 220, 221, 222 |
| `test_network_stats.py` | 网络流量统计 | 230, 231, 232 |
| `test_wifi.py` | WiFi 管理 | 50, 52, 53, 54 |
| `test_all.py` | 综合测试 | 运行所有测试 |

## 🚀 使用方法

### 前置条件

1. 确保 Panda 服务已启动
2. 确保 Python 3.x 已安装
3. 确保设备已连接（通过 adb）

### 运行单个测试

```bash
# 测试 CPU 性能监控
python3 test_cpu.py

# 测试 GPU 性能监控
python3 test_gpu.py

# 测试 FPS 性能监控
python3 test_fps.py

# 测试内存监控
python3 test_memory.py

# 测试电池信息
python3 test_battery.py

# 测试网络流量统计
python3 test_network_stats.py

# 测试 WiFi 管理
python3 test_wifi.py
```

### 运行所有测试

```bash
# 运行综合测试（会依次执行所有测试脚本）
python3 test_all.py
```

## 📊 测试输出示例

### CPU 测试输出

```
==================================================
CPU 性能监控测试
==================================================

=== 测试 CPU 使用率 (命令 200) ===
CPU 使用率: 15.23%

=== 测试 CPU 核心使用率 (命令 201) ===
CPU 核心数: 8
  核心 0: 12.45%
  核心 1: 18.32%
  ...
平均使用率: 15.23%

=== 测试 CPU 频率 (命令 202) ===
CPU 核心数: 8
  核心 0: 2400000 kHz (2400.00 MHz)
  ...

=== 测试 CPU 温度 (命令 206) ===
CPU 温度: 45.50°C

==================================================
测试结果汇总
==================================================
CPU 使用率: ✓ 通过
CPU 核心使用率: ✓ 通过
CPU 频率: ✓ 通过
CPU 温度: ✓ 通过
线程 CPU 使用率: ✓ 通过
```

### GPU 测试输出

```
==================================================
GPU 性能监控测试
==================================================

=== 测试 GPU 使用率和频率 (命令 203) ===
GPU 使用率: 25.50%
GPU 频率: 500000 kHz (500.00 MHz)

==================================================
测试结果汇总
==================================================
GPU 使用率和频率: ✓ 通过
```

### FPS 测试输出

```
==================================================
FPS 性能监控测试
==================================================

=== 测试 FPS (命令 204) ===
当前 FPS: 0

=== 测试开始性能分析 (命令 208) ===
监控间隔: 1000ms
性能分析已启动

--- 连续获取 FPS 5 次 ---
  第 1 次: 60 FPS
  第 2 次: 60 FPS
  ...
平均 FPS: 60.00

=== 测试停止性能分析 (命令 209) ===
性能分析已停止
```

## 🔧 测试脚本说明

### test_cpu.py

测试 CPU 相关功能：
- 整体 CPU 使用率
- 各核心 CPU 使用率
- CPU 频率
- CPU 温度
- 线程 CPU 使用率

### test_gpu.py

测试 GPU 相关功能：
- GPU 使用率
- GPU 频率

### test_fps.py

测试 FPS 相关功能：
- 获取当前 FPS
- 启动性能分析
- 停止性能分析
- 连续获取 FPS 值

### test_memory.py

测试内存相关功能：
- 获取进程内存使用（PSS、PrivateDirty、SharedDirty）
- 支持测试指定 PID 的进程

### test_battery.py

测试电池相关功能：
- 电池信息（电流、电压、电量、充电状态、时间戳）
- 电池电量
- 电池监控支持检查

### test_network_stats.py

测试网络流量统计功能：
- 总网络流量
- 按 UID 统计网络流量
- 按包名统计网络流量

### test_wifi.py

测试 WiFi 管理功能：
- WiFi 状态
- 扫描 WiFi 网络
- 当前连接的 WiFi 信息
- 已配置的网络列表

## ⚠️ 注意事项

1. **权限要求**: 某些测试需要系统权限，确保 Panda 服务以系统权限运行
2. **网络统计**: 网络流量统计需要 `READ_NETWORK_USAGE_HISTORY` 权限
3. **FPS 监控**: FPS 监控需要启动性能分析后才能获取准确值
4. **WiFi 扫描**: WiFi 扫描可能需要几秒钟时间
5. **电池信息**: 某些设备可能不支持电池电流监控

## 🐛 故障排除

### 连接失败

```
连接失败: [Errno 111] Connection refused
```

**解决方法**:
1. 检查 Panda 服务是否已启动
2. 检查 socket 名称是否正确（`\0panda-1.0.0`）
3. 确保设备已通过 adb 连接

### 测试失败

如果某个测试失败：
1. 检查服务日志：`adb shell cat /data/local/tmp/panda.log`
2. 确认设备支持该功能
3. 检查权限是否足够

## 📝 扩展测试

你可以基于现有测试脚本创建自定义测试：

```python
#!/usr/bin/env python3
import socket
import struct

SOCKET_NAME = '\0panda-1.0.0'

sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
sock.connect(SOCKET_NAME)

# 发送命令
sock.sendall(struct.pack('>I', 200))  # 命令 200: CPU 使用率

# 接收响应
usage = struct.unpack('>f', sock.recv(4))[0]
print(f"CPU 使用率: {usage}%")

sock.close()
```

## 📚 相关文档

- [README.md](README.md) - 项目主文档
- [PERFORMANCE_API.md](docs/PERFORMANCE_API.md) - 性能 API 文档
- 源码注释 - 每个模块的详细实现说明

