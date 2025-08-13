#ifndef JNI_COMMON_H
#define JNI_COMMON_H

#include <jni.h>

#if defined(__ANDROID__) && !defined(DISABLE_JNI)

// 获取JavaVM的公共函数
JavaVM* get_java_vm();

// 获取JNI环境的公共函数
JNIEnv* get_jni_env();

#endif // defined(__ANDROID__) && !defined(DISABLE_JNI)

#endif // JNI_COMMON_H