#!/bin/bash

# Socket客户端脚本
# 用于向截图服务发送命令

set -e

# 配置
DEVICE_PATH="/data/local/tmp"
SERVICE_DEX="k-server.dex"
TEST_CLASS="com.github.kirer.adb.test.SocketClient"
SOCKET_PORT=8888

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

# 发送命令
send_command() {
    local command="$1"
    if [ -z "$command" ]; then
        log_error "命令不能为空"
        return 1
    fi

    log_info "发送命令: $command"

    # 检查DEX文件是否存在
    if ! adb shell "test -f $DEVICE_PATH/$SERVICE_DEX" 2>/dev/null; then
        log_error "DEX文件不存在: $DEVICE_PATH/$SERVICE_DEX"
        log_info "请先运行部署脚本: ./server-adb-shell/deploy.sh"
        return 1
    fi

    # 使用Java客户端发送命令
    local response=$(adb shell "CLASSPATH=$DEVICE_PATH/$SERVICE_DEX app_process /system/bin $TEST_CLASS localhost $SOCKET_PORT '$command'" 2>/dev/null || echo "ERROR")

    if [[ "$response" == *"ERROR"* ]] || [ -z "$response" ]; then
        log_error "命令执行失败或服务未响应"
        log_info "请检查服务是否正在运行: ./server-adb-shell/deploy.sh --test"
        return 1
    else
        log_info "服务响应:"
        echo "$response"
        return 0
    fi
}

# 显示使用说明
show_usage() {
    echo "Screenshot Service Socket 客户端"
    echo ""
    echo "用法:"
    echo "  $0 <command>           # 发送命令到服务"
    echo "  $0 --port 9999 <cmd>   # 使用自定义端口"
    echo "  $0 --help             # 显示帮助"
    echo ""
    echo "可用命令:"
    echo "  screenshot <path>      # 截图并保存到指定路径"
    echo "  status                # 获取服务状态"
    echo "  help                  # 显示服务端帮助"
    echo ""
    echo "示例:"
    echo "  $0 'screenshot /sdcard/test.png'"
    echo "  $0 'status'"
    echo "  $0 --port 9999 'status'"
    echo ""
}

# 主函数
main() {
    local command=""

    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --port)
                SOCKET_PORT="$2"
                shift 2
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                if [ -z "$command" ]; then
                    command="$1"
                else
                    log_warn "忽略额外参数: $1"
                fi
                shift
                ;;
        esac
    done

    if [ -z "$command" ]; then
        show_usage
        exit 1
    fi

    log_info "=== Screenshot Service Socket 客户端 ==="

    # 检查设备连接
    check_device

    # 发送命令
    send_command "$command"
}

# 执行主函数
main "$@"
