# C语言中使用OpenCV的解决方案

## 概述

虽然main.cpp已经转换为main.c，但我们仍然可以在C语言项目中使用OpenCV。这里提供了几种方案。

## 方案1: C++包装函数（推荐）

### 实现方式
在C文件中嵌入C++代码块，使用`extern "C"`包装：

```c
#ifdef HAVE_OPENCV
/* C++包装函数，供C代码调用 */
extern "C" {
    int save_image_with_opencv(const uint8_t *data, size_t size, const char *filename);
}

int save_image_with_opencv(const uint8_t *data, size_t size, const char *filename) {
    try {
        /* 使用OpenCV解码图片数据 */
        std::vector<uchar> buffer(data, data + size);
        cv::Mat img = cv::imdecode(buffer, cv::IMREAD_COLOR);
        
        if (!img.empty()) {
            /* 保存图片 */
            if (cv::imwrite(filename, img)) {
                LOG_INFO(MAIN_LOG_TAG, "截图已保存: %s (尺寸: %dx%d)", filename, img.cols, img.rows);
                return 0; /* 成功 */
            }
        }
        return -1;
    } catch (const cv::Exception& e) {
        LOG_ERROR(MAIN_LOG_TAG, "OpenCV异常: %s", e.what());
        return -1;
    }
}
#endif
```

### 优势
- ✅ 保持主要代码为C语言
- ✅ 充分利用OpenCV的C++ API功能
- ✅ 异常处理完善
- ✅ 性能优秀

### 使用方式
```c
void save_screenshot_to_file(const uint8_t *data, size_t size, uint64_t timestamp) {
    char filename[256];
    snprintf(filename, sizeof(filename), "screenshot_%llu_%d.png", timestamp, g_screenshot_count);
    
#ifdef HAVE_OPENCV
    /* 尝试使用OpenCV保存 */
    if (save_image_with_opencv(data, size, filename) == 0) {
        return; /* OpenCV保存成功 */
    }
    LOG_WARN(MAIN_LOG_TAG, "OpenCV保存失败，回退到原始数据保存");
#endif
    
    /* 回退方案：直接保存原始数据 */
    FILE *fp = fopen(filename, "wb");
    if (fp) {
        fwrite(data, 1, size, fp);
        fclose(fp);
        LOG_INFO(MAIN_LOG_TAG, "截图原始数据已保存: %s", filename);
    }
}
```

## 方案2: 分离的C++文件

### 创建opencv_wrapper.cpp
```cpp
#include "opencv_wrapper.h"
#ifdef HAVE_OPENCV
#include <opencv2/opencv.hpp>
#include <opencv2/imgcodecs.hpp>

extern "C" {
    int save_image_with_opencv_wrapper(const uint8_t *data, size_t size, const char *filename) {
        try {
            std::vector<uchar> buffer(data, data + size);
            cv::Mat img = cv::imdecode(buffer, cv::IMREAD_COLOR);
            
            if (!img.empty() && cv::imwrite(filename, img)) {
                return 0;
            }
            return -1;
        } catch (const cv::Exception& e) {
            return -1;
        }
    }
}
#endif
```

### 创建opencv_wrapper.h
```c
#ifndef OPENCV_WRAPPER_H
#define OPENCV_WRAPPER_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifdef HAVE_OPENCV
int save_image_with_opencv_wrapper(const uint8_t *data, size_t size, const char *filename);
#endif

#ifdef __cplusplus
}
#endif

#endif /* OPENCV_WRAPPER_H */
```

### 优势
- ✅ 完全分离C和C++代码
- ✅ 更清晰的项目结构
- ✅ 易于维护

## 方案3: 纯C实现（无OpenCV）

如果不想使用OpenCV，可以使用其他C语言图像库：

### 使用stb_image
```c
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image.h"
#include "stb_image_write.h"

void save_screenshot_to_file(const uint8_t *data, size_t size, uint64_t timestamp) {
    char filename[256];
    snprintf(filename, sizeof(filename), "screenshot_%llu_%d.png", timestamp, g_screenshot_count);
    
    /* 使用stb_image解码和保存 */
    int width, height, channels;
    unsigned char *img_data = stbi_load_from_memory(data, size, &width, &height, &channels, 0);
    
    if (img_data) {
        if (stbi_write_png(filename, width, height, channels, img_data, width * channels)) {
            LOG_INFO(MAIN_LOG_TAG, "截图已保存: %s (%dx%d)", filename, width, height);
        }
        stbi_image_free(img_data);
    } else {
        /* 回退：直接保存原始数据 */
        FILE *fp = fopen(filename, "wb");
        if (fp) {
            fwrite(data, 1, size, fp);
            fclose(fp);
        }
    }
}
```

## CMakeLists.txt配置

```cmake
# 设置C和C++标准
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

# 检查OpenCV
find_package(OpenCV QUIET)
if(OpenCV_FOUND)
    message(STATUS "Found OpenCV: ${OpenCV_VERSION}")
    add_definitions(-DHAVE_OPENCV)
endif()

# 客户端源文件（混合C/C++）
set(CLIENT_SOURCES
    ${COMMON_SOURCES}
    client/client.c
    client/main.c          # 主要是C代码，但包含C++块
)

# 创建客户端可执行文件
add_executable(k-client ${CLIENT_SOURCES})

# 链接OpenCV
if(OpenCV_FOUND)
    target_link_libraries(k-client ${OpenCV_LIBS})
endif()
```

## 编译和使用

### 编译客户端
```bash
# 使用自定义构建任务
./gradlew :server-k:buildClient

# 或者直接使用CMake
mkdir build && cd build
cmake -DHAVE_OPENCV=ON -DBUILD_CLIENT=ON ..
make k-client
```

### 运行客户端
```bash
# 部署到Android设备
adb push build/bin/k-client /data/local/tmp/
adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --help'
```

## 总结

**推荐使用方案1**，因为它：
- 保持了代码的简洁性
- 充分利用了OpenCV的强大功能
- 提供了优雅的回退机制
- 编译配置简单

这样，你就可以在C语言项目中继续使用OpenCV的图像处理功能了！