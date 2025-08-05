#include "ashmem.h"
#include <android/log.h>
#include <string.h>
#include <errno.h>

#define LOG_TAG "AshmemJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局变量
static void* g_mapped_memory = NULL;
static size_t g_memory_size = 0;
static int g_ashmem_fd = -1;

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Ashmem_init(JNIEnv *env, jobject thiz, jint fd, jlong size) {
    LOGI("Initializing ashmem with fd=%d, size=%ld", fd, (long)size);
    
    if (fd < 0 || size <= 0) {
        LOGE("Invalid parameters: fd=%d, size=%ld", fd, (long)size);
        return -1;
    }
    
    // 如果已经初始化过，先清理
    if (g_mapped_memory != NULL) {
        munmap(g_mapped_memory, g_memory_size);
        g_mapped_memory = NULL;
    }
    
    if (g_ashmem_fd >= 0) {
        close(g_ashmem_fd);
    }
    
    g_ashmem_fd = fd;
    g_memory_size = (size_t)size;
    
    // 映射共享内存
    g_mapped_memory = mmap(NULL, g_memory_size, PROT_READ | PROT_WRITE, MAP_SHARED, g_ashmem_fd, 0);
    if (g_mapped_memory == MAP_FAILED) {
        LOGE("Failed to mmap ashmem: %s", strerror(errno));
        g_mapped_memory = NULL;
        g_ashmem_fd = -1;
        g_memory_size = 0;
        return -1;
    }
    
    LOGI("Ashmem initialized successfully, mapped at %p", g_mapped_memory);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Ashmem_writeData(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (g_mapped_memory == NULL) {
        LOGE("Ashmem not initialized");
        return -1;
    }
    
    if (data == NULL) {
        LOGE("Data array is null");
        return -1;
    }
    
    jsize data_length = (*env)->GetArrayLength(env, data);
    if (data_length <= 0) {
        LOGE("Data array is empty");
        return -1;
    }
    
    if ((size_t)data_length > g_memory_size) {
        LOGE("Data size (%d) exceeds memory size (%zu)", data_length, g_memory_size);
        return -1;
    }
    
    // 获取数据指针
    jbyte* data_ptr = (*env)->GetByteArrayElements(env, data, NULL);
    if (data_ptr == NULL) {
        LOGE("Failed to get data pointer");
        return -1;
    }
    
    // 复制数据到共享内存
    memcpy(g_mapped_memory, data_ptr, data_length);
    
    // 释放数据指针
    (*env)->ReleaseByteArrayElements(env, data, data_ptr, JNI_ABORT);
    
    LOGI("Written %d bytes to ashmem", data_length);
    return 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_github_kirer_server_Ashmem_readData(JNIEnv *env, jobject thiz) {
    if (g_mapped_memory == NULL) {
        LOGE("Ashmem not initialized");
        return NULL;
    }
    
    // 创建字节数组
    jbyteArray result = (*env)->NewByteArray(env, (jsize)g_memory_size);
    if (result == NULL) {
        LOGE("Failed to create byte array");
        return NULL;
    }
    
    // 复制数据到字节数组
    (*env)->SetByteArrayRegion(env, result, 0, (jsize)g_memory_size, (const jbyte*)g_mapped_memory);
    
    LOGI("Read %zu bytes from ashmem", g_memory_size);
    return result;
}

JNIEXPORT void JNICALL
Java_com_github_kirer_server_Ashmem_destroy(JNIEnv *env, jobject thiz) {
    LOGI("Destroying ashmem");
    
    if (g_mapped_memory != NULL) {
        munmap(g_mapped_memory, g_memory_size);
        g_mapped_memory = NULL;
    }
    
    if (g_ashmem_fd >= 0) {
        close(g_ashmem_fd);
        g_ashmem_fd = -1;
    }
    
    g_memory_size = 0;
    LOGI("Ashmem destroyed");
}
