#include "shared_memory.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <errno.h>
#include <time.h>

// 全局共享内存实例
shared_memory_t g_shm = {-1, NULL, 0, NULL, NULL, NULL, 0};

/**
 * 获取当前时间戳（毫秒）
 */
uint64_t shm_get_timestamp(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)ts.tv_nsec / 1000000ULL;
}

/**
 * 创建共享内存
 */
int shm_create(size_t size) {
    // 总大小 = 头部(16字节) + 数据区域
    size_t total_size = size + 16;
    LOG_INFO(SHM_LOG_TAG, "创建共享内存，数据大小: %zu 字节，总大小: %zu 字节", size, total_size);
    // 清理现有资源
    shm_cleanup();
    // 删除可能存在的文件
    unlink(SHM_FILE_PATH);
    // 创建文件
    int fd = open(SHM_FILE_PATH, O_CREAT | O_RDWR, 0666);
    if (fd < 0) {
        LOG_ERROR(SHM_LOG_TAG, "创建共享内存文件失败: %s", strerror(errno));
        return -1;
    }
    // 设置文件大小
    if (ftruncate(fd, (off_t)total_size) < 0) {
        LOG_ERROR(SHM_LOG_TAG, "设置共享内存文件大小失败: %s", strerror(errno));
        close(fd);
        unlink(SHM_FILE_PATH);
        return -1;
    }
    // 映射内存
    void *base = mmap(NULL, total_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) {
        LOG_ERROR(SHM_LOG_TAG, "映射共享内存失败: %s", strerror(errno));
        close(fd);
        unlink(SHM_FILE_PATH);
        return -1;
    }
    // 初始化全局结构体
    g_shm.fd = fd;
    g_shm.base = base;
    g_shm.total_size = total_size;
    g_shm.timestamp = (uint64_t *)base;
    g_shm.data_size = (uint64_t *)((unsigned char *)base + 8);
    g_shm.data_ptr = (unsigned char *)base + 16;
    g_shm.last_read_timestamp = 0;
    // 初始化头部数据
    *g_shm.timestamp = 0;
    *g_shm.data_size = 0;
    LOG_INFO(SHM_LOG_TAG, "共享内存创建成功：%d，%s", g_shm.fd, base);
    return 0;
}

/**
 * 连接到现有共享内存
 */
int shm_connect(size_t size) {
    size_t total_size = size + 16;
    
    LOG_INFO(SHM_LOG_TAG, "连接共享内存，数据大小: %zu 字节，总大小: %zu 字节", size, total_size);
    
    // 打开文件
    int fd = open(SHM_FILE_PATH, O_RDWR);
    if (fd < 0) {
        LOG_ERROR(SHM_LOG_TAG, "打开共享内存文件失败: %s", strerror(errno));
        return -1;
    }
    
    // 检查文件大小
    struct stat st;
    if (fstat(fd, &st) < 0) {
        LOG_ERROR(SHM_LOG_TAG, "获取共享内存文件信息失败: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    if ((size_t)st.st_size != total_size) {
        LOG_ERROR(SHM_LOG_TAG, "共享内存文件大小不匹配，期望: %zu，实际: %ld", total_size, st.st_size);
        close(fd);
        return -1;
    }
    
    // 映射内存
    void *base = mmap(NULL, total_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) {
        LOG_ERROR(SHM_LOG_TAG, "映射共享内存失败: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    // 初始化全局结构体
    g_shm.fd = fd;
    g_shm.base = base;
    g_shm.total_size = total_size;
    g_shm.timestamp = (uint64_t *)base;
    g_shm.data_size = (uint64_t *)((unsigned char *)base + 8);
    g_shm.data_ptr = (unsigned char *)base + 16;
    g_shm.last_read_timestamp = 0;
    
    LOG_INFO(SHM_LOG_TAG, "共享内存连接成功");
    return 0;
}

/**
 * 写入数据到共享内存
 */
int shm_write(const void *data, size_t size) {
    if (!data || !shm_is_initialized()) {
        LOG_ERROR(SHM_LOG_TAG, "写入失败：参数无效或共享内存未初始化: %d", g_shm.fd);
        return -1;
    }
    
    // 检查数据大小合理性（50MB限制）
    if (size > 50 * 1024 * 1024) {
        LOG_ERROR(SHM_LOG_TAG, "写入数据大小过大: %zu", size);
        return -1;
    }
    
    // 检查缓冲区大小
    size_t available_size = g_shm.total_size - 16;
    if (size > available_size) {
        LOG_ERROR(SHM_LOG_TAG, "数据大小超出缓冲区容量: %zu > %zu", size, available_size);
        return -1;
    }
    
    // 复制数据
    memcpy(g_shm.data_ptr, data, size);
    
    // 内存屏障确保数据写入完成
    __sync_synchronize();
    
    // 更新数据大小
    *g_shm.data_size = (uint64_t)size;
    
    // 内存屏障确保大小更新完成
    __sync_synchronize();
    
    // 更新时间戳（最后更新，表示数据就绪）
    *g_shm.timestamp = shm_get_timestamp();
    
    LOG_DEBUG(SHM_LOG_TAG, "写入数据成功，大小: %zu 字节，时间戳: %llu", size, *g_shm.timestamp);
    return 0;
}

/**
 * 检查是否有新数据
 */
int shm_has_new_data(void) {
    if (!shm_is_initialized()) {
        return 0;
    }
    
    uint64_t current_timestamp = *g_shm.timestamp;
    return (current_timestamp > g_shm.last_read_timestamp) ? 1 : 0;
}

/**
 * 从共享内存读取数据
 */
int shm_read(uint8_t **data, size_t *data_size) {
    if (!data || !data_size || !shm_is_initialized()) {
        LOG_ERROR(SHM_LOG_TAG, "读取失败：参数无效或共享内存未初始化");
        return -1;
    }
    
    // 检查是否有新数据
    uint64_t current_timestamp = *g_shm.timestamp;
    if (current_timestamp <= g_shm.last_read_timestamp) {
        LOG_DEBUG(SHM_LOG_TAG, "无新数据可读取");
        return 1; // 无新数据
    }
    
    // 读取数据大小
    size_t size = (size_t)*g_shm.data_size;
    if (size == 0) {
        LOG_DEBUG(SHM_LOG_TAG, "数据大小为0");
        return 1; // 无数据
    }
    
    // 检查数据大小合理性
    if (size > g_shm.total_size - 16) {
        LOG_ERROR(SHM_LOG_TAG, "数据大小异常: %zu", size);
        return -1;
    }
    
    // 分配内存并复制数据
    uint8_t *buffer = (uint8_t *)malloc(size);
    if (!buffer) {
        LOG_ERROR(SHM_LOG_TAG, "分配内存失败: %zu 字节", size);
        return -1;
    }
    
    memcpy(buffer, g_shm.data_ptr, size);
    
    // 更新最后读取时间戳
    g_shm.last_read_timestamp = current_timestamp;
    
    *data = buffer;
    *data_size = size;
    
    LOG_DEBUG(SHM_LOG_TAG, "读取数据成功，大小: %zu 字节，时间戳: %llu", size, current_timestamp);
    return 0;
}

/**
 * 检查共享内存是否已初始化
 */
int shm_is_initialized(void) {
    return (g_shm.fd >= 0 && g_shm.base != NULL) ? 1 : 0;
}

/**
 * 清理共享内存
 */
int shm_cleanup(void) {
    int result = 0;
    if (g_shm.base != NULL && g_shm.base != MAP_FAILED) {
        if (munmap(g_shm.base, g_shm.total_size) < 0) {
            LOG_ERROR(SHM_LOG_TAG, "取消内存映射失败: %s", strerror(errno));
            result = -1;
        }
        g_shm.base = NULL;
    }
    if (g_shm.fd >= 0) {
        if (close(g_shm.fd) < 0) {
            LOG_ERROR(SHM_LOG_TAG, "关闭文件描述符失败: %s", strerror(errno));
            result = -1;
        }
        g_shm.fd = -1;
    }
    // 重置结构体
    g_shm.total_size = 0;
    g_shm.timestamp = NULL;
    g_shm.data_size = NULL;
    g_shm.data_ptr = NULL;
    g_shm.last_read_timestamp = 0;
    LOG_INFO(SHM_LOG_TAG, "共享内存清理完成");
    return result;
}

#ifdef __ANDROID__
// JNI接口实现
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_create(JNIEnv *env, jclass clazz, jint size) {
    return shm_create((size_t)size);
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_connect(JNIEnv *env, jclass clazz, jint size) {
    return shm_connect((size_t)size);
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_write(JNIEnv *env, jclass clazz, jbyteArray data) {
    if (!data) {
        return -1;
    }
    
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) {
        return -1;
    }
    
    int result = shm_write(bytes, (size_t)len);
    
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_github_kirer_server_ShareMemory_read(JNIEnv *env, jclass clazz) {
    uint8_t *data = NULL;
    size_t data_size = 0;
    
    int result = shm_read(&data, &data_size);
    if (result != 0 || !data) {
        return NULL;
    }
    
    jbyteArray array = (*env)->NewByteArray(env, (jsize)data_size);
    if (array) {
        (*env)->SetByteArrayRegion(env, array, 0, (jsize)data_size, (jbyte *)data);
    }
    
    free(data);
    return array;
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_clean(JNIEnv *env, jclass clazz) {
    return shm_cleanup();
}
#endif