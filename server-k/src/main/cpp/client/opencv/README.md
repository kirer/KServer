# OpenCV Android SDK (精简版)

这是从完整的 OpenCV Android SDK 中提取的必需文件，仅包含构建所需的组件。

## 目录结构

```
opencv/
├── include/           # OpenCV 头文件
│   └── opencv2/       # OpenCV C++ API
├── libs/              # OpenCV 静态库 (arm64-v8a)
│   ├── libopencv_core.a
│   ├── libopencv_imgproc.a
│   ├── libopencv_imgcodecs.a
│   └── ...
├── 3rdparty/          # 第三方依赖库 (arm64-v8a)
│   ├── libtegra_hal.a
│   ├── libtbb.a
│   ├── libittnotify.a
│   ├── liblibjpeg-turbo.a
│   ├── liblibpng.a
│   └── ...
├── LICENSE            # OpenCV 许可证
└── README.md          # 本文件
```

## 使用的库

### OpenCV 核心库
- `libopencv_core.a` - 核心功能
- `libopencv_imgproc.a` - 图像处理
- `libopencv_imgcodecs.a` - 图像编解码

### 第三方依赖库
- `libtegra_hal.a` - ARM 优化库
- `libtbb.a` - 线程构建块
- `libittnotify.a` - Intel 跟踪工具
- `liblibjpeg-turbo.a` - JPEG 编解码
- `liblibpng.a` - PNG 编解码
- `liblibwebp.a` - WebP 编解码
- `liblibtiff.a` - TIFF 编解码
- `libIlmImf.a` - OpenEXR 图像格式

## 原始来源

从 OpenCV Android SDK 4.x 提取，原始目录结构：
- `sdk/native/jni/include/` → `include/`
- `sdk/native/staticlibs/arm64-v8a/` → `libs/`
- `sdk/native/3rdparty/libs/arm64-v8a/` → `3rdparty/`

## 许可证

请参阅 `LICENSE` 文件了解 OpenCV 的许可证条款。
