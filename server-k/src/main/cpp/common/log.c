#include "log.h"
#include <stdio.h>
#include <stdarg.h>

#ifdef __ANDROID__

#include <android/log.h>

#else
#endif

// 日志级别字符串
static const char *level_strings[] __attribute__((unused)) = {
        "DEBUG",
        "INFO",
        "WARN",
        "ERROR"
};

// Android日志级别映射
#ifdef __ANDROID__
static const int android_log_levels[] = {
        ANDROID_LOG_DEBUG,
        ANDROID_LOG_INFO,
        ANDROID_LOG_WARN,
        ANDROID_LOG_ERROR
};
#endif

void log_print(log_level_t level, const char *tag, const char *format, ...) {
    // 检查参数有效性
    if (!tag || !format || level > LOG_LEVEL_ERROR) {
        return;
    }
    va_list args;
    va_start(args, format);

#if __ANDROID__
    // 使用Android日志系统
    __android_log_vprint(android_log_levels[level], tag, format, args);
#else
    // 使用标准输出 - 简单实现
    printf("%s/%s: ", level_strings[level], tag);
    vprintf(format, args);
    printf("\n");
    fflush(stdout);
#endif

    va_end(args);
}
