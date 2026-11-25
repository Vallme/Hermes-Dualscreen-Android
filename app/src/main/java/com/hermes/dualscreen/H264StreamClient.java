
package com.hermes.dualscreen;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.DataInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class H264StreamClient {

    private static final int PORT = 8644;
    private static final int MAGIC = 0x4845524D;

    private volatile boolean running = false;
    private Socket socket;
    private MediaCodec decoder;
    private Thread thread;

    public void start(String host, Surface surface,
                      Listener listener) {

        if (running) return;
        running = true;

        thread = new Thread(() -> {
            try {
                socket = new Socket(host, PORT);
                socket.setTcpNoDelay(true);

                DataInputStream input =
                    new DataInputStream(socket.getInputStream());

                MediaFormat format =
                    MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC,
                        1280,
                        720
                    );

                decoder = MediaCodec.createDecoderByType(
                    MediaFormat.MIMETYPE_VIDEO_AVC
                );

                decoder.configure(format, surface, null, 0);
                decoder.start();

                listener.onStatus("TERHUBUNG KE POCO");

                MediaCodec.BufferInfo info =
                    new MediaCodec.BufferInfo();

                while (running) {
                    int magic = input.readInt();

                    if (magic != MAGIC) {
                        throw new Exception("Format paket tidak valid");
                    }

                    int type = input.readUnsignedByte();
                    long timestamp = input.readLong();
                    int length = input.readInt();

                    if (length <= 0 || length > 5_000_000) {
                        throw new Exception("Ukuran paket tidak valid");
                    }

                    byte[] packet = new byte[length];
                    input.readFully(packet);

                    if (type == 0) {
                        queuePacket(packet, timestamp,
                            MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                    } else {
                        queuePacket(packet, timestamp, 0);
                    }

                    int outputIndex;

                    while ((outputIndex =
                        decoder.dequeueOutputBuffer(info, 0)) >= 0) {

                        decoder.releaseOutputBuffer(
                            outputIndex, true
                        );
                    }
                }

            } catch (Exception e) {
                listener.onStatus(
                    "KONEKSI ERROR: " + e.getMessage()
                );
            } finally {
                stop();
            }
        });

        thread.start();
    }

    private void queuePacket(byte[] packet,
                             long timestamp,
                             int flags) throws Exception {

        int index = decoder.dequeueInputBuffer(10000);

        if (index < 0) return;

        ByteBuffer buffer = decoder.getInputBuffer(index);

        if (buffer == null) return;

        buffer.clear();
        buffer.put(packet);

        decoder.queueInputBuffer(
            index, 0, packet.length, timestamp, flags
        );
    }

    public void stop() {
        running = false;

        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}

        try {
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
        } catch (Exception ignored) {}

        socket = null;
        decoder = null;
    }

    public interface Listener {
        void onStatus(String message);
    }
}
