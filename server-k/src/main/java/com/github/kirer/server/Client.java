package com.github.kirer.server;

/**
 * Native客户端JNI接口类
 * 提供C语言实现的客户端功能
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
     * 使用参数初始化客户端
     *
     * @param mode 通信模式 (0=SHARED_MEMORY, 1=UNIX_SOCKET, 2=TCP_SOCKET)
     * @param memorySize 共享内存大小（仅共享内存模式使用）
     * @param socketName Unix套接字名称（仅Unix套接字模式使用）
     * @param tcpHost TCP主机地址（仅TCP模式使用）
     * @param tcpPort TCP端口（仅TCP模式使用）
     * @return 0表示成功，-1表示失败
     */
    public static native int initializeWithParams(int mode, int memorySize,
                                                  String socketName, String tcpHost, int tcpPort);

    /**
     * 连接到服务器
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int connect();

    /**
     * 断开连接
     */
    public static native void disconnect();

    /**
     * 截图
     *
     * @return 响应对象
     */
    public static native Response takeScreenshot();

    /**
     * 获取客户端状态信息
     *
     * @return 状态信息字符串
     */
    public static native String getStatusInfo();
}
