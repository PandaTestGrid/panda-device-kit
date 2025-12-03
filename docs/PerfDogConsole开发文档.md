# PerfDog Console 开发文档

## 项目概述

PerfDog Console 是一个 Android 性能监控工具的后端服务，用于与 PC 端 PerfDog 客户端通信，实时采集并传输设备性能数据。

**版本号**: 1.6.99  
**主要功能**: 性能数据采集、网络通信、应用管理、系统交互

---

## 目录

1. [系统架构](#1-系统架构)
2. [网络通信模块](#2-网络通信模块)
3. [性能数据采集](#3-性能数据采集)
4. [电池信息采集](#4-电池信息采集)
5. [网络流量统计](#5-网络流量统计)
6. [屏幕截图功能](#6-屏幕截图功能)
7. [应用管理功能](#7-应用管理功能)
8. [日志系统](#8-日志系统)
9. [进程监控](#9-进程监控)
10. [系统交互功能](#10-系统交互功能)

---

## 1. 系统架构

### 1.1 启动流程

**核心代码位置**: `com.perfdog.cmd.Console.main()`

```50:141:decompiled/PerfDogConsole/sources/com/perfdog/cmd/Console.java
public static void main(java.lang.String[] r6) throws java.lang.InterruptedException {
    // 权限降级处理（从 root 降级到 shell）
    int r2 = android.os.Process.myUid()
    if (r2 != 0) goto L14
    r2 = 2000(0x7d0, float:2.803E-42)
    android.os.Process.setGid(r2)
    android.os.Process.setUid(r2)
    
    // 初始化 Android 环境
    android.os.Looper.prepareMainLooper()
    com.b.a()  // 初始化 ActivityThread
    com.a r3 = com.a.a()  // 获取 Context
    
    // 启动模式判断
    if (r3 == 0) {
        // 无参数：输出应用列表
        com.gi r6 = com.gh.k()
        // ... 获取应用列表并输出
    } else if (r6[0].equals("--start")) {
        // --start 参数：启动服务器模式
        android.os.Process.setArgV0("PerfDogServerExt")
        
        // 启动 LocalSocket 通信线程
        java.lang.Thread r6 = new java.lang.Thread(new com.dj(r3))
        r6.start()
        
        // 启动 TCP 服务器（端口 43305）
        java.net.ServerSocket r3 = new java.net.ServerSocket(43305, 128)
        java.lang.Thread r4 = new java.lang.Thread(new com.di(r3, r3))
        r4.start()
        
        System.out.println("PerfDogServerExt Started")
        r6.join()
    }
}
```

**关键点**:
- 支持 root 权限降级，从 uid 0 降级到 2000（shell 用户）
- 两种启动模式：应用列表模式、服务器模式
- 服务器模式监听端口：**43305**

---

## 2. 网络通信模块

### 2.1 TCP 服务器

**核心代码位置**: `com.di.java`

```7:26:decompiled/PerfDogConsole/sources/com/di.java
public final class di implements Runnable {
    private Context a;
    private final ServerSocket b;

    public di(Context context, ServerSocket serverSocket) {
        this.a = context;
        this.b = serverSocket;
    }

    @Override
    public final void run() {
        while (true) {
            try {
                // 接受客户端连接，为每个连接创建新线程
                new Thread(new dh(this.b.accept(), this.a)).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
```

**功能说明**:
- 监听端口 43305，接受 PC 端连接
- 每个客户端连接创建独立线程处理
- 使用 `dh` 类处理具体的客户端请求

### 2.2 客户端连接处理

**核心代码位置**: `com.dh.java`

```17:177:decompiled/PerfDogConsole/sources/com/dh.java
public final class dh implements Cdo<eh>, Runnable {
    private Socket b;
    private final Context c;
    private boolean d = false;

    public dh(Socket socket, Context context) {
        this.b = socket;
        this.c = context;
    }

    @Override
    public final void run() throws IOException {
        // 循环读取客户端请求
        while (!this.d && dl.a(this.b, new dn() {
            @Override
            public final Object parse(byte[] bArr) {
                return eh.a(bArr);  // 解析 Protocol Buffer 消息
            }
        }, this, null)) {
            // 处理请求...
        }
    }

    @Override
    public final void onResponse(eh ehVar) {
        // 根据请求类型分发处理
        switch (ej.a(ehVar2.d).ordinal()) {
            case 1:  // GET_APP_RUNNING_PROCESS_REQ
                // 获取运行进程信息
                ActivityManager activityManager = (ActivityManager) this.c.getSystemService("activity");
                for (ActivityManager.RunningAppProcessInfo processInfo : 
                     activityManager.getRunningAppProcesses()) {
                    // 处理进程信息...
                }
                break;
            case 2:  // GET_APP_INFO_REQ
                // 获取应用列表
                Console.a(this.c, new dq<hn>() {
                    @Override
                    public final void accept(Object obj) {
                        this.f$0.a((hn) obj);
                    }
                });
                break;
            case 3:  // LISTENLOGREQ
                // 建立日志监听连接
                wb wbVarA = wb.a();
                wbVarA.e = this.b;
                this.d = true;
                break;
            case 4:  // GETPACKAGEINFOREQ
                // 获取包信息
                ei eiVarK = eh.k();
                ehVarG = eiVarK.a(Console.a(context, ebVarK.d)).g();
                break;
        }
        
        // 发送响应
        if (ehVarG != null) {
            a(ehVarG);
        }
    }

    private void a(eh ehVar) {
        synchronized (this.b) {
            try {
                dl.a(ehVar, this.b);  // 发送响应数据
            } catch (Exception e) {
                a.c(e.toString());
            }
        }
    }
}
```

### 2.3 数据序列化与传输

**核心代码位置**: `com.dl.java`

```143:188:decompiled/PerfDogConsole/sources/com/dl.java
// 发送数据到 Socket
public static <T extends ce> void a(T t2, Socket socket) throws IOException {
    a(t2, socket.getOutputStream());
}

// 发送数据到 LocalSocket
public static <T extends ce> void a(T t2, LocalSocket localSocket) throws IOException {
    a(t2, localSocket.getOutputStream());
}

// 核心发送方法
private static <T extends ce> void a(T t2, OutputStream outputStream) throws IOException {
    DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
    if (t2 != null) {
        // 先写入数据长度（4字节）
        dataOutputStream.writeInt(t2.j());
        // 写入 Protocol Buffer 序列化数据
        t2.a(dataOutputStream);
    } else {
        dataOutputStream.writeInt(0);
    }
    dataOutputStream.flush();
}

// 接收数据
private static <T> boolean a(InputStream inputStream, dn<T> dnVar, Cdo<T> cdo, dp dpVar) 
        throws IOException {
    DataInputStream dataInputStream = new DataInputStream(inputStream);
    byte[] bArr = new byte[10240];
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    
    // 读取数据长度（4字节）
    int i2 = dataInputStream.readInt();
    if (i2 == 0) {
        return false;
    }
    
    // 分块读取数据
    while (i2 > 0) {
        int i3 = dataInputStream.read(bArr, 0, Math.min(i2, 10240));
        if (-1 == i3) {
            return false;
        }
        i2 -= i3;
        byteArrayOutputStream.write(bArr, 0, i3);
    }
    
    // 解析 Protocol Buffer 并回调
    cdo.onResponse(dnVar.parse(byteArrayOutputStream.toByteArray()));
    return true;
}
```

**协议格式**:
- 前 4 字节：数据长度（int）
- 后续字节：Protocol Buffer 序列化的消息体

### 2.4 LocalSocket 通信

**核心代码位置**: `com.dj.java`

```18:79:decompiled/PerfDogConsole/sources/com/dj.java
public final class dj implements Runnable {
    private Context b;
    private LocalSocket c;
    private ExecutorService d = Executors.newSingleThreadExecutor();
    private rd e = null;  // 性能数据采集器
    private AtomicBoolean f = new AtomicBoolean(true);
    private PowerManager g;

    @Override
    public final void run() throws InterruptedException, IOException {
        while (true) {
            try {
                // 连接到本地服务 "perfdog"
                LocalSocket localSocket = new LocalSocket();
                this.c = localSocket;
                localSocket.connect(new LocalSocketAddress("perfdog"));
                
                // 发送初始化消息
                dl.a(pa.o().a(tz.k().a(pq.k()
                    .a(dl.a(this.b))  // 设备信息
                    .b(Build.VERSION.SDK_INT >= 23)  // Android 版本
                    .g()).g()).g(), this.c);
                
                // 循环接收消息
                while (true) {
                    dl.a(this.c, new dn() {
                        @Override
                        public final Object parse(byte[] bArr) {
                            return pa.a(bArr);
                        }
                    }, new Cdo() {
                        @Override
                        public final void onResponse(Object obj) {
                            this.f$0.a((pa) obj);  // 处理消息
                        }
                    }, null);
                }
            } catch (Exception e) {
                a.a("", (Throwable) e);
                try {
                    this.c.close();
                } catch (IOException unused) {
                }
                Thread.sleep(1000L);  // 重连等待
            }
        }
    }
}
```

**功能说明**:
- 通过 LocalSocket 与系统服务通信
- 自动重连机制
- 使用线程池处理性能数据采集请求

---

## 3. 性能数据采集

### 3.1 支持的数据类型

**核心代码位置**: `com.kd.java`

```4:27:decompiled/PerfDogConsole/sources/com/kd.java
public enum kd implements bq {
    NONE(0),
    CPU_USAGE(1),              // CPU 使用率
    CPU_FREQ(2),               // CPU 频率
    GPU_USAGE(3),              // GPU 使用率
    GPU_FREQ(4),               // GPU 频率
    FPS(5),                    // 帧率
    NETWORK(6),                // 网络流量
    SCREENSHOT(8),             // 截图
    MEMORY_USAGE(9),           // 内存使用率
    POWER(10),                 // 功耗
    CPU_TEMPERATURE(11),       // CPU 温度
    FRAME_DETAIL(12),          // 帧详情
    MEMORY_DETAIL(13),         // 内存详情
    CPU_CORE_USAGE(14),        // CPU 核心使用率
    NORMALIZED_CPU_USAGE(15),  // 归一化 CPU 使用率
    NORMALIZED_CPU_CORE_USAGE(16),  // 归一化 CPU 核心使用率
    CONNECTION_DETAIL(22),     // 连接详情
    THREAD_CPU_USAGE(23),      // 线程 CPU 使用率
    SCREEN_BRIGHTNESS(24),     // 屏幕亮度
    BATTERY_LEVEL(25),         // 电池电量
    IRIS_FPS(26),              // Iris FPS
    THERMAL_STATUS(27),        // 热状态
    UNRECOGNIZED(-1);
}
```

### 3.2 内存数据采集

**核心代码位置**: `com.dl.java` (方法 `a(Context, int, long)`)

```83:89:decompiled/PerfDogConsole/sources/com/dl.java
static com.rd a(android.content.Context r17, int r18, long r19) {
    // 该方法通过反射调用 Debug.MemoryInfo 获取内存信息
    // 包括：
    // - getTotalSwappedOut()  // 总交换内存
    // - getOtherLabel(int)     // 其他内存标签
    // - getOtherPss(int)       // 其他 PSS 内存
    // - hasSwappedOutPss()     // 是否有交换 PSS
    // - getTotalSwappedOutPss() // 总交换 PSS
    
    // 使用反射获取方法
    private static Method b = a("getTotalSwappedOut", new Class<?>[0]);
    private static Method c = a("getOtherLabel", new Class<?>[]{Integer.TYPE});
    private static Method d = a("getOtherPss", new Class<?>[]{Integer.TYPE});
    private static Method e = a("hasSwappedOutPss", new Class<?>[0]);
    private static Method f = a("getTotalSwappedOutPss", new Class<?>[0]);
    
    // 通过 ActivityManager 获取进程内存信息
    ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
    activityManager.getMemoryInfo(memInfo);
    
    // 获取进程详细内存信息
    Debug.MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(new int[]{pid});
    Debug.MemoryInfo memoryInfo = memoryInfoArray[0];
    
    // 构建返回数据
    return rd.k()
        .a(memoryInfo.getTotalPss())
        .b(memoryInfo.getTotalPrivateDirty())
        .c(memoryInfo.getTotalSharedDirty())
        // ... 更多内存字段
        .g();
}
```

**关键 API**:
- `ActivityManager.getMemoryInfo()` - 获取系统内存信息
- `ActivityManager.getProcessMemoryInfo()` - 获取进程内存信息
- `Debug.MemoryInfo` - 内存详细信息（PSS、Private Dirty、Shared Dirty 等）

### 3.3 性能数据请求处理

**核心代码位置**: `com.dj.java` 的 `a(pa)` 方法

```139:266:decompiled/PerfDogConsole/sources/com/dj.java
private void a(final pa paVar) {
    switch (AnonymousClass1.b[pc.a(paVar.d).ordinal()]) {
        case 1:  // PROFILEREQ - 开始性能分析
            this.e = null;
            this.f.set(true);
            break;
            
        case 2:  // GETMEMORYUSAGEREQ - 获取内存使用
            final boolean z = !this.f.get();
            this.d.execute(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.a(z, paVar);
                }
            });
            break;
            
        case 3:  // GETBATTERYINFOREQ - 获取电池信息
            paVarG2 = pa.o().a(dl.a(this.b, paVar.m().d, 
                paVar.m().e, paVar.m().k().d)).g();
            break;
            
        case 5:  // GETNETWORKUSAGEREQ - 获取网络使用
            mz mzVarK = my.k();
            if (dl.a(this.b, paVar.d == 40 ? (mv) paVar.e : mv.k(), mzVarK)) {
                paVarG2 = pa.o().a(mzVarK).g();
            }
            break;
    }
    
    // 发送响应
    if (paVarG2 != null) {
        synchronized (this) {
            try {
                dl.a(paVarG2, this.c);
            } catch (IOException e4) {
                a.c(e4.toString());
            }
        }
    }
}

// 内存数据采集实现
private void a(boolean z, pa paVar) {
    if (this.e != null && z) {
        // 使用缓存的性能数据
        paVarG = pa.o().a(ms.k().a(rd.l()
            .a(this.e.k())
            .a(this.e.d)
            .b(this.e.e)
            .a(paVar.l().e))).g();
    } else {
        // 重新采集性能数据
        this.f.set(false);
        this.e = dl.a(this.b, paVar.l().d, paVar.l().e);
        this.f.set(true);
        paVarG = pa.o().a(ms.k().a(this.e)).g();
    }
    
    // 发送响应
    synchronized (this) {
        try {
            dl.a(paVarG, this.c);
        } catch (IOException e) {
            a.c(e.toString());
        }
    }
}
```

---

## 4. 电池信息采集

### 4.1 电池信息获取

**核心代码位置**: `com.dl.java`

```91:141:decompiled/PerfDogConsole/sources/com/dl.java
public static ln a(Context context, long j2, boolean z, boolean z2) throws IOException {
    if (Build.VERSION.SDK_INT >= 21) {
        // 获取 BatteryManager
        if (h == null) {
            h = (BatteryManager) context.getSystemService("batterymanager");
        }
        
        // 获取电池电流（微安）
        int intProperty = h.getIntProperty(2);  // BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
        if (Math.abs(intProperty) > 10000) {
            intProperty /= 1000;  // 转换为毫安
        }
        
        // 获取电池电压（毫伏）
        int iA = a();  // 从 battery service 获取
        if (Math.abs(iA) > 10000) {
            iA /= 1000;  // 转换为伏
        }
        
        // 如果是充电状态，电流翻倍
        if (z2) {
            iA <<= 1;
        }
        
        // 构建返回数据
        return ln.k().a(rk.k()
            .a(intProperty)  // 电流
            .b(iA)           // 电压
            .a(z)            // 是否充电
            .a(j2)           // 时间戳
            .g()).g();
    }
    return ln.l();
}

// 从 battery service 获取电压
private static int a() throws IOException {
    IBinder service = ServiceManager.getService("battery");
    if (service != null) {
        try {
            // 创建管道
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try {
                // 调用 dump 方法获取电池信息
                service.dump(pipe[1].getFileDescriptor(), new String[0]);
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                        new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])));
                
                // 解析输出，查找 voltage 字段
                while (reader.ready()) {
                    String line = reader.readLine().trim();
                    if (line.startsWith("voltage:")) {
                        int voltage = Integer.parseInt(line.substring(8).trim());
                        reader.close();
                        return voltage;
                    }
                }
                reader.close();
            } finally {
                pipe[0].close();
                pipe[1].close();
            }
        } catch (Exception e2) {
            a.a("", (Throwable) e2);
        }
    }
    return 0;
}

// 检查是否支持电池监控
public static boolean a(Context context) {
    if (Build.VERSION.SDK_INT >= 21) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService("batterymanager");
        h = batteryManager;
        if (batteryManager != null && batteryManager.getIntProperty(2) != 0) {
            return true;
        }
    }
    return false;
}
```

**关键 API**:
- `BatteryManager.getIntProperty(2)` - 获取当前电池电流（BATTERY_PROPERTY_CURRENT_NOW）
- `ServiceManager.getService("battery")` - 获取电池服务，通过 dump 获取电压信息

**返回数据**:
- 电流（毫安）
- 电压（毫伏）
- 充电状态
- 时间戳

---

## 5. 网络流量统计

### 5.1 网络流量获取

**核心代码位置**: `com.dl.java`

```210:242:decompiled/PerfDogConsole/sources/com/dl.java
public static boolean a(Context context, mv mvVar, mz mzVar) 
        throws IllegalAccessException, NoSuchFieldException, RemoteException, 
               SecurityException, IllegalArgumentException {
    if (Build.VERSION.SDK_INT < 23) {
        return false;  // 需要 Android 6.0+
    }
    
    try {
        // 获取 NetworkStatsManager
        NetworkStatsManager networkStatsManager = 
            (NetworkStatsManager) context.getSystemService("netstats");
        
        // 通过反射设置 Context（某些版本需要）
        Field declaredField = networkStatsManager.getClass().getDeclaredField("mContext");
        declaredField.setAccessible(true);
        declaredField.set(networkStatsManager, context);
        
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        
        // 强制刷新统计数据
        networkStatsManager.setPollForce(true);
        NetworkStats networkStatsQuerySummary = networkStatsManager.querySummary(
            1,  // TYPE_MOBILE
            null, 
            Long.MIN_VALUE, 
            Long.MAX_VALUE);
        networkStatsQuerySummary.close();
        
        // 恢复正常轮询
        networkStatsManager.setPollForce(false);
        NetworkStats networkStatsQuerySummary2 = networkStatsManager.querySummary(
            0,  // TYPE_WIFI
            null, 
            Long.MIN_VALUE, 
            Long.MAX_VALUE);
        networkStatsQuerySummary2.close();
        
        // 统计指定 UID 的流量
        long rxBytes = 0;
        long txBytes = 0;
        for (NetworkStats networkStats : Arrays.asList(
            networkStatsQuerySummary, networkStatsQuerySummary2)) {
            while (networkStats.getNextBucket(bucket)) {
                if (bucket.getUid() == mvVar.e && bucket.getTag() == 0) {
                    rxBytes += bucket.getRxBytes();  // 接收字节数
                    txBytes += bucket.getTxBytes();  // 发送字节数
                }
            }
        }
        
        // 构建返回数据
        mzVar.a(mvVar.d)      // 包名
            .a(mvVar.e)       // UID
            .b(rxBytes)       // 接收字节数
            .c(txBytes);      // 发送字节数
        return true;
    } catch (Exception e2) {
        a.a("", (Throwable) e2);
        return false;
    }
}
```

**关键 API**:
- `NetworkStatsManager.querySummary()` - 查询网络统计摘要
- `NetworkStats.Bucket` - 网络统计桶，包含 UID、接收/发送字节数等信息

**注意事项**:
- 需要 Android 6.0+ (API 23+)
- 需要 `READ_NETWORK_USAGE_HISTORY` 权限
- 通过反射设置 Context 以绕过某些限制

---

## 6. 屏幕截图功能

### 6.1 截图实现

**核心代码位置**: `com.dl.java`

```244:366:decompiled/PerfDogConsole/sources/com/dl.java
// 获取 HardwareBuffer（截图缓冲区）
private static HardwareBuffer a(IBinder iBinder, int i2, int i3) 
        throws IllegalAccessException, InstantiationException, 
               ClassNotFoundException, IllegalArgumentException, InvocationTargetException {
    // 延迟初始化截图 API
    if (!m) {
        m = true;
        try {
            // 尝试使用 ScreenCapture API（Android 12+）
            Class.forName("android.window.ScreenCapture$DisplayCaptureArgs");
            n = true;
        } catch (ClassNotFoundException unused) {
            // 降级到 ScreenCaptureInternal
            vj vjVar = a;
            vjVar.b("ScreenCapture无效,尝试ScreenCaptureInternal");
            try {
                Class<?> cls = Class.forName("android.window.ScreenCaptureInternal$DisplayCaptureArgs");
                Class<?> cls2 = Class.forName("android.window.ScreenCaptureInternal$DisplayCaptureArgs$Builder");
                q = cls2.getConstructor(IBinder.class);
                r = cls2.getMethod("setSize", Integer.TYPE, Integer.TYPE);
                s = cls2.getMethod("build", new Class[0]);
                p = Class.forName("android.window.ScreenCaptureInternal").getMethod("captureDisplay", cls);
                t = Class.forName("android.window.ScreenCaptureInternal$ScreenshotHardwareBuffer")
                    .getMethod("getHardwareBuffer", new Class[0]);
                n = true;
                o = true;  // 标记使用内部 API
                vjVar.b("使用ScreenCaptureInternal");
            } catch (Exception e2) {
                a.a("ScreenCaptureInternal无效,不支持截屏", (Throwable) e2);
            }
        }
    }
    
    if (!n) {
        return null;
    }
    
    if (!o) {
        // 使用公开 API
        ScreenCapture.DisplayCaptureArgs.Builder builder = 
            new ScreenCapture.DisplayCaptureArgs.Builder(iBinder);
        builder.setSize(i2, i3);
        ScreenCapture.ScreenshotHardwareBuffer screenshot = 
            ScreenCapture.captureDisplay(builder.build());
        if (screenshot == null) {
            return null;
        }
        return screenshot.getHardwareBuffer();
    } else {
        // 使用反射调用内部 API
        try {
            Object builder = q.newInstance(iBinder);
            r.invoke(builder, Integer.valueOf(i2), Integer.valueOf(i3));
            Object screenshot = p.invoke(null, s.invoke(builder, new Object[0]));
            if (screenshot == null) {
                return null;
            }
            return (HardwareBuffer) t.invoke(screenshot, new Object[0]);
        } catch (Exception e3) {
            a.a("", (Throwable) e3);
            return null;
        }
    }
}

// 获取截图字节数组
public static byte[] a(Context context, int i2) 
        throws IllegalAccessException, NoSuchMethodException, 
               InstantiationException, ClassNotFoundException, 
               SecurityException, IllegalArgumentException, InvocationTargetException {
    // 初始化 DisplayControl
    if (!i) {
        Class<?> clsLoadClass = ((ClassLoader) Class.forName("com.android.internal.os.ClassLoaderFactory")
            .getDeclaredMethod("createClassLoader", String.class, String.class, 
                String.class, ClassLoader.class, Integer.TYPE, Boolean.TYPE, String.class)
            .invoke(null, System.getenv("SYSTEMSERVERCLASSPATH"), null, null, 
                ClassLoader.getSystemClassLoader(), 0, Boolean.TRUE, null))
            .loadClass("com.android.server.display.DisplayControl");
        j = clsLoadClass.getMethod("getPhysicalDisplayToken", Long.TYPE);
        
        // 加载系统库
        Method declaredMethod = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
        declaredMethod.setAccessible(true);
        declaredMethod.invoke(Runtime.getRuntime(), clsLoadClass, "android_servers");
        i = true;
    }
    
    // 获取 Display
    if (k == null) {
        DisplayManager displayManager = (DisplayManager) context.getSystemService("display");
        k = displayManager;
        l = displayManager.getDisplay(0);
    }
    
    if (l == null) {
        a.c("getDisplay return null");
        return null;
    }
    
    // 获取 DisplayInfo
    DisplayInfo displayInfo = new DisplayInfo();
    if (!l.getDisplayInfo(displayInfo)) {
        a.c("getDisplayInfo return false");
        return null;
    }
    
    // 解析 uniqueId 获取物理显示 ID
    if (displayInfo.uniqueId == null || !displayInfo.uniqueId.contains(":")) {
        a.c("display uniqueId invalid");
        return null;
    }
    
    try {
        // 获取 Display Token
        IBinder iBinder = (IBinder) j.invoke(null, 
            Long.valueOf(Long.parseLong(displayInfo.uniqueId.split(":")[1])));
        if (iBinder == null) {
            a.c("getDisplayToken return null");
            return null;
        }
        
        // 计算截图尺寸（考虑旋转）
        dm dmVar = new dm(displayInfo.logicalWidth, displayInfo.logicalHeight, 
            i2, displayInfo.rotation);
        // ... 尺寸计算逻辑 ...
        
        // 获取 HardwareBuffer
        HardwareBuffer hardwareBufferA = a(iBinder, dmVar.a, dmVar.b);
        if (hardwareBufferA == null) {
            a.c("getHardwareBuffer return null");
            return null;
        }
        
        // 转换为字节数组（通过 JNI）
        byte[] bArrAsByteArray = NativeInterface.asByteArray(hardwareBufferA, dmVar.d);
        hardwareBufferA.close();
        return bArrAsByteArray;
    } catch (Exception unused) {
        a.a("invalid physicalDisplayId:{}", (Object) 0L);
        return null;
    }
}
```

**Native 接口**:

```6:16:decompiled/PerfDogConsole/sources/com/perfdog/app/NativeInterface.java
public class NativeInterface {
    public static native byte[] asByteArray(HardwareBuffer hardwareBuffer, int i);

    static {
        try {
            System.load("/data/local/tmp/libPerfDogConsole64.so");
        } catch (Throwable unused) {
            System.load("/data/local/tmp/libPerfDogConsole32.so");
        }
    }
}
```

**关键步骤**:
1. 获取 Display Token（通过 DisplayControl.getPhysicalDisplayToken）
2. 调用 ScreenCapture API 获取 HardwareBuffer
3. 通过 JNI 将 HardwareBuffer 转换为字节数组
4. 考虑屏幕旋转，调整截图尺寸

**注意事项**:
- 优先使用公开 API（Android 12+）
- 降级使用内部 API（ScreenCaptureInternal）
- 需要系统权限或 root 权限

---

## 7. 应用管理功能

### 7.1 获取应用列表

**核心代码位置**: `com.perfdog.cmd.Console.java`

```193:316:decompiled/PerfDogConsole/sources/com/perfdog/cmd/Console.java
public static void a(Context context, dq<hn> dqVar) throws Resources.NotFoundException {
    ho hoVarK = hn.k();
    PackageManager packageManager = context.getPackageManager();
    Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
    int currentUser = ActivityManager.getCurrentUser();
    
    // 获取已安装应用列表
    List<ApplicationInfo> installedApplicationsAsUser;
    if (Build.VERSION.SDK_INT < 26) {
        installedApplicationsAsUser = a(128, currentUser);  // 使用反射
    } else {
        installedApplicationsAsUser = packageManager.getInstalledApplicationsAsUser(128, currentUser);
    }
    
    // 遍历应用
    for (ApplicationInfo applicationInfo : installedApplicationsAsUser) {
        try {
            intent.setPackage(applicationInfo.packageName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 只处理有主 Activity 的应用
        if (!context.getPackageManager().queryIntentActivitiesAsUser(intent, 0, currentUser).isEmpty()) {
            hoVarK.c();
            
            // 设置应用信息
            boolean z = true;
            if ((applicationInfo.flags & 1) != 0) {  // FLAG_SYSTEM
                try {
                    hoVarK.a(true);  // 系统应用
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }
            
            hoVarK.a(applicationInfo.packageName);  // 包名
            hoVarK.a(applicationInfo.uid);          // UID
            
            if ((applicationInfo.flags & 2) == 0) {  // FLAG_DEBUGGABLE
                z = false;
            }
            hoVarK.b(z);  // 是否可调试
            
            // 获取元数据
            if (applicationInfo.metaData != null) {
                for (String str : applicationInfo.metaData.keySet()) {
                    hoVarK.a(str, String.valueOf(applicationInfo.metaData.get(str)));
                }
            }
            
            // 获取版本信息
            try {
                PackageInfo packageInfoA = a(packageManager, applicationInfo.packageName, 0, currentUser);
                hoVarK.b(packageInfoA.versionName == null ? "null" : packageInfoA.versionName);
                if (Build.VERSION.SDK_INT >= 28) {
                    hoVarK.e(String.valueOf(packageInfoA.getLongVersionCode()));
                } else {
                    hoVarK.e(String.valueOf(packageInfoA.versionCode));
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            
            // 获取应用名称
            try {
                hoVarK.c(applicationInfo.loadLabel(context.getPackageManager()).toString());
            } catch (Exception e4) {
                hoVarK.c(applicationInfo.packageName);
                e4.printStackTrace();
            }
            
            // 获取应用图标
            try {
                int i = applicationInfo.icon;
                AssetManager assetManager = new AssetManager();
                assetManager.addAssetPath(applicationInfo.sourceDir);
                Resources resources = context.getResources();
                Drawable drawable = new Resources(assetManager, resources.getDisplayMetrics(), 
                    resources.getConfiguration()).getDrawable(i);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                a(drawable).compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream);
                hoVarK.a(ae.a(byteArrayOutputStream.toByteArray()));  // Base64 编码图标
                assetManager.close();
            } catch (Exception e5) {
                e5.printStackTrace();
            }
            
            // 获取进程名列表
            try {
                PackageInfo packageArchiveInfo = a(packageManager, applicationInfo.packageName, 15, currentUser);
                if (packageArchiveInfo != null) {
                    TreeSet treeSet = new TreeSet();
                    
                    // 收集 Activity 进程名
                    if (packageArchiveInfo.activities != null) {
                        for (ActivityInfo activityInfo : packageArchiveInfo.activities) {
                            if (activityInfo.processName != null) {
                                treeSet.add(activityInfo.processName);
                            }
                        }
                    }
                    
                    // 收集 Receiver 进程名
                    if (packageArchiveInfo.receivers != null) {
                        for (ActivityInfo activityInfo2 : packageArchiveInfo.receivers) {
                            if (activityInfo2.processName != null) {
                                treeSet.add(activityInfo2.processName);
                            }
                        }
                    }
                    
                    // 收集 Provider 进程名
                    if (packageArchiveInfo.providers != null) {
                        for (ProviderInfo providerInfo : packageArchiveInfo.providers) {
                            if (providerInfo.processName != null) {
                                treeSet.add(providerInfo.processName);
                            }
                        }
                    }
                    
                    // 收集 Service 进程名
                    if (packageArchiveInfo.services != null) {
                        for (ServiceInfo serviceInfo : packageArchiveInfo.services) {
                            if (serviceInfo.processName != null) {
                                treeSet.add(serviceInfo.processName);
                            }
                        }
                    }
                    
                    treeSet.remove(applicationInfo.packageName);  // 移除主进程名
                    
                    // 添加其他进程名
                    Iterator it = treeSet.iterator();
                    while (it.hasNext()) {
                        hoVarK.d((String) it.next());
                    }
                }
            } catch (Exception e6) {
                e6.printStackTrace();
            }
            
            // 回调返回应用信息
            try {
                dqVar.accept(hoVarK.g());
            } catch (Exception e6) {
                e6.printStackTrace();
            }
        }
    }
}
```

### 7.2 获取应用信息

```318:334:decompiled/PerfDogConsole/sources/com/perfdog/cmd/Console.java
public static ee a(Context context, String str) {
    ef efVarK = ee.k();
    try {
        PackageInfo packageInfoA = a(context.getPackageManager(), str, 0, 
            ActivityManager.getCurrentUser());
        efVarK.a(true);  // 应用存在
        efVarK.a(packageInfoA.applicationInfo.uid);  // UID
        efVarK.a(packageInfoA.versionName == null ? "null" : packageInfoA.versionName);  // 版本名
        if (Build.VERSION.SDK_INT >= 28) {
            efVarK.b(String.valueOf(packageInfoA.getLongVersionCode()));  // 版本号
        } else {
            efVarK.b(String.valueOf(packageInfoA.versionCode));
        }
    } catch (Exception e) {
        a.a("getPackageInfo:", (Throwable) e);
    }
    return efVarK.g();
}
```

**返回的应用信息包括**:
- 包名
- UID
- 版本名和版本号
- 应用名称
- 应用图标（Base64 编码）
- 是否系统应用
- 是否可调试
- 进程名列表
- 元数据

---

## 8. 日志系统

### 8.1 日志收集与传输

**核心代码位置**: `com.wb.java`

```12:89:decompiled/PerfDogConsole/sources/com/wb.java
public final class wb {
    private static final wb f = new wb();
    private static final SimpleDateFormat g = new SimpleDateFormat("HH:mm:ss.SSS");
    public final m<wc> a = m.a(50);  // 日志队列（容量 50）
    public final LinkedList<wc> b = new LinkedList<>();
    public final ReentrantLock c;
    public final Condition d;
    public Socket e;  // 日志传输 Socket
    private final Thread h;

    public static wb a() {
        return f;
    }

    public wb() {
        ReentrantLock reentrantLock = new ReentrantLock();
        this.c = reentrantLock;
        this.d = reentrantLock.newCondition();
        
        // 启动日志传输线程
        Thread thread = new Thread(new Runnable() {
            @Override
            public final void run() {
                this.f$0.c();  // 日志传输循环
            }
        });
        this.h = thread;
        thread.start();
    }

    // 添加日志
    public final void a(int i, String str, String str2, Throwable th) {
        try {
            this.c.lock();
            // 添加到队列
            this.a.add(new wc(System.currentTimeMillis(), i, str, str2, th));
            if (this.e != null) {
                this.d.signal();  // 唤醒传输线程
            }
        } finally {
            this.c.unlock();
        }
    }

    // 日志传输循环
    private void c() {
        ei eiVarK = eh.k();
        while (true) {
            try {
                this.c.lock();
                // 等待 Socket 连接或日志数据
                if (this.e == null || (this.a.isEmpty() && this.b.isEmpty())) {
                    this.d.await();
                }
                
                // 将新日志移到发送队列
                this.b.addAll(this.a);
                this.a.clear();
                Socket socket = this.e;
                this.c.unlock();
                
                try {
                    // 构建日志消息
                    eiVarK.c();
                    hd hdVarK = hc.k();
                    Iterator<wc> it = this.b.iterator();
                    while (it.hasNext()) {
                        hdVarK.a(it.next().toString());  // 添加日志行
                    }
                    eiVarK.a(hdVarK.g());
                    
                    // 发送日志
                    dl.a(eiVarK.g(), socket);
                    this.b.clear();
                } catch (IOException unused) {
                    // 连接断开，清理
                    this.c.lock();
                    dl.a(this.e);
                    this.e = null;
                }
            } catch (InterruptedException unused2) {
                return;
            } finally {
                // ...
            }
        }
    }
}
```

**功能说明**:
- 使用有界队列（容量 50）缓存日志
- 使用锁和条件变量实现线程安全的日志传输
- 支持实时日志传输到 PC 端
- 自动处理连接断开和重连

**日志格式**:
- 时间戳（HH:mm:ss.SSS）
- 日志级别
- 标签（Tag）
- 消息内容
- 异常堆栈（可选）

---

## 9. 进程监控

### 9.1 获取运行进程

**核心代码位置**: `com.dh.java`

```74:128:decompiled/PerfDogConsole/sources/com/dh.java
// 处理 GET_APP_RUNNING_PROCESS_REQ 请求
case 1:
    ActivityManager activityManager = (ActivityManager) this.c.getSystemService("activity");
    dz dzVarK = dy.k();
    
    try {
        // 设置发送缓冲区大小
        this.b.setSendBufferSize(524288);
    } catch (Exception unused) {
    }
    
    StringBuilder sb = new StringBuilder("RunningAppProcesses:\n");
    int i2 = 0;
    
    // 遍历所有运行进程
    for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : 
         activityManager.getRunningAppProcesses()) {
        sb.append(runningAppProcessInfo.pid);
        sb.append(",");
        sb.append(runningAppProcessInfo.processName);
        sb.append(",");
        sb.append(runningAppProcessInfo.flags);
        sb.append(",");
        sb.append(runningAppProcessInfo.importance);
        sb.append(";");
        
        String[] strArr = runningAppProcessInfo.pkgList;
        int length = strArr.length;
        int i3 = 0;
        while (true) {
            if (i3 < length) {
                String str = strArr[i3];
                if (str.equals(dvVarK.d)) {  // 匹配目标包名
                    // 获取进程命令行
                    dt dtVarA = ds.k()
                        .a(runningAppProcessInfo.pid)
                        .a(a(runningAppProcessInfo.pid));  // 获取 cmdline
                    
                    // 判断是否前台进程
                    if ((runningAppProcessInfo.flags & 4) <= 0 || 
                        runningAppProcessInfo.importance != 100) {
                        z = false;
                        dzVarK.a(dtVarA.a(z).g());
                    } else {
                        int i4 = i2 + 1;
                        if (i2 == 0) {
                            i2 = i4;
                            z = true;  // 第一个匹配的进程标记为前台
                            dzVarK.a(dtVarA.a(z).g());
                        } else {
                            i2 = i4;
                            z = false;
                            dzVarK.a(dtVarA.a(z).g());
                        }
                    }
                } else {
                    i3++;
                }
            }
        }
    }
    a.b(sb.toString());
    ehVarG = eh.k().a(dzVarK.g()).g();
    break;

// 获取进程命令行
private static String a(int i) throws IllegalAccessException, IOException, 
        IllegalArgumentException, InvocationTargetException {
    try {
        BufferedReader bufferedReaderA = t.a(
            new File(String.format("/proc/%d/cmdline", Integer.valueOf(i))), 
            StandardCharsets.UTF_8);
        try {
            String strTrim = bufferedReaderA.readLine().trim();
            bufferedReaderA.close();
            return strTrim;
        } finally {
        }
    } catch (Exception e) {
        return "error:" + e;
    }
}
```

**返回的进程信息**:
- PID
- 进程名
- 命令行参数（从 /proc/pid/cmdline 读取）
- 是否前台进程
- 进程标志
- 重要性级别

---

## 10. 系统交互功能

### 10.1 Shell 命令执行

**协议支持**: `EXECUTESHELLCOMMANDREQ`、`SHELLREQ`

通过 LocalSocket 接收命令执行请求，调用系统 Shell 执行命令并返回结果。

### 10.2 dumpsys 调用

**协议支持**: `DUMPSYSREQ`、`DUMPSYSRSP`

调用 Android 系统的 `dumpsys` 命令获取系统服务信息。

### 10.3 Java 类反射

**核心代码位置**: `com.dj.java`

```170:193:decompiled/PerfDogConsole/sources/com/dj.java
case 6:  // REFLECTJAVACLASSREQ
    pb pbVarO = pa.o();
    tu tuVarK = tt.k();
    try {
        // 加载类
        Class<?> cls = Class.forName((paVar.d == 56 ? (tq) paVar.e : tq.k()).d);
        
        // 遍历类层次结构
        do {
            os osVarK = or.k();
            osVarK.a(cls.getName());
            
            // 获取非静态字段
            for (Field field : cls.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    osVarK.a(ox.k()
                        .a(Modifier.toString(field.getModifiers()))  // 修饰符
                        .b(field.getType().getTypeName())            // 类型
                        .c(field.getName())                          // 字段名
                        .g());
                }
            }
            tuVarK.a(osVarK.g());
            cls = cls.getSuperclass();
        } while (cls != Object.class);
    } catch (Throwable th) {
        StringWriter stringWriter = new StringWriter();
        th.printStackTrace(new PrintWriter(stringWriter));
        pbVarO.a(ku.k()
            .a(kw.kGeneralError)
            .a(stringWriter.toString())
            .g());
    }
    pbVarO.a(tuVarK.g());
    paVarG2 = pbVarO.g();
    break;
```

**功能说明**:
- 通过反射获取指定类的字段信息
- 遍历整个类层次结构（包括父类）
- 只返回非静态字段
- 返回字段的修饰符、类型和名称

### 10.4 系统设置读取

**核心代码位置**: `com.dk.java`

```8:22:decompiled/PerfDogConsole/sources/com/dk.java
public final class dk {
    private static final dk b = new dk();
    final f a;

    public static dk a() {
        return b;
    }

    public dk() throws IllegalAccessException, NoSuchFieldException, 
            IllegalArgumentException, InvocationTargetException {
        e eVarA = g.a();
        Binder binder = new Binder();
        // 获取 Settings ContentProvider
        IContentProvider iContentProviderA = eVarA.a("settings", binder);
        this.a = iContentProviderA == null ? null : 
            new f(eVarA, iContentProviderA, "settings", binder);
    }
}
```

**使用示例**（从 `dj.java` 获取屏幕亮度）:

```224:231:decompiled/PerfDogConsole/sources/com/dj.java
case 1:  // SCREEN_BRIGHTNESS
    try {
        paVarG = pa.o().a(lt.k()
            .a(paVar.n().d)
            .a(rx.k().a(Integer.parseInt(
                dk.a().a.a("system", "screen_brightness"))).g()).g()).g();
        paVarG2 = paVarG;
    } catch (Exception e3) {
        a.c(e3.toString());
    }
    break;
```

---

## 通信协议总结

### 协议类型枚举

**PC 端协议** (`com.pc.java`):
- `PROFILEREQ(1)` / `PROFILERSP(2)` - 性能分析请求/响应
- `GETMEMORYUSAGEREQ(3)` / `GETMEMORYUSAGERSP(4)` - 内存使用请求/响应
- `GETBATTERYINFOREQ(17)` / `GETBATTERYINFORSP(18)` - 电池信息请求/响应
- `GETNETWORKUSAGEREQ(40)` / `GETNETWORKUSAGERSP(41)` - 网络使用请求/响应
- `HEARTBEATREQ(35)` / `HEARTBEATRSP(36)` - 心跳请求/响应
- `LISTENLOGREQ(54)` / `LOGMESSAGE(55)` - 日志监听请求/消息
- 等 60+ 种协议类型

**应用端协议** (`com.eu.java`):
- `GETAPPINFOREQ(1)` / `GETAPPINFORSP(2)` - 应用信息请求/响应
- `GETSCREENINFOREQ(3)` / `GETSCREENINFORSP(4)` - 屏幕信息请求/响应
- `STARTNETSIMULATIONREQ(17)` / `STARTNETSIMULATIONRSP(18)` - 网络模拟请求/响应
- 等 20+ 种协议类型

### 数据格式

所有协议消息使用 **Protocol Buffers** 序列化：
- 消息长度（4 字节 int）+ 消息体（Protocol Buffer 二进制数据）
- 通过 `DataInputStream`/`DataOutputStream` 传输

---

## 开发注意事项

### 权限要求

1. **系统权限**: 部分功能需要系统签名或 root 权限
   - 截图功能需要系统权限
   - 网络统计需要 `READ_NETWORK_USAGE_HISTORY` 权限
   - 电池信息需要系统权限

2. **Android 版本兼容性**:
   - 网络统计：Android 6.0+ (API 23+)
   - 电池信息：Android 5.0+ (API 21+)
   - 截图功能：根据 API 版本选择不同实现

### 性能优化

1. **内存数据缓存**: 使用 `AtomicBoolean` 和缓存机制避免重复采集
2. **线程池**: 使用 `ExecutorService` 处理耗时操作
3. **缓冲区大小**: Socket 发送缓冲区设置为 512KB

### 错误处理

1. **自动重连**: LocalSocket 连接断开后自动重连（1 秒延迟）
2. **异常捕获**: 所有关键操作都有异常处理
3. **降级策略**: 截图功能支持 API 降级

---

## 总结

PerfDog Console 是一个功能完整的 Android 性能监控后端服务，主要特点：

1. **多协议支持**: 60+ 种通信协议，覆盖性能监控的各个方面
2. **实时数据采集**: CPU、GPU、内存、网络、电池等全方位监控
3. **灵活的系统交互**: Shell 命令、dumpsys、反射等系统级操作
4. **稳定的通信机制**: TCP + LocalSocket 双通道，自动重连
5. **完善的日志系统**: 实时日志传输，支持多级别日志

该工具为 Android 应用性能分析和优化提供了强大的数据支持。

