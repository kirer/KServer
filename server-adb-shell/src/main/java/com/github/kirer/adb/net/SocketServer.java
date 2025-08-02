package com.github.kirer.adb.net;

import com.genymobile.scrcpy.util.Ln;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Socket服务器
 * 处理客户端连接和命令请求
 */
public class SocketServer {
    
    private final int port;
    private final CommandProcessor commandProcessor;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public SocketServer(int port, CommandProcessor commandProcessor) {
        this.port = port;
        this.commandProcessor = commandProcessor;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "SocketServer-Worker");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 启动服务器
     */
    public void start() throws IOException {
        if (running.get()) {
            Ln.w("Socket server already running");
            return;
        }
        
        serverSocket = new ServerSocket(port);
        running.set(true);
        
        Ln.i("Socket server started on port " + port);
        
        // 启动接受连接的线程
        Thread acceptThread = new Thread(this::acceptConnections, "SocketServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Ln.w("Error closing server socket", e);
        }
        
        if (executor != null) {
            executor.shutdown();
        }
        
        Ln.i("Socket server stopped");
    }
    
    /**
     * 接受客户端连接
     */
    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    Ln.e("Error accepting client connection", e);
                }
            }
        }
    }
    
    /**
     * 处理客户端请求
     */
    private void handleClient(Socket clientSocket) {
        String clientAddress = clientSocket.getRemoteSocketAddress().toString();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             java.io.OutputStream out = clientSocket.getOutputStream()) {

            String request = in.readLine();
            if (request != null) {
                CommandResult result = commandProcessor.execute(request);

                if (result.getType() == CommandResult.Type.BYTES) {
                    handleBytesResponse(out, result);
                } else if (result.getType() == CommandResult.Type.STREAMING) {
                    handleStreamingResponse(out, result);
                } else {
                    String response = result.toStringResponse();
                    try (java.io.PrintWriter textOut = new java.io.PrintWriter(out, true)) {
                        textOut.println(response);
                    }
                }
            } else {
                Ln.w("Received null request from " + clientAddress);
            }

        } catch (IOException e) {
            Ln.e("IO error handling client " + clientAddress, e);
        } catch (Exception e) {
            Ln.e("Error handling client " + clientAddress, e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Ln.w("Error closing client socket", e);
            }
            Ln.d("Client disconnected: " + clientAddress);
        }
    }

    /**
     * 处理字节数组响应，发送二进制数据
     */
    private void handleBytesResponse(java.io.OutputStream out, CommandResult result) throws IOException {
        byte[] imageBytes = (byte[]) result.getData();
        if (imageBytes != null) {
            // 发送协议头：消息类型 + 数据长度 + 消息
            String header = "BYTES:" + imageBytes.length + ":" + result.getMessage() + "\n";
            out.write(header.getBytes("UTF-8"));

            // 发送二进制数据
            out.write(imageBytes);
            out.flush();
        } else {
            String errorResponse = "ERROR Bytes data is null\n";
            out.write(errorResponse.getBytes("UTF-8"));
            out.flush();
        }
    }

    /**
     * 处理流式传输响应
     */
    private void handleStreamingResponse(java.io.OutputStream out, CommandResult result) throws IOException {
        try {
            Object streamingData = result.getData();
            if (streamingData instanceof StreamingScreenshotCommand.StreamingResult) {
                StreamingScreenshotCommand.StreamingResult streamingResult =
                    (StreamingScreenshotCommand.StreamingResult) streamingData;

                // 发送成功响应头
                String header = "STREAMING:OK:" + result.getMessage() + "\n";
                out.write(header.getBytes("UTF-8"));
                out.flush();

                // 使用流式协议发送数据
                streamingResult.sendToStream(out);

            } else {
                String errorResponse = "ERROR Invalid streaming data type\n";
                out.write(errorResponse.getBytes("UTF-8"));
                out.flush();
            }
        } catch (Exception e) {
            Ln.e("Error handling streaming response", e);
            String errorResponse = "ERROR Streaming failed: " + e.getMessage() + "\n";
            out.write(errorResponse.getBytes("UTF-8"));
            out.flush();
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public int getPort() {
        return port;
    }
}
