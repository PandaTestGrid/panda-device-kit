# 📘 Makefile 使用指南

## 🚀 常用命令

### 快速开始

```bash
cd /Users/mini/Desktop/icon-puser/panda

# 查看所有命令
make help

# 快速部署（推荐）
make quick

# 完整流程
make all
```

---

## 📦 构建命令

### `make build`
编译项目（需要在 Android Studio 中先同步）

### `make package`
从 APK 提取 DEX 并打包为 JAR

### `make clean`
清理所有构建产物

---

## 📱 部署命令

### `make deploy`
完整部署流程：
1. 打包 JAR
2. 推送到设备
3. 重启服务

### `make push`
只推送 JAR 到设备

### `make quick`
快速部署（最常用）：
- 打包
- 推送
- 重启
- 设置转发

---

## ⚙️ 服务管理

### `make start`
启动 Panda 服务

### `make stop`
停止 Panda 服务

### `make restart`
重启服务

### `make status`
查看服务状态（PID、Socket）

### `make log`
查看服务日志（最后 20 行）

---

## 🧪 测试命令

### `make forward`
设置 adb 端口转发
```
tcp:9999 -> @panda-1.1.0
```

### `make test`
运行基础功能测试：
- APK 信息
- WiFi 状态
- 自动点击

### `make icons`
提取所有应用图标
- 输出到 `../icons/`
- 338 个应用，约 5 秒

### `make monitor`
启动自动点击监控
- 16 个默认关键词
- 后台自动处理弹框

---

## 🎯 典型工作流

### 首次部署

```bash
cd panda

# 1. 在 Android Studio 中编译项目
#    Build → Make Project

# 2. 完整部署
make all
```

### 代码修改后重新部署

```bash
# 1. 在 Android Studio 中重新编译

# 2. 快速部署
make quick

# 3. 测试
make test
```

### 日常使用

```bash
# 查看状态
make status

# 查看日志
make log

# 提取图标
make icons

# 启动自动监控
make monitor
```

---

## 💡 提示

### 并行执行

```bash
# 同时查看日志
make log &

# 运行测试
make test
```

### 组合使用

```bash
# 部署并测试
make deploy && make test

# 快速部署并提取图标
make quick && make icons
```

---

## 🔧 自定义

### 修改端口

编辑 Makefile：
```makefile
PORT = 8888  # 改为其他端口
```

### 修改设备路径

```makefile
DEVICE_PATH = /sdcard/panda.jar
```

---

## 📊 命令速查表

| 命令 | 用途 | 耗时 |
|------|------|------|
| `make help` | 显示帮助 | <1s |
| `make status` | 查看状态 | <1s |
| `make quick` | 快速部署 | ~5s |
| `make test` | 运行测试 | ~2s |
| `make icons` | 提取图标 | ~5s |
| `make all` | 完整流程 | ~30s |

---

**使用 Makefile 让 Panda 管理变得简单！** ✨

