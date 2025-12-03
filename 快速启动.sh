#!/bin/bash
# Panda 快速启动脚本

cd "$(dirname "$0")"

echo "🐼 Panda 服务快速启动"
echo "===================="

# 1. 编译打包
echo ""
echo "📦 步骤 1: 编译打包..."
./gradlew assembleDebug > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

make package > /dev/null 2>&1
if [ ! -f panda-kit.jar ]; then
    echo "❌ 打包失败"
    exit 1
fi
echo "✅ 编译打包完成"

# 2. 推送到设备
echo ""
echo "📤 步骤 2: 推送到设备..."
adb push panda-kit.jar /data/local/tmp/panda.jar > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ 推送失败，请检查设备连接: adb devices"
    exit 1
fi
echo "✅ 推送完成"

# 3. 停止旧服务
echo ""
echo "🛑 步骤 3: 停止旧服务..."
adb shell "pkill -f 'com.panda.Main'" > /dev/null 2>&1
sleep 1
echo "✅ 已停止旧服务"

# 4. 启动服务
echo ""
echo "🚀 步骤 4: 启动服务..."
adb shell "CLASSPATH=/data/local/tmp/panda.jar app_process / com.panda.Main daemon"
echo "✅ 服务已启动"

# 5. 设置端口转发
echo ""
echo "🔌 步骤 5: 设置端口转发..."
adb forward tcp:9999 localabstract:panda-1.1.0  > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "⚠️  端口转发可能已存在，尝试移除后重新设置..."
    adb forward --remove tcp:9999 > /dev/null 2>&1
    adb forward tcp:9999 localabstract:panda-1.1.0 > /dev/null 2>&1
fi
echo "✅ 端口转发已设置"

# 6. 检查状态
echo ""
echo "📊 步骤 6: 检查服务状态..."
sleep 1
PID=$(adb shell "ps -ef | grep 'com.panda.Main' | grep -v grep | head -1 | awk '{print \$2}'" | tr -d '\r')
if [ -n "$PID" ]; then
    echo "✅ 服务运行中 (PID: $PID)"
else
    echo "⚠️  服务可能未启动，请检查日志: adb shell 'cat /data/local/tmp/panda.log'"
fi

echo ""
echo "===================="
echo "✅ 启动完成！"
echo ""
echo "🧪 运行测试:"
echo "   python3 test_simple.py"
echo ""
echo "📋 其他命令:"
echo "   查看状态: make status"
echo "   查看日志: make log"
echo "   停止服务: make stop"

