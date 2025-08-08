#ifndef SHARED_MEMORY_CLIENT_H
#define SHARED_MEMORY_CLIENT_H

#include "../core/client_core.h"
#include "../../common/ashmem.h"

#define SHM_CLIENT_LOG_TAG "SERVER-K-SHM-CLIENT"

// 共享内存客户端函数声明
int shm_client_connect(client_config_t* config);
void shm_client_disconnect(void);
client_response_t* shm_client_take_screenshot(void);
int shm_client_cleanup(void);

// 获取共享内存客户端操作函数
const client_ops_t* get_shared_memory_client_ops(void);

#endif // SHARED_MEMORY_CLIENT_H
