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

# 启动Socket服务
start_socket_service() {
    log_info "启动Socket服务 (端口: $SOCKET_PORT)..."

    # 清理日志
    adb shell "rm -f $LOG_FILE" 2>/dev/null || true

    # 启动服务
    adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $SERVICE_CLASS --socket --port $SOCKET_PORT --debug > $LOG_FILE 2>&1 &"

    # 等待启动
    sleep 3

    # 测试连接
    log_info "测试服务连接..."
    local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $TEST_CLASS localhost $SOCKET_PORT status" 2>/dev/null || echo "ERROR")

    if [[ "$response" == *"OK"* ]]; then
        log_info "✅ Socket服务启动成功！"
        log_info "响应: $response"
    else
        log_error "❌ Socket服务启动失败"
        log_info "查看日志:"
        adb shell "cat $LOG_FILE 2>/dev/null || echo '无日志文件'"
        exit 1
    fi
}

# 测试直接模式
test_direct_mode() {
    log_info "测试直接模式..."

    # 测试状态命令
    log_info "测试状态命令..."
    local status_response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $SERVICE_CLASS status" 2>/dev/null || echo "ERROR")

    if [[ "$status_response" == *"OK"* ]]; then
        log_info "✅ 直接模式状态测试成功"
        log_info "响应: $status_response"
    else
        log_error "❌ 直接模式状态测试失败"
        log_info "响应: $status_response"
        return 1
    fi

    # 测试截图命令
    log_info "测试截图命令..."
    local screenshot_path="/sdcard/test_direct_$(date +%s).png"

    # 记录开始时间
    local start_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))

    local screenshot_response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $SERVICE_CLASS screenshot $screenshot_path" 2>/dev/null || echo "ERROR")

    # 记录结束时间并计算耗时
    local end_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))
    local duration=$((end_time - start_time))

    if [[ "$screenshot_response" == *"OK"* ]]; then
        log_info "✅ 直接模式截图测试成功"
        log_info "⏱️ 截图耗时: ${duration}ms"
        log_info "响应: $screenshot_response"
    else
        log_warn "⚠️ 直接模式截图测试失败"
        log_info "⏱️ 截图耗时: ${duration}ms"
        log_info "响应: $screenshot_response"
    fi
}

# 显示使用说明
show_usage() {
    echo "KServer 快速部署脚本"
    echo ""
    echo "用法:"
    echo "  $0 -d                         # 直接模式部署"
    echo "  $0 -d --screenshot            # 直接模式部署并获取bitmap字节数组（测试用，不显示数据）"
    echo "  $0 -d --screenshot <path>     # 直接模式部署并截图到路径（返回成功/失败）"
    echo "  $0 -s                         # Socket模式部署"
    echo "  $0 -s --port 8888             # Socket模式部署指定端口"
    echo "  $0 -s -t screenshot           # Socket模式测试获取bitmap字节数组（测试用，不显示数据）"
    echo "  $0 -s -t screenshot <path>    # Socket模式测试截图到路径（返回成功/失败）"
    echo ""
    echo "其他选项:"
    echo "  --no-build                    # 跳过编译"
    echo "  --stop                        # 停止Socket服务"
    echo "  --help                        # 显示帮助"
    echo ""
    echo "模式说明:"
    echo "  -d (直接模式)                 部署并执行单个命令后退出"
    echo "  -s (Socket模式)               部署并启动持续的Socket服务"
    echo "  -t (测试模式)                 测试已运行的Socket服务"
    echo ""
}

# 测试现有Socket服务
test_socket_service() {
    log_info "测试现有Socket服务..."
    local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $TEST_CLASS localhost $SOCKET_PORT status" 2>/dev/null || echo "ERROR")

    if [[ "$response" == *"OK"* ]]; then
        log_info "✅ Socket服务运行正常"
        log_info "响应: $response"
    else
        log_error "❌ Socket服务未运行或连接失败"
    fi
}

# 执行直接命令
execute_direct_command() {
    local command="$1"
    if [ -z "$command" ]; then
        log_error "直接命令不能为空"
        exit 1
    fi

    log_info "执行直接命令: $command"

    # 记录开始时间
    local start_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))

    local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $SERVICE_CLASS $command" 2>&1)

    # 记录结束时间并计算耗时
    local end_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))
    local duration=$((end_time - start_time))

    echo "$response"

    # 如果是截图命令，显示耗时
    if [[ "$command" == screenshot* ]]; then
        log_info "⏱️ 命令执行耗时: ${duration}ms"
    fi

    if [[ "$response" == *"ERROR"* ]]; then
        exit 1
    else
        exit 0
    fi
}

# 直接模式截图测试
test_direct_screenshot() {
    local screenshot_path="$1"

    if [ -n "$screenshot_path" ]; then
        log_info "直接模式截图到文件: $screenshot_path" >&2

        # 记录总开始时间
        local total_start_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))

        local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $SERVICE_CLASS screenshot $screenshot_path" 2>&1)

        # 记录总结束时间
        local total_end_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))
        local total_duration=$((total_end_time - total_start_time))

        if [[ "$response" == *"OK"* ]]; then
            log_info "✅ 直接模式截图成功" >&2
            log_info "⏱️ 总耗时: ${total_duration}ms" >&2
            echo "响应: $response" >&2
        else
            log_error "❌ 直接模式截图失败" >&2
            echo "$response" >&2
            exit 1
        fi
    else
        log_info "直接模式获取bitmap（不保存文件）" >&2

        # 记录开始时间
        local start_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))

        # 使用screenshot命令（无参数）来测试bitmap获取，将二进制数据丢弃，避免在终端显示乱码
        adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $SERVICE_CLASS screenshot" > /dev/null
        local exit_code=$?

        # 记录结束时间
        local end_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))
        local total_duration=$((end_time - start_time))

        if [ $exit_code -eq 0 ]; then
            log_info "✅ 直接模式bitmap获取成功" >&2
            log_info "⏱️ 总耗时: ${total_duration}ms" >&2
            log_info "💡 提示: 二进制数据已生成但未显示（避免终端乱码）" >&2
        else
            log_error "❌ 直接模式bitmap获取失败" >&2
            exit 1
        fi
    fi
}

# Socket模式截图测试
test_socket_screenshot() {
    local screenshot_path="$1"

    # 截图前先检查服务状态
    log_info "检查服务状态..."
    local status_response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $TEST_CLASS localhost $SOCKET_PORT 'status'" 2>/dev/null || echo "ERROR")

    if [[ "$status_response" != *"OK"* ]]; then
        log_error "❌ 服务状态异常，无法执行截图"
        echo "状态响应: $status_response"
        exit 1
    fi

    log_info "✅ 服务状态正常，开始截图..."

    if [ -n "$screenshot_path" ]; then
        log_info "Socket模式截图到文件: $screenshot_path"

        # 记录总开始时间
        local total_start_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))

        local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $TEST_CLASS localhost $SOCKET_PORT 'screenshot $screenshot_path'" 2>/dev/null || echo "ERROR")

        # 记录总结束时间
        local total_end_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))
        local total_duration=$((total_end_time - total_start_time))

        if [[ "$response" == *"OK"* ]]; then
            log_info "✅ Socket模式截图成功"
            log_info "⏱️ Socket命令总耗时: ${total_duration}ms"
            echo "响应: $response"
        else
            log_error "❌ Socket模式截图失败"
            echo "$response"
            exit 1
        fi
    else
        log_info "Socket模式获取bitmap（不保存文件）"

        # 记录开始时间
        local start_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))

        # 将二进制数据重定向到/dev/null，只保留stderr的响应信息
        local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $TEST_CLASS localhost $SOCKET_PORT 'screenshot'" 2>&1 > /dev/null || echo "ERROR")

        # 记录结束时间
        local end_time=$(python3 -c "import time; print(int(time.time() * 1000))" 2>/dev/null || echo $(($(date +%s) * 1000)))
        local total_duration=$((end_time - start_time))

        if [[ "$response" == *"OK"* ]] || [[ "$response" == *"BYTES_SIZE"* ]]; then
            log_info "✅ Socket模式bitmap获取成功"
            log_info "⏱️ Socket通信总耗时: ${total_duration}ms"
            log_info "💡 提示: 二进制数据已生成但未显示（避免终端乱码）"
            echo "响应信息: $response"
        else
            log_error "❌ Socket模式bitmap获取失败"
            echo "$response"
            exit 1
        fi
    fi
}

# 主函数
main() {
    local do_build=true
    local mode=""           # -d (direct) 或 -s (socket)
    local test_mode=false   # -t (test)
    local screenshot_mode=false
    local screenshot_path=""

    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -d)
                mode="direct"
                shift
                ;;
            -s)
                mode="socket"
                shift
                ;;
            -t)
                test_mode=true
                shift
                ;;
            --screenshot|screenshot)
                screenshot_mode=true
                # 检查下一个参数是否是路径
                if [[ $# -gt 1 && ! "$2" =~ ^- ]]; then
                    screenshot_path="$2"
                    shift 2
                else
                    shift
                fi
                ;;
            --no-build)
                do_build=false
                shift
                ;;
            --port)
                SOCKET_PORT="$2"
                shift 2
                ;;
            --stop)
                stop_existing_service
                log_info "Socket服务已停止"
                exit 0
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

    # 验证参数组合
    if [ -z "$mode" ]; then
        log_error "必须指定模式: -d (直接模式) 或 -s (Socket模式)"
        show_usage
        exit 1
    fi

    if [ "$test_mode" = true ] && [ "$mode" != "socket" ]; then
        log_error "-t (测试模式) 只能与 -s (Socket模式) 一起使用"
        exit 1
    fi

    log_info "=== KServer 快速部署 ==="

    # 检查设备
    check_device

    # 根据模式执行相应操作
    if [ "$mode" = "direct" ]; then
        # 直接模式 - 所有日志输出到stderr
        log_info "直接模式部署" >&2

        # 停止现有服务
        stop_existing_service >&2

        # 编译
        if [ "$do_build" = true ]; then
            build_dex >&2
        fi

        # 部署
        deploy >&2

        # 如果有截图需求，执行截图测试
        if [ "$screenshot_mode" = true ]; then
            test_direct_screenshot "$screenshot_path"
        else
            log_info "✅ 直接模式部署完成" >&2
        fi

    elif [ "$mode" = "socket" ]; then
        # Socket模式
        if [ "$test_mode" = true ]; then
            # 测试模式
            log_info "Socket模式测试"

            if [ "$screenshot_mode" = true ]; then
                test_socket_screenshot "$screenshot_path"
            else
                test_socket_service
            fi
        else
            # 部署模式
            log_info "Socket模式部署"

            # 停止现有服务
            stop_existing_service

            # 编译
            if [ "$do_build" = true ]; then
                build_dex
            fi

            # 部署
            deploy

            # 启动Socket服务
            start_socket_service

            # 如果有截图需求，执行截图测试
            if [ "$screenshot_mode" = true ]; then
                echo ""
                test_socket_screenshot "$screenshot_path"
            else
                echo ""
                log_info "🎉 Socket模式部署完成！"
                log_info ""
                log_info "测试命令:"
                echo "  $0 -s -t --screenshot         # 测试bitmap字节数组获取（不显示数据）"
                echo "  $0 -s -t --screenshot <path>  # 测试截图到文件"
            fi
        fi
    fi
}

# 执行主函数
main "$@"
