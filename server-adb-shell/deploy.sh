#!/bin/bash

# Screenshot Service 快速部署脚本
# 一键编译、部署、启动

set -e

# 配置
DEVICE_PATH="/data/local/tmp"
SERVICE_DEX="k-server.dex"
SERVICE_CLASS="com.github.kirer.adb.Launcher"
TEST_CLASS="com.github.kirer.adb.test.SocketClient"
SOCKET_PORT=8888
LOG_FILE="/sdcard/screenshot_service.log"

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 检查设备连接
check_device() {
    if ! adb devices | grep -q "device$"; then
        log_error "没有检测到Android设备连接"
        exit 1
    fi
}

# 停止现有服务
stop_existing_service() {
    log_info "停止现有服务..."

    # 通过端口查找并杀死进程
    local pids=$(adb shell "netstat -tulpn 2>/dev/null | grep ':$SOCKET_PORT ' | awk '{print \$7}' | cut -d'/' -f1" 2>/dev/null || true)
    for pid in $pids; do
        if [ -n "$pid" ] && [ "$pid" != "-" ]; then
            log_info "杀死进程: $pid"
            adb shell "kill $pid" 2>/dev/null || true
        fi
    done
    
    # 等待端口释放
    sleep 2
}

# 编译DEX
build_dex() {
    log_info "编译DEX文件..."
    if ./gradlew :server-adb-shell:buildDex; then
        log_info "编译成功"
    else
        log_error "编译失败"
        exit 1
    fi
}

# 部署到设备
deploy() {
    log_info "部署到设备..."
    if adb push server-adb-shell/build/dex/classes.dex $DEVICE_PATH/$SERVICE_DEX; then
        log_info "部署成功"
    else
        log_error "部署失败"
        exit 1
    fi
}

# 启动服务
start_service() {
    log_info "启动Socket服务 (端口: $SOCKET_PORT)..."
    
    # 清理日志
    adb shell "rm -f $LOG_FILE" 2>/dev/null || true
    
    # 启动服务
    adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $SERVICE_CLASS --port $SOCKET_PORT --debug > $LOG_FILE 2>&1 &"
    
    # 等待启动
    sleep 3
    
    # 测试连接
    log_info "测试服务连接..."
    local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $TEST_CLASS localhost $SOCKET_PORT status" 2>/dev/null || echo "ERROR")
    
    if [[ "$response" == *"OK"* ]]; then
        log_info "✅ 服务启动成功！"
        log_info "响应: $response"
    else
        log_error "❌ 服务启动失败"
        log_info "查看日志:"
        adb shell "cat $LOG_FILE 2>/dev/null || echo '无日志文件'"
        exit 1
    fi
}

# 显示使用说明
show_usage() {
    echo "KServer 快速部署脚本"
    echo ""
    echo "用法:"
    echo "  $0              # 完整部署 (编译+部署+启动)"
    echo "  $0 --no-build   # 跳过编译 (仅部署+启动)"
    echo "  $0 --port 9999  # 使用自定义端口"
    echo "  $0 --test       # 测试现有服务"
    echo "  $0 --stop       # 停止服务"
    echo ""
}

# 测试现有服务
test_service() {
    log_info "测试现有服务..."
    local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin com.screenshot.test.SocketClient localhost $SOCKET_PORT status" 2>/dev/null || echo "ERROR")
    
    if [[ "$response" == *"OK"* ]]; then
        log_info "✅ 服务运行正常"
        log_info "响应: $response"
        
        # 测试截图
        log_info "测试截图功能..."
        local screenshot_response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin com.screenshot.test.SocketClient localhost $SOCKET_PORT 'screenshot /sdcard/test_$(date +%s).png'" 2>/dev/null || echo "ERROR")
        log_info "截图响应: $screenshot_response"
    else
        log_error "❌ 服务未运行或连接失败"
    fi
}

# 主函数
main() {
    local do_build=true
    local action="deploy"
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-build)
                do_build=false
                shift
                ;;
            --port)
                SOCKET_PORT="$2"
                shift 2
                ;;
            --test)
                action="test"
                shift
                ;;
            --stop)
                action="stop"
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                log_warn "未知选项: $1"
                shift
                ;;
        esac
    done
    
    log_info "=== Screenshot Service 快速部署 ==="
    
    # 检查设备
    check_device
    
    case "$action" in
        "test")
            test_service
            ;;
        "stop")
            stop_existing_service
            log_info "服务已停止"
            ;;
        "deploy")
            # 停止现有服务
            stop_existing_service
            
            # 编译
            if [ "$do_build" = true ]; then
                build_dex
            fi
            
            # 部署
            deploy
            
            # 启动
            start_service
            
            echo ""
            log_info "🎉 部署完成！"
            log_info ""
            log_info "测试命令:"
            echo "  ./server-adb-shell/socket_client.sh \"screenshot /sdcard/test.png\""
            echo "  ./server-adb-shell/socket_client.sh \"status\""
            echo ""
            log_info "管理命令:"
            echo "  $0 --test    # 测试服务"
            echo "  $0 --stop    # 停止服务"
            ;;
    esac
}

# 执行主函数
main "$@"
