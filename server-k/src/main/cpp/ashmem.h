#ifndef LIBASHMEM_H
#define LIBASHMEM_H

#include <stdint.h>
#include <sys/types.h>
#include <jni.h>

// IOCTL commands for ashmem
#define ASHMEM_SET_NAME         0x41007701
#define ASHMEM_SET_SIZE         0x40087703

// Syscall number for memfd_create on ARM64
#define SYS_MEMFD_CREATE        279

// Constants
#define ASHMEM_DEVICE           "/dev/ashmem"
#define SHARED_MEMORY_NAME      "shared_memory"
#define AUTOGO_SHARED_MEMORY    "autogo_shared_memory"
#define SHMEM_LOG_TAG           "SHMEM"
#define SHMEM_FILE_PATH         "/data/local/tmp/server-k"

// Global variables
extern int g_shmem_fd;          // dword_55F8
extern void* g_shmem_base;      // qword_5600
extern uint64_t* g_timestamp;   // qword_5608
extern uint64_t* g_data_size;   // qword_5610
extern void* g_data_ptr;        // qword_5618
extern uint64_t g_last_read_ts; // qword_5628

// Core functions
int try_create_ashmem(size_t size);
int try_create_memfd(size_t size);
int shmem_create(size_t size);
int shmem_connect(size_t size);
int shmem_read(void* buffer, size_t* data_size);
int shmem_write(const void* data, size_t size);
int shmem_cleanup(void);
char* shell(const char* command);

// TCP Socket functions已移除，使用直接文件访问

// JNI functions
JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_create(JNIEnv* env, jclass clazz, jint size);
JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_connect(JNIEnv* env, jclass clazz, jint size);
JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_write(JNIEnv* env, jclass clazz, jbyteArray data);
JNIEXPORT jbyteArray JNICALL Java_com_github_kirer_server_Ashmem_read(JNIEnv* env, jclass clazz);
JNIEXPORT jint JNICALL Java_com_github_kirer_server_Ashmem_cleanup(JNIEnv* env, jclass clazz);

#endif // LIBASHMEM_H
