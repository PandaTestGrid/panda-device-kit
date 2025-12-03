# FPS 监控测试说明

## 概述

Panda 设备工具包提供了 FPS（帧率）监控功能，可以实时获取 Android 设备的屏幕刷新率。

## 工作原理

FPS 监控使用 Android 的 `Choreographer` API 来监听帧渲染事件。`Choreographer` 会在每一帧渲染时调用注册的 `FrameCallback`，通过统计一定时间内的帧数来计算 FPS。

## 重要限制

⚠️ **Choreographer 的限制**：

1. **需要活动的 UI 渲染**：`Choreographer` 的 `FrameCallback` 只有在有 UI 渲染时才会被调用
2. **需要活动的应用窗口**：如果设备当前没有活动的应用窗口，FPS 将始终为 0
3. **屏幕必须唤醒**：如果屏幕处于休眠状态，不会触发帧回调

### 为什么在后台服务中 FPS 为 0？

- Panda 服务运行在后台，没有活动的 UI 窗口
- 没有 UI 渲染，`Choreographer` 不会触发 `FrameCallback`
- 因此 FPS 值始终为 0

**这是正常现象，不是 bug！**

## 如何获取有效的 FPS？

### 方法 1: 在实际应用中测试（推荐）

1. **确保设备屏幕已唤醒**
2. **打开一个应用**（如设置、浏览器、游戏等）
3. **在应用运行时运行测试脚本**：
   ```bash
   python3 test_fps.py --duration 10
   ```
4. **如果应用有动画或滚动**，FPS 值会更准确

### 方法 2: 在应用内集成 FPS 监控

如果需要在特定应用中监控 FPS，可以在应用代码中：

1. 启动 FPS 监控（命令 208）
2. 定期获取 FPS 值（命令 204）
3. 停止 FPS 监控（命令 209）

## 测试脚本使用

### 基本用法

```bash
# 完整监控测试（默认，监控 10 秒）
python3 test_fps.py

# 简单测试（不启动监控）
python3 test_fps.py --mode simple

# 自定义参数
python3 test_fps.py --duration 20 --interval 500 --sample 0.3
```

### 参数说明

- `--mode`: 测试模式
  - `simple`: 简单测试，直接获取 FPS
  - `monitor`: 完整监控测试（默认）
- `--duration`: 监控持续时间（秒），默认 10 秒
- `--interval`: FPS 计算间隔（毫秒），默认 1000ms
- `--sample`: 采样间隔（秒），默认 0.5 秒

## API 使用

### 启动 FPS 监控

```python
# 命令 208: 启动性能分析
sock.sendall(struct.pack('>I', 208))
sock.sendall(struct.pack('>i', 1000))  # 间隔 1000ms
result = struct.unpack('>i', sock.recv(4))[0]  # 1=成功, 0=失败
```

### 获取 FPS

```python
# 命令 204: 获取 FPS
sock.sendall(struct.pack('>I', 204))
fps = struct.unpack('>I', sock.recv(4))[0]
print(f"FPS: {fps}")
```

### 停止 FPS 监控

```python
# 命令 209: 停止性能分析
sock.sendall(struct.pack('>I', 209))
result = struct.unpack('>i', sock.recv(4))[0]  # 1=成功, 0=失败
```

## 实际测试场景

### 场景 1: 测试游戏 FPS

1. 打开游戏应用
2. 进入游戏场景（有动画/渲染）
3. 运行测试脚本：
   ```bash
   python3 test_fps.py --duration 30 --interval 1000
   ```

### 场景 2: 测试应用滚动性能

1. 打开一个列表应用（如设置、联系人等）
2. 开始滚动列表
3. 同时运行测试脚本：
   ```bash
   python3 test_fps.py --duration 10 --sample 0.2
   ```

### 场景 3: 测试动画性能

1. 打开有动画的应用（如启动器、过渡动画等）
2. 触发动画
3. 运行测试脚本监控 FPS

## 常见问题

### Q: 为什么 FPS 始终为 0？

A: 这是因为：
- 设备当前没有活动的应用窗口
- 屏幕处于休眠状态
- Choreographer 无法获取帧回调

**解决方案**：确保设备屏幕已唤醒，并打开一个应用后再测试。

### Q: FPS 值不准确怎么办？

A: 
- 增加监控间隔（`--interval` 参数）
- 确保应用有持续的 UI 渲染
- 多次测试取平均值

### Q: 可以在后台服务中获取 FPS 吗？

A: 不可以。`Choreographer` 需要活动的 UI 渲染才能工作。如果需要系统级 FPS，可以考虑：
- 通过 `dumpsys SurfaceFlinger` 获取（需要 root 权限）
- 使用其他系统级监控工具

## 技术细节

### Choreographer 工作原理

1. `Choreographer.getInstance()` 获取主线程的 Choreographer 实例
2. `postFrameCallback()` 注册帧回调
3. 每一帧渲染时，`doFrame()` 被调用
4. 统计一定时间内的帧数，计算 FPS

### 计算公式

```
FPS = (帧数 × 1000) / 时间间隔(ms)
```

例如：在 1000ms 内收到 60 帧，FPS = (60 × 1000) / 1000 = 60

## 总结

FPS 监控功能正常工作，但在后台服务中返回 0 是**正常现象**。要获取有效的 FPS 值，需要：

1. ✅ 设备屏幕已唤醒
2. ✅ 有活动的应用窗口
3. ✅ 应用有 UI 渲染

在实际应用中测试时，FPS 监控功能会正常工作并提供准确的帧率数据。

