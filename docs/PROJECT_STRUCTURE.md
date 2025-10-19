# 📂 项目目录结构

## icon-puser/ (根目录)

### 核心文档
- `README.md` - 项目总览和介绍
- `PROJECT_SUMMARY.md` - 完整项目总结
- `QUICK_START.md` - 快速开始指南
- `USAGE.md` - 使用说明
- `PROJECT_STRUCTURE.md` - 本文件

### 使用脚本
- `test_panda.py` - 基础功能测试
- `get_all_icons.py` - 批量提取应用图标
- `start_autoclick.py` - 启动自动点击监控
- `check_autoclick.py` - 查看监控状态
- `stop_autoclick.py` - 停止监控

### 参考工具
- `tango.jar` - 原始 Tango 工具（304 KB）

### 输出目录
- `icons/` - 提取的 338 个应用图标

---

## panda/ (Panda 项目)

### 核心文件
- `panda.jar` - 编译产物（1.4 MB）**可执行**
- `README.md` - 项目说明
- `API_REFERENCE.md` - 完整 API 文档
- `LICENSE` - MIT 开源许可
- `Makefile` - 自动化构建、部署、测试
- `deploy.sh` - 部署脚本

### 配置文件
- `build.gradle.kts` - 项目构建配置
- `settings.gradle.kts` - Gradle 设置
- `gradle.properties` - Gradle 属性
- `local.properties` - 本地配置（SDK 路径）

### 文档目录 (docs/)
- `CHANGELOG.md` - 版本更新日志
- `ROADMAP.md` - 功能路线图
- `AUTOCLICK_ALGORITHM.md` - 自动点击算法说明
- `MAKEFILE_GUIDE.md` - Makefile 使用指南

### 源码目录 (app/src/main/)
```
app/src/main/
├── AndroidManifest.xml
└── java/com/panda/
    ├── Main.kt                    # 主入口
    ├── core/                      # 核心框架
    │   ├── CommandDispatcher.kt  # 命令分发器
    │   └── InstrumentShellWrapper.kt  # UiAutomation 包装
    ├── modules/                   # 功能模块
    │   ├── AppModule.kt          # 应用管理
    │   ├── WiFiModule.kt         # WiFi 管理
    │   ├── ClipboardModule.kt    # 剪贴板
    │   ├── NotificationModule.kt # 通知管理
    │   ├── StorageModule.kt      # 存储设备
    │   ├── AudioModule.kt        # 音频捕获
    │   ├── SystemModule.kt       # 系统操作
    │   └── AutoClickModule.kt    # 自动点击
    ├── utils/                     # 工具类
    │   ├── IOUtils.kt            # IO 工具
    │   ├── Logger.kt             # 日志工具
    │   └── FakeContext.kt        # Context 获取
    └── mirror/                    # 反射框架
        ├── Reflection.kt         # 反射工具类
        ├── AndroidMirror.kt      # Android 系统反射
        └── SystemServices.kt     # 系统服务反射
```

---

## 📊 文件统计

### icon-puser 根目录
- 文档: 5 个
- 脚本: 5 个
- 参考工具: 1 个
- 图标目录: 1 个

### panda 项目
- Kotlin 源文件: 17 个
- 配置文件: 6 个
- 文档: 6 个 (4个在 docs/)
- 脚本: 2 个

**总计**: 约 40+ 个文件，结构清晰

---

## 🎯 快速导航

### 开发相关
- 源码: `panda/app/src/main/java/com/panda/`
- 构建: `panda/Makefile`
- 配置: `panda/build.gradle.kts`

### 文档相关
- 使用说明: `README.md`, `USAGE.md`
- API 文档: `panda/API_REFERENCE.md`
- 版本历史: `panda/docs/CHANGELOG.md`
- 功能规划: `panda/docs/ROADMAP.md`

### 运行测试
- 基础测试: `test_panda.py`
- 提取图标: `get_all_icons.py`
- 自动点击: `start_autoclick.py`

---

**项目结构清晰，易于维护和扩展！** ✨

