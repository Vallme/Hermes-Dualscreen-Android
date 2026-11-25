
package com.hermes.dualscreen;

import android.app.Activity;
import android.app.Presentation;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Display;
import android.view.Surface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private TextView status;
    private MediaCodec encoder;
    private VirtualDisplay virtualDisplay;
    private Presentation presentation;
    private Surface encoderSurface;
    private Thread encoderThread;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int frameCount = 0;

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
        status.setText("Status: Siap menguji encoder H.264");
        status.setTextSize(16);

        Button startButton = new Button(this);
        startButton.setText("START H.264 TEST");
        startButton.setOnClickListener(v -> startEncoder());

        layout.addView(title);
        layout.addView(status);
        layout.addView(startButton);

        setContentView(layout);
    }

    private void startEncoder() {
        if (running.get()) return;

        try {
            MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                WIDTH,
                HEIGHT
            );

            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfoColorFormat()
            );
            format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            encoder = MediaCodec.createEncoderByType(
                MediaFormat.MIMETYPE_VIDEO_AVC
            );

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
                throw new IOException("Virtual Display gagal dibuat");
            }

            Display display = virtualDisplay.getDisplay();

            presentation = new Presentation(this, display);

            TextView screen = new TextView(this);
            screen.setText(
                "HERMES DUALSCREEN\n\n" +
                "H.264 VIDEO ENCODER ACTIVE\n\n" +
                "Display ID: " + display.getDisplayId() + "\n" +
                "Resolution: 1280 x 720\n" +
                "Frame rate target: 30 FPS"
            );
            screen.setTextSize(22);
            screen.setPadding(40, 40, 40, 40);

            presentation.setContentView(screen);
            presentation.show();

            frameCount = 0;
            running.set(true);

            encoderThread = new Thread(this::drainEncoder);
            encoderThread.start();

            status.setText(
                "ENCODER AKTIF\nMenunggu frame H.264..."
            );

        } catch (Exception e) {
            running.set(false);
            status.setText(
                "GAGAL:\n" + e.getClass().getSimpleName() +
                "\n" + e.getMessage()
            );
            releaseEncoder();
        }
    }

    private int MediaCodecInfoColorFormat() {
        return android.media.MediaCodecInfo.CodecCapabilities
            .COLOR_FormatSurface;
    }

    private void drainEncoder() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (running.get()) {
            try {
                int index = encoder.dequeueOutputBuffer(info, 10000);

                if (index >= 0) {
                    if (info.size > 0 &&
                        (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {

                        frameCount++;

                        if (frameCount % 15 == 0) {
                            int count = frameCount;
                            runOnUiThread(() ->
                                status.setText(
                                    "H.264 ENCODER BERHASIL\n" +
                                    "Display ID: " +
                                    virtualDisplay.getDisplay().getDisplayId() +
                                    "\nFrame encoded: " + count
                                )
                            );
                        }
                    }

                    encoder.releaseOutputBuffer(index, false);
                }

            } catch (Exception e) {
                running.set(false);
                break;
            }
        }
    }

    private void releaseEncoder() {
        running.set(false);

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
            } catch (Exception ignored) {
            }

            try {
                encoder.release();
            } catch (Exception ignored) {
            }

            encoder = null;
        }

        if (encoderSurface != null) {
            encoderSurface.release();
            encoderSurface = null;
        }
    }

    @Override
    protected void onDestroy() {
        running.set(false);

        if (encoderThread != null) {
            try {
                encoderThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        releaseEncoder();
        super.onDestroy();
    }
}
