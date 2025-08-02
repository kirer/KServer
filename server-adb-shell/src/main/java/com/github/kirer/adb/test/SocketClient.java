package com.github.kirer.adb.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 简单的Socket客户端，用于测试截图服务
 */
public class SocketClient {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: SocketClient <host> <port> [command]");
            System.out.println("Example: SocketClient localhost 8888 status");
            return;
        }
        
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String command = args.length > 2 ? args[2] : "status";
        
        try {
            Socket socket = new Socket(host, port);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            java.io.InputStream in = socket.getInputStream();

            // 发送命令
            out.println(command);

            // 读取响应
            handleResponse(in);

            socket.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void handleResponse(java.io.InputStream in) throws Exception {
        // 读取第一行来判断响应类型
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String firstLine = reader.readLine();

        if (firstLine == null) {
            System.err.println("No response received");
            return;
        }

        if (firstLine.startsWith("BYTES:")) {
            // 处理字节数组响应
            handleBytesResponse(firstLine, in);
        } else {
            // 普通文本响应
            System.out.println("Response: " + firstLine);
        }
    }

    private static void handleBytesResponse(String header, java.io.InputStream in) throws Exception {
        // 解析头部：BYTES:size:message
        String[] parts = header.split(":", 3);
        if (parts.length < 3) {
            System.err.println("Invalid bytes header: " + header);
            return;
        }

        int dataSize = Integer.parseInt(parts[1]);
        String message = parts[2];

        System.err.println("Response: OK " + message);
        System.err.println("BYTES_SIZE: " + dataSize);

        // 读取二进制数据
        byte[] imageData = new byte[dataSize];
        int totalRead = 0;
        while (totalRead < dataSize) {
            int bytesRead = in.read(imageData, totalRead, dataSize - totalRead);
            if (bytesRead == -1) {
                throw new Exception("Unexpected end of stream");
            }
            totalRead += bytesRead;
        }

        // 将二进制数据写入stdout
        System.out.write(imageData);
        System.out.flush();

        System.err.println("Bytes data written to stdout (" + totalRead + " bytes)");
    }
}
