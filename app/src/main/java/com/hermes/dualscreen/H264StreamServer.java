
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

    public synchronized void start() {
        if (running) return;

        running = true;

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverSocket.setReuseAddress(true);

                while (running) {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    socket.setKeepAlive(true);

                    synchronized (this) {
                        closeClient();
                        clientSocket = socket;
                        output = new DataOutputStream(
                            socket.getOutputStream()
                        );
                    }
                }

            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
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
        if (!isConnected()) return;

        try {
            output.writeInt(MAGIC);
            output.writeByte(type);
            output.writeLong(timestamp);
            output.writeInt(length);
            output.write(data, offset, length);
            output.flush();

        } catch (IOException e) {
            closeClient();
        }
    }

    private void closeClient() {
        try {
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {
        }

        clientSocket = null;
        output = null;
    }

    public synchronized void stop() {
        running = false;
        closeClient();

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
