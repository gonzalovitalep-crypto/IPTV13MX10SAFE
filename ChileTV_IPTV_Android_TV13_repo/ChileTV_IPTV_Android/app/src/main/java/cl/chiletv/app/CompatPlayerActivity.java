package cl.chiletv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.HashMap;
import java.util.Map;

public class CompatPlayerActivity extends Activity {
    private static final long OVERLAY_TIMEOUT = 4500L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlay = () -> {
        if (overlay != null && errorPanel != null && errorPanel.getVisibility() != View.VISIBLE) {
            overlay.animate().alpha(0f).setDuration(180).withEndAction(() -> overlay.setVisibility(View.GONE)).start();
        }
    };

    private VideoView videoView;
    private View overlay;
    private View errorPanel;
    private TextView errorText;
    private ProgressBar loading;
    private String name;
    private String url;
    private final Map<String, String> headers = new HashMap<>();
    private boolean prepared = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compat_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveLegacy();

        videoView = findViewById(R.id.compatVideoView);
        overlay = findViewById(R.id.compatPlayerOverlay);
        errorPanel = findViewById(R.id.compatErrorPanel);
        errorText = findViewById(R.id.compatErrorText);
        loading = findViewById(R.id.compatPlayerLoading);
        TextView title = findViewById(R.id.compatPlayerTitle);
        Button close = findViewById(R.id.btnCompatClose);
        Button retry = findViewById(R.id.btnCompatRetry);
        Button external = findViewById(R.id.btnCompatExternal);

        name = getIntent().getStringExtra("name");
        url = getIntent().getStringExtra("url");
        Bundle b = getIntent().getBundleExtra("headers");
        if (b != null) {
            for (String key : b.keySet()) {
                String value = b.getString(key);
                if (value != null && !value.trim().isEmpty()) headers.put(key, value);
            }
        }

        title.setText(name == null ? "Canal" : name);
        close.setOnClickListener(v -> finish());
        retry.setOnClickListener(v -> startPlayback());
        external.setOnClickListener(v -> openExternal());
        videoView.setOnClickListener(v -> showOverlay());
        videoView.setOnTouchListener((v, event) -> { showOverlay(); return false; });

        showOverlay();
        startPlayback();
    }

    private void startPlayback() {
        prepared = false;
        handler.removeCallbacks(hideOverlay);
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);

        if (url == null || url.trim().isEmpty()) {
            showError("URL de canal vacía.");
            return;
        }

        DiagnosticStore.savePlayerLog(this, name, url, "Inicio VideoView / MediaPlayer nativo");
        try {
            videoView.stopPlayback();
            videoView.setVideoURI(Uri.parse(url), headers);
            videoView.setOnPreparedListener(mp -> {
                prepared = true;
                loading.setVisibility(View.GONE);
                try {
                    mp.setOnInfoListener((player, what, extra) -> {
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.setVisibility(View.VISIBLE);
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) loading.setVisibility(View.GONE);
                        return false;
                    });
                } catch (Throwable ignored) {}
                DiagnosticStore.savePlayerLog(this, name, url,
                        "PREPARED native | video=" + mp.getVideoWidth() + "x" + mp.getVideoHeight());
                videoView.start();
                showOverlay();
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                loading.setVisibility(View.GONE);
                DiagnosticStore.savePlayerLog(this, name, url,
                        "ERROR MediaPlayer nativo | what=" + what + " extra=" + extra);
                showError("El reproductor nativo del MX10 no pudo abrir esta señal.\nCódigo: " + what + " / " + extra
                        + "\n\nPrueba 'Abrir externo' o revisa Diagnóstico.");
                return true;
            });
            videoView.start();
        } catch (Throwable t) {
            DiagnosticStore.savePlayerLog(this, name, url,
                    "EXCEPCION VideoView | " + t.getClass().getName() + ": " + t.getMessage());
            showError("Error al iniciar video: " + t.getClass().getSimpleName());
        }
    }

    private void openExternal() {
        if (url == null || url.trim().isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(url), "video/*");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No hay un reproductor externo instalado para esta URL.", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "No se pudo abrir externamente.", Toast.LENGTH_LONG).show();
        }
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(1f);
        findViewById(R.id.btnCompatRetry).requestFocus();
    }

    private void showOverlay() {
        if (overlay == null) return;
        handler.removeCallbacks(hideOverlay);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(1f);
        handler.postDelayed(hideOverlay, OVERLAY_TIMEOUT);
    }

    private void enterImmersiveLegacy() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) showOverlay();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        showOverlay();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (prepared) {
                if (videoView.isPlaying()) videoView.pause(); else videoView.start();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY && prepared) {
            videoView.start();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE && prepared) {
            videoView.pause();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        try { videoView.stopPlayback(); } catch (Throwable ignored) {}
        super.onStop();
    }

    @Override
    protected void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveLegacy();
    }
}
