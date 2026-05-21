package com.example.djiminibridge;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class BridgeWebSocketServer {

    interface StatusProvider {
        String getStatusJson();
    }

    interface CommandHandler {
        String handleCommand(String command);
    }

    private static final String WEB_SOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final StatusProvider statusProvider;
    private final CommandHandler commandHandler;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running;

    BridgeWebSocketServer(int port, StatusProvider statusProvider, CommandHandler commandHandler) {
        this.port = port;
        this.statusProvider = statusProvider;
        this.commandHandler = commandHandler;
    }

    boolean start() {
        if (running) {
            return true;
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            serverThread = new Thread(this::runServer, "DJI-Bridge-WebSocket");
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
                Thread clientThread = new Thread(() -> handleClient(socket), "DJI-Bridge-WebSocket-Client");
                clientThread.start();
            } catch (IOException exception) {
                if (running) {
                    running = false;
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(250);
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();

            if (!performHandshake(input, output)) {
                return;
            }

            long lastStatusAt = 0L;
            while (running && !client.isClosed()) {
                long now = System.currentTimeMillis();
                if (now - lastStatusAt >= 1000L) {
                    sendText(output, "{\"type\":\"status\",\"data\":" + statusProvider.getStatusJson() + "}");
                    lastStatusAt = now;
                }

                try {
                    String command = readTextFrame(input);
                    if (command == null) {
                        continue;
                    }
                    if ("__close__".equals(command)) {
                        break;
                    }
                    sendText(output, commandHandler.handleCommand(command));
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private boolean performHandshake(InputStream input, OutputStream output) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line;
        String key = null;

        while ((line = reader.readLine()) != null && line.length() > 0) {
            String lower = line.toLowerCase(java.util.Locale.US);
            if (lower.startsWith("sec-websocket-key:")) {
                key = line.substring(line.indexOf(':') + 1).trim();
            }
        }

        if (key == null) {
            return false;
        }

        String accept = createAcceptKey(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.UTF_8));
        output.flush();
        return true;
    }

    private String createAcceptKey(String key) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((key + WEB_SOCKET_GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException(exception);
        }
    }

    private String readTextFrame(InputStream input) throws IOException {
        int first = input.read();
        if (first == -1) {
            return "__close__";
        }

        int opcode = first & 0x0F;
        if (opcode == 8) {
            return "__close__";
        }

        int second = input.read();
        if (second == -1) {
            return "__close__";
        }

        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = (input.read() << 8) | input.read();
        } else if (length == 127) {
            throw new IOException("Frame too large");
        }

        byte[] mask = new byte[4];
        if (masked) {
            readFully(input, mask);
        }

        byte[] payload = new byte[(int) length];
        readFully(input, payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    private void sendText(OutputStream output, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81);
        if (payload.length <= 125) {
            frame.write(payload.length);
        } else if (payload.length <= 65535) {
            frame.write(126);
            frame.write((payload.length >> 8) & 0xFF);
            frame.write(payload.length & 0xFF);
        } else {
            throw new IOException("Frame too large");
        }
        frame.write(payload);
        output.write(frame.toByteArray());
        output.flush();
    }

    private void readFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read == -1) {
                throw new IOException("Socket closed");
            }
            offset += read;
        }
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
