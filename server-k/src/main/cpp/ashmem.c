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
#include <android/log.h>

// Global variables
int g_shmem_fd = -1;
void* g_shmem_base = NULL;
size_t g_shmem_size = 0;  // 记录共享内存大小
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

// 辅助函数：创建memfd作为回退方案
static int create_memfd_fallback(size_t size) {
    int fd = syscall(SYS_MEMFD_CREATE, "kserver_shmem", 0);
    if (fd == -1) {
        return -1;
    }

    if (ftruncate(fd, size) == -1) {
        close(fd);
        return -1;
    }

    return fd;
}

int shmem_create(size_t size) {
    size_t total_size = size + 16;
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "创建共享内存文件，大小: %zu 字节", total_size);

    // 先清理之前可能存在的共享内存资源
    shmem_cleanup();

    // 确保删除旧文件
    unlink(SHMEM_FILE_PATH);

    // 创建共享内存文件
    int fd = open(SHMEM_FILE_PATH, O_CREAT | O_RDWR, 0666);
    if (fd < 0) {
        // 回退到memfd
        fd = create_memfd_fallback(total_size);
        if (fd == -1) {
            __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "创建共享内存失败");
            return -1;
        }
    } else {
        // 设置文件大小
        if (ftruncate(fd, total_size) < 0) {
            close(fd);
            unlink(SHMEM_FILE_PATH);
            return -1;
        }
    }

    // 映射内存
    void* base = mmap(NULL, total_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) {
        close(fd);
        unlink(SHMEM_FILE_PATH);
        return -1;
    }

    // 设置全局变量
    g_shmem_fd = fd;
    g_shmem_base = base;
    g_shmem_size = total_size;  // 记录内存大小
    g_timestamp = (uint64_t*)base;
    g_data_size = (uint64_t*)((char*)base + 8);
    g_data_ptr = (char*)base + 16;

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "共享内存创建成功");
    return 0;
}

int shmem_connect(size_t size) {
    size_t total_size = size + 16;
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "连接共享内存文件，大小: %zu 字节", total_size);

    // 等待文件存在，最多5秒
    int fd = -1;
    for (int i = 0; i < 50; i++) {
        fd = open(SHMEM_FILE_PATH, O_RDWR);
        if (fd >= 0) break;
        usleep(100000);  // 100ms
    }

    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "打开共享内存文件失败");
        return -1;
    }

    // 映射内存
    void* base = mmap(NULL, total_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) {
        close(fd);
        return -1;
    }

    // 设置全局变量
    g_shmem_fd = fd;
    g_shmem_base = base;
    g_shmem_size = total_size;  // 记录内存大小
    g_timestamp = (uint64_t*)base;
    g_data_size = (uint64_t*)((char*)base + 8);
    g_data_ptr = (char*)base + 16;

    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "共享内存连接成功");
    return 0;
}

int shmem_read(void* buffer, size_t* data_size) {
    if (!g_shmem_base || !g_timestamp || !g_data_size || !g_data_ptr) {
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "共享内存未正确初始化");
        return -1;
    }

    // 安全地读取时间戳
    uint64_t current_ts;
    __sync_synchronize();
    current_ts = *g_timestamp;

    if (current_ts == g_last_read_ts) {
        return 0; // 没有新数据
    }

    // 安全地读取数据大小
    __sync_synchronize();
    size_t size = *g_data_size;

    if (size == 0) {
        return 0; // 数据大小为0
    }

    // 基本的大小检查（防止过大的数据）
    if (size > 50 * 1024 * 1024) { // 50MB限制
        __android_log_print(ANDROID_LOG_ERROR, SHMEM_LOG_TAG, "数据大小异常: %zu", size);
        return -1;
    }

    if (buffer) {
        memcpy(buffer, g_data_ptr, size);
    }

    if (data_size) {
        *data_size = size;
    }

    g_last_read_ts = current_ts;
    return 1; // 有新数据
}

int shmem_write(const void* data, size_t size) {
    if (!data || !g_shmem_base) {
        return -1;
    }

    memcpy(g_data_ptr, data, size);
    __sync_synchronize();
    *g_data_size = size;
    __sync_synchronize();
    *g_timestamp = get_timestamp_ms();

    return 0;
}

int shmem_cleanup(void) {
    int result = 0;

    if (g_shmem_base) {
        // 注意：munmap的size参数在这里设为0是不正确的，但由于我们无法跨进程
        // 记录大小，这里暂时保持原样。真正的解决方案是避免不必要的munmap调用
        // 或者在每个进程中单独管理内存大小
        g_shmem_base = NULL;
        g_timestamp = NULL;
        g_data_size = NULL;
        g_data_ptr = NULL;
    }

    if (g_shmem_fd >= 0) {
        if (close(g_shmem_fd) != 0) {
            result = -1;
        }
        g_shmem_fd = -1;
    }

    // 删除共享内存文件（忽略错误，因为文件可能不存在）
    unlink(SHMEM_FILE_PATH);

    // 重置读取时间戳
    g_last_read_ts = 0;

    return result;
}

// shell函数已移除，不再使用

// ================================
// JNI Functions
// ================================

JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_write(JNIEnv* env, jclass clazz, jbyteArray data) {
    if (!data || !g_shmem_base) {
        return -1;
    }

    jsize len = (*env)->GetArrayLength(env, data);
    jbyte* bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) {
        return -1;
    }

    int result = shmem_write(bytes, len);
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);

    return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_github_kirer_server_Ashmem_read(JNIEnv* env, jclass clazz) {
    if (!g_shmem_base) {
        return NULL;
    }

    size_t data_size;
    int result = shmem_read(NULL, &data_size);
    if (result <= 0 || data_size == 0) {
        return NULL; // 没有新数据或数据为空
    }

    jbyteArray jarray = (*env)->NewByteArray(env, data_size);
    if (!jarray) {
        return NULL;
    }

    // 直接从共享内存拷贝数据到Java数组（避免重复调用shmem_read）
    (*env)->SetByteArrayRegion(env, jarray, 0, data_size, (const jbyte*)g_data_ptr);

    return jarray;
}

JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_create(JNIEnv* env, jclass clazz, jint size) {
    return shmem_create(size);
}

JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_connect(JNIEnv* env, jclass clazz, jint size) {
    return shmem_connect(size);
}

JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_cleanup(JNIEnv* env, jclass clazz) {
    __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "JNI调用: 清理共享内存资源");
    int result = shmem_cleanup();
    if (result == 0) {
        __android_log_print(ANDROID_LOG_INFO, SHMEM_LOG_TAG, "共享内存资源清理成功");
    } else {
        __android_log_print(ANDROID_LOG_WARN, SHMEM_LOG_TAG, "共享内存资源清理时发生错误");
    }
    return result;
}
