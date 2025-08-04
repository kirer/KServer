#ifndef ASHMEM_H
#define ASHMEM_H

#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>

// Ashmem ioctl 命令定义
#define ASHMEM_NAME_LEN         256
#define ASHMEM_NAME_DEF         "dev/ashmem"

#define ASHMEM_SET_NAME         _IOW('a', 1, char[ASHMEM_NAME_LEN])
#define ASHMEM_GET_NAME         _IOR('a', 2, char[ASHMEM_NAME_LEN])
#define ASHMEM_SET_SIZE         _IOW('a', 3, size_t)
#define ASHMEM_GET_SIZE         _IO('a', 4)
#define ASHMEM_SET_PROT_MASK    _IOW('a', 5, unsigned long)
#define ASHMEM_GET_PROT_MASK    _IO('a', 6)
#define ASHMEM_PIN              _IOW('a', 7, struct ashmem_pin)
#define ASHMEM_UNPIN            _IOW('a', 8, struct ashmem_pin)
#define ASHMEM_GET_PIN_STATUS   _IO('a', 9)
#define ASHMEM_PURGE_ALL_CACHES _IO('a', 10)

struct ashmem_pin {
    unsigned int offset;
    unsigned int len;
};

// JNI 函数声明
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_memory_Ashmem_init(JNIEnv *env, jobject thiz, jint fd, jlong size);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_memory_Ashmem_writeData(JNIEnv *env, jobject thiz, jbyteArray data);

JNIEXPORT jbyteArray JNICALL
Java_com_github_kirer_server_memory_Ashmem_readData(JNIEnv *env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_github_kirer_server_memory_Ashmem_destroy(JNIEnv *env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif // ASHMEM_H
