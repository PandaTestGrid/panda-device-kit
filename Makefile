# Panda v1.1.0 Makefile
# 自动化构建、打包、部署、测试等操作

.PHONY: help build package push deploy start stop restart status test icons clean all

# 默认目标
.DEFAULT_GOAL := help

# 变量定义
APK_DEBUG = app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE = app/build/outputs/apk/release/app-release.apk
JAR_FILE = panda.jar
DEVICE_PATH = /data/local/tmp/panda.jar
SOCKET_NAME = panda-1.1.0
PORT = 9999

help:  ## 显示帮助信息
	@echo "╔══════════════════════════════════════════════════════════╗"
	@echo "║          🐼 Panda Makefile - 自动化工具                  ║"
	@echo "╚══════════════════════════════════════════════════════════╝"
	@echo ""
	@echo "使用方法: make <target>"
	@echo ""
	@echo "📦 构建相关:"
	@echo "  build      - 编译项目（Android Studio）"
	@echo "  package    - 从 APK 提取 DEX 并打包为 JAR"
	@echo "  clean      - 清理构建产物"
	@echo ""
	@echo "📱 部署相关:"
	@echo "  push       - 推送 JAR 到设备"
	@echo "  deploy     - 完整部署（package + push + start）"
	@echo ""
	@echo "⚙️  服务管理:"
	@echo "  start      - 启动 Panda 服务"
	@echo "  stop       - 停止 Panda 服务"
	@echo "  restart    - 重启服务"
	@echo "  status     - 查看服务状态"
	@echo "  log        - 查看服务日志"
	@echo ""
	@echo "🧪 测试相关:"
	@echo "  forward    - 设置 adb 端口转发"
	@echo "  test       - 运行基础功能测试"
	@echo "  icons      - 提取所有应用图标"
	@echo "  monitor    - 启动自动点击监控"
	@echo ""
	@echo "🎯 快捷命令:"
	@echo "  all        - 完整流程（build + deploy + test）"
	@echo "  quick      - 快速部署（package + push + restart）"
	@echo ""

build:  ## 编译项目（使用 Gradle 8）
	@echo "🔨 编译 Panda..."
	/opt/homebrew/opt/gradle@8/bin/gradle assembleDebug
	@echo "✅ 编译完成"

package: ## 打包为 JAR
	@echo "📦 打包为 JAR..."
	@if [ -f $(APK_DEBUG) ]; then \
		APK_FILE=$(APK_DEBUG); \
	elif [ -f $(APK_RELEASE) ]; then \
		APK_FILE=$(APK_RELEASE); \
	else \
		echo "✗ 未找到 APK 文件"; \
		echo "  请先运行: make build"; \
		exit 1; \
	fi; \
	rm -rf build/dex; \
	mkdir -p build/dex; \
	unzip -j $$APK_FILE "classes*.dex" -d build/dex; \
	cd build/dex && zip -q $(JAR_FILE) classes*.dex && cd ../..; \
	if [ -f build/dex/$(JAR_FILE) ]; then mv build/dex/$(JAR_FILE) .; fi; \
	ls -lh $(JAR_FILE)
	@echo "✅ JAR 已创建: $(JAR_FILE)"

push: ## 推送到设备
	@echo "📤 推送到设备..."
	@if [ ! -f $(JAR_FILE) ]; then \
		echo "✗ $(JAR_FILE) 不存在"; \
		echo "  请先运行: make package"; \
		exit 1; \
	fi
	@adb devices | grep -w "device" > /dev/null || (echo "✗ 设备未连接" && exit 1)
	adb push $(JAR_FILE) $(DEVICE_PATH)
	@echo "✅ 已推送到 $(DEVICE_PATH)"

start: ## 启动服务
	@echo "🚀 启动 Panda 服务..."
	@adb shell "nohup sh -c 'CLASSPATH=$(DEVICE_PATH) app_process / com.panda.Main > /data/local/tmp/panda.log 2>&1' &" > /dev/null 2>&1
	@sleep 2
	@echo "✅ 服务已启动"
	@make --no-print-directory status

stop: ## 停止服务
	@echo "🛑 停止 Panda 服务..."
	@PID=$$(adb shell "ps -A | grep 'com.panda.Main' | grep -v grep | awk '{print \$$2}'" | tr -d '\r'); \
	if [ -n "$$PID" ]; then \
		adb shell "kill $$PID"; \
		echo "✅ 已停止服务 (PID: $$PID)"; \
	else \
		echo "⚠️  服务未运行"; \
	fi

restart: stop start  ## 重启服务

status: ## 查看服务状态
	@echo "📊 Panda 服务状态:"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@PID=$$(adb shell "ps -A | grep 'com.panda.Main' | grep -v grep | awk '{print \$$2}'" | tr -d '\r'); \
	if [ -n "$$PID" ]; then \
		echo "  状态: ✅ 运行中"; \
		echo "  PID: $$PID"; \
		SOCKET=$$(adb shell "lsof | grep '@$(SOCKET_NAME)' | head -1" | tr -d '\r'); \
		if [ -n "$$SOCKET" ]; then \
			echo "  Socket: @$(SOCKET_NAME) ✓"; \
		fi; \
	else \
		echo "  状态: ⚫ 未运行"; \
	fi
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

log: ## 查看日志
	@adb shell "cat /data/local/tmp/panda.log 2>/dev/null | tail -20" || echo "日志文件不存在"

deploy: package push restart  ## 完整部署

forward: ## 设置端口转发
	@echo "🔌 设置端口转发..."
	@adb forward tcp:$(PORT) localabstract:$(SOCKET_NAME)
	@echo "✅ 端口转发: tcp:$(PORT) -> @$(SOCKET_NAME)"
	@adb forward --list | grep $(SOCKET_NAME)

test: forward  ## 运行测试
	@echo "🧪 运行 Panda 功能测试..."
	@cd .. && python3 test_panda.py

icons: forward  ## 提取所有应用图标
	@echo "🎨 提取所有应用图标..."
	@cd .. && python3 get_all_icons.py

monitor: forward  ## 启动自动点击监控
	@echo "🤖 启动自动点击监控..."
	@cd .. && echo "" | python3 start_autoclick.py

check-monitor: forward  ## 查看监控状态
	@cd .. && python3 check_autoclick.py

stop-monitor: forward  ## 停止监控
	@cd .. && python3 stop_autoclick.py

clean: ## 清理构建产物
	@echo "🧹 清理构建产物..."
	@rm -rf build/
	@rm -f $(JAR_FILE)
	@if [ -f gradlew ]; then ./gradlew clean; fi
	@echo "✅ 清理完成"

all: build deploy forward test  ## 完整流程

quick: package push restart forward  ## 快速部署

# 开发辅助
dev-rebuild: clean build package  ## 开发：重新构建

dev-test: quick test  ## 开发：快速测试

install-deps: ## 安装依赖（首次使用）
	@echo "📦 检查依赖..."
	@which adb > /dev/null || (echo "✗ adb 未安装" && exit 1)
	@echo "✅ adb 已安装"
	@adb devices | grep -w "device" > /dev/null || (echo "⚠️  设备未连接" && exit 1)
	@echo "✅ 设备已连接"
	@echo "✅ 依赖检查通过"

info: ## 显示项目信息
	@echo "🐼 Panda v1.1.0"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "项目路径: $$(pwd)"
	@echo "JAR 文件: $(JAR_FILE)"
	@if [ -f $(JAR_FILE) ]; then \
		echo "JAR 大小: $$(ls -lh $(JAR_FILE) | awk '{print $$5}')"; \
	else \
		echo "JAR 状态: 未生成"; \
	fi
	@echo "设备路径: $(DEVICE_PATH)"
	@echo "Socket: @$(SOCKET_NAME)"
	@echo "端口: $(PORT)"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

