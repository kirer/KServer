#include "core/client_core.h"
#include "client_opencv.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <fcntl.h>

// 嵌入的文件数据
#include "embedded/k_server_dex.h"
#include "embedded/libserver_k_so.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <fcntl.h>

#define PROGRAM_NAME "k-client"
#define VERSION "1.0.0"

// 全局变量用于信号处理
static volatile int g_running = 1;
static int g_debug_mode = 0;
static int g_auto_start_server = 0;

// 信号处理函数
void signal_handler(int sig) {
    (void)sig;
    g_running = 0;
    if (g_debug_mode) {
        printf("\n[DEBUG] 收到中断信号，正在退出...\n");
    } else {
        printf("\n收到中断信号，正在退出...\n");
    }
}

// 打印使用帮助
void print_usage(const char* program_name) {
    printf("用法: %s [选项]\n", program_name);
    printf("\n选项:\n");
    printf("  --mode=MODE              通信模式 (SHARED_MEMORY|UNIX_SOCKET|TCP_SOCKET，默认: TCP_SOCKET)\n");
    printf("  --tcpHost=HOST           TCP主机地址 (默认: 127.0.0.1)\n");
    printf("  --tcpPort=PORT           TCP端口 (默认: 7777)\n");
    printf("  --socketName=NAME        Unix套接字名称 (默认: server-k)\n");
    printf("  --memorySize=SIZE        共享内存大小 (默认: 16777216)\n");
    printf("  --continuous             连续截图模式\n");
    printf("  --interval=SECONDS       截图间隔秒数 (默认: 1)\n");
    printf("  --output=FILE            输出文件前缀 (默认: screenshot)\n");
    printf("  --auto-start-server      自动启动服务器（如果未运行）\n");
    printf("  --debug                  调试模式\n");
    printf("  --help                   显示此帮助信息\n");
    printf("  --version                显示版本信息\n");
    printf("\n示例:\n");
    printf("  %s --mode=TCP_SOCKET --tcpHost=127.0.0.1 --tcpPort=7777 --debug\n", program_name);
    printf("  %s --mode=UNIX_SOCKET --socketName=my-server\n", program_name);
    printf("  %s --mode=SHARED_MEMORY --memorySize=2097152\n", program_name);
    printf("  %s --continuous --interval=2 --output=capture --debug\n", program_name);
    printf("  %s --auto-start-server --mode=TCP_SOCKET --tcpHost=127.0.0.1 --tcpPort=7777 --debug\n", program_name);
}

// 打印版本信息
void print_version(void) {
    printf("%s 版本 %s\n", PROGRAM_NAME, VERSION);
    printf("Server-K 高性能客户端程序\n");
}

// 解析模式字符串
client_mode_t parse_mode(const char* mode_str) {
    if (strcmp(mode_str, "SHARED_MEMORY") == 0) {
        return CLIENT_MODE_SHARED_MEMORY;
    } else if (strcmp(mode_str, "UNIX_SOCKET") == 0) {
        return CLIENT_MODE_UNIX_SOCKET;
    } else if (strcmp(mode_str, "TCP_SOCKET") == 0) {
        return CLIENT_MODE_TCP_SOCKET;
    } else {
        return CLIENT_MODE_TCP_SOCKET; // 默认
    }
}

// 检查文件是否存在
int check_file_exists(const char* filepath) {
    return access(filepath, F_OK) == 0;
}

// 写入临时文件
int write_temp_file(const char* path, const unsigned char* data, unsigned int len) {
    FILE* file = fopen(path, "wb");
    if (!file) {
        printf("❌ 无法创建临时文件: %s\n", path);
        return -1;
    }

    size_t written = fwrite(data, 1, len, file);
    fclose(file);

    if (written != len) {
        printf("❌ 写入文件失败: %s (写入 %zu/%u 字节)\n", path, written, len);
        return -1;
    }

    if (g_debug_mode) {
        printf("[DEBUG] 成功写入文件: %s (%u 字节)\n", path, len);
    }

    return 0;
}

// 释放嵌入的文件
int extract_embedded_files(char* dex_path, char* lib_path) {
    // 创建临时目录
    char temp_dir[] = "/data/local/tmp/k-server-XXXXXX";
    if (mkdtemp(temp_dir) == NULL) {
        printf("❌ 无法创建临时目录\n");
        return -1;
    }

    if (g_debug_mode) {
        printf("[DEBUG] 临时目录: %s\n", temp_dir);
    }

    // 创建lib目录
    char lib_dir[1024];
    snprintf(lib_dir, sizeof(lib_dir), "%s/lib/arm64-v8a", temp_dir);
    char mkdir_cmd[1024];
    snprintf(mkdir_cmd, sizeof(mkdir_cmd), "mkdir -p %s", lib_dir);
    system(mkdir_cmd);

    // 写入k-server.dex
    snprintf(dex_path, 1024, "%s/k-server.dex", temp_dir);
    if (write_temp_file(dex_path, k_server_dex_data, k_server_dex_len) != 0) {
        return -1;
    }

    // 写入libserver-k.so
    snprintf(lib_path, 1024, "%s/lib/arm64-v8a", temp_dir);
    char so_file[1024];
    snprintf(so_file, sizeof(so_file), "%s/libserver-k.so", lib_path);
    if (write_temp_file(so_file, libserver_k_so_data, libserver_k_so_len) != 0) {
        return -1;
    }

    // 设置执行权限
    chmod(so_file, 0755);

    printf("✅ 文件释放完成\n");
    printf("  DEX: %s (%u 字节)\n", dex_path, k_server_dex_len);
    printf("  SO:  %s (%u 字节)\n", so_file, libserver_k_so_len);

    return 0;
}

// 停止已运行的服务器
int stop_existing_server(void) {
    if (g_debug_mode) {
        printf("[DEBUG] 停止已运行的服务器...\n");
    }

    char command[256];
    snprintf(command, sizeof(command), "pkill -f 'com.github.kirer.server.Launcher'");

    printf("🛑 停止已运行的服务器...\n");
    printf("📋 停止命令: %s\n", command);

    int result = system(command);
    if (result == 0) {
        printf("✅ 服务器停止命令执行成功\n");
        sleep(2); // 等待进程完全停止
    } else {
        if (g_debug_mode) {
            printf("[DEBUG] 停止命令执行完成 (可能没有运行的服务器)\n");
        }
    }

    return 0;
}

// 启动k-server
int start_k_server(client_mode_t mode, const char* tcp_host, int tcp_port, const char* socket_name, int memory_size) {
    char dex_path[1024];
    char lib_path[1024];

    printf("📦 释放嵌入的服务器文件...\n");

    // 释放嵌入的文件
    if (extract_embedded_files(dex_path, lib_path) != 0) {
        printf("❌ 文件释放失败\n");
        return -1;
    }

    // 构造app_process命令
    char command[2048];
    const char* mode_str = (mode == CLIENT_MODE_TCP_SOCKET) ? "TCP_SOCKET" :
                          (mode == CLIENT_MODE_UNIX_SOCKET) ? "UNIX_SOCKET" : "SHARED_MEMORY";

    // 根据不同模式构造不同的参数
    switch (mode) {
        case CLIENT_MODE_TCP_SOCKET:
            snprintf(command, sizeof(command),
                "CLASSPATH=%s app_process /system/bin com.github.kirer.server.Launcher "
                "--mode=%s --libPath=%s --tcpHost=%s --tcpPort=%d %s > /data/local/tmp/k-server.log 2>&1 &",
                dex_path, mode_str, lib_path, tcp_host, tcp_port,
                g_debug_mode ? "--debug" : "");
            break;

        case CLIENT_MODE_UNIX_SOCKET:
            snprintf(command, sizeof(command),
                "CLASSPATH=%s app_process /system/bin com.github.kirer.server.Launcher "
                "--mode=%s --libPath=%s --socketName=%s %s > /data/local/tmp/k-server.log 2>&1 &",
                dex_path, mode_str, lib_path, socket_name,
                g_debug_mode ? "--debug" : "");
            break;

        case CLIENT_MODE_SHARED_MEMORY:
            snprintf(command, sizeof(command),
                "CLASSPATH=%s app_process /system/bin com.github.kirer.server.Launcher "
                "--mode=%s --libPath=%s --memorySize=%d %s > /data/local/tmp/k-server.log 2>&1 &",
                dex_path, mode_str, lib_path, memory_size,
                g_debug_mode ? "--debug" : "");
            break;

        default:
            printf("❌ 不支持的通信模式: %d\n", mode);
            return -1;
    }

    printf("🚀 启动服务器...\n");
    printf("📋 启动命令: %s\n", command);

    // 执行命令
    int result = system(command);
    if (result == 0) {
        printf("✅ 服务器启动命令执行成功\n");
    } else {
        printf("❌ 服务器启动命令执行失败，返回码: %d\n", result);
    }

    return result;
}

// 解析等号格式的参数
int parse_argument(const char* arg, const char* prefix, char* value, size_t value_size) {
    size_t prefix_len = strlen(prefix);
    if (strncmp(arg, prefix, prefix_len) == 0 && arg[prefix_len] == '=') {
        strncpy(value, arg + prefix_len + 1, value_size - 1);
        value[value_size - 1] = '\0';
        return 1;
    }
    return 0;
}

// 解析等号格式的整数参数
int parse_int_argument(const char* arg, const char* prefix, int* value) {
    char str_value[64];
    if (parse_argument(arg, prefix, str_value, sizeof(str_value))) {
        *value = atoi(str_value);
        return 1;
    }
    return 0;
}

// 保存截图数据到文件
int save_screenshot(const uint8_t* data, size_t size, const char* filename) {
    if (size < 8) {
        printf("错误: 数据格式错误，数据太小\n");
        return -1;
    }

    // 首先尝试使用OpenCV保存
    if (g_debug_mode) {
        printf("[DEBUG] 尝试使用OpenCV保存截图...\n");
    }

    int opencv_result = opencv_save_screenshot(data, size, filename, g_debug_mode);
    if (opencv_result == 0) {
        if (g_debug_mode) {
            printf("[DEBUG] ✅ OpenCV保存成功\n");
        }
    } else {
        if (g_debug_mode) {
            printf("[DEBUG] ❌ OpenCV保存失败\n");
        }
    }
    return 0;
}

// 主函数
int main(int argc, char* argv[]) {
    // 初始化OpenCV
    if (opencv_init() != 0) {
        printf("警告: OpenCV初始化失败，将使用传统方法保存图片\n");
    }

    // 默认配置
    client_mode_t mode = CLIENT_MODE_TCP_SOCKET;
    char tcp_host[256] = "127.0.0.1";
    int tcp_port = 7777;
    char socket_name[256] = "server-k";
    int memory_size = 16 * 1024 * 1024; // 16MB (足够容纳1200x2670 RGBA截图)
    int continuous = 0;
    int interval = 1;
    char output_prefix[256] = "screenshot";
    
    // 解析命令行参数
    for (int i = 1; i < argc; i++) {
        char value[256];
        
        if (parse_argument(argv[i], "--mode", value, sizeof(value))) {
            mode = parse_mode(value);
        } else if (parse_argument(argv[i], "--tcpHost", value, sizeof(value))) {
            strncpy(tcp_host, value, sizeof(tcp_host) - 1);
        } else if (parse_int_argument(argv[i], "--tcpPort", &tcp_port)) {
            if (tcp_port <= 0 || tcp_port > 65535) {
                printf("错误: 无效的端口号 %d\n", tcp_port);
                return 1;
            }
        } else if (parse_argument(argv[i], "--socketName", value, sizeof(value))) {
            strncpy(socket_name, value, sizeof(socket_name) - 1);
        } else if (parse_int_argument(argv[i], "--memorySize", &memory_size)) {
            if (memory_size <= 0) {
                printf("错误: 无效的内存大小 %d\n", memory_size);
                return 1;
            }
        } else if (strcmp(argv[i], "--continuous") == 0) {
            continuous = 1;
        } else if (strcmp(argv[i], "--auto-start-server") == 0) {
            g_auto_start_server = 1;
        } else if (parse_int_argument(argv[i], "--interval", &interval)) {
            if (interval <= 0) {
                printf("错误: 无效的间隔时间 %d\n", interval);
                return 1;
            }
        } else if (parse_argument(argv[i], "--output", value, sizeof(value))) {
            strncpy(output_prefix, value, sizeof(output_prefix) - 1);
        } else if (strcmp(argv[i], "--debug") == 0) {
            g_debug_mode = 1;
        } else if (strcmp(argv[i], "--help") == 0) {
            print_usage(argv[0]);
            return 0;
        } else if (strcmp(argv[i], "--version") == 0) {
            print_version();
            return 0;
        } else {
            printf("未知参数: %s\n", argv[i]);
            printf("使用 --help 查看帮助信息\n");
            return 1;
        }
    }
    
    // 设置信号处理
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    
    // 打印配置信息
    if (g_debug_mode) {
        printf("[DEBUG] === Server-K 客户端 v%s ===\n", VERSION);
    } else {
        printf("=== Server-K 客户端 v%s ===\n", VERSION);
    }
    
    const char* mode_names[] = {"共享内存", "Unix套接字", "TCP套接字"};
    printf("通信模式: %s\n", mode_names[mode]);
    
    switch (mode) {
        case CLIENT_MODE_TCP_SOCKET:
            printf("服务器地址: %s:%d\n", tcp_host, tcp_port);
            break;
        case CLIENT_MODE_UNIX_SOCKET:
            printf("套接字名称: %s\n", socket_name);
            break;
        case CLIENT_MODE_SHARED_MEMORY:
            printf("内存大小: %d 字节\n", memory_size);
            break;
    }
    
    if (continuous) {
        printf("连续截图模式: 间隔 %d 秒\n", interval);
    }
    printf("输出前缀: %s\n", output_prefix);
    printf("调试模式: %s\n", g_debug_mode ? "开启" : "关闭");
    printf("自动启动服务器: %s\n", g_auto_start_server ? "开启" : "关闭");
    printf("================================\n\n");
    
    // 初始化客户端
    if (g_debug_mode) {
        printf("[DEBUG] 正在初始化客户端...\n");
    } else {
        printf("正在初始化客户端...\n");
    }
    
    int result = client_initialize_with_params(mode, memory_size, socket_name, tcp_host, tcp_port);
    if (result != 0) {
        printf("错误: 客户端初始化失败 (错误代码: %d)\n", result);
        return 1;
    }
    
    // 自动启动服务器
    if (g_auto_start_server) {
        if (g_debug_mode) {
            printf("[DEBUG] 启用自动启动服务器功能\n");
        }

        // 先停止已运行的服务器
        stop_existing_server();

        // 启动服务器
        if (start_k_server(mode, tcp_host, tcp_port, socket_name, memory_size) == 0) {
            printf("⏳ 等待服务器启动...\n");
            sleep(1); // 等待服务器启动
            printf("✅ 服务器启动完成！\n");
        } else {
            printf("❌ 服务器启动失败\n");
            return 1;
        }
    }

    // 连接服务器
    if (g_debug_mode) {
        printf("[DEBUG] 正在连接服务器...\n");
    } else {
        printf("正在连接服务器...\n");
    }
    
    result = client_connect();
    if (result != 0) {
        printf("错误: 连接服务器失败 (错误代码: %d)\n", result);
        client_disconnect();
        return 1;
    }
    
    printf("✅ 连接成功！\n\n");
    
    // 截图循环
    int screenshot_count = 0;
    while (g_running) {
        if (g_debug_mode) {
            printf("[DEBUG] 正在截图 #%d...\n", screenshot_count + 1);
        } else {
            printf("正在截图 #%d...\n", screenshot_count + 1);
        }
        
        client_response_t* response = client_take_screenshot();
        if (response) {
            if (response->success && response->data && response->data_size > 0) {
                // 生成文件名
                char filename[512];
                if (continuous) {
                    snprintf(filename, sizeof(filename), "%s_%04d", output_prefix, screenshot_count + 1);
                } else {
                    snprintf(filename, sizeof(filename), "%s", output_prefix);
                }
                
                // 保存文件
                if (save_screenshot(response->data, response->data_size, filename) == 0) {
                    if (g_debug_mode) {
                        printf("[DEBUG] ✅ 截图成功! 耗时: %ld ms\n", response->processing_time);
                    } else {
                        printf("✅ 截图成功! 耗时: %ld ms\n", response->processing_time);
                    }
                    screenshot_count++;
                } else {
                    printf("❌ 保存文件失败\n");
                }
            } else {
                printf("❌ 截图失败: %s\n", response->message);
            }
            
            if (g_debug_mode && response->message[0]) {
                printf("[DEBUG] 详细信息: %s\n", response->message);
            }
            
            free_client_response(response);
        } else {
            printf("❌ 截图响应为空\n");
        }
        
        if (!continuous) {
            break;
        }
        
        printf("\n");
        
        // 等待间隔时间
        for (int i = 0; i < interval && g_running; i++) {
            sleep(1);
        }
    }
    
    // 清理资源
    if (g_debug_mode) {
        printf("\n[DEBUG] 正在断开连接...\n");
    } else {
        printf("\n正在断开连接...\n");
    }
    client_disconnect();

    // 停止服务器
    stop_existing_server();
    
    if (g_debug_mode) {
        printf("[DEBUG] 总共完成 %d 次截图\n", screenshot_count);
        printf("[DEBUG] 程序退出\n");
    } else {
        printf("总共完成 %d 次截图\n", screenshot_count);
        printf("程序退出\n");
    }

    // 清理OpenCV资源
    opencv_cleanup();

    return 0;
}
