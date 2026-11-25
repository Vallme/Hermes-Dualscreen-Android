
package com.hermes.dualscreen;

import android.app.Activity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ReceiverActivity extends Activity {

    private EditText ipInput;
    private TextView status;
    private SurfaceView surfaceView;

    private H264StreamClient client;
    private Surface surface;
    private boolean surfaceReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 32, 24, 24);

        TextView title = new TextView(this);
        title.setText("HERMES DUALSCREEN RECEIVER");
        title.setTextSize(20);

        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setText("10.32.202.234");
        ipInput.setHint("IP POCO");

        Button connect = new Button(this);
        connect.setText("CONNECT TO POCO");

        Button disconnect = new Button(this);
        disconnect.setText("DISCONNECT");

        status = new TextView(this);
        status.setText("Menunggu koneksi...");
        status.setTextSize(16);

        surfaceView = new SurfaceView(this);

        layout.addView(title);
        layout.addView(ipInput);
        layout.addView(connect);
        layout.addView(disconnect);
        layout.addView(status);
        layout.addView(surfaceView,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1
            )
        );

        setContentView(layout);

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
    protected void onDestroy() {
        stopClient();
        super.onDestroy();
    }
}
