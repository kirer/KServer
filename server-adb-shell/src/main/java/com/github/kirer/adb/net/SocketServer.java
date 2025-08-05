package com.github.kirer.adb.net;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.commands.CommandProcessor;
import com.github.kirer.adb.commands.CommandResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
    private final ExecutorService executor;
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

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); java.io.OutputStream out = clientSocket.getOutputStream()) {

            String request = in.readLine();
            if (request != null) {
                CommandResult result = commandProcessor.execute(request);
                if (result.getType() == CommandResult.Type.BYTES) {
                    handleBytesResponse(out, result);
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
            out.write(header.getBytes(StandardCharsets.UTF_8));
            // 发送二进制数据
            out.write(imageBytes);
            out.flush();
        } else {
            String errorResponse = "ERROR Bytes data is null\n";
            out.write(errorResponse.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
