#ifndef ASM_H
#define ASM_H

#include <stdint.h>
#include <sys/types.h>
#include "log.h"

#ifdef __ANDROID__

#include <jni.h>

#endif

#define SM_LOG_TAG "SERVER-K-ASM"
#define SM_FILE_PATH "/data/local/tmp/server-k-asm"

extern int g_asm_fd;
extern void *g_asm_base;
extern uint64_t *g_asm_timestamp;
extern uint64_t *g_asm_data_size;
extern void *g_asm_data_ptr;
extern uint64_t g_asm_last_read_ts;

int asm_create(size_t size);

int asm_connect(size_t size);

int asm_read(uint8_t **data, size_t *data_size);

int asm_write(const void *data, size_t size);

int asm_cleanup(void);

//#ifdef __ANDROID__
//
//JNIEXPORT jint JNICALL
//Java_com_github_kirer_server_ShareMemory_create(JNIEnv *env, jclass clazz, jint size);
//
//JNIEXPORT jint JNICALL
//Java_com_github_kirer_server_ShareMemory_connect(JNIEnv *env, jclass clazz, jint size);
//
//JNIEXPORT jint JNICALL
//Java_com_github_kirer_server_ShareMemory_write(JNIEnv *env, jclass clazz, jbyteArray data);
//
//JNIEXPORT jbyteArray JNICALL
//Java_com_github_kirer_server_ShareMemory_read(JNIEnv *env, jclass clazz);
//
//JNIEXPORT jint JNICALL Java_com_github_kirer_server_ShareMemory_cleanup(JNIEnv *env, jclass clazz);
//
//#endif

#endif // ASM_H
