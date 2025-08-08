#include "../core/client_core.h"

#if defined(__ANDROID__) && !defined(DISABLE_JNI)
#include <string.h>


// JNI初始化函数（参数配置）
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_initializeWithParams(JNIEnv *env, jclass clazz,
                                                         jint mode, jint memory_size,
                                                         jstring socket_name,
                                                         jstring tcp_host, jint tcp_port) {
    (void) clazz; // 抑制未使用参数警告

    LOG_INFO(CLIENT_LOG_TAG, "JNI: 使用参数配置初始化客户端");

    const char *socket_name_str = NULL;
    const char *tcp_host_str = NULL;

    // 获取字符串参数
    if (socket_name) {
        socket_name_str = (*env)->GetStringUTFChars(env, socket_name, NULL);
    }
    if (tcp_host) {
        tcp_host_str = (*env)->GetStringUTFChars(env, tcp_host, NULL);
    }

    int result = client_initialize_with_params((client_mode_t) mode, memory_size,
                                               socket_name_str, tcp_host_str, tcp_port);

    // 释放字符串资源
    if (socket_name_str) {
        (*env)->ReleaseStringUTFChars(env, socket_name, socket_name_str);
    }
    if (tcp_host_str) {
        (*env)->ReleaseStringUTFChars(env, tcp_host, tcp_host_str);
    }

    if (result == 0) {
        LOG_INFO(CLIENT_LOG_TAG, "JNI: 使用参数配置的客户端初始化成功");
    } else {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 使用参数配置的客户端初始化失败");
    }

    return result;
}

// JNI连接函数
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_connect(JNIEnv *env, jclass clazz) {
    (void) env;   // 抑制未使用参数警告
    (void) clazz; // 抑制未使用参数警告

    LOG_INFO(CLIENT_LOG_TAG, "JNI: 连接服务器");
    int result = client_connect();

    if (result == 0) {
        LOG_INFO(CLIENT_LOG_TAG, "JNI: 客户端连接成功");
    } else {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 客户端连接失败");
    }

    return result;
}

// JNI断开连接函数
JNIEXPORT void JNICALL
Java_com_github_kirer_server_Client_disconnect(JNIEnv *env, jclass clazz) {
    (void) env;   // 抑制未使用参数警告
    (void) clazz; // 抑制未使用参数警告

    LOG_INFO(CLIENT_LOG_TAG, "JNI: 断开客户端连接");
    client_disconnect();
    LOG_INFO(CLIENT_LOG_TAG, "JNI: 客户端连接已断开");
}

// JNI截图函数
JNIEXPORT jobject JNICALL
Java_com_github_kirer_server_Client_takeScreenshot(JNIEnv *env, jclass clazz) {
    (void) clazz; // 抑制未使用参数警告

    LOG_DEBUG(CLIENT_LOG_TAG, "JNI: 开始截图");
    client_response_t *response = client_take_screenshot();

    if (!response) {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 截图响应为空");
        return NULL;
    }

    // 查找Response类
    jclass responseClass = (*env)->FindClass(env, "com/github/kirer/server/Client$Response");
    if (!responseClass) {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 找不到Response类");
        free_client_response(response);
        return NULL;
    }

    // 查找Response构造函数
    jmethodID constructor = (*env)->GetMethodID(env, responseClass, "<init>",
                                                "(ZLjava/lang/String;[BIJ)V");
    if (!constructor) {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 找不到Response构造函数");
        free_client_response(response);
        return NULL;
    }

    // 创建消息字符串
    jstring message = (*env)->NewStringUTF(env, response->message);
    if (!message) {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 创建消息字符串失败");
        free_client_response(response);
        return NULL;
    }

    // 创建数据字节数组
    jbyteArray dataArray = NULL;
    if (response->data && response->data_size > 0) {
        dataArray = (*env)->NewByteArray(env, (jsize) response->data_size);
        if (dataArray) {
            (*env)->SetByteArrayRegion(env, dataArray, 0, (jsize) response->data_size,
                                       (const jbyte *) response->data);
        } else {
            LOG_ERROR(CLIENT_LOG_TAG, "JNI: 创建数据数组失败");
        }
    }

    // 创建Response对象
    jobject responseObj = (*env)->NewObject(env, responseClass, constructor,
                                            (jboolean) response->success,
                                            message,
                                            dataArray,
                                            (jint) response->data_size,
                                            (jlong) response->processing_time);

    if (responseObj) {
        LOG_DEBUG(CLIENT_LOG_TAG, "JNI: 截图响应创建成功");
    } else {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 创建截图响应对象失败");
    }

    // 清理资源
    free_client_response(response);

    return responseObj;
}

// JNI获取状态信息函数
JNIEXPORT jstring JNICALL
Java_com_github_kirer_server_Client_getStatusInfo(JNIEnv *env, jclass clazz) {
    (void) clazz; // 抑制未使用参数警告

    const char *status_info = client_get_status_info();
    if (!status_info) {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 获取状态信息失败");
        return NULL;
    }

    jstring result = (*env)->NewStringUTF(env, status_info);
    if (!result) {
        LOG_ERROR(CLIENT_LOG_TAG, "JNI: 创建状态信息字符串失败");
        return NULL;
    }

    return result;
}


#endif // defined(__ANDROID__) && !defined(DISABLE_JNI)
