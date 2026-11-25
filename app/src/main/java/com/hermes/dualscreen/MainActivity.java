
package com.hermes.dualscreen;

import android.app.Activity;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final int REQUEST_CAPTURE = 8646;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private TextView status;
    private MediaCodec encoder;
    private VirtualDisplay virtualDisplay;
    private Surface encoderSurface;
    private MediaProjection mediaProjection;
    private MediaProjection.Callback projectionCallback;
    private Thread encoderThread;
    private H264StreamServer streamServer;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile int frameCount = 0;
    private volatile String codecName = "Belum aktif";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("HERMES DUALSCREEN");
        title.setTextSize(24);

        status = new TextView(this);
        status.setText("Siap memulai screen capture");

        Button start = new Button(this);
        start.setText("START STREAMING");
        start.setOnClickListener(v -> requestScreenCapture());

        Button stop = new Button(this);
        stop.setText("STOP STREAMING");
        stop.setOnClickListener(v -> stopEncoder());

        Button receiver = new Button(this);
        receiver.setText("OPEN RECEIVER");
        receiver.setOnClickListener(v ->
            startActivity(new Intent(this, ReceiverActivity.class))
        );

        layout.addView(title);
        layout.addView(status);
        layout.addView(start);
        layout.addView(stop);
        layout.addView(receiver);

        setContentView(layout);
    }

    private void requestScreenCapture() {
        if (running.get()) return;

        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(
                MEDIA_PROJECTION_SERVICE
            );

        status.setText("Meminta izin Screen Capture...");
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        );
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_CAPTURE) return;

        if (resultCode != RESULT_OK || data == null) {
            status.setText("Izin Screen Capture ditolak.");
            return;
        }

        status.setText("Izin diterima. Menyiapkan streaming...");

        Intent serviceIntent =
            new Intent(this, ProjectionForegroundService.class);

        try {
            startForegroundService(serviceIntent);

            handler.postDelayed(() -> {
                try {
                    startProjection(resultCode, data);
                } catch (Exception e) {
                    stopEncoderResources();
                    status.setText(
                        "START ERROR: " +
                        e.getClass().getSimpleName() +
                        "\n" + String.valueOf(e.getMessage())
                    );
                }
            }, 500);

        } catch (Exception e) {
            status.setText(
                "SERVICE ERROR: " +
                e.getClass().getSimpleName() +
                "\n" + String.valueOf(e.getMessage())
            );
        }
    }

    private void startProjection(int resultCode, Intent data)
        throws Exception {

        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(
                MEDIA_PROJECTION_SERVICE
            );

        mediaProjection =
            manager.getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            throw new RuntimeException("MediaProjection tidak tersedia");
        }

        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                mediaProjection = null;
                running.set(false);

                runOnUiThread(() -> {
                    stopEncoderResources();
                    status.setText("Screen Capture dihentikan Android.");
                });
            }
        };

        mediaProjection.registerCallback(projectionCallback, handler);

        streamServer = new H264StreamServer();
        streamServer.start();

        MediaFormat format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            WIDTH,
            HEIGHT
        );

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        );
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        encoder = MediaCodec.createEncoderByType(
            MediaFormat.MIMETYPE_VIDEO_AVC
        );

        codecName = encoder.getName();

        encoder.configure(
            format,
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE
        );

        encoderSurface = encoder.createInputSurface();
        encoder.start();

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "HERMES_SCREEN_CAPTURE",
            WIDTH,
            HEIGHT,
            getResources().getDisplayMetrics().densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            encoderSurface,
            null,
            handler
        );

        if (virtualDisplay == null) {
            throw new RuntimeException("Virtual Display gagal dibuat");
        }

        frameCount = 0;
        running.set(true);

        encoderThread = new Thread(
            this::drainEncoder,
            "Hermes-Encoder"
        );

        encoderThread.start();

        status.setText(
            "SCREEN STREAMING AKTIF\n" +
            "Port: 8644\n" +
            "Codec: " + codecName +
            "\nResolusi: 1280x720\n" +
            "Menunggu koneksi HONOR..."
        );
    }

    private void drainEncoder() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        try {
            while (running.get()) {
                MediaCodec currentEncoder = encoder;
                if (currentEncoder == null) break;

                int index = currentEncoder.dequeueOutputBuffer(
                    info,
                    10000
                );

                if (index >= 0) {
                    ByteBuffer buffer =
                        currentEncoder.getOutputBuffer(index);

                    if (buffer != null && info.size > 0) {
                        ByteBuffer data = buffer.duplicate();
                        data.position(info.offset);
                        data.limit(info.offset + info.size);

                        byte[] packet = new byte[info.size];
                        data.get(packet);

                        int type =
                            (info.flags &
                            MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            ? 0 : 1;

                        H264StreamServer server = streamServer;

                        if (server != null) {
                            server.sendPacket(
                                type,
                                info.presentationTimeUs,
                                packet,
                                0,
                                packet.length
                            );
                        }

                        if (type == 1) {
                            frameCount++;

                            if (frameCount % 30 == 0) {
                                int count = frameCount;

                                runOnUiThread(() -> {
                                    if (!running.get()) return;

                                    H264StreamServer currentServer =
                                        streamServer;

                                    boolean connected =
                                        currentServer != null &&
                                        currentServer.isConnected();

                                    status.setText(
                                        "SCREEN STREAMING AKTIF\n" +
                                        "Codec: " + codecName +
                                        "\nFrame: " + count +
                                        "\nHONOR: " +
                                        (connected
                                            ? "TERHUBUNG"
                                            : "MENUNGGU KONEKSI")
                                    );
                                });
                            }
                        }
                    }

                    currentEncoder.releaseOutputBuffer(index, false);
                }
            }
        } catch (Exception e) {
            String error =
                "STREAM ERROR: " +
                e.getClass().getSimpleName() +
                "\n" + String.valueOf(e.getMessage());

            runOnUiThread(() -> {
                if (running.get()) {
                    stopEncoder();
                    status.setText(error);
                }
            });
        }
    }

    private void stopEncoderResources() {
        running.set(false);

        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception ignored) {}
            virtualDisplay = null;
        }

        if (mediaProjection != null) {
            try {
                if (projectionCallback != null) {
                    mediaProjection.unregisterCallback(projectionCallback);
                }
                mediaProjection.stop();
            } catch (Exception ignored) {}
            mediaProjection = null;
        }

        projectionCallback = null;

        if (encoder != null) {
            try {
                encoder.stop();
            } catch (Exception ignored) {}

            try {
                encoder.release();
            } catch (Exception ignored) {}

            encoder = null;
        }

        if (encoderSurface != null) {
            try {
                encoderSurface.release();
            } catch (Exception ignored) {}
            encoderSurface = null;
        }

        if (streamServer != null) {
            try {
                streamServer.stop();
            } catch (Exception ignored) {}
            streamServer = null;
        }

        stopService(new Intent(this, ProjectionForegroundService.class));
    }

    private void stopEncoder() {
        running.set(false);

        Thread thread = encoderThread;
        encoderThread = null;

        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        stopEncoderResources();
        status.setText("Streaming dihentikan.");
    }

    @Override
    protected void onDestroy() {
        stopEncoder();
        super.onDestroy();
    }
}
