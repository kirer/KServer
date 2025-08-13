#ifndef SHARED_MEMORY_H
#define SHARED_MEMORY_H

#include <stdint.h>
#include <sys/types.h>
#include "log.h"
#include "protocol.h"

#ifdef __ANDROID__
#include <jni.h>
#endif

#define SHM_LOG_TAG "SERVER-K-SHM"
#define SHM_FILE_PATH "/data/local/tmp/server-k-shm"

// 共享内存结构体
typedef struct {
    int fd;                         // 文件描述符
    void *base;                     // 内存映射基地址
    size_t total_size;              // 总大小
    uint64_t *timestamp;            // 时间戳指针
    uint64_t *data_size;            // 数据大小指针
    void *data_ptr;                 // 数据指针
    uint64_t last_read_timestamp;   // 最后读取时间戳
} shared_memory_t;

// 全局共享内存实例
extern shared_memory_t g_shm;

/**
 * 创建共享内存
 * @param size 数据区域大小（不包括头部）
 * @return 0成功，-1失败
 */
int shm_create(size_t size);

/**
 * 连接到现有共享内存
 * @param size 数据区域大小（不包括头部）
 * @return 0成功，-1失败
 */
int shm_connect(size_t size);

/**
 * 写入数据到共享内存
 * @param data 数据指针
 * @param size 数据大小
 * @return 0成功，-1失败
 */
int shm_write(const void *data, size_t size);

/**
 * 从共享内存读取数据
 * @param data 输出数据指针（调用者负责释放）
 * @param data_size 输出数据大小
 * @return 0成功，-1失败，1无新数据
 */
int shm_read(uint8_t **data, size_t *data_size);

/**
 * 检查是否有新数据
 * @return 1有新数据，0无新数据
 */
int shm_has_new_data(void);

/**
 * 获取当前时间戳
 * @return 时间戳（毫秒）
 */
uint64_t shm_get_timestamp(void);

/**
 * 清理共享内存
 * @return 0成功，-1失败
 */
int shm_cleanup(void);

/**
 * 检查共享内存是否已初始化
 * @return 1已初始化，0未初始化
 */
int shm_is_initialized(void);

#ifdef __ANDROID__
// JNI接口（如果需要的话）
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_create(JNIEnv *env, jclass clazz, jint size);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_connect(JNIEnv *env, jclass clazz, jint size);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_write(JNIEnv *env, jclass clazz, jbyteArray data);

JNIEXPORT jbyteArray JNICALL
Java_com_github_kirer_server_ShareMemory_read(JNIEnv *env, jclass clazz);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_ShareMemory_clean(JNIEnv *env, jclass clazz);
#endif

#endif // SHARED_MEMORY_H