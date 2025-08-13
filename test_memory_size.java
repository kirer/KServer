import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class test_memory_size {
    public static void main(String[] args) {
        // 模拟服务端发送共享内存大小
        int memorySize = 16777216; // 16MB
        
        // 服务端：使用BIG_ENDIAN编码
        byte[] data = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(memorySize)
            .array();
        
        System.out.println("服务端发送的数据:");
        for (int i = 0; i < data.length; i++) {
            System.out.printf("data[%d] = 0x%02X (%d)\n", i, data[i] & 0xFF, data[i] & 0xFF);
        }
        
        // 客户端：解析接收到的数据
        int receivedSize = ByteBuffer.wrap(data).int; // 默认使用BIG_ENDIAN
        System.out.println("\n客户端解析结果:");
        System.out.println("原始大小: " + memorySize);
        System.out.println("接收大小: " + receivedSize);
        System.out.println("是否匹配: " + (memorySize == receivedSize));
        
        // 测试小端序解析（可能的错误情况）
        int receivedSizeLittleEndian = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int;
        System.out.println("小端序解析: " + receivedSizeLittleEndian);
    }
}