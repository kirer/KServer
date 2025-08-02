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
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // 发送命令
            out.println(command);
            
            // 读取响应
            String response = in.readLine();
            System.out.println("Response: " + response);
            
            socket.close();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
