#!/bin/bash
# Panda 部署脚本 v1.0.0

set -e

echo "🐼 Panda 部署工具"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

# 检查设备连接
echo "🔍 检查设备..."
if ! adb devices | grep -w "device" > /dev/null; then
    echo "❌ 未检测到设备"
    exit 1
fi
echo "✅ 设备已连接"
echo

# 检查编译产物（优先使用 release，没有则用 debug）
APK_FILE="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_FILE" ]; then
    APK_FILE="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$APK_FILE" ]; then
    echo "❌ 未找到编译文件"
    echo "请先在 Android Studio 中构建项目"
    exit 1
fi

echo "📦 使用文件: $(basename $APK_FILE)"

# 上传
echo "📤 上传到设备..."
adb push "$APK_FILE" /data/local/tmp/panda.jar > /dev/null
echo "✅ 上传完成"
echo

# 停止旧进程
OLD_PID=$(adb shell "ps -A | grep 'com.panda.Main' | awk '{print \$2}'" 2>/dev/null | tr -d '\r')
if [ ! -z "$OLD_PID" ]; then
    echo "🛑 停止旧进程 (PID: $OLD_PID)"
    adb shell "kill $OLD_PID" 2>/dev/null || true
    sleep 1
fi

# 启动服务
echo "🚀 启动 Panda..."
adb shell "nohup sh -c 'CLASSPATH=/data/local/tmp/panda.jar app_process / com.panda.Main > /data/local/tmp/panda.log 2>&1' &" > /dev/null 2>&1
sleep 2

# 验证
NEW_PID=$(adb shell "ps -A | grep 'com.panda.Main' | awk '{print \$2}'" 2>/dev/null | tr -d '\r')
if [ ! -z "$NEW_PID" ]; then
    echo "✅ Panda 已启动 (PID: $NEW_PID)"
    echo
    echo "📄 最新日志:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    adb shell "cat /data/local/tmp/panda.log" 2>/dev/null | tail -10 || echo "(暂无日志)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    echo "✅ 部署完成！"
    echo
    echo "Socket: \0panda-1.0.0"
    echo "停止: adb shell kill $NEW_PID"
else
    echo "⚠️  无法确认服务状态"
    echo "查看日志: adb shell cat /data/local/tmp/panda.log"
fi

