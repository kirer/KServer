package com.github.kirer.adb.net;

import com.genymobile.scrcpy.util.Ln;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 流式传输协议
 * 支持大数据的分块传输和元数据传递
 */
public class StreamingProtocol {
    
    // 协议常量
    private static final int MAGIC_NUMBER = 0x4B534552; // "KSER"
    private static final byte VERSION = 1;
    private static final int HEADER_SIZE = 32; // 固定头部大小
    private static final int MAX_CHUNK_SIZE = 64 * 1024; // 64KB分块
    
    // 消息类型
    public enum MessageType {
        DATA(0x01),           // 数据消息
        METADATA(0x02),       // 元数据消息
        ERROR(0x03),          // 错误消息
        HEARTBEAT(0x04);      // 心跳消息
        
        private final byte value;
        
        MessageType(int value) {
            this.value = (byte) value;
        }
        
        public byte getValue() {
            return value;
        }
        
        public static MessageType fromValue(byte value) {
            for (MessageType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return null;
        }
    }
    
    // 数据格式
    public enum DataFormat {
        RAW_RGBA(0x01),
        PNG(0x02),
        JPEG(0x03),
        WEBP(0x04);
        
        private final byte value;
        
        DataFormat(int value) {
            this.value = (byte) value;
        }
        
        public byte getValue() {
            return value;
        }
        
        public static DataFormat fromValue(byte value) {
            for (DataFormat format : values()) {
                if (format.value == value) {
                    return format;
                }
            }
            return null;
        }
    }
    
    // 消息头部
    public static class MessageHeader {
        public final int magicNumber;
        public final byte version;
        public final MessageType messageType;
        public final DataFormat dataFormat;
        public final int totalSize;
        public final int chunkIndex;
        public final int totalChunks;
        public final int width;
        public final int height;
        public final long timestamp;
        
        public MessageHeader(MessageType messageType, DataFormat dataFormat, 
                           int totalSize, int chunkIndex, int totalChunks,
                           int width, int height, long timestamp) {
            this.magicNumber = MAGIC_NUMBER;
            this.version = VERSION;
            this.messageType = messageType;
            this.dataFormat = dataFormat;
            this.totalSize = totalSize;
            this.chunkIndex = chunkIndex;
            this.totalChunks = totalChunks;
            this.width = width;
            this.height = height;
            this.timestamp = timestamp;
        }
        
        public byte[] toBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
            buffer.order(ByteOrder.BIG_ENDIAN);
            
            buffer.putInt(magicNumber);           // 4 bytes
            buffer.put(version);                  // 1 byte
            buffer.put(messageType.getValue());   // 1 byte
            buffer.put(dataFormat.getValue());    // 1 byte
            buffer.put((byte) 0);                 // 1 byte reserved
            buffer.putInt(totalSize);             // 4 bytes
            buffer.putInt(chunkIndex);            // 4 bytes
            buffer.putInt(totalChunks);           // 4 bytes
            buffer.putInt(width);                 // 4 bytes
            buffer.putInt(height);                // 4 bytes
            buffer.putLong(timestamp);            // 8 bytes
            
            return buffer.array();
        }
        
        public static MessageHeader fromBytes(byte[] data) {
            if (data.length < HEADER_SIZE) {
                return null;
            }
            
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.BIG_ENDIAN);
            
            int magic = buffer.getInt();
            if (magic != MAGIC_NUMBER) {
                return null;
            }
            
            byte version = buffer.get();
            MessageType messageType = MessageType.fromValue(buffer.get());
            DataFormat dataFormat = DataFormat.fromValue(buffer.get());
            buffer.get(); // reserved
            int totalSize = buffer.getInt();
            int chunkIndex = buffer.getInt();
            int totalChunks = buffer.getInt();
            int width = buffer.getInt();
            int height = buffer.getInt();
            long timestamp = buffer.getLong();
            
            return new MessageHeader(messageType, dataFormat, totalSize, 
                                   chunkIndex, totalChunks, width, height, timestamp);
        }
    }
    
    /**
     * 发送数据消息
     */
    public static void sendDataMessage(OutputStream out, byte[] data, DataFormat format,
                                     int width, int height) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        long timestamp = System.currentTimeMillis();
        int totalSize = data.length;
        int totalChunks = (totalSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE;
        
        Ln.d("Sending data message: format=" + format + ", size=" + totalSize + 
             ", chunks=" + totalChunks + ", resolution=" + width + "x" + height);
        
        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            int chunkStart = chunkIndex * MAX_CHUNK_SIZE;
            int chunkEnd = Math.min(chunkStart + MAX_CHUNK_SIZE, totalSize);
            int chunkSize = chunkEnd - chunkStart;
            
            // 创建消息头
            MessageHeader header = new MessageHeader(
                MessageType.DATA, format, totalSize, chunkIndex, totalChunks,
                width, height, timestamp
            );
            
            // 发送头部
            out.write(header.toBytes());
            
            // 发送数据块大小
            ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
            sizeBuffer.order(ByteOrder.BIG_ENDIAN);
            sizeBuffer.putInt(chunkSize);
            out.write(sizeBuffer.array());
            
            // 发送数据块
            out.write(data, chunkStart, chunkSize);
            out.flush();
            
            Ln.d("Sent chunk " + (chunkIndex + 1) + "/" + totalChunks + 
                 ", size=" + chunkSize + " bytes");
        }
    }
    
    /**
     * 发送元数据消息
     */
    public static void sendMetadataMessage(OutputStream out, String metadata) throws IOException {
        if (metadata == null) {
            metadata = "";
        }
        
        byte[] metadataBytes = metadata.getBytes("UTF-8");
        long timestamp = System.currentTimeMillis();
        
        MessageHeader header = new MessageHeader(
            MessageType.METADATA, DataFormat.RAW_RGBA, metadataBytes.length,
            0, 1, 0, 0, timestamp
        );
        
        // 发送头部
        out.write(header.toBytes());
        
        // 发送元数据大小
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
        sizeBuffer.order(ByteOrder.BIG_ENDIAN);
        sizeBuffer.putInt(metadataBytes.length);
        out.write(sizeBuffer.array());
        
        // 发送元数据
        out.write(metadataBytes);
        out.flush();
        
        Ln.d("Sent metadata: " + metadata);
    }
    
    /**
     * 发送错误消息
     */
    public static void sendErrorMessage(OutputStream out, String errorMessage) throws IOException {
        if (errorMessage == null) {
            errorMessage = "Unknown error";
        }
        
        byte[] errorBytes = errorMessage.getBytes("UTF-8");
        long timestamp = System.currentTimeMillis();
        
        MessageHeader header = new MessageHeader(
            MessageType.ERROR, DataFormat.RAW_RGBA, errorBytes.length,
            0, 1, 0, 0, timestamp
        );
        
        // 发送头部
        out.write(header.toBytes());
        
        // 发送错误消息大小
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
        sizeBuffer.order(ByteOrder.BIG_ENDIAN);
        sizeBuffer.putInt(errorBytes.length);
        out.write(sizeBuffer.array());
        
        // 发送错误消息
        out.write(errorBytes);
        out.flush();
        
        Ln.d("Sent error message: " + errorMessage);
    }
    
    /**
     * 发送心跳消息
     */
    public static void sendHeartbeat(OutputStream out) throws IOException {
        long timestamp = System.currentTimeMillis();
        
        MessageHeader header = new MessageHeader(
            MessageType.HEARTBEAT, DataFormat.RAW_RGBA, 0,
            0, 1, 0, 0, timestamp
        );
        
        // 发送头部
        out.write(header.toBytes());
        
        // 发送数据大小（0）
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
        sizeBuffer.order(ByteOrder.BIG_ENDIAN);
        sizeBuffer.putInt(0);
        out.write(sizeBuffer.array());
        
        out.flush();
        
        Ln.d("Sent heartbeat");
    }
    
    /**
     * 将OptimizedImageProcessor的格式转换为协议格式
     */
    public static DataFormat convertFormat(com.github.kirer.adb.image.OptimizedImageProcessor.OutputFormat format) {
        switch (format) {
            case RAW_RGBA:
                return DataFormat.RAW_RGBA;
            case PNG:
                return DataFormat.PNG;
            case JPEG:
                return DataFormat.JPEG;
            case WEBP:
                return DataFormat.WEBP;
            default:
                return DataFormat.PNG;
        }
    }
}
