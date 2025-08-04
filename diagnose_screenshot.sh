#!/bin/bash

# 截图功能诊断脚本
# 用于排查KServer截图功能失败的原因

set -e

echo "=== KServer 截图功能诊断脚本 ==="
echo "时间: $(date)"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查函数
check_step() {
    local step_name="$1"
    local command="$2"
    local expected_pattern="$3"
    
    echo -e "${BLUE}检查: $step_name${NC}"
    
    if eval "$command" 2>/dev/null; then
        if [ -n "$expected_pattern" ]; then
            if eval "$command" 2>/dev/null | grep -q "$expected_pattern"; then
                echo -e "${GREEN}✅ $step_name - 通过${NC}"
                return 0
            else
                echo -e "${RED}❌ $step_name - 失败 (未找到预期模式: $expected_pattern)${NC}"
                return 1
            fi
        else
            echo -e "${GREEN}✅ $step_name - 通过${NC}"
            return 0
        fi
    else
        echo -e "${RED}❌ $step_name - 失败${NC}"
        return 1
    fi
}

# 1. 检查ADB连接
echo -e "${YELLOW}=== 1. 基础环境检查 ===${NC}"
check_step "ADB设备连接" "adb devices" "device"

if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ 没有检测到Android设备，请确保设备已连接并启用USB调试${NC}"
    exit 1
fi

# 2. 检查Shizuku状态
echo -e "${YELLOW}=== 2. Shizuku状态检查 ===${NC}"
check_step "Shizuku应用安装" "adb shell pm list packages | grep moe.shizuku.privileged.api"
check_step "Shizuku服务运行" "adb shell ps | grep shizuku"

# 3. 检查KServer进程
echo -e "${YELLOW}=== 3. KServer进程检查 ===${NC}"
echo "检查KServer相关进程..."
adb shell "ps | grep -E '(kserver|k-server|com.github.kirer)' || echo '未找到KServer进程'"

echo "检查端口监听状态..."
adb shell "netstat -an 2>/dev/null | grep ':8888.*LISTEN' || echo '端口8888未监听'"

# 4. 检查应用安装状态
echo -e "${YELLOW}=== 4. 应用状态检查 ===${NC}"
check_step "app-server应用安装" "adb shell pm list packages | grep com.github.kirer.app_server"
check_step "app-k应用安装" "adb shell pm list packages | grep com.github.kirer.appk"

# 5. 检查权限状态
echo -e "${YELLOW}=== 5. 权限检查 ===${NC}"
echo "检查app-server权限..."
adb shell "dumpsys package com.github.kirer.app_server | grep -A 5 'requested permissions:' || echo '无法获取权限信息'"

echo "检查系统权限..."
adb shell "ls -la /system/bin/app_process* || echo '无法访问app_process'"

# 6. 检查截图相关文件
echo -e "${YELLOW}=== 6. 截图组件检查 ===${NC}"
echo "检查APK文件访问..."
APP_APK_PATH=$(adb shell pm path com.github.kirer.app_server | cut -d: -f2 | tr -d '\r')
if [ -n "$APP_APK_PATH" ]; then
    echo "APK路径: $APP_APK_PATH"
    adb shell "ls -la '$APP_APK_PATH' || echo 'APK文件不可访问'"
else
    echo -e "${RED}❌ 无法获取APK路径${NC}"
fi

# 7. 尝试手动启动KServer
echo -e "${YELLOW}=== 7. 手动启动测试 ===${NC}"
echo "尝试手动启动KServer..."

if [ -n "$APP_APK_PATH" ]; then
    echo "使用APK路径启动KServer..."
    adb shell "pkill -f 'com.github.kirer.adb.Launcher' 2>/dev/null || true"
    sleep 2
    
    # 尝试启动KServer
    adb shell "nohup sh -c \"CLASSPATH='$APP_APK_PATH' app_process /system/bin com.github.kirer.adb.Launcher -s -p 8888\" > /dev/null 2>&1 &"
    sleep 3
    
    # 检查启动结果
    if adb shell "pgrep -f 'com.github.kirer.adb.Launcher'" > /dev/null; then
        echo -e "${GREEN}✅ KServer进程启动成功${NC}"
        
        # 检查端口监听
        if adb shell "netstat -an 2>/dev/null | grep ':8888.*LISTEN'" > /dev/null; then
            echo -e "${GREEN}✅ 端口8888监听成功${NC}"
        else
            echo -e "${RED}❌ 端口8888未监听${NC}"
        fi
    else
        echo -e "${RED}❌ KServer进程启动失败${NC}"
    fi
fi

# 8. 测试截图功能
echo -e "${YELLOW}=== 8. 截图功能测试 ===${NC}"
echo "测试基础截图命令..."

# 尝试通过socket连接测试
if command -v nc >/dev/null 2>&1; then
    echo "使用netcat测试socket连接..."
    echo "help" | timeout 5 nc localhost 8888 2>/dev/null || echo "Socket连接失败"
else
    echo "netcat不可用，跳过socket测试"
fi

# 9. 收集日志信息
echo -e "${YELLOW}=== 9. 日志信息收集 ===${NC}"
echo "收集相关日志..."
adb logcat -d | grep -E "(KServer|Screenshot|ERROR|FATAL)" | tail -20 || echo "无相关日志"

# 10. 生成诊断报告
echo -e "${YELLOW}=== 10. 诊断总结 ===${NC}"
echo "诊断完成，请检查上述输出中的错误信息。"
echo ""
echo "常见问题和解决方案:"
echo "1. 如果Shizuku未运行，请启动Shizuku应用并授予权限"
echo "2. 如果KServer进程未启动，检查APK路径和权限"
echo "3. 如果端口未监听，可能是进程启动失败或端口被占用"
echo "4. 如果截图失败，检查系统权限和显示权限"
echo ""
echo "=== 诊断脚本执行完成 ==="
