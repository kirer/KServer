#ifndef LOG_H
#define LOG_H

// 日志级别
typedef enum {
    LOG_LEVEL_DEBUG = 0,
    LOG_LEVEL_INFO,
    LOG_LEVEL_WARN,
    LOG_LEVEL_ERROR
} log_level_t;


// 日志函数声明
void log_print(log_level_t level, const char *tag, const char *format, ...);

// 便捷宏定义
#define LOG_DEBUG(tag, ...) log_print(LOG_LEVEL_DEBUG, tag, __VA_ARGS__)
#define LOG_INFO(tag, ...)  log_print(LOG_LEVEL_INFO, tag, __VA_ARGS__)
#define LOG_WARN(tag, ...)  log_print(LOG_LEVEL_WARN, tag, __VA_ARGS__)
#define LOG_ERROR(tag, ...) log_print(LOG_LEVEL_ERROR, tag, __VA_ARGS__)

#endif // LOG_H
