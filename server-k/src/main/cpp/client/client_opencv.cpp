#include "client_opencv.h"

// 标准头文件
#include <cstdint>
#include <stdint.h>
#include <cstddef>
#include <stddef.h>
#include <iostream>
#include <string>
#include <cstring>
#include <vector>

// OpenCV 头文件
#include <opencv2/opencv.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>

extern "C" {

int opencv_init(void) {
    try {
        std::cout << "✅ OpenCV version: " << CV_VERSION << std::endl;
        return 0;
    } catch (const std::exception &e) {
        std::cerr << "❌ OpenCV初始化失败: " << e.what() << std::endl;
        return -1;
    }
}

void opencv_cleanup(void) {
    // OpenCV通常不需要显式清理
}

int opencv_save_screenshot(const uint8_t *data, size_t size, const char *filename, int debug_mode) {
    if (!data || size < 8 || !filename) {
        std::cerr << "错误: 参数无效" << std::endl;
        return -1;
    }

    try {
        // 解析头部信息（小端序）
        uint32_t width = static_cast<uint32_t>(data[0]) |
                         (static_cast<uint32_t>(data[1]) << 8) |
                         (static_cast<uint32_t>(data[2]) << 16) |
                         (static_cast<uint32_t>(data[3]) << 24);

        uint32_t height = static_cast<uint32_t>(data[4]) |
                          (static_cast<uint32_t>(data[5]) << 8) |
                          (static_cast<uint32_t>(data[6]) << 16) |
                          (static_cast<uint32_t>(data[7]) << 24);

        if (debug_mode) {
            std::cout << "[OpenCV] 解析图像尺寸: " << width << "x" << height << std::endl;
        }

        // 验证数据大小
        size_t pixel_data_size = size - 8;
        size_t expected_size = static_cast<size_t>(width) * height * 4;

        if (pixel_data_size != expected_size) {
            std::cerr << "警告: 像素数据大小不匹配 (实际: " << pixel_data_size
                      << ", 期望: " << expected_size << ")" << std::endl;
        }

        // 创建OpenCV Mat对象（RGBA格式）
        cv::Mat rgba_image(height, width, CV_8UC4, const_cast<uint8_t *>(data + 8));

        if (rgba_image.empty()) {
            std::cerr << "错误: 无法创建OpenCV Mat对象" << std::endl;
            return -1;
        }

        if (debug_mode) {
            std::cout << "[OpenCV] 创建Mat成功: " << rgba_image.cols << "x" << rgba_image.rows
                      << ", 通道数: " << rgba_image.channels() << std::endl;
        }

        // 转换RGBA到BGR（OpenCV默认格式）
        cv::Mat bgr_image;
        cv::cvtColor(rgba_image, bgr_image, cv::COLOR_RGBA2BGR);

        // 构建输出文件路径
        std::string base_filename(filename);

        // 如果filename不包含路径，则保存到/data/local/tmp/
        if (base_filename.find('/') == std::string::npos) {
            base_filename = "/data/local/tmp/" + base_filename;
        }

        // 保存为PNG格式
        std::string png_filename = base_filename + ".png";
        std::vector<int> png_params = {cv::IMWRITE_PNG_COMPRESSION, 6}; // 压缩级别6

        bool png_success = cv::imwrite(png_filename, bgr_image, png_params);

        if (png_success) {
            if (debug_mode) {
                std::cout << "[OpenCV] ✅ PNG格式已保存到: " << png_filename << std::endl;
            } else {
                std::cout << "✅ PNG格式已保存到: " << png_filename << std::endl;
            }
        } else {
            std::cerr << "❌ 保存PNG文件失败: " << png_filename << std::endl;
        }

        // 显示图像信息
        if (debug_mode) {
            std::cout << "[OpenCV] 图像处理完成:" << std::endl;
            std::cout << "  - 原始尺寸: " << width << "x" << height << std::endl;
            std::cout << "  - OpenCV尺寸: " << bgr_image.cols << "x" << bgr_image.rows << std::endl;
            std::cout << "  - 数据类型: " << bgr_image.type() << std::endl;
            std::cout << "  - 通道数: " << bgr_image.channels() << std::endl;
        }
        return png_success ? 0 : -1;

    } catch (const cv::Exception &e) {
        std::cerr << "OpenCV错误: " << e.what() << std::endl;
        return -1;
    } catch (const std::exception &e) {
        std::cerr << "标准错误: " << e.what() << std::endl;
        return -1;
    } catch (...) {
        std::cerr << "未知错误" << std::endl;
        return -1;
    }
}

} // extern "C"
