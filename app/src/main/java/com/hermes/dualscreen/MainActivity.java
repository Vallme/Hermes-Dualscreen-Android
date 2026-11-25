
package com.hermes.dualscreen;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.provider.Settings;
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

    private int secondaryDisplayId = -1;

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
        status.setText("Siap membuat extended display");

        Button start = new Button(this);
        start.setText("START EXTENDED DISPLAY");
        start.setOnClickListener(v -> startEncoder());

        Button settings = new Button(this);
        settings.setText("OPEN SETTINGS ON HONOR");
        settings.setOnClickListener(v -> openSettingsOnDisplay());

        Button receiver = new Button(this);
        receiver.setText("OPEN RECEIVER");
        receiver.setOnClickListener(v ->
            startActivity(new Intent(this, ReceiverActivity.class))
        );

        Button stop = new Button(this);
        stop.setText("STOP DISPLAY");
        stop.setOnClickListener(v -> stopEncoder());

        layout.addView(title);
        layout.addView(status);
        layout.addView(start);
        layout.addView(settings);
        layout.addView(receiver);
        layout.addView(stop);

        setContentView(layout);
    }

    private void startEncoder() {
        if (running.get()) return;

        status.setText("Membuat virtual display non-mirroring...");

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

            format.setInteger(MediaFormat.KEY_BIT_RATE, 3000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            // Optimasi encoder untuk streaming real-time
            format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);

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

            DisplayManager manager =
                (DisplayManager) getSystemService(DISPLAY_SERVICE);

            int flags =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC |
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION |
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;

            virtualDisplay = manager.createVirtualDisplay(
                "HERMES_WORKSPACE",
                WIDTH,
                HEIGHT,
                getResources().getDisplayMetrics().densityDpi,
                encoderSurface,
                flags
            );

            if (virtualDisplay == null) {
                throw new RuntimeException(
                    "Virtual display gagal dibuat"
                );
            }

            Display display = virtualDisplay.getDisplay();

            if (display == null) {
                throw new RuntimeException(
                    "Display tidak tersedia"
                );
            }

            secondaryDisplayId = display.getDisplayId();

            presentation = new Presentation(this, display);
            presentation.setContentView(new AnimatedTestView(this));
            presentation.show();

            frameCount = 0;
            running.set(true);

            encoderThread = new Thread(
                this::drainEncoder,
                "Hermes-Encoder"
            );

            encoderThread.start();

            boolean supported = getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS
            );

            status.setText(
                "EXTENDED DISPLAY AKTIF\n" +
                "Display ID: " + secondaryDisplayId +
                "\nMode: PUBLIC + OWN_CONTENT_ONLY" +
                "\nMirroring: NONAKTIF" +
                "\nAplikasi di display kedua: " +
                (supported ? "DIDUKUNG" : "BELUM DIDUKUNG") +
                "\nCodec: " + codecName +
                "\nPort: 8644\nMenunggu HONOR..."
            );

        } catch (Exception e) {
            String error =
                "START ERROR: " +
                e.getClass().getSimpleName() +
                "\n" + String.valueOf(e.getMessage());

            stopEncoderResources();
            running.set(false);
            status.setText(error);
        }
    }

    private void openSettingsOnDisplay() {
        if (!running.get() || secondaryDisplayId < 0) {
            status.setText(
                "Jalankan extended display terlebih dahulu."
            );
            return;
        }

        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(secondaryDisplayId);

            startActivity(intent, options.toBundle());

            status.setText(
                "Permintaan membuka Settings pada display " +
                secondaryDisplayId + " telah dikirim."
            );

        } catch (Exception e) {
            status.setText(
                "LAUNCH ERROR: " +
                e.getClass().getSimpleName() +
                "\n" + String.valueOf(e.getMessage())
            );
        }
    }

    private class AnimatedTestView extends View {

        private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

        private final long startTime =
            System.currentTimeMillis();

        AnimatedTestView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.rgb(15, 25, 40));

            paint.setColor(Color.CYAN);
            paint.setTextSize(48);
            canvas.drawText(
                "HERMES EXTENDED DISPLAY",
                50, 100, paint
            );

            paint.setColor(Color.WHITE);
            paint.setTextSize(32);
            canvas.drawText(
                "NON-MIRRORING WORKSPACE",
                50, 170, paint
            );

            float x = 80 +
                (System.currentTimeMillis() - startTime) % 900;

            paint.setColor(Color.GREEN);
            canvas.drawCircle(x, 350, 45, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(24);
            canvas.drawText(
                "Independent display rendering",
                50, 500, paint
            );

            postInvalidateDelayed(33);
        }
    }

    private void drainEncoder() {
        MediaCodec.BufferInfo info =
            new MediaCodec.BufferInfo();

        try {
            while (running.get()) {
                MediaCodec currentEncoder = encoder;
                if (currentEncoder == null) break;

                // Polling lebih singkat untuk mengurangi antrean
                int index = currentEncoder.dequeueOutputBuffer(
                    info, 1000
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
                                        "EXTENDED DISPLAY AKTIF\n" +
                                        "Display ID: " + secondaryDisplayId +
                                        "\nCodec: " + codecName +
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
                stopEncoderResources();
                running.set(false);
                status.setText(error);
            });
        }
    }

    private void stopEncoderResources() {
        running.set(false);
        secondaryDisplayId = -1;

        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (Exception ignored) {}
            presentation = null;
        }

        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception ignored) {}
            virtualDisplay = null;
        }

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
        status.setText("Extended display dihentikan.");
    }

    @Override
    protected void onDestroy() {
        stopEncoder();
        super.onDestroy();
    }
}
