#include <jni.h>
#include <android/log.h>

#if defined(__ANDROID__) && !defined(DISABLE_JNI)

// 全局JNI变量
static JavaVM *g_java_vm = NULL;

// JNI_OnLoad - 只定义一次
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_java_vm = vm;
    return JNI_VERSION_1_6;
}

// 获取JavaVM的公共函数
JavaVM* get_java_vm() {
    return g_java_vm;
}

// 获取JNI环境的公共函数
JNIEnv* get_jni_env() {
    if (!g_java_vm) return NULL;
    
    JNIEnv *env = NULL;
    if ((*g_java_vm)->AttachCurrentThread(g_java_vm, (void **) &env, NULL) != JNI_OK) {
        return NULL;
    }
    return env;
}

#endif // defined(__ANDROID__) && !defined(DISABLE_JNI)