#include "ashmem.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <errno.h>
#include <time.h>
#include <pthread.h>
#ifdef ANDROID
#include <android/log.h>
#else
// Mock Android log for non-Android systems
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_ERROR 6
int __android_log_print(int prio, const char* tag, const char* fmt, ...);
#endif

// Global variables
int g_shmem_fd = -1;
void* g_shmem_base = NULL;
uint64_t* g_timestamp = NULL;
uint64_t* g_data_size = NULL;
void* g_data_ptr = NULL;
uint64_t g_last_read_ts = 0;

// Helper function to get current timestamp in milliseconds
static uint64_t get_timestamp_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

int try_create_ashmem(size_t size) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "尝试创建ashmem共享内存，大小: %zu 字节", size);

    int fd = open(ASHMEM_DEVICE, O_RDWR);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "打开ashmem设备失败");
        return -1;
    }

    // Set name
    if (ioctl(fd, ASHMEM_SET_NAME, AUTOGO_SHARED_MEMORY) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "设置ashmem名称失败");
        close(fd);
        return -1;
    }

    // Set size
    if (ioctl(fd, ASHMEM_SET_SIZE, size) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "设置ashmem大小失败");
        close(fd);
        return -1;
    }

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "ashmem创建成功，文件描述符: %d", fd);
    return fd;
}

int try_create_memfd(size_t size) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "尝试创建memfd共享内存，大小: %zu 字节", size);

    int fd = syscall(SYS_MEMFD_CREATE, SHARED_MEMORY_NAME, O_RDWR);
    if (fd == -1) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "创建memfd失败");
        return -1;
    }

    if (ftruncate(fd, size) == -1) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "设置memfd大小失败");
        close(fd);
        return -1;
    }

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "memfd创建成功，文件描述符: %d", fd);
    return fd;
}

int shmem_create(size_t size) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "【服务端】创建共享内存文件，大小: %zu 字节", size + 16);

    // 删除可能存在的旧文件
    unlink(SHMEM_FILE_PATH);

    // 创建共享内存文件
    int fd = open(SHMEM_FILE_PATH, O_CREAT | O_RDWR, 0666);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "创建共享内存文件失败: %s", strerror(errno));

        // 回退到memfd方式
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "回退到memfd方式");
        fd = try_create_memfd(size + 16);
        if (fd == -1) {
            __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "memfd创建也失败");
            return -1;
        }
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "memfd创建成功");
    } else {
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "共享内存文件创建成功: %s", SHMEM_FILE_PATH);

        // 设置文件大小
        if (ftruncate(fd, size + 16) < 0) {
            __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "设置文件大小失败: %s", strerror(errno));
            close(fd);
            unlink(SHMEM_FILE_PATH);
            return -1;
        }
    }

    g_shmem_fd = fd;

    // 映射内存
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "映射内存到进程地址空间");
    void* base = mmap(NULL, size + 16, PROT_READ | PROT_WRITE, MAP_SHARED, g_shmem_fd, 0);
    if (base == MAP_FAILED) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "内存映射失败: %s", strerror(errno));
        close(g_shmem_fd);
        unlink(SHMEM_FILE_PATH);
        return -1;
    }

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "内存映射成功，基地址: %p", base);
    g_shmem_base = base;
    g_timestamp = (uint64_t*)base;                    // 前8字节存储时间戳
    g_data_size = (uint64_t*)((char*)base + 8);       // 第8-16字节存储数据大小
    g_data_ptr = (char*)base + 16;                    // 第16字节后存储实际数据

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "【服务端】共享内存创建完成，文件路径: %s", SHMEM_FILE_PATH);
    return 0;
}

/**
 * 客户端连接到共享内存文件
 */
int shmem_connect(size_t size) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "【客户端】连接共享内存文件，大小: %zu 字节", size + 16);

    // 等待共享内存文件存在，最多等待5秒
    int fd = -1;
    for (int i = 0; i < 50; i++) {
        fd = open(SHMEM_FILE_PATH, O_RDWR);
        if (fd >= 0) {
            __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "成功打开共享内存文件: %s", SHMEM_FILE_PATH);
            break;
        }

        if (i == 0) {
            __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "等待共享内存文件创建: %s", SHMEM_FILE_PATH);
        }

        usleep(100000);  // 等待100ms
    }

    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "打开共享内存文件失败: %s", strerror(errno));
        return -1;
    }

    // 映射内存
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "映射共享内存到客户端进程");
    void* base = mmap(NULL, size + 16, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "客户端内存映射失败: %s", strerror(errno));
        close(fd);
        return -1;
    }

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "客户端内存映射成功，基地址: %p", base);

    // 设置全局变量
    g_shmem_fd = fd;
    g_shmem_base = base;
    g_timestamp = (uint64_t*)base;                    // 前8字节存储时间戳
    g_data_size = (uint64_t*)((char*)base + 8);       // 第8-16字节存储数据大小
    g_data_ptr = (char*)base + 16;                    // 第16字节后存储实际数据

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "【客户端】共享内存连接完成");
    return 0;
}

int shmem_read(void* buffer, size_t max_size) {
    if (g_shmem_fd < 0 || !g_shmem_base) {
        return -1;
    }

    uint64_t current_ts = *g_timestamp;
    if (current_ts == g_last_read_ts) {
        return 0; // No new data
    }

    size_t data_size = *g_data_size;
    if (!buffer) {
        return data_size; // Return size only
    }

    if (data_size > max_size) {
        return -1; // Buffer too small
    }

    memcpy(buffer, g_data_ptr, data_size);
    g_last_read_ts = current_ts;
    return data_size;
}

int shmem_write(const void* data, size_t size) {
    if (!data || g_shmem_fd < 0 || !g_shmem_base) {
        return -1;
    }

    // Copy data
    memcpy(g_data_ptr, data, size);

    // Memory barrier
    __sync_synchronize();

    // Update size
    *g_data_size = size;

    // Memory barrier
    __sync_synchronize();

    // Update timestamp
    *g_timestamp = get_timestamp_ms();

    return 0;
}

int shmem_cleanup(void) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "清理共享内存资源");

    // 清理共享内存映射
    if (g_shmem_base) {
        munmap(g_shmem_base, 0); // Note: size should be tracked
        g_shmem_base = NULL;
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "共享内存映射已清理");
    }

    // 关闭文件描述符
    if (g_shmem_fd >= 0) {
        close(g_shmem_fd);
        g_shmem_fd = -1;
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "共享内存文件描述符已关闭");
    }

    // 删除共享内存文件（仅服务端需要）
    if (access(SHMEM_FILE_PATH, F_OK) == 0) {
        unlink(SHMEM_FILE_PATH);
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "共享内存文件已删除: %s", SHMEM_FILE_PATH);
    }

    return 0;
}

char* shell(const char* command) {
    if (!command) {
        return NULL;
    }

    char cmd_buf[1024];
    snprintf(cmd_buf, sizeof(cmd_buf), "%s 2>&1", command);

    FILE* fp = popen(cmd_buf, "r");
    if (!fp) {
        return NULL;
    }

    char* result = NULL;
    char* line = NULL;
    size_t len = 0;
    size_t total_len = 0;

    while (getline(&line, &len, fp) != -1) {
        size_t line_len = strlen(line);
        result = realloc(result, total_len + line_len + 1);
        if (!result) {
            free(line);
            pclose(fp);
            return NULL;
        }

        if (total_len == 0) {
            strcpy(result, line);
        } else {
            strcat(result, line);
        }
        total_len += line_len;
    }

    free(line);
    pclose(fp);
    return result;
}

// JNI Functions
JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_init(JNIEnv* env, jclass clazz, jint size) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI调用: 初始化共享内存服务端");
    return shmem_create(size);
}

// Unix Socket connect JNI函数已移除，只使用TCP连接

JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_write(JNIEnv* env, jclass clazz, jbyteArray data) {
    if (!data) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "JNI写入: 数据为空");
        return -1;
    }
    jsize len = (*env)->GetArrayLength(env, data);
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI写入: 数据长度 %d 字节", len);

    jbyte* bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "JNI写入: 获取字节数组失败");
        return -1;
    }

    int result = -1;
    if (g_shmem_fd >= 0 && g_shmem_base) {
        // Copy data
        memcpy(g_data_ptr, bytes, len);
        // Memory barrier
        __sync_synchronize();
        // Update size
        *g_data_size = len;
        // Memory barrier
        __sync_synchronize();
        // Update timestamp
        *g_timestamp = get_timestamp_ms();
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI写入: 数据写入成功，时间戳: %llu", *g_timestamp);
        result = 0;
    } else {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "JNI写入: 共享内存未初始化");
    }
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_github_kirer_server_Ashmem_read(JNIEnv* env, jclass clazz) {
    if (g_shmem_fd < 0 || !g_shmem_base) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "JNI读取: 共享内存未初始化");
        return NULL;
    }

    // Check if there's new data
    uint64_t current_ts = *g_timestamp;
    if (current_ts == g_last_read_ts) {
        __android_log_print(ANDROID_LOG_DEBUG, SHMEM_LOG_TAG, "JNI读取: 没有新数据");
        return NULL; // No new data
    }

    size_t data_size = *g_data_size;
    if (data_size == 0) {
        __android_log_print(ANDROID_LOG_DEBUG, SHMEM_LOG_TAG, "JNI读取: 数据大小为0");
        return NULL;
    }

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI读取: 读取数据 %zu 字节，时间戳: %llu", data_size, current_ts);

    // Create Java byte array
    jbyteArray result = (*env)->NewByteArray(env, data_size);
    if (!result) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "JNI读取: 创建字节数组失败");
        return NULL;
    }

    // Copy data to Java array
    (*env)->SetByteArrayRegion(env, result, 0, data_size, (const jbyte*)g_data_ptr);

    // Update last read timestamp
    g_last_read_ts = current_ts;

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI读取: 数据读取成功");
    return result;
}

// Socket相关代码已完全移除，使用直接文件访问

// ================================
// JNI Functions
// ================================

/**
 * JNI: 创建共享内存文件（服务端）
 */
JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_create(JNIEnv* env, jclass clazz, jint size) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI调用: 创建共享内存，大小: %d", size);
    return shmem_create(size);
}

/**
 * JNI: 连接共享内存文件（客户端）
 */
JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_connect(JNIEnv* env, jclass clazz, jint size) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI调用: 连接共享内存，大小: %d", size);
    return shmem_connect(size);
}
