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
    private volatile Socket socket;

    private MediaCodec decoder;
    private Thread thread;

    public void start(String host, Surface surface, Listener listener) {

        if (running) return;

        if (surface == null || !surface.isValid()) {
            listener.onStatus("Surface tidak tersedia");
            return;
        }

        running = true;

        thread = new Thread(() -> {
            try {
                Socket connection = new Socket(host, PORT);
                socket = connection;

                connection.setTcpNoDelay(true);
                connection.setKeepAlive(true);
                connection.setReceiveBufferSize(256 * 1024);

                DataInputStream input =
                    new DataInputStream(connection.getInputStream());

                listener.onStatus("MENERIMA STREAM H.264...");

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

                        if (decoder != null) continue;

                        byte[][] config = extractSpsPps(packet);

                        if (config[0] == null || config[1] == null) {
                            throw new Exception("SPS/PPS tidak ditemukan");
                        }

                        MediaFormat format =
                            MediaFormat.createVideoFormat(
                                MediaFormat.MIMETYPE_VIDEO_AVC,
                                1280,
                                720
                            );

                        format.setByteBuffer(
                            "csd-0",
                            ByteBuffer.wrap(config[0])
                        );

                        format.setByteBuffer(
                            "csd-1",
                            ByteBuffer.wrap(config[1])
                        );

                        // Meminta mode decoding latensi rendah.
                        format.setInteger(
                            MediaFormat.KEY_LOW_LATENCY,
                            1
                        );

                        decoder =
                            MediaCodec.createDecoderByType(
                                MediaFormat.MIMETYPE_VIDEO_AVC
                            );

                        decoder.configure(format, surface, null, 0);
                        decoder.start();

                        listener.onStatus(
                            "DECODER LOW LATENCY AKTIF"
                        );

                        continue;
                    }

                    if (decoder == null) continue;

                    queueFrame(packet, timestamp);

                    // Ambil semua frame yang sudah selesai tanpa menunggu.
                    drainOutput(info);
                }

            } catch (Exception e) {

                if (running) {
                    listener.onStatus(
                        "KONEKSI ERROR: " + e.getMessage()
                    );
                }

            } finally {
                running = false;
                releaseDecoder();

                try {
                    if (socket != null) socket.close();
                } catch (Exception ignored) {}

                socket = null;
            }

        }, "Hermes-LowLatency-Decoder");

        thread.start();
    }

    private void queueFrame(byte[] packet, long timestamp)
        throws Exception {

        int index = -1;

        while (running && index < 0) {
            index = decoder.dequeueInputBuffer(1000);
        }

        if (!running || index < 0) return;

        ByteBuffer buffer = decoder.getInputBuffer(index);

        if (buffer == null) {
            throw new Exception("Input buffer kosong");
        }

        buffer.clear();

        if (packet.length > buffer.remaining()) {
            throw new Exception("Frame melebihi kapasitas decoder");
        }

        buffer.put(packet);

        decoder.queueInputBuffer(
            index,
            0,
            packet.length,
            timestamp,
            0
        );
    }

    private void drainOutput(MediaCodec.BufferInfo info) {

        if (decoder == null) return;

        try {
            int outputIndex;

            while ((outputIndex =
                decoder.dequeueOutputBuffer(info, 0)) >= 0) {

                decoder.releaseOutputBuffer(outputIndex, true);
            }

        } catch (Exception ignored) {}
    }

    private byte[][] extractSpsPps(byte[] data) {

        byte[] sps = null;
        byte[] pps = null;

        int start = findStartCode(data, 0);

        while (start >= 0) {

            int prefix = startCodeLength(data, start);
            int nalStart = start + prefix;
            int next = findStartCode(data, nalStart);
            int nalEnd = next >= 0 ? next : data.length;

            if (nalStart < nalEnd) {

                int nalType = data[nalStart] & 0x1F;

                byte[] nal = new byte[nalEnd - start];

                System.arraycopy(
                    data, start, nal, 0, nal.length
                );

                if (nalType == 7) sps = nal;
                if (nalType == 8) pps = nal;
            }

            start = next;
        }

        return new byte[][] { sps, pps };
    }

    private int findStartCode(byte[] data, int from) {

        for (int i = from; i < data.length - 3; i++) {

            if (data[i] == 0 && data[i + 1] == 0) {

                if (data[i + 2] == 1) return i;

                if (data[i + 2] == 0 && data[i + 3] == 1) {
                    return i;
                }
            }
        }

        return -1;
    }

    private int startCodeLength(byte[] data, int index) {
        return data[index + 2] == 1 ? 3 : 4;
    }

    public void stop() {

        running = false;

        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }

    private void releaseDecoder() {

        if (decoder != null) {

            try {
                decoder.stop();
            } catch (Exception ignored) {}

            try {
                decoder.release();
            } catch (Exception ignored) {}

            decoder = null;
        }
    }

    public interface Listener {
        void onStatus(String message);
    }
}
