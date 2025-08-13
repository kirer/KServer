#include "client.h"
#include "embedded/k_server_dex.h"
#include "embedded/libserver_k_so.h"
#include "common/shared_memory.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <getopt.h>
#include <sys/stat.h>
#include <errno.h>

#ifdef HAVE_OPENCV
/* OpenCV C API headers */
#include <opencv2/opencv.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#endif

// 声明嵌入文件的变量
extern unsigned char k_server_dex_data[];
extern unsigned int k_server_dex_len;
extern unsigned char libserver_k_so_data[];
extern unsigned int libserver_k_so_len;

#define MAIN_LOG_TAG "K-CLIENT-MAIN"

// 全局变量
static volatile int g_running = 1;
static int g_screenshot_count = 0;
static int g_auto_start_server = 0;

#ifdef HAVE_OPENCV
/* C++包装函数，供C代码调用 */
extern "C" {
    int save_image_with_opencv(const uint8_t *data, size_t size, const char *filename);
}

int save_image_with_opencv(const uint8_t *data, size_t size, const char *filename) {
    try {
        /* 使用OpenCV解码图片数据 */
        std::vector<uchar> buffer(data, data + size);
        cv::Mat img = cv::imdecode(buffer, cv::IMREAD_COLOR);
        
        if (!img.empty()) {
            /* 保存图片 */
            if (cv::imwrite(filename, img)) {
                LOG_INFO(MAIN_LOG_TAG, "截图已保存: %s (尺寸: %dx%d)", filename, img.cols, img.rows);
                return 0; /* 成功 */
            } else {
                LOG_ERROR(MAIN_LOG_TAG, "OpenCV保存截图失败: %s", filename);
                return -1;
            }
        } else {
            LOG_ERROR(MAIN_LOG_TAG, "OpenCV解码图片失败");
            return -1;
        }
    } catch (const cv::Exception& e) {
        LOG_ERROR(MAIN_LOG_TAG, "OpenCV异常: %s", e.what());
        return -1;
    }
}
#endif

/**
 * 保存截图到文件（支持OpenCV和纯C实现）
 */
void save_screenshot_to_file(const uint8_t *data, size_t size, uint64_t timestamp) {
    char filename[256];
    snprintf(filename, sizeof(filename), "screenshot_%llu_%d.png", (unsigned long long) timestamp,
             g_screenshot_count);

#ifdef HAVE_OPENCV
    /* 尝试使用OpenCV保存 */
    if (save_image_with_opencv(data, size, filename) == 0) {
        return; /* OpenCV保存成功 */
    }
    LOG_WARN(MAIN_LOG_TAG, "OpenCV保存失败，回退到原始数据保存");
#endif
    /* 直接保存原始数据（PNG格式） */
    FILE *fp = fopen(filename, "wb");
    if (fp) {
        size_t written = fwrite(data, 1, size, fp);
        fclose(fp);
        if (written == size) {
            LOG_INFO(MAIN_LOG_TAG, "截图原始数据已保存: %s (%zu 字节)", filename, size);
        } else {
            LOG_ERROR(MAIN_LOG_TAG, "写入截图数据失败: %s", filename);
        }
    } else {
        LOG_ERROR(MAIN_LOG_TAG, "创建截图文件失败: %s - %s", filename, strerror(errno));
    }
}

/**
 * 信号处理函数
 */
void signal_handler(int sig) {
    if (sig == SIGINT || sig == SIGTERM) {
        LOG_INFO(MAIN_LOG_TAG, "收到退出信号，正在关闭...");
        g_running = 0;
    }
}

/**
 * 消息回调函数 - 处理来自服务器的消息
 */
void on_message(message_type_t type, const uint8_t *data, uint32_t data_size) {
    switch (type) {
        case 112: { /* MSG_TYPE_NOTIFY_SHARE_MEMORY_SIZE */
            if (data_size >= 4) {
                /* 解析共享内存大小（大端序） */
                uint32_t memory_size = (data[0] << 24) | (data[1] << 16) | (data[2] << 8) | data[3];
                LOG_INFO(MAIN_LOG_TAG, "收到共享内存大小通知: %u 字节", memory_size);
                shm_connect(memory_size);
            }
            break;
        }
        case 113: { /* MSG_TYPE_NOTIFY_SCREEN_CAPTURE */
            if (data_size >= 12) {
                /* 解析截图通知数据 */
                uint64_t timestamp = 0;
                uint32_t screenshot_size = 0;
                int i;
                /* 解析时间戳（8字节，大端序） */
                for (i = 0; i < 8; i++) {
                    timestamp = (timestamp << 8) | data[i];
                }
                /* 解析数据大小（4字节，大端序） */
                for (i = 8; i < 12; i++) {
                    screenshot_size = (screenshot_size << 8) | data[i];
                }
                g_screenshot_count++;
                LOG_INFO(MAIN_LOG_TAG, "收到截图通知 #%d: 大小=%u 字节, 时间戳=%llu", g_screenshot_count, screenshot_size, (unsigned long long) timestamp);
                save_screenshot_to_file(screenshot_data, screenshot_size, timestamp);
            }
            break;
        }
        default:
            LOG_DEBUG(MAIN_LOG_TAG, "收到未知业务消息类型: %d, 数据长度: %u", type, data_size);
            break;
    }
}

/**
 * 显示使用帮助
 */
void show_usage(const char *program_name) {
    printf("用法: %s [选项]\n", program_name);
    printf("\n选项:\n");
    printf("  --socket-type=TYPE    Socket类型 (TCP|UNIX)\n");
    printf("  --address=ADDR        服务器地址\n");
    printf("                        TCP格式: host:port (例如: 127.0.0.1:7777)\n");
    printf("                        Unix格式: socket_name (例如: k.socket)\n");
    printf("  --auto-start-server   自动启动服务器\n");
    printf("  --debug               启用调试输出\n");
    printf("  --extract-files       释放嵌入的DEX和SO文件\n");
    printf("  --temp-dir=DIR        临时文件目录 (默认: /data/local/tmp)\n");
    printf("  --help                显示此帮助信息\n");
    printf("\n示例:\n");
    printf("  %s --socket-type=TCP --address=127.0.0.1:7777\n", program_name);
    printf("  %s --socket-type=UNIX --address=k.socket --auto-start-server\n", program_name);
    printf("  %s --extract-files --temp-dir=/sdcard/tmp\n", program_name);
}

/**
 * 解析socket类型字符串
 */
socket_type_t parse_socket_type(const char *type_str) {
    if (strcasecmp(type_str, "TCP") == 0) {
        return SOCKET_TYPE_TCP;
    } else if (strcasecmp(type_str, "UNIX") == 0) {
        return SOCKET_TYPE_UNIX;
    } else {
        return SOCKET_TYPE_TCP; /* 默认返回TCP类型 */
    }
}

/**
 * 检查socket类型是否有效
 */
int is_valid_socket_type(const char *type_str) {
    return (strcasecmp(type_str, "TCP") == 0 || strcasecmp(type_str, "UNIX") == 0);
}

/**
 * 检查文件是否存在
 */
int check_file_exists(const char *filepath) {
    struct stat st;
    return (stat(filepath, &st) == 0);
}

/**
 * 写入临时文件
 */
int write_temp_file(const char *filepath, const unsigned char *data, size_t size) {
    FILE *fp = fopen(filepath, "wb");
    if (!fp) {
        LOG_ERROR(MAIN_LOG_TAG, "无法创建文件: %s - %s", filepath, strerror(errno));
        return -1;
    }

    size_t written = fwrite(data, 1, size, fp);
    fclose(fp);

    if (written != size) {
        LOG_ERROR(MAIN_LOG_TAG, "写入文件失败: %s", filepath);
        return -1;
    }

    /* 设置执行权限（对于.so文件） */
    if (strstr(filepath, ".so") != NULL) {
        if (chmod(filepath, 0755) != 0) {
            LOG_ERROR(MAIN_LOG_TAG, "设置文件权限失败: %s - %s", filepath, strerror(errno));
            return -1;
        }
    }

    return 0;
}

/**
 * 释放嵌入的文件
 */
int extract_embedded_files(const char *temp_dir) {
    char dex_path[512];
    char so_path[512];

    snprintf(dex_path, sizeof(dex_path), "%s/k_server.dex", temp_dir);
    snprintf(so_path, sizeof(so_path), "%s/libserver_k.so", temp_dir);

    /* 检查文件是否已存在 */
    if (check_file_exists(dex_path) && check_file_exists(so_path)) {
        LOG_INFO(MAIN_LOG_TAG, "嵌入文件已存在，跳过释放");
        return 0;
    }

    /* 释放DEX文件 */
    if (write_temp_file(dex_path, k_server_dex_data, k_server_dex_len) < 0) {
        return -1;
    }
    LOG_INFO(MAIN_LOG_TAG, "已释放DEX文件: %s (%u 字节)", dex_path, k_server_dex_len);

    /* 释放SO文件 */
    if (write_temp_file(so_path, libserver_k_so_data, libserver_k_so_len) < 0) {
        return -1;
    }
    LOG_INFO(MAIN_LOG_TAG, "已释放SO文件: %s (%u 字节)", so_path, libserver_k_so_len);

    return 0;
}

/**
 * 停止已运行的服务器
 */
static int stop_existing_server(void) {
    LOG_INFO(MAIN_LOG_TAG, "停止已运行的服务器...");

    char command[256];
    snprintf(command, sizeof(command), "pkill -f 'com.github.kirer.server.Launcher'");

    LOG_INFO(MAIN_LOG_TAG, "停止命令: %s", command);

    int result = system(command);
    if (result == 0) {
        LOG_INFO(MAIN_LOG_TAG, "服务器停止命令执行成功");
        sleep(2); /* 等待进程完全停止 */
    } else {
        LOG_DEBUG(MAIN_LOG_TAG, "停止命令执行完成 (可能没有运行的服务器)");
    }

    return 0;
}

/**
 * 启动k-server
 */
static int start_server(socket_type_t socket_type, const char *address) {
    const char *temp_dir = "/data/local/tmp";
    char dex_path[512];
    char so_path[512];
    snprintf(dex_path, sizeof(dex_path), "%s/k_server.dex", temp_dir);
    snprintf(so_path, sizeof(so_path), "%s/libserver_k.so", temp_dir);
    LOG_INFO(MAIN_LOG_TAG, "释放嵌入的服务器文件...");
    /* 释放嵌入的文件 */
    if (extract_embedded_files(temp_dir) != 0) {
        LOG_ERROR(MAIN_LOG_TAG, "文件释放失败");
        return -1;
    }
    /* 构造app_process命令 */
    char command[2048];
    const char *mode_str = (socket_type == SOCKET_TYPE_TCP) ? "TCP_SOCKET" : "UNIX_SOCKET";
    /* 解析地址 */
    char host[256] = {0};
    int port = 0;
    char socket_name[256] = {0};
    if (client_parse_address(address, host, &port, socket_name, socket_type) < 0) {
        LOG_ERROR(MAIN_LOG_TAG, "解析地址失败: %s", address);
        return -1;
    }
    /* 根据不同模式构造不同的参数 */
    if (socket_type == SOCKET_TYPE_TCP) {
        snprintf(command, sizeof(command),
                 "CLASSPATH=%s app_process /system/bin com.github.kirer.server.Launcher "
                 "--lib-path=%s --socket-type=%s --address=%s:%d %s > /data/local/tmp/k-server.log 2>&1 &",
                 dex_path, mode_str, so_path, host, port, g_client.config.debug ? "--debug" : "");
    } else {
        snprintf(command, sizeof(command),
                 "CLASSPATH=%s app_process /system/bin com.github.kirer.server.Launcher "
                 "--lib-path=%s --socket-type=%s --address=%s %s > /data/local/tmp/k-server.log 2>&1 &",
                 dex_path, mode_str, so_path, socket_name, g_client.config.debug ? "--debug" : "");
    }
    LOG_INFO(MAIN_LOG_TAG, "启动服务器...");
    LOG_INFO(MAIN_LOG_TAG, "启动命令: %s", command);
    /* 执行命令 */
    int result = system(command);
    if (result == 0) {
        LOG_INFO(MAIN_LOG_TAG, "服务器启动命令执行成功");
        sleep(3); /* 等待服务器启动 */
    } else {
        LOG_ERROR(MAIN_LOG_TAG, "服务器启动命令执行失败，返回码: %d", result);
        return -1;
    }
    return 0;
}

/**
 * 自动启动服务器
 */
int client_auto_start_server(void) {
    if (!g_auto_start_server) {
        return 0; /* 未配置自动启动 */
    }
    LOG_INFO(MAIN_LOG_TAG, "开始自动启动服务器");
    /* 停止已运行的服务器 */
    stop_existing_server();
    /* 启动新的服务器 */
    if (start_server(g_client.config.socket_type, g_client.config.address) < 0) {
        LOG_ERROR(MAIN_LOG_TAG, "自动启动服务器失败");
        return -1;
    }
    LOG_INFO(MAIN_LOG_TAG, "自动启动服务器完成");
    return 0;
}

/**
 * 主函数
 */
int main(int argc, char *argv[]) {
    /* 默认配置 */
    client_config_t config = {
            .socket_type = SOCKET_TYPE_TCP,
            .address = "127.0.0.1:7777",
            .debug = 0
    };

    int extract_files = 0;
    const char *temp_dir = "/data/local/tmp";

    /* 命令行选项 */
    static struct option long_options[] = {
            {"socket-type",       required_argument, 0, 's'},
            {"address",           required_argument, 0, 'a'},
            {"auto-start-server", no_argument,       0, 'S'},
            {"debug",             no_argument,       0, 'd'},
            {"extract-files",     no_argument,       0, 'e'},
            {"temp-dir",          required_argument, 0, 't'},
            {"help",              no_argument,       0, 'h'},
            {0, 0,                                   0, 0}
    };

    int option_index = 0;
    int c;

    while ((c = getopt_long(argc, argv, "s:a:Sdet:h", long_options, &option_index)) != -1) {
        switch (c) {
            case 's': {
                if (!is_valid_socket_type(optarg)) {
                    fprintf(stderr, "错误: 无效的socket类型: %s\n", optarg);
                    return 1;
                }
                config.socket_type = parse_socket_type(optarg);
                break;
            }
            case 'a':
                strncpy(config.address, optarg, sizeof(config.address) - 1);
                config.address[sizeof(config.address) - 1] = '\0';
                break;
            case 'S':
                g_auto_start_server = 1;
                break;
            case 'd':
                config.debug = 1;
                break;
            case 'e':
                extract_files = 1;
                break;
            case 't':
                temp_dir = optarg;
                break;
            case 'h':
                show_usage(argv[0]);
                return 0;
            case '?':
                fprintf(stderr, "使用 --help 查看帮助信息\n");
                return 1;
            default:
                break;
        }
    }

    /* 设置信号处理 */
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    LOG_INFO(MAIN_LOG_TAG, "K-Client 启动");
    LOG_INFO(MAIN_LOG_TAG, "Socket类型: %s",
             config.socket_type == SOCKET_TYPE_TCP ? "TCP" : "UNIX");
    LOG_INFO(MAIN_LOG_TAG, "服务器地址: %s", config.address);
    LOG_INFO(MAIN_LOG_TAG, "自动启动服务器: %s", g_auto_start_server ? "是" : "否");
    LOG_INFO(MAIN_LOG_TAG, "调试模式: %s", config.debug ? "是" : "否");

    /* 释放嵌入文件 */
    if (extract_files) {
        LOG_INFO(MAIN_LOG_TAG, "正在释放嵌入文件到: %s", temp_dir);
        if (extract_embedded_files(temp_dir) < 0) {
            LOG_ERROR(MAIN_LOG_TAG, "释放嵌入文件失败");
            return 1;
        }
        LOG_INFO(MAIN_LOG_TAG, "嵌入文件释放完成");
    }

    /* 初始化客户端 */
    if (client_init(&config) < 0) {
        LOG_ERROR(MAIN_LOG_TAG, "初始化客户端失败");
        return 1;
    }

    /* 设置消息回调函数 */
    client_set_message_callback(on_message);

    /* 自动启动服务器（如果配置了） */
    if (client_auto_start_server() < 0) {
        LOG_ERROR(MAIN_LOG_TAG, "自动启动服务器失败");
        client_cleanup();
        return 1;
    }

    /* 连接到服务器 */
    if (client_connect() < 0) {
        LOG_ERROR(MAIN_LOG_TAG, "连接服务器失败");
        client_cleanup();
        return 1;
    }

    LOG_INFO(MAIN_LOG_TAG, "连接成功，等待截图数据...");

    /* 主循环 */
    while (g_running && client_get_state() == CLIENT_STATE_CONNECTED) {
        /* 定期发送心跳 */
        static int heartbeat_counter = 0;
        if (++heartbeat_counter >= 100) { /* 每2秒发送一次心跳（假设循环间隔20ms） */
            client_send_heartbeat();
            heartbeat_counter = 0;
        }

        usleep(20000); /* 20ms */
    }

    LOG_INFO(MAIN_LOG_TAG, "正在关闭客户端...");

    /* 断开连接 */
    client_disconnect();

    /* 清理资源 */
    client_cleanup();

    LOG_INFO(MAIN_LOG_TAG, "K-Client 已退出，共收到 %d 张截图", g_screenshot_count);
    return 0;
}