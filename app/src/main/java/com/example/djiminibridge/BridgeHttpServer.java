package com.example.djiminibridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

class BridgeHttpServer {

    interface StatusProvider {
        String getStatusJson();
    }

    private final int port;
    private final StatusProvider statusProvider;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running;

    BridgeHttpServer(int port, StatusProvider statusProvider) {
        this.port = port;
        this.statusProvider = statusProvider;
    }

    boolean start() {
        if (running) {
            return true;
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            serverThread = new Thread(this::runServer, "DJI-Bridge-HTTP");
            serverThread.start();
            return true;
        } catch (IOException exception) {
            running = false;
            closeSocket();
            return false;
        }
    }

    void stop() {
        running = false;
        closeSocket();
    }

    boolean isRunning() {
        return running;
    }

    private void runServer() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                handleClient(socket);
            } catch (IOException exception) {
                if (running) {
                    running = false;
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             OutputStream output = client.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine != null && requestLine.startsWith("GET /status ")) {
                writeResponse(output, 200, "OK", "application/json", statusProvider.getStatusJson());
            } else {
                writeResponse(output, 404, "Not Found", "text/plain", "Use GET /status");
            }
        } catch (IOException ignored) {
        }
    }

    private void writeResponse(OutputStream output, int code, String message, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " " + message + "\r\n"
                + "Content-Type: " + contentType + "; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.flush();
    }

    private void closeSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
    }
}
