package com.hermes.dualscreen;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ReceiverActivity extends Activity {

    private EditText ipInput;
    private TextView status;
    private SurfaceView surfaceView;
    private LinearLayout controlPanel;

    private H264StreamClient client;
    private Surface surface;
    private boolean surfaceReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        enableFullscreen();

        FrameLayout root = new FrameLayout(this);

        surfaceView = new SurfaceView(this);

        FrameLayout.LayoutParams videoParams =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );

        root.addView(surfaceView, videoParams);

        controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setPadding(20, 16, 20, 16);
        controlPanel.setBackgroundColor(0xCC101820);

        TextView title = new TextView(this);
        title.setText("HERMES DUALSCREEN");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);

        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setText("10.32.202.234");
        ipInput.setHint("IP POCO");
        ipInput.setTextColor(0xFFFFFFFF);
        ipInput.setHintTextColor(0xFFBBBBBB);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button connect = new Button(this);
        connect.setText("CONNECT");

        Button disconnect = new Button(this);
        disconnect.setText("DISCONNECT");

        Button hide = new Button(this);
        hide.setText("HIDE CONTROLS");

        status = new TextView(this);
        status.setText("Menunggu koneksi...");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(14);

        buttons.addView(connect,
            new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        buttons.addView(disconnect,
            new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        buttons.addView(hide,
            new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        controlPanel.addView(title);
        controlPanel.addView(ipInput);
        controlPanel.addView(buttons);
        controlPanel.addView(status);

        FrameLayout.LayoutParams panelParams =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            );

        root.addView(controlPanel, panelParams);

        setContentView(root);

        surfaceView.getHolder().addCallback(
            new SurfaceHolder.Callback() {

                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    surface = holder.getSurface();
                    surfaceReady = true;
                }

                @Override
                public void surfaceChanged(
                    SurfaceHolder holder,
                    int format,
                    int width,
                    int height
                ) {
                    surface = holder.getSurface();
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    surfaceReady = false;
                    surface = null;
                    stopClient();
                }
            }
        );

        connect.setOnClickListener(v -> startClient());
        disconnect.setOnClickListener(v -> stopClient());

        hide.setOnClickListener(v ->
            controlPanel.setVisibility(View.GONE)
        );

        surfaceView.setOnClickListener(v -> {
            controlPanel.setVisibility(
                controlPanel.getVisibility() == View.VISIBLE
                    ? View.GONE
                    : View.VISIBLE
            );
        });
    }

    private void enableFullscreen() {
        Window window = getWindow();

        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void startClient() {
        if (!surfaceReady || surface == null) {
            status.setText("Layar belum siap.");
            return;
        }

        stopClient();

        String host = ipInput.getText().toString().trim();

        if (host.isEmpty()) {
            status.setText("Masukkan IP POCO.");
            return;
        }

        client = new H264StreamClient();

        status.setText("Menghubungkan ke " + host + "...");

        client.start(host, surface, message ->
            runOnUiThread(() -> status.setText(message))
        );
    }

    private void stopClient() {
        if (client != null) {
            client.stop();
            client = null;
        }

        if (status != null) {
            status.setText("Koneksi dihentikan.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableFullscreen();
    }

    @Override
    protected void onDestroy() {
        stopClient();
        super.onDestroy();
    }
}
