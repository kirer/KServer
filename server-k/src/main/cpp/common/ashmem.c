#include "ashmem.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <errno.h>
#include <time.h>

int g_asm_fd = -1;
void *g_asm_base = NULL;
uint64_t *g_asm_timestamp = NULL;
uint64_t *g_asm_data_size = NULL;
void *g_asm_data_ptr = NULL;
uint64_t g_asm_last_read_ts = 0;

static uint64_t get_timestamp_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return (uint64_t) ts.tv_sec * 1000ULL + (uint64_t) ts.tv_nsec / 1000000ULL;
}

int asm_create(size_t size) {
    size_t total_size = size + 16;
    LOG_INFO(SM_LOG_TAG, "创建共享内存文件，大小: %zu 字节", total_size);
    (void) asm_cleanup();
    (void) unlink(SM_FILE_PATH);
    int fd = open(SM_FILE_PATH, O_CREAT | O_RDWR, 0666);
    if (fd < 0) {
        LOG_ERROR(SM_LOG_TAG, "创建共享内存失败");
        return -1;
    }
    if (ftruncate(fd, (off_t) total_size) < 0) {
        LOG_ERROR(SM_LOG_TAG, "创建共享内存失败");
        close(fd);
        (void) unlink(SM_FILE_PATH);
        return -1;
    }
    LOG_DEBUG(SM_LOG_TAG, "创建共享内存成功");
    // 映射内存
    void *base = mmap(NULL, total_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) {
        close(fd);
        (void) unlink(SM_FILE_PATH); // 忽略返回值
        return -1;
    }
    // 设置全局变量
    g_asm_fd = fd;
    g_asm_base = base;
    g_asm_timestamp = (uint64_t *) base;
    g_asm_data_size = (uint64_t *) ((unsigned char *) base + 8);
    g_asm_data_ptr = (unsigned char *) base + 16;
    LOG_INFO(SM_LOG_TAG, "共享内存创建成功");
    return 0;
}

int asm_connect(size_t size) {
    size_t total_size = size + 16;
    LOG_INFO(SM_LOG_TAG, "连接共享内存文件，大小: %zu 字节", total_size);
    int fd = open(SM_FILE_PATH, O_RDWR);
    if (fd < 0) {
        LOG_ERROR(SM_LOG_TAG, "打开共享内存文件失败");
        return -1;
    }
    // 映射内存
    void *base = mmap(NULL, total_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (base == MAP_FAILED) {
        close(fd);
        return -1;
    }
    // 设置全局变量
    g_asm_fd = fd;
    g_asm_base = base;
    g_asm_timestamp = (uint64_t *) base;
    g_asm_data_size = (uint64_t *) ((unsigned char *) base + 8);
    g_asm_data_ptr = (unsigned char *) base + 16;
    LOG_INFO(SM_LOG_TAG, "共享内存连接成功");
    return 0;
}

int asm_write(const void *data, size_t size) {
    if (!data || !g_asm_base || !g_asm_data_ptr || !g_asm_data_size || !g_asm_timestamp) {
        return -1;
    }
    // 检查数据大小合理性
    if (size > 50 * 1024 * 1024) { // 50MB限制
        LOG_ERROR(SM_LOG_TAG, "写入数据大小过大: %zu", size);
        return -1;
    }
    memcpy(g_asm_data_ptr, data, size);
    __sync_synchronize();
    *g_asm_data_size = (uint64_t) size;
    __sync_synchronize();
    *g_asm_timestamp = get_timestamp_ms();

    return 0;
}

int asm_read(uint8_t **data, size_t *data_size) {
    if (!data || !data_size) {
        LOG_ERROR(SM_LOG_TAG, "参数无效");
        return -1;
    }

    if (!g_asm_base || !g_asm_timestamp || !g_asm_data_size || !g_asm_data_ptr) {
        LOG_ERROR(SM_LOG_TAG, "共享内存未初始化");
        return -1;
    }

    // 读取当前时间戳和数据大小
    uint64_t current_ts = *g_asm_timestamp;
    uint64_t current_size = *g_asm_data_size;

    // 检查是否有新数据
    if (current_ts <= g_asm_last_read_ts) {
        *data = NULL;
        *data_size = 0;
        return 1; // 没有新数据
    }

    if (current_size == 0) {
        *data = NULL;
        *data_size = 0;
        g_asm_last_read_ts = current_ts;
        return 1; // 数据为空
    }

    // 分配内存并复制数据
    uint8_t *buffer = malloc(current_size);
    if (!buffer) {
        LOG_ERROR(SM_LOG_TAG, "分配内存失败");
        return -1;
    }

    memcpy(buffer, g_asm_data_ptr, current_size);

    *data = buffer;
    *data_size = current_size;
    g_asm_last_read_ts = current_ts;

    LOG_DEBUG(SM_LOG_TAG, "成功读取数据，大小: %zu 字节", current_size);
    return 0;
}

int asm_cleanup(void) {
    int result = 0;

    if (g_asm_base) {
        // 重置指针
        g_asm_base = NULL;
        g_asm_timestamp = NULL;
        g_asm_data_size = NULL;
        g_asm_data_ptr = NULL;
    }

    if (g_asm_fd >= 0) {
        if (close(g_asm_fd) != 0) {
            result = -1;
        }
        g_asm_fd = -1;
    }

    // 删除共享内存文件（忽略错误，因为文件可能不存在）
    (void) unlink(SM_FILE_PATH);

    // 重置读取时间戳
    g_asm_last_read_ts = 0;

    return result;
}

//#ifdef __ANDROID__
//
//JNIEXPORT jint JNICALL
//Java_com_github_kirer_server_ShareMemory_create(JNIEnv *env, jclass clazz, jint size) {
//    (void) env;   // 抑制未使用参数警告
//    (void) clazz; // 抑制未使用参数警告
//    return asm_create((size_t) size);
//}
//
//JNIEXPORT jint JNICALL
//Java_com_github_kirer_server_ShareMemory_connect(JNIEnv *env, jclass clazz, jint size) {
//    (void) env;   // 抑制未使用参数警告
//    (void) clazz; // 抑制未使用参数警告
//    return asm_connect((size_t) size);
//}
//
//JNIEXPORT jint JNICALL
//Java_com_github_kirer_server_ShareMemory_write(JNIEnv *env, jclass clazz, jbyteArray data) {
//    (void) clazz; // 抑制未使用参数警告
//
//    if (!data || !g_asm_base) {
//        return -1;
//    }
//
//    jsize len = (*env)->GetArrayLength(env, data);
//    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
//    if (!bytes) {
//        return -1;
//    }
//
//    int result = asm_write(bytes, (size_t) len);
//    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
//
//    return result;
//}
//
//JNIEXPORT jbyteArray JNICALL
//Java_com_github_kirer_server_ShareMemory_read(JNIEnv *env, jclass clazz) {
//    (void) clazz; // 抑制未使用参数警告
//    if (!g_asm_base) {
//        return NULL;
//    }
//    size_t data_size;
//    int result = asm_read(NULL, &data_size);
//    if (result <= 0 || data_size == 0) {
//        return NULL; // 没有新数据或数据为空
//    }
//    jbyteArray jarray = (*env)->NewByteArray(env, (jsize) data_size);
//    if (!jarray) {
//        return NULL;
//    }
//    // 直接从共享内存拷贝数据到Java数组（避免重复调用shmem_read）
//    (*env)->SetByteArrayRegion(env, jarray, 0, (jsize) data_size, (const jbyte *) g_asm_data_ptr);
//    return jarray;
//}
//
//JNIEXPORT jint JNICALL Java_com_github_kirer_server_ShareMemory_cleanup(JNIEnv *env, jclass clazz) {
//    (void) env;   // 抑制未使用参数警告
//    (void) clazz; // 抑制未使用参数警告
//    LOG_INFO(SM_LOG_TAG, "JNI调用: 清理共享内存资源");
//    int result = asm_cleanup();
//    if (result == 0) {
//        LOG_INFO(SM_LOG_TAG, "共享内存资源清理成功");
//    } else {
//        LOG_WARN(SM_LOG_TAG, "共享内存资源清理时发生错误");
//    }
//    return result;
//}

//#endif // __ANDROID__
