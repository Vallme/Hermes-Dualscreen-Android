
package com.hermes.dualscreen;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class H264StreamServer {

    public static final int PORT = 8644;
    public static final int MAGIC = 0x4845524D;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataOutputStream output;
    private Thread serverThread;

    private volatile boolean running = false;

    private byte[] codecConfig;
    private long configTimestamp;

    public synchronized void start() {
        if (running) return;

        running = true;

        serverThread = new Thread(() -> {
            try {
                ServerSocket server = new ServerSocket(PORT);
                server.setReuseAddress(true);

                synchronized (this) {
                    serverSocket = server;
                }

                while (running) {
                    Socket socket = server.accept();
                    socket.setTcpNoDelay(true);
                    socket.setKeepAlive(true);

                    synchronized (this) {
                        closeClient();

                        clientSocket = socket;
                        output = new DataOutputStream(
                            socket.getOutputStream()
                        );

                        if (codecConfig != null) {
                            writePacket(
                                0,
                                configTimestamp,
                                codecConfig
                            );
                        }
                    }
                }

            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        });

        serverThread.start();
    }

    public synchronized boolean isConnected() {
        return clientSocket != null
            && clientSocket.isConnected()
            && !clientSocket.isClosed();
    }

    public synchronized void sendPacket(
        int type,
        long timestamp,
        byte[] data,
        int offset,
        int length
    ) {
        if (type == 0) {
            codecConfig = new byte[length];
            System.arraycopy(
                data, offset, codecConfig, 0, length
            );
            configTimestamp = timestamp;
        }

        if (!isConnected()) return;

        try {
            writePacket(type, timestamp, data, offset, length);
        } catch (IOException e) {
            closeClient();
        }
    }

    private void writePacket(
        int type,
        long timestamp,
        byte[] data
    ) throws IOException {
        writePacket(type, timestamp, data, 0, data.length);
    }

    private void writePacket(
        int type,
        long timestamp,
        byte[] data,
        int offset,
        int length
    ) throws IOException {

        output.writeInt(MAGIC);
        output.writeByte(type);
        output.writeLong(timestamp);
        output.writeInt(length);
        output.write(data, offset, length);
        output.flush();
    }

    private void closeClient() {
        try {
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {}

        clientSocket = null;
        output = null;
    }

    public synchronized void stop() {
        running = false;
        closeClient();

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}

        serverSocket = null;
    }
}
