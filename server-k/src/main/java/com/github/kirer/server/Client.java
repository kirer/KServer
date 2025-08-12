package com.github.kirer.server;

/**
 * Server-K 客户端JNI接口类 (v2.0.0)
 * 提供C语言实现的客户端功能
 * <p>
 * 新架构特点:
 * - 数据层: 从共享内存读取截图数据，实现高性能数据传输
 * - 控制层: 通过Socket通信进行服务控制和状态查询
 * - 分层设计: 数据获取与控制逻辑分离，提升性能和可维护性
 */
public class Client {

    public static class Response {
        private final boolean success;
        private final String message;
        private final byte[] data;
        private final int dataSize;
        private final long processingTime;

        public Response(boolean success, String message) {
            this(success, message, null, 0, 0);
        }

        public Response(boolean success, String message, byte[] data, int dataSize, long processingTime) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.dataSize = dataSize;
            this.processingTime = processingTime;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public byte[] getData() {
            return data;
        }

        public int getDataSize() {
            return dataSize;
        }

        public long getProcessingTime() {
            return processingTime;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Response response = (Response) obj;
            if (success != response.success) return false;
            if (dataSize != response.dataSize) return false;
            if (processingTime != response.processingTime) return false;
            if (!message.equals(response.message)) return false;
            if (data != null) {
                if (response.data == null) return false;
                if (data.length != response.data.length) return false;
                for (int i = 0; i < data.length; i++) {
                    if (data[i] != response.data[i]) return false;
                }
            } else return response.data == null;
            return true;
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(success);
            result = 31 * result + message.hashCode();
            result = 31 * result + (data != null ? java.util.Arrays.hashCode(data) : 0);
            result = 31 * result + dataSize;
            result = 31 * result + Long.hashCode(processingTime);
            return result;
        }
    }

    /**
     * 初始化客户端 - 新分层架构
     *
     * @param socketType Socket控制层类型 (1=UNIX_SOCKET, 2=TCP_SOCKET)
     * @param address    统一地址格式 (Unix: socket名称, TCP: host:port)
     * @param debug      是否启用调试模式
     * @return 0表示成功，-1表示失败
     */
    public static native int initialize(int socketType, String address, boolean debug);

    /**
     * 连接到服务器
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int connect();

    /**
     * 断开连接
     */
    public static native int disconnect();

    /**
     * 读取截图数据
     *
     * @return 包含截图数据的响应对象
     */
    public static native byte[] readScreenshot();

    /**
     * 获取客户端状态
     *
     * @return 包含状态信息的响应对象
     */
    public static native int getState();


    /**
     * 获取客户端统计信息
     *
     * @return 统计信息数组
     */
    public static native int clean();

}
