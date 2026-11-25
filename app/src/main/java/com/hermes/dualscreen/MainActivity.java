
package com.hermes.dualscreen;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private TextView status;
    private MediaCodec encoder;
    private VirtualDisplay virtualDisplay;
    private Presentation presentation;
    private Surface encoderSurface;
    private Thread encoderThread;
    private H264StreamServer streamServer;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile int frameCount = 0;
    private volatile String codecName = "Belum aktif";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

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
        status.setText("Siap memulai server H.264");

        Button start = new Button(this);
        start.setText("START STREAMING");
        start.setOnClickListener(v -> startEncoder());

        Button stop = new Button(this);
        stop.setText("STOP STREAMING");
        stop.setOnClickListener(v -> stopEncoder());

        layout.addView(title);
        layout.addView(status);
        layout.addView(start);
        layout.addView(stop);

        setContentView(layout);
    }

    private void startEncoder() {
        if (running.get()) return;

        try {
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
                format, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            );

            encoderSurface = encoder.createInputSurface();
            encoder.start();

            DisplayManager manager =
                (DisplayManager) getSystemService(DISPLAY_SERVICE);

            virtualDisplay = manager.createVirtualDisplay(
                "HERMES_WORKSPACE",
                WIDTH,
                HEIGHT,
                getResources().getDisplayMetrics().densityDpi,
                encoderSurface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            );

            if (virtualDisplay == null) {
                throw new RuntimeException("Virtual Display gagal dibuat");
            }

            Display display = virtualDisplay.getDisplay();

            presentation = new Presentation(this, display);
            presentation.setContentView(new AnimatedTestView(this));
            presentation.show();

            frameCount = 0;
            running.set(true);

            encoderThread = new Thread(this::drainEncoder);
            encoderThread.start();

            status.setText(
                "SERVER AKTIF\n" +
                "Port: 8644\n" +
                "Codec: " + codecName +
                "\nDisplay ID: " + display.getDisplayId() +
                "\nMenunggu koneksi HONOR..."
            );

        } catch (Exception e) {
            status.setText(
                "ERROR: " + e.getClass().getSimpleName() +
                "\n" + e.getMessage()
            );
            stopEncoder();
        }
    }

    private class AnimatedTestView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long startTime = System.currentTimeMillis();

        AnimatedTestView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.rgb(15, 25, 40));

            paint.setColor(Color.CYAN);
            paint.setTextSize(48);
            canvas.drawText("HERMES DUALSCREEN", 50, 100, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(32);
            canvas.drawText("H264 WIFI STREAM TEST", 50, 170, paint);

            float x = 80 +
                (System.currentTimeMillis() - startTime) % 900;

            paint.setColor(Color.GREEN);
            canvas.drawCircle(x, 350, 45, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(24);
            canvas.drawText("Rendering active", 50, 500, paint);

            postInvalidateDelayed(33);
        }
    }

    private void drainEncoder() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        try {
            while (running.get()) {
                int index = encoder.dequeueOutputBuffer(info, 10000);

                if (index >= 0) {
                    ByteBuffer buffer = encoder.getOutputBuffer(index);

                    if (buffer != null && info.size > 0) {
                        ByteBuffer data = buffer.duplicate();
                        data.position(info.offset);
                        data.limit(info.offset + info.size);

                        byte[] packet = new byte[info.size];
                        data.get(packet);

                        int type =
                            (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            ? 0 : 1;

                        streamServer.sendPacket(
                            type,
                            info.presentationTimeUs,
                            packet,
                            0,
                            packet.length
                        );

                        if (type == 1) {
                            frameCount++;

                            if (frameCount % 30 == 0) {
                                int count = frameCount;
                                boolean connected = streamServer.isConnected();

                                runOnUiThread(() -> status.setText(
                                    "H.264 STREAMING AKTIF\n" +
                                    "Codec: " + codecName +
                                    "\nFrame: " + count +
                                    "\nHONOR: " +
                                    (connected ? "TERHUBUNG" : "MENUNGGU KONEKSI")
                                ));
                            }
                        }
                    }

                    encoder.releaseOutputBuffer(index, false);
                }
            }

        } catch (Exception e) {
            runOnUiThread(() -> status.setText(
                "STREAM ERROR:\n" + e.getMessage()
            ));
        }
    }

    private void stopEncoder() {
        running.set(false);

        if (encoderThread != null &&
            encoderThread != Thread.currentThread()) {
            try {
                encoderThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            encoderThread = null;
        }

        if (presentation != null) {
            presentation.dismiss();
            presentation = null;
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (encoder != null) {
            try {
                encoder.stop();
            } catch (Exception ignored) {}

            encoder.release();
            encoder = null;
        }

        if (encoderSurface != null) {
            encoderSurface.release();
            encoderSurface = null;
        }

        if (streamServer != null) {
            streamServer.stop();
            streamServer = null;
        }

        status.setText("Streaming dihentikan.");
    }

    @Override
    protected void onDestroy() {
        stopEncoder();
        super.onDestroy();
    }
}
