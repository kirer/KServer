#!/bin/bash

# Server-K 部署脚本
# 构建并部署server-k到Android设备

set -e

echo "=== Server-K 部署脚本 ==="

# 检查ADB连接
if ! adb devices | grep -q "device$"; then
    echo "❌ 错误: 没有检测到Android设备"
    echo "请确保设备已连接并启用USB调试"
    exit 1
fi

echo "✅ 检测到Android设备"

# 构建DEX文件
echo "🔨 构建DEX文件..."
./gradlew :server-k:buildDex
if [ $? -ne 0 ]; then
    echo "❌ DEX构建失败"
    exit 1
fi

# 构建Native库
echo "🔨 构建Native库..."
./gradlew :server-k:buildNative
if [ $? -ne 0 ]; then
    echo "❌ Native库构建失败"
    exit 1
fi

# 检查文件是否存在
DEX_FILE="server-k/build/dex/classes.dex"
SO_FILE="server-k/build/libs/libashmem.so"

if [ ! -f "$DEX_FILE" ]; then
    echo "❌ DEX文件不存在: $DEX_FILE"
    exit 1
fi

if [ ! -f "$SO_FILE" ]; then
    echo "❌ SO文件不存在: $SO_FILE"
    exit 1
fi

echo "✅ 文件检查通过"

# 推送文件到设备
echo "📱 推送DEX文件到设备..."
adb push "$DEX_FILE" /data/local/tmp/server-k.dex
if [ $? -ne 0 ]; then
    echo "❌ DEX文件推送失败"
    exit 1
fi

echo "📱 推送SO文件到设备..."
adb push "$SO_FILE" /data/local/tmp/libashmem.so
if [ $? -ne 0 ]; then
    echo "❌ SO文件推送失败"
    exit 1
fi

# 设置文件权限
echo "🔧 设置文件权限..."
adb shell chmod 755 /data/local/tmp/server-k.dex
adb shell chmod 755 /data/local/tmp/libashmem.so

# 验证文件
echo "🔍 验证部署的文件..."
echo "DEX文件:"
adb shell ls -la /data/local/tmp/server-k.dex

echo "SO文件:"
adb shell ls -la /data/local/tmp/libashmem.so

# 测试server-k是否可以启动
echo "🧪 测试server-k启动..."
adb shell "dalvikvm -cp /data/local/tmp/server-k.dex com.github.kirer.server.Main test" &
TEST_PID=$!

# 等待测试完成
sleep 3
kill $TEST_PID 2>/dev/null || true

echo "✅ Server-K部署完成!"
echo ""
echo "现在可以在App-K中启动服务器了。"
echo "如果仍有问题，请检查："
echo "1. Shizuku是否正常运行"
echo "2. App-K是否有Shizuku权限"
echo "3. 设备是否有足够的存储空间"
