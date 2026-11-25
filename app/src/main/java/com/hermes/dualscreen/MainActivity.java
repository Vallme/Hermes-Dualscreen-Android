package com.hermes.dualscreen;

import android.app.Activity;
import android.app.Presentation;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.view.Display;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;
    private VirtualDisplay virtualDisplay;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private Presentation presentation;

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
        status.setText("Status: Siap menguji Virtual Display");
        status.setTextSize(16);

        Button testButton = new Button(this);
        testButton.setText("UJI VIRTUAL DISPLAY");
        testButton.setOnClickListener(v -> testVirtualDisplay());

        layout.addView(title);
        layout.addView(status);
        layout.addView(testButton);

        setContentView(layout);
    }

    private void testVirtualDisplay() {
        try {
            DisplayManager manager =
                (DisplayManager) getSystemService(DISPLAY_SERVICE);

            surfaceTexture = new SurfaceTexture(0);
            surfaceTexture.setDefaultBufferSize(1280, 720);
            surface = new Surface(surfaceTexture);

            int flags =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

            virtualDisplay = manager.createVirtualDisplay(
                "HERMES_WORKSPACE",
                1280,
                720,
                getResources().getDisplayMetrics().densityDpi,
                surface,
                flags
            );

            if (virtualDisplay == null) {
                status.setText("GAGAL: Android tidak membuat Virtual Display.");
                return;
            }

            Display display = virtualDisplay.getDisplay();

            presentation = new Presentation(this, display);

            TextView screen = new TextView(this);
            screen.setText(
                "HERMES DUALSCREEN\n\n" +
                "Virtual Display berhasil dibuat.\n" +
                "Display ID: " + display.getDisplayId() + "\n" +
                "Resolusi: 1280 x 720"
            );
            screen.setTextSize(22);
            screen.setPadding(40, 40, 40, 40);

            presentation.setContentView(screen);
            presentation.show();

            status.setText(
                "BERHASIL: Virtual Display dibuat.\nDisplay ID: " +
                display.getDisplayId()
            );

        } catch (SecurityException e) {
            status.setText(
                "DITOLAK OLEH ANDROID:\n" + e.getMessage()
            );
        } catch (Exception e) {
            status.setText(
                "ERROR:\n" + e.getClass().getSimpleName() +
                "\n" + e.getMessage()
            );
        }
    }

    @Override
    protected void onDestroy() {
        if (presentation != null) presentation.dismiss();
        if (virtualDisplay != null) virtualDisplay.release();
        if (surface != null) surface.release();
        if (surfaceTexture != null) surfaceTexture.release();
        super.onDestroy();
    }
}
