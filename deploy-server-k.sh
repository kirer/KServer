#!/bin/bash

# KServer 部署和测试脚本
# 功能：构建server-k模块，推送到Android设备并运行测试

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置参数
DEVICE_PATH="/data/local/tmp/server-k"
LIB_PATH="$DEVICE_PATH/lib"
ADB_TIMEOUT=10

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."

    # 检查adb
    if ! command -v adb &> /dev/null; then
        log_error "adb 未找到，请确保Android SDK已安装并添加到PATH"
        exit 1
    fi

    # 检查设备连接
    if ! adb devices | grep -q "device$"; then
        log_error "未找到连接的Android设备"
        log_info "请确保："
        echo "  1. 设备已连接并开启USB调试"
        echo "  2. 已授权调试权限"
        echo "  3. 运行 'adb devices' 确认设备状态"
        exit 1
    fi

    local device_count=$(adb devices | grep -c "device$")
    if [ $device_count -gt 1 ]; then
        log_warning "检测到多个设备，将使用第一个设备"
    fi

    # 检查设备架构
    local abi=$(MSYS_NO_PATHCONV=1 adb shell getprop ro.product.cpu.abi)
    log_info "设备架构: $abi"

    if [[ "$abi" != "arm64-v8a" && "$abi" != "armeabi-v7a" ]]; then
        log_warning "设备架构 $abi 可能不受支持"
    fi

    log_success "依赖检查完成"
}

# 构建项目
build_project() {
    log_info "开始构建server-k模块..."

    # 清理之前的构建
    ./gradlew :server-k:clean

    # 构建项目
    if ./gradlew :server-k:publish; then
        log_success "构建完成"
    else
        log_error "构建失败"
        exit 1
    fi

    # 检查输出文件
    local output_dir="server-k/build/server-k-output"
    if [ ! -d "$output_dir" ]; then
        log_error "构建输出目录不存在: $output_dir"
        exit 1
    fi

    # 列出生成的文件
    log_info "生成的文件:"
    find "$output_dir" -type f | while read file; do
        local size=$(du -h "$file" | cut -f1)
        echo "  $(basename "$file") ($size)"
    done
}

# 推送文件到设备
push_files() {
    log_info "推送文件到设备..."

    local output_dir="server-k/build/server-k-output"

    # 检查输出目录是否存在
    if [ ! -d "$output_dir" ]; then
        log_error "构建输出目录不存在: $output_dir"
        log_info "请先运行构建: ./gradlew :server-k:publish"
        exit 1
    fi

    # 创建设备目录
    MSYS_NO_PATHCONV=1 adb shell "mkdir -p $DEVICE_PATH" || {
        log_error "无法创建设备目录: $DEVICE_PATH"
        exit 1
    }

    # 获取设备架构
    local device_abi=$(MSYS_NO_PATHCONV=1 adb shell getprop ro.product.cpu.abi | tr -d '\r\n')
    log_info "设备架构: $device_abi"

    # 推送DEX文件（优先）或JAR文件
    local dex_file=$(find "$output_dir" -name "*.dex" | head -1)
    local jar_file=$(find "$output_dir" -name "*.jar" | head -1)

    if [ -n "$dex_file" ]; then
        log_info "推送DEX文件: $(basename "$dex_file")"
        # 使用MSYS_NO_PATHCONV避免Git Bash路径转换问题
        MSYS_NO_PATHCONV=1 adb push "$dex_file" "$DEVICE_PATH/" || {
            log_error "DEX文件推送失败"
            exit 1
        }
        log_success "✅ 已推送DEX文件: $(basename "$dex_file")"
    elif [ -n "$jar_file" ]; then
        log_info "推送JAR文件: $(basename "$jar_file")"
        # 使用MSYS_NO_PATHCONV避免Git Bash路径转换问题
        MSYS_NO_PATHCONV=1 adb push "$jar_file" "$DEVICE_PATH/" || {
            log_error "JAR文件推送失败"
            exit 1
        }
        log_success "✅ 已推送JAR文件: $(basename "$jar_file")"
    else
        log_error "未找到DEX或JAR文件"
        exit 1
    fi

    # 推送对应架构的SO文件
    local so_pushed=false
    local so_file=""

    # 根据设备架构选择合适的SO文件
    case "$device_abi" in
        "arm64-v8a")
            so_file="$output_dir/lib/arm64-v8a/libashmem.so"
            ;;
        "armeabi-v7a")
            so_file="$output_dir/lib/armeabi-v7a/libashmem.so"
            ;;
        *)
            log_warning "未知设备架构: $device_abi，尝试使用arm64-v8a"
            so_file="$output_dir/lib/arm64-v8a/libashmem.so"
            ;;
    esac

    if [ -f "$so_file" ]; then
        log_info "推送SO文件: $(basename "$so_file") for $device_abi"
        # 使用MSYS_NO_PATHCONV避免Git Bash路径转换问题
        MSYS_NO_PATHCONV=1 adb push "$so_file" "$DEVICE_PATH/libashmem.so" || {
            log_error "SO文件推送失败"
            exit 1
        }
        log_success "✅ 已推送SO文件: $(basename "$so_file")"
        so_pushed=true
    else
        log_error "未找到适合架构 $device_abi 的SO文件: $so_file"
        exit 1
    fi

    # 设置文件权限
    log_info "设置文件权限..."
    MSYS_NO_PATHCONV=1 adb shell "chmod 755 $DEVICE_PATH" 2>/dev/null || true
    MSYS_NO_PATHCONV=1 adb shell "chmod 644 $DEVICE_PATH/*" 2>/dev/null || true
    MSYS_NO_PATHCONV=1 adb shell "chmod 755 $DEVICE_PATH/libashmem.so" 2>/dev/null || true

    # 验证推送的文件
    log_info "验证推送的文件..."
    MSYS_NO_PATHCONV=1 adb shell "ls -la $DEVICE_PATH/" || {
        log_warning "无法列出设备文件"
    }

    if [ "$so_pushed" = true ]; then
        # 验证SO文件架构
        local so_info=$(MSYS_NO_PATHCONV=1 adb shell "file $DEVICE_PATH/libashmem.so" 2>/dev/null | tr -d '\r\n')
        if [[ "$so_info" == *"64-bit"* ]] && [[ "$device_abi" == "arm64-v8a" ]]; then
            log_success "✅ SO文件架构匹配: 64-bit for $device_abi"
        elif [[ "$so_info" == *"32-bit"* ]] && [[ "$device_abi" == "armeabi-v7a" ]]; then
            log_success "✅ SO文件架构匹配: 32-bit for $device_abi"
        else
            log_warning "⚠️  SO文件架构可能不匹配: $so_info"
        fi
    fi

    log_success "文件推送完成"
}

# 运行KServer
run_kserver() {
    log_info "启动KServer..."

    # 检查SO文件是否存在
    if ! MSYS_NO_PATHCONV=1 adb shell "test -f $DEVICE_PATH/libashmem.so"; then
        log_error "设备上未找到libashmem.so文件"
        exit 1
    fi

    # 检查DEX/JAR文件是否存在
    local dex_file=$(MSYS_NO_PATHCONV=1 adb shell "ls $DEVICE_PATH/*.dex 2>/dev/null | head -1" | tr -d '\r\n')
    local jar_file=$(MSYS_NO_PATHCONV=1 adb shell "ls $DEVICE_PATH/*.jar 2>/dev/null | head -1" | tr -d '\r\n')

    if [ -z "$dex_file" ] && [ -z "$jar_file" ]; then
        log_error "设备上未找到DEX或JAR文件"
        exit 1
    fi

    # 构建运行命令
    local run_file="$dex_file"
    # 构建运行命令 - 使用正确的app_process格式
    local cmd="cd $DEVICE_PATH && app_process -Djava.class.path=$run_file / com.github.kirer.server.Launcher --debug --libPath=$DEVICE_PATH"
    log_info "执行命令: $cmd"
    log_success "✅ 启动KServer - 程序将持续运行"
    log_info "按 Ctrl+C 停止程序"
    echo ""
    # 直接运行程序
    MSYS_NO_PATHCONV=1 adb shell "$cmd"
}



# 清理设备文件
cleanup_device() {
    if [ "$1" = "--cleanup" ]; then
        log_info "清理设备文件..."
        MSYS_NO_PATHCONV=1 adb shell "rm -rf $DEVICE_PATH"
        log_success "清理完成"
    fi
}

# 显示帮助信息
show_help() {
    echo "KServer 部署和测试脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --help          显示此帮助信息"
    echo "  --cleanup       清理设备上的文件"
    echo "  --build-only    仅构建，不部署"
    echo "  --deploy-only   仅部署，不运行"
    echo "  --run           仅运行KServer（需要先部署）"
    echo ""
    echo "示例:"
    echo "  $0                    # 完整的构建、部署和运行流程"
    echo "  $0 --build-only       # 仅构建"
    echo "  $0 --deploy-only      # 仅部署已构建的文件"
    echo "  $0 --run              # 仅运行KServer"
    echo "  $0 --cleanup          # 清理设备文件"
}

# 主函数
main() {
    local build_only=false
    local deploy_only=false
    local run_only=false
    local cleanup=false

    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help)
                show_help
                exit 0
                ;;
            --cleanup)
                cleanup=true
                shift
                ;;
            --build-only)
                build_only=true
                shift
                ;;
            --deploy-only)
                deploy_only=true
                shift
                ;;
            --run)
                run_only=true
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # 执行清理
    if [ "$cleanup" = true ]; then
        cleanup_device --cleanup
        exit 0
    fi

    # 仅运行KServer
    if [ "$run_only" = true ]; then
        check_dependencies
        run_kserver
        exit 0
    fi

    # 检查依赖
    check_dependencies

    # 执行构建
    if [ "$deploy_only" = false ]; then
        build_project
    fi

    # 执行部署
    if [ "$build_only" = false ]; then
        push_files

        # 如果不是仅部署，则运行KServer
        if [ "$deploy_only" = false ]; then
            run_kserver
        fi
    fi

    # 如果是仅部署，显示完成信息
    if [ "$deploy_only" = true ] || [ "$build_only" = true ]; then
        log_success "操作完成!"
    fi
}

# 脚本入口
main "$@"
