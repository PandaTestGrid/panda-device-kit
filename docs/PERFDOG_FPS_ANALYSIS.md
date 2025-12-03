# PerfDog Console FPS 实现分析

## PerfDog Console 架构特点

### 1. 双通道通信架构

PerfDog Console 使用**双通道通信**：

1. **TCP 服务器**（端口 43305）
   - 与 PC 端 PerfDog 客户端通信
   - 处理应用管理、日志传输等请求

2. **LocalSocket 连接**（连接到 "perfdog"）
   - 与系统服务通信
   - 处理性能数据采集请求
   - **这是关键！** 性能数据（包括 FPS）是通过系统服务获取的

### 2. 关键代码分析

```java
// com.dj.java - LocalSocket 通信线程
public final class dj implements Runnable {
    private LocalSocket c;
    private rd e = null;  // 性能数据采集器
    
    @Override
    public final void run() {
        // 连接到本地服务 "perfdog"
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress("perfdog"));
        
        // 循环接收消息并处理性能数据请求
        while (true) {
            // 接收消息并处理
        }
    }
}
```

**关键发现**：
- PerfDog Console **不是直接获取 FPS**
- 它通过 LocalSocket 连接到名为 **"perfdog"** 的系统服务
- 性能数据（包括 FPS）由这个系统服务提供

### 3. 性能数据采集流程

```java
// 性能数据请求处理
case 2:  // GETMEMORYUSAGEREQ
    // 使用缓存的性能数据
    if (this.e != null && z) {
        paVarG = pa.o().a(ms.k().a(rd.l()
            .a(this.e.k())  // 使用缓存
            .a(this.e.d)
            .b(this.e.e)
            .a(paVar.l().e))).g();
    } else {
        // 重新采集性能数据
        this.e = dl.a(this.b, paVar.l().d, paVar.l().e);
        paVarG = pa.o().a(ms.k().a(this.e)).g();
    }
```

**特点**：
- 使用缓存机制避免重复采集
- 性能数据采集器 `rd` 通过 `dl.a()` 方法获取
- 数据可能来自系统服务，而不是直接计算

## PerfDog 如何获取 FPS？

### 推测的实现方式

由于文档中没有详细的 FPS 实现代码，但根据架构分析，PerfDog 可能使用以下方式之一：

#### 方式 1: 通过系统服务获取 SurfaceFlinger FPS

PerfDog 可能安装了一个系统级服务，该服务可以：
- 通过反射访问 SurfaceFlinger API
- 获取系统级 FPS（不依赖特定应用）
- 在系统服务中运行，有更高权限

#### 方式 2: 通过 dumpsys SurfaceFlinger

```bash
# 可能的实现方式
dumpsys SurfaceFlinger --latency <window_name>
```

#### 方式 3: 通过系统服务中的 Choreographer

系统服务可能：
- 在系统进程中运行
- 可以访问系统 UI 渲染数据
- 使用 Choreographer 但不受应用窗口限制

## 与我们的实现对比

### 我们的实现（Panda Device Kit）

```kotlin
// 直接在后台服务中使用 Choreographer
private fun startFpsMonitoring(interval: Int) {
    val choreographer = android.view.Choreographer.getInstance()
    frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount.incrementAndGet()
            // 计算 FPS...
        }
    }
    choreographer.postFrameCallback(frameCallback!!)
}
```

**限制**：
- ❌ Choreographer 需要活动的 UI 渲染
- ❌ 后台服务没有 UI 窗口，FPS 始终为 0
- ❌ 无法获取系统级 FPS

### PerfDog 的实现（推测）

```
PC 客户端
    ↓ (TCP)
PerfDog Console (后台服务)
    ↓ (LocalSocket)
PerfDog 系统服务 (系统级服务)
    ↓
获取 FPS (可能通过 SurfaceFlinger 或系统 API)
```

**优势**：
- ✅ 系统服务有更高权限
- ✅ 可以访问 SurfaceFlinger
- ✅ 不依赖应用窗口
- ✅ 可以获取系统级 FPS

## 改进建议

### 方案 1: 通过 dumpsys SurfaceFlinger（推荐）

```kotlin
fun getFpsFromSurfaceFlinger(): Int {
    try {
        val process = Runtime.getRuntime().exec("dumpsys SurfaceFlinger --latency")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        // 解析输出获取 FPS
        // ...
    } catch (e: Exception) {
        Logger.error("Error getting FPS from SurfaceFlinger", e)
    }
    return 0
}
```

**优点**：
- 不需要系统服务
- 可以获取系统级 FPS
- 不依赖应用窗口

**缺点**：
- 需要 root 权限或系统权限
- 需要解析文本输出
- 性能开销较大

### 方案 2: 通过反射 SurfaceFlinger API

```kotlin
fun getFpsFromSurfaceFlingerReflection(): Int {
    try {
        // 通过反射访问 SurfaceFlinger
        val service = ServiceManager.getService("SurfaceFlinger")
        // 调用相关方法获取 FPS
        // ...
    } catch (e: Exception) {
        Logger.error("Error getting FPS via reflection", e)
    }
    return 0
}
```

**优点**：
- 直接 API 调用，性能好
- 可以获取实时 FPS

**缺点**：
- 需要系统权限
- 不同 Android 版本 API 可能不同
- 实现复杂

### 方案 3: 在应用内集成 FPS 监控

如果需要在特定应用中监控 FPS，可以在应用代码中：
1. 使用 Choreographer（在应用主线程）
2. 通过我们的 API 上报 FPS 数据

**优点**：
- 实现简单
- 准确度高
- 不需要系统权限

**缺点**：
- 只能在应用运行时工作
- 需要修改应用代码

## 总结

### PerfDog 的关键优势

1. **系统级服务**：通过 LocalSocket 连接到系统服务，有更高权限
2. **架构分离**：Console 负责通信，系统服务负责数据采集
3. **系统级 FPS**：可以获取不依赖应用窗口的系统级 FPS

### 我们的实现限制

1. **后台服务限制**：Choreographer 需要 UI 渲染
2. **权限限制**：无法直接访问 SurfaceFlinger
3. **架构差异**：单一服务，没有系统服务支持

### 建议

1. **短期方案**：在应用内集成 FPS 监控（方案 3）
2. **长期方案**：实现通过 dumpsys 或反射获取系统级 FPS（方案 1 或 2）
3. **文档说明**：明确说明 FPS 监控的限制和使用场景

## 参考

- PerfDog Console 开发文档
- Android SurfaceFlinger 源码
- Choreographer API 文档

