#ifndef CLIENT_OPENCV_H
#define CLIENT_OPENCV_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 使用OpenCV保存截图数据
 * 
 * @param data 截图数据（前8字节为宽高信息，后续为RGBA像素数据）
 * @param size 数据总大小
 * @param filename 输出文件名（不含扩展名）
 * @param debug_mode 是否启用调试模式
 * @return 0表示成功，-1表示失败
 */
int opencv_save_screenshot(const uint8_t* data, size_t size, const char* filename, int debug_mode);

/**
 * 初始化OpenCV（如果需要）
 * 
 * @return 0表示成功，-1表示失败
 */
int opencv_init(void);

/**
 * 清理OpenCV资源（如果需要）
 */
void opencv_cleanup(void);

#ifdef __cplusplus
}
#endif

#endif // CLIENT_OPENCV_H
