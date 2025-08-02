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
        Ln.d("Client connected: " + clientAddress);
        
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String request = in.readLine();
            if (request != null) {
                Ln.d("Received request: " + request);
                String response = commandProcessor.processCommand(request);
                out.println(response);
                Ln.d("Sent response: " + response);
            }
            
        } catch (IOException e) {
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
    
    public boolean isRunning() {
        return running.get();
    }
    
    public int getPort() {
        return port;
    }
}
