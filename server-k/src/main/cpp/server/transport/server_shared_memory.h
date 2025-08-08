#ifndef SHARED_MEMORY_SERVER_H
#define SHARED_MEMORY_SERVER_H

#include "../core/server_core.h"
#include "../../common/ashmem.h"

#define SHM_SERVER_LOG_TAG "SERVER-K-SHM"

// 共享内存服务器函数声明
int shm_server_initialize(server_config_t* config);
int shm_server_start(void);
int shm_server_stop(void);
int shm_server_write_data(const uint8_t* data, size_t size);
int shm_server_cleanup(void);

// 获取共享内存服务器操作函数
const server_ops_t* get_shared_memory_server_ops(void);

#endif // SHARED_MEMORY_SERVER_H
