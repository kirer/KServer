package com.github.kirer.adb.net;

/**
 * 命令执行结果包装类
 * 支持返回不同类型的数据（字符串、Bitmap等）
 */
public class CommandResult {
    
    public enum Type {
        STRING,    // 字符串结果
        BYTES,     // 字节数组
        BOOLEAN,   // 布尔值
        ERROR,     // 错误信息
        STREAMING  // 流式传输
    }
    
    private final Type type;
    private final Object data;
    private final String message;
    private final boolean success;
    
    private CommandResult(Type type, Object data, String message, boolean success) {
        this.type = type;
        this.data = data;
        this.message = message;
        this.success = success;
    }
    
    /**
     * 创建字符串结果
     */
    public static CommandResult success(String message) {
        return new CommandResult(Type.STRING, null, message, true);
    }
    
    /**
     * 创建字节数组结果
     */
    public static CommandResult bytes(byte[] bytes, String message) {
        return new CommandResult(Type.BYTES, bytes, message, true);
    }

    /**
     * 创建布尔结果
     */
    public static CommandResult bool(boolean value, String message) {
        return new CommandResult(Type.BOOLEAN, value, message, true);
    }
    
    /**
     * 创建错误结果
     */
    public static CommandResult error(String message) {
        return new CommandResult(Type.ERROR, null, message, false);
    }

    /**
     * 创建流式传输结果
     */
    public static CommandResult streaming(Object streamingData, String message) {
        return new CommandResult(Type.STREAMING, streamingData, message, true);
    }
    
    // Getters
    public Type getType() {
        return type;
    }
    
    public Object getData() {
        return data;
    }
    
    public String getMessage() {
        return message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 转换为字符串格式（用于Socket通信）
     */
    public String toStringResponse() {
        if (success) {
            return "OK " + message;
        } else {
            return "ERROR " + message;
        }
    }
    
    @Override
    public String toString() {
        return "CommandResult{" +
                "type=" + type +
                ", success=" + success +
                ", message='" + message + '\'' +
                ", hasData=" + (data != null) +
                '}';
    }
}
