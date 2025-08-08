#include "../core/server_core.h"
#include <string.h>

#ifdef __ANDROID__

// JNI初始化函数（参数配置）
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_initializeWithParams(JNIEnv *env, jclass clazz,
                                                         jint mode, jint memory_size,
                                                         jstring lib_path,
                                                         jstring socket_name, jstring tcp_host,
                                                         jint tcp_port) {
    (void) clazz; // 抑制未使用参数警告

    LOG_INFO(SERVER_LOG_TAG, "JNI: 使用参数配置初始化服务器");

    const char *lib_path_str = NULL;
    const char *socket_name_str = NULL;
    const char *tcp_host_str = NULL;

    // 获取字符串参数
    if (lib_path) {
        lib_path_str = (*env)->GetStringUTFChars(env, lib_path, NULL);
    }
    if (socket_name) {
        socket_name_str = (*env)->GetStringUTFChars(env, socket_name, NULL);
    }
    if (tcp_host) {
        tcp_host_str = (*env)->GetStringUTFChars(env, tcp_host, NULL);
    }

    int result = server_initialize_with_params((server_mode_t) mode, memory_size, lib_path_str,
                                               socket_name_str, tcp_host_str, tcp_port);

    // 释放字符串资源
    if (lib_path_str) {
        (*env)->ReleaseStringUTFChars(env, lib_path, lib_path_str);
    }
    if (socket_name_str) {
        (*env)->ReleaseStringUTFChars(env, socket_name, socket_name_str);
    }
    if (tcp_host_str) {
        (*env)->ReleaseStringUTFChars(env, tcp_host, tcp_host_str);
    }

    if (result == 0) {
        LOG_INFO(SERVER_LOG_TAG, "JNI: 使用参数配置的服务器初始化成功");
    } else {
        LOG_ERROR(SERVER_LOG_TAG, "JNI: 使用参数配置的服务器初始化失败");
    }

    return result;
}
// JNI启动函数
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_start(JNIEnv *env, jclass clazz) {
    (void) env;   // 抑制未使用参数警告
    (void) clazz; // 抑制未使用参数警告

    LOG_INFO(SERVER_LOG_TAG, "JNI: 启动服务器");
    int result = server_start();

    if (result == 0) {
        LOG_INFO(SERVER_LOG_TAG, "JNI: 服务器启动成功");
    } else {
        LOG_ERROR(SERVER_LOG_TAG, "JNI: 服务器启动失败");
    }

    return result;
}

// JNI停止函数
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_stop(JNIEnv *env, jclass clazz) {
    (void) env;   // 抑制未使用参数警告
    (void) clazz; // 抑制未使用参数警告

    LOG_INFO(SERVER_LOG_TAG, "JNI: 停止服务器");
    int result = server_stop();

    if (result == 0) {
        LOG_INFO(SERVER_LOG_TAG, "JNI: 服务器停止成功");
    } else {
        LOG_ERROR(SERVER_LOG_TAG, "JNI: 服务器停止失败");
    }

    return result;
}

// JNI写入数据函数
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_writeData(JNIEnv *env, jclass clazz, jbyteArray data) {
    (void) clazz; // 抑制未使用参数警告

    if (!data) {
        LOG_ERROR(SERVER_LOG_TAG, "JNI: 数据数组为空");
        return -1;
    }

    jsize len = (*env)->GetArrayLength(env, data);
    if (len <= 0) {
        LOG_WARN(SERVER_LOG_TAG, "JNI: 数据数组为空");
        return -1;
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) {
        LOG_ERROR(SERVER_LOG_TAG, "JNI: 获取字节数组元素失败");
        return -1;
    }

    LOG_DEBUG(SERVER_LOG_TAG, "JNI: 写入数据，大小: %d 字节", len);
    int result = server_write_data((const uint8_t *) bytes, (size_t) len);

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);

    if (result == 0) {
        LOG_DEBUG(SERVER_LOG_TAG, "JNI: 数据写入成功");
    } else {
        LOG_ERROR(SERVER_LOG_TAG, "JNI: 数据写入失败");
    }

    return result;
}

#endif // __ANDROID__
