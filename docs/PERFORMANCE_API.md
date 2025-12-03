# 性能数据采集 API 文档

## 📊 新增功能

### 1. 屏幕截图

#### 命令 120: 完整屏幕截图

**功能**: 截取当前屏幕显示内容（不是壁纸）

**请求**: 无参数

**响应**: 
- 图片大小 (int, 字节数)
- PNG 图片数据 (byte[])

**示例**:
```python
# Python 客户端示例
sock.sendall(struct.pack('>I', 120))  # 发送命令 120

# 读取图片大小
size_bytes = sock.recv(4)
size = struct.unpack('>I', size_bytes)[0]

# 读取图片数据
image_data = b''
while len(image_data) < size:
    chunk = sock.recv(min(4096, size - len(image_data)))
    if not chunk:
        break
    image_data += chunk

# 保存图片
with open('screenshot.png', 'wb') as f:
    f.write(image_data)
```

---

### 2. 性能数据采集

#### 命令 200: 获取整体 CPU 使用率

**功能**: 获取系统整体 CPU 使用率

**请求**: 无参数

**响应**: CPU 使用率 (float, 0-100)

**示例**:
```python
sock.sendall(struct.pack('>I', 200))
usage_bytes = sock.recv(4)
usage = struct.unpack('>f', usage_bytes)[0]
print(f"CPU Usage: {usage}%")
```

---

#### 命令 201: 获取 CPU 核心使用率

**功能**: 获取每个 CPU 核心的使用率

**请求**: 无参数

**响应**: 
- 核心数量 (int)
- 每个核心的使用率 (float[], 0-100)

**示例**:
```python
sock.sendall(struct.pack('>I', 201))
core_count = struct.unpack('>I', sock.recv(4))[0]
for i in range(core_count):
    usage = struct.unpack('>f', sock.recv(4))[0]
    print(f"Core {i}: {usage}%")
```

---

#### 命令 202: 获取 CPU 频率

**功能**: 获取每个 CPU 核心的当前频率

**请求**: 无参数

**响应**: 
- 核心数量 (int)
- 每个核心的频率 (int[], kHz)

**示例**:
```python
sock.sendall(struct.pack('>I', 202))
core_count = struct.unpack('>I', sock.recv(4))[0]
for i in range(core_count):
    freq = struct.unpack('>I', sock.recv(4))[0]
    print(f"Core {i}: {freq} kHz")
```

---

#### 命令 203: 获取 GPU 使用率和频率

**功能**: 获取 GPU 使用率和当前频率

**请求**: 无参数

**响应**: 
- GPU 使用率 (float, 0-100)
- GPU 频率 (int, kHz)

**示例**:
```python
sock.sendall(struct.pack('>I', 203))
usage = struct.unpack('>f', sock.recv(4))[0]
freq = struct.unpack('>I', sock.recv(4))[0]
print(f"GPU Usage: {usage}%, Frequency: {freq} kHz")
```

**注意**: GPU 使用率需要设备支持，某些设备可能返回 0

---

#### 命令 204: 获取 FPS（帧率）

**功能**: 获取当前屏幕刷新率（FPS）

**请求**: 无参数

**响应**: FPS (int)

**示例**:
```python
sock.sendall(struct.pack('>I', 204))
fps = struct.unpack('>I', sock.recv(4))[0]
print(f"FPS: {fps}")
```

**注意**: 需要先调用命令 208 启动 FPS 监控

---

#### 命令 205: 获取进程内存使用

**功能**: 获取指定进程的内存使用情况

**请求**: 
- PID (int)

**响应**: 
- PSS (long, KB) - 进程实际使用的物理内存
- Private Dirty (long, KB) - 私有脏页内存
- Shared Dirty (long, KB) - 共享脏页内存

**示例**:
```python
pid = 12345
sock.sendall(struct.pack('>I', 205))
sock.sendall(struct.pack('>I', pid))

pss = struct.unpack('>Q', sock.recv(8))[0]
private_dirty = struct.unpack('>Q', sock.recv(8))[0]
shared_dirty = struct.unpack('>Q', sock.recv(8))[0]

print(f"PID {pid} Memory:")
print(f"  PSS: {pss} KB")
print(f"  Private Dirty: {private_dirty} KB")
print(f"  Shared Dirty: {shared_dirty} KB")
```

---

#### 命令 206: 获取 CPU 温度

**功能**: 获取 CPU 温度

**请求**: 无参数

**响应**: 温度 (float, 摄氏度)

**示例**:
```python
sock.sendall(struct.pack('>I', 206))
temp = struct.unpack('>f', sock.recv(4))[0]
print(f"CPU Temperature: {temp}°C")
```

**注意**: 需要设备支持温度传感器，某些设备可能返回 0

---

#### 命令 207: 获取线程 CPU 使用率

**功能**: 获取指定线程的 CPU 使用率

**请求**: 
- PID (int)
- TID (int)

**响应**: CPU 使用率 (float, 0-100)

**示例**:
```python
pid = 12345
tid = 12346
sock.sendall(struct.pack('>I', 207))
sock.sendall(struct.pack('>I', pid))
sock.sendall(struct.pack('>I', tid))

usage = struct.unpack('>f', sock.recv(4))[0]
print(f"Thread {tid} CPU Usage: {usage}%")
```

---

#### 命令 208: 开始性能分析

**功能**: 启动性能数据采集（主要是 FPS 监控）

**请求**: 
- 监控间隔 (int, 毫秒)

**响应**: 成功 (int, 1=成功, 0=失败)

**示例**:
```python
interval = 1000  # 1秒更新一次
sock.sendall(struct.pack('>I', 208))
sock.sendall(struct.pack('>I', interval))
result = struct.unpack('>I', sock.recv(4))[0]
if result == 1:
    print("Profiling started")
```

---

#### 命令 209: 停止性能分析

**功能**: 停止性能数据采集

**请求**: 无参数

**响应**: 成功 (int, 1=成功, 0=失败)

**示例**:
```python
sock.sendall(struct.pack('>I', 209))
result = struct.unpack('>I', sock.recv(4))[0]
if result == 1:
    print("Profiling stopped")
```

---

## 3. 电池信息采集

#### 命令 220: 获取完整电池信息

**功能**: 获取电池的完整信息

**请求**: 无参数

**响应**: 
- 电流 (int, 毫安)
- 电压 (int, 毫伏)
- 电量 (int, 0-100)
- 充电状态 (int, 0=未充电, 1=充电中)
- 时间戳 (long, 毫秒)

**示例**:
```python
sock.sendall(struct.pack('>I', 220))
current = struct.unpack('>i', sock.recv(4))[0]
voltage = struct.unpack('>i', sock.recv(4))[0]
level = struct.unpack('>i', sock.recv(4))[0]
charging = struct.unpack('>i', sock.recv(4))[0]
timestamp = struct.unpack('>Q', sock.recv(8))[0]

print(f"Battery: {level}%, {voltage}mV, {current}mA, Charging: {charging == 1}")
```

**注意**: 需要 Android 5.0+ (API 21+)

---

#### 命令 221: 获取电池电量

**功能**: 仅获取电池电量百分比

**请求**: 无参数

**响应**: 电量 (int, 0-100)

**示例**:
```python
sock.sendall(struct.pack('>I', 221))
level = struct.unpack('>i', sock.recv(4))[0]
print(f"Battery level: {level}%")
```

---

#### 命令 222: 检查电池监控支持

**功能**: 检查设备是否支持电池监控

**请求**: 无参数

**响应**: 支持 (int, 1=支持, 0=不支持)

**示例**:
```python
sock.sendall(struct.pack('>I', 222))
supported = struct.unpack('>i', sock.recv(4))[0]
if supported == 1:
    print("Battery monitoring is supported")
```

---

## 4. 网络流量统计

#### 命令 230: 获取指定 UID 的网络流量

**功能**: 获取指定 UID 的网络流量统计

**请求**: 
- UID (int)

**响应**: 
- 总接收字节数 (long)
- 总发送字节数 (long)
- WiFi 接收字节数 (long)
- WiFi 发送字节数 (long)
- 移动网络接收字节数 (long)
- 移动网络发送字节数 (long)

**示例**:
```python
uid = 10123  # 应用的 UID
sock.sendall(struct.pack('>I', 230))
sock.sendall(struct.pack('>i', uid))

total_rx = struct.unpack('>Q', sock.recv(8))[0]
total_tx = struct.unpack('>Q', sock.recv(8))[0]
wifi_rx = struct.unpack('>Q', sock.recv(8))[0]
wifi_tx = struct.unpack('>Q', sock.recv(8))[0]
mobile_rx = struct.unpack('>Q', sock.recv(8))[0]
mobile_tx = struct.unpack('>Q', sock.recv(8))[0]

print(f"UID {uid} Network:")
print(f"  Total: RX={total_rx}, TX={total_tx}")
print(f"  WiFi: RX={wifi_rx}, TX={wifi_tx}")
print(f"  Mobile: RX={mobile_rx}, TX={mobile_tx}")
```

**注意**: 需要 Android 6.0+ (API 23+)

---

#### 命令 231: 获取总网络流量

**功能**: 获取所有 UID 的总网络流量

**请求**: 无参数

**响应**: 
- 总接收字节数 (long)
- 总发送字节数 (long)

**示例**:
```python
sock.sendall(struct.pack('>I', 231))
total_rx = struct.unpack('>Q', sock.recv(8))[0]
total_tx = struct.unpack('>Q', sock.recv(8))[0]
print(f"Total network: RX={total_rx}, TX={total_tx}")
```

---

#### 命令 232: 获取指定包名的网络流量

**功能**: 通过包名获取应用的网络流量

**请求**: 
- 包名 (string)

**响应**: 
- UID (int)
- 接收字节数 (long)
- 发送字节数 (long)

**示例**:
```python
package_name = "com.example.app"
sock.sendall(struct.pack('>I', 232))
# 发送包名
sock.sendall(struct.pack('>I', len(package_name)))
sock.sendall(package_name.encode('utf-8'))

uid = struct.unpack('>i', sock.recv(4))[0]
rx_bytes = struct.unpack('>Q', sock.recv(8))[0]
tx_bytes = struct.unpack('>Q', sock.recv(8))[0]

print(f"Package {package_name} (UID {uid}):")
print(f"  RX={rx_bytes}, TX={tx_bytes}")
```

---

## 📝 使用建议

### 性能监控流程

1. **启动监控**:
   ```python
   # 启动 FPS 监控（间隔 1 秒）
   start_profiling(1000)
   ```

2. **定期采集数据**:
   ```python
   while True:
       cpu_usage = get_cpu_usage()
       memory = get_memory_usage(pid)
       fps = get_fps()
       # 记录数据...
       time.sleep(1)
   ```

3. **停止监控**:
   ```python
   stop_profiling()
   ```

### 数据采集频率建议

- **CPU 使用率**: 1-2 秒采集一次
- **内存使用**: 1-2 秒采集一次
- **FPS**: 需要先启动监控，然后每秒查询
- **GPU**: 2-5 秒采集一次（某些设备可能不支持）
- **温度**: 5-10 秒采集一次

### 注意事项

1. **权限要求**: 
   - 某些功能需要 root 权限或系统权限
   - 内存信息需要 `ActivityManager` 权限

2. **设备兼容性**:
   - GPU 使用率：不同设备路径可能不同
   - CPU 温度：需要设备支持温度传感器
   - FPS：通过 Choreographer 实现，兼容性较好

3. **性能影响**:
   - 频繁采集数据会有一定性能开销
   - 建议根据实际需求调整采集频率

---

## 🔧 完整示例

```python
import socket
import struct
import time

class PandaPerformanceClient:
    def __init__(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.connect('\0panda-1.1.0')
    
    def send_command(self, cmd):
        self.sock.sendall(struct.pack('>I', cmd))
    
    def get_cpu_usage(self):
        self.send_command(200)
        return struct.unpack('>f', self.sock.recv(4))[0]
    
    def get_memory_usage(self, pid):
        self.send_command(205)
        self.sock.sendall(struct.pack('>I', pid))
        pss = struct.unpack('>Q', self.sock.recv(8))[0]
        private = struct.unpack('>Q', self.sock.recv(8))[0]
        shared = struct.unpack('>Q', self.sock.recv(8))[0]
        return {'pss': pss, 'private': private, 'shared': shared}
    
    def get_fps(self):
        self.send_command(204)
        return struct.unpack('>I', self.sock.recv(4))[0]
    
    def start_profiling(self, interval=1000):
        self.send_command(208)
        self.sock.sendall(struct.pack('>I', interval))
        return struct.unpack('>I', self.sock.recv(4))[0] == 1
    
    def stop_profiling(self):
        self.send_command(209)
        return struct.unpack('>I', self.sock.recv(4))[0] == 1
    
    def screenshot(self, filename='screenshot.png'):
        self.send_command(120)
        size = struct.unpack('>I', self.sock.recv(4))[0]
        data = b''
        while len(data) < size:
            chunk = self.sock.recv(min(4096, size - len(data)))
            if not chunk:
                break
            data += chunk
        with open(filename, 'wb') as f:
            f.write(data)
        return len(data)
    
    def get_battery_info(self):
        self.send_command(220)
        current = struct.unpack('>i', self.sock.recv(4))[0]
        voltage = struct.unpack('>i', self.sock.recv(4))[0]
        level = struct.unpack('>i', self.sock.recv(4))[0]
        charging = struct.unpack('>i', self.sock.recv(4))[0]
        timestamp = struct.unpack('>Q', self.sock.recv(8))[0]
        return {
            'current': current,
            'voltage': voltage,
            'level': level,
            'charging': charging == 1,
            'timestamp': timestamp
        }
    
    def get_network_usage(self, uid):
        self.send_command(230)
        self.sock.sendall(struct.pack('>i', uid))
        total_rx = struct.unpack('>Q', self.sock.recv(8))[0]
        total_tx = struct.unpack('>Q', self.sock.recv(8))[0]
        wifi_rx = struct.unpack('>Q', self.sock.recv(8))[0]
        wifi_tx = struct.unpack('>Q', self.sock.recv(8))[0]
        mobile_rx = struct.unpack('>Q', self.sock.recv(8))[0]
        mobile_tx = struct.unpack('>Q', self.sock.recv(8))[0]
        return {
            'total_rx': total_rx,
            'total_tx': total_tx,
            'wifi_rx': wifi_rx,
            'wifi_tx': wifi_tx,
            'mobile_rx': mobile_rx,
            'mobile_tx': mobile_tx
        }
    
    def get_network_usage_by_package(self, package_name):
        self.send_command(232)
        # 发送包名
        pkg_bytes = package_name.encode('utf-8')
        self.sock.sendall(struct.pack('>I', len(pkg_bytes)))
        self.sock.sendall(pkg_bytes)
        
        uid = struct.unpack('>i', self.sock.recv(4))[0]
        rx = struct.unpack('>Q', self.sock.recv(8))[0]
        tx = struct.unpack('>Q', self.sock.recv(8))[0]
        return {'uid': uid, 'rx': rx, 'tx': tx}

# 使用示例
client = PandaPerformanceClient()

# 启动监控
client.start_profiling(1000)

# 采集数据
for i in range(10):
    cpu = client.get_cpu_usage()
    fps = client.get_fps()
    battery = client.get_battery_info()
    print(f"CPU: {cpu:.1f}%, FPS: {fps}, Battery: {battery['level']}%")
    time.sleep(1)

# 停止监控
client.stop_profiling()

# 截图
client.screenshot('screenshot.png')

# 获取网络流量
network = client.get_network_usage_by_package('com.example.app')
print(f"Network: RX={network['rx']}, TX={network['tx']}")
```

---

## 📚 参考

- 参考 PerfDog Console 实现
- Android 系统文件: `/proc/stat`, `/proc/[pid]/stat`
- Android API: `ActivityManager`, `Choreographer`

