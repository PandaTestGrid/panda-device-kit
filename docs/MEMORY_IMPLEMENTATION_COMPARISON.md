# 内存数据采集实现对比

本文档对比了 PerfDog Console 官方实现与当前 panda-device-kit 实现的差异。

## 1. 官方实现（PerfDog Console）

### 1.1 核心代码位置
- **文件**: `com.dl.java`
- **方法**: `a(Context, int, long)` - 内存数据采集方法

### 1.2 实现特点

#### 1.2.1 系统内存信息
```java
// 获取系统级内存信息
ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
activityManager.getMemoryInfo(memInfo);
```

#### 1.2.2 进程内存信息
```java
// 获取进程详细内存信息
Debug.MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(new int[]{pid});
Debug.MemoryInfo memoryInfo = memoryInfoArray[0];
```

#### 1.2.3 反射获取高级内存信息
官方实现通过反射调用 `Debug.MemoryInfo` 的隐藏方法获取更详细的内存信息：

```java
// 使用反射获取方法
private static Method b = a("getTotalSwappedOut", new Class<?>[0]);           // 总交换内存
private static Method c = a("getOtherLabel", new Class<?>[]{Integer.TYPE});   // 其他内存标签
private static Method d = a("getOtherPss", new Class<?>[]{Integer.TYPE});     // 其他 PSS 内存
private static Method e = a("hasSwappedOutPss", new Class<?>[0]);             // 是否有交换 PSS
private static Method f = a("getTotalSwappedOutPss", new Class<?>[0]);         // 总交换 PSS
```

#### 1.2.4 返回的数据字段
官方实现返回多个内存字段（通过 builder 模式构建）：
- `getTotalPss()` - 总 PSS 内存 
- `getTotalPrivateDirty()` - 总私有脏页内存
- `getTotalSharedDirty()` - 总共享脏页内存
- `getTotalSwappedOut()` - 总交换内存（通过反射）
- `getTotalSwappedOutPss()` - 总交换 PSS（通过反射）
- `getOtherPss(int)` - 其他 PSS 内存（通过反射）
- 更多字段...

### 1.3 关键 API
- `ActivityManager.getMemoryInfo()` - 获取系统内存信息
- `ActivityManager.getProcessMemoryInfo()` - 获取进程内存信息
- `Debug.MemoryInfo` - 内存详细信息（PSS、Private Dirty、Shared Dirty 等）
- 反射调用 `Debug.MemoryInfo` 的隐藏方法获取高级内存信息

---

## 2. 当前实现（panda-device-kit）

### 2.1 核心代码位置
- **文件**: `app/src/main/java/com/panda/modules/PerformanceModule.kt`
- **方法**: `getProcessMemoryInfo(pid: Int)` - 内存数据采集方法

### 2.2 当前实现

```kotlin
private fun getProcessMemoryInfo(pid: Int): MemoryInfo {
    val context = FakeContext.get()
    val activityManager = context.getSystemService(ActivityManager::class.java)
    
    try {
        val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(pid))
        if (memoryInfoArray.isNotEmpty()) {
            val memInfo = memoryInfoArray[0]
            return MemoryInfo(
                pss = memInfo.totalPss.toLong(),
                privateDirty = memInfo.totalPrivateDirty.toLong(),
                sharedDirty = memInfo.totalSharedDirty.toLong()
            )
        }
    } catch (e: Exception) {
        Logger.error("Error getting process memory info", e)
    }
    
    return MemoryInfo(0, 0, 0)
}
```

### 2.3 当前返回的数据字段
- `totalPss` - 总 PSS 内存 ✅
- `totalPrivateDirty` - 总私有脏页内存 ✅
- `totalSharedDirty` - 总共享脏页内存 ✅

---

## 3. 差异对比

| 特性 | 官方实现 | 当前实现 | 状态 |
|------|---------|---------|------|
| **系统内存信息** | ✅ 使用 `ActivityManager.getMemoryInfo()` | ❌ 未实现 | **缺失** |
| **进程内存信息** | ✅ 使用 `ActivityManager.getProcessMemoryInfo()` | ✅ 已实现 | **一致** |
| **基本内存字段** | ✅ PSS、PrivateDirty、SharedDirty | ✅ 已实现 | **一致** |
| **交换内存信息** | ✅ 通过反射获取 `getTotalSwappedOut()` | ❌ 未实现 | **缺失** |
| **交换 PSS** | ✅ 通过反射获取 `getTotalSwappedOutPss()` | ❌ 未实现 | **缺失** |
| **其他内存标签** | ✅ 通过反射获取 `getOtherLabel(int)` | ❌ 未实现 | **缺失** |
| **其他 PSS** | ✅ 通过反射获取 `getOtherPss(int)` | ❌ 未实现 | **缺失** |
| **数据缓存机制** | ✅ 支持缓存性能数据 | ❌ 未实现 | **缺失** |

---

## 4. 改进建议

### 4.1 短期改进（保持 API 兼容）
1. ✅ **添加系统内存信息采集** - 虽然不返回给客户端，但可用于日志和调试
2. ✅ **添加反射获取高级内存信息** - 增强内部数据采集能力
3. ✅ **添加数据缓存机制** - 提高性能，减少系统调用

### 4.2 长期改进（API 扩展）
1. **扩展内存响应格式** - 返回更多内存字段（需要修改协议）
2. **添加系统内存查询命令** - 新增命令获取系统级内存信息
3. **添加内存详细分析** - 提供更细粒度的内存分析功能

---

## 5. 实现优先级

### 高优先级
- ✅ 添加系统内存信息采集（内部使用）
- ✅ 添加反射获取高级内存信息（内部使用）
- ✅ 添加数据缓存机制

### 中优先级
- 扩展内存响应格式（需要协议变更）
- 添加系统内存查询命令

### 低优先级
- 添加内存详细分析功能
- 优化内存采集性能

---

## 6. 参考文档

- [Android Debug.MemoryInfo 文档](https://developer.android.com/reference/android/os/Debug.MemoryInfo)
- [Android ActivityManager 文档](https://developer.android.com/reference/android/app/ActivityManager)
- [PerfDog Console 开发文档](./PerfDogConsole开发文档.md)

