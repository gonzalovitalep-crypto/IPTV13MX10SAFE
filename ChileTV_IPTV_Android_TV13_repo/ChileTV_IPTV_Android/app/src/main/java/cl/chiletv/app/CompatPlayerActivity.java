package cl.chiletv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.HashMap;
import java.util.Map;

/**
 * Reproductor ultra-safe para firmwares MX10/RK322x que reportan API 25.
 * Al entrar NO crea VideoView ni inicializa MediaPlayer. El usuario elige primero
 * entre un reproductor externo (recomendado), navegador o el motor nativo.
 */
public class CompatPlayerActivity extends Activity {
    private static final long OVERLAY_TIMEOUT = 4500L;

    private FrameLayout playerRoot;
    private LinearLayout gatePanel;
    private View overlay;
    private View errorPanel;
    private TextView errorText;
    private ProgressBar loading;
    private VideoView videoView;
    private boolean prepared;
    private String name;
    private String url;
    private String source;
    private final Map<String, String> headers = new HashMap<String, String>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hideOverlay = new Runnable() {
        @Override public void run() {
            if (overlay != null && errorPanel != null && errorPanel.getVisibility() != View.VISIBLE) {
                overlay.setVisibility(View.GONE);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compat_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveLegacy();

        playerRoot = (FrameLayout) findViewById(R.id.compatPlayerRoot);
        gatePanel = (LinearLayout) findViewById(R.id.compatGatePanel);
        overlay = findViewById(R.id.compatPlayerOverlay);
        errorPanel = findViewById(R.id.compatErrorPanel);
        errorText = (TextView) findViewById(R.id.compatErrorText);
        loading = (ProgressBar) findViewById(R.id.compatPlayerLoading);
        TextView title = (TextView) findViewById(R.id.compatPlayerTitle);
        TextView gateTitle = (TextView) findViewById(R.id.compatGateTitle);
        TextView gateInfo = (TextView) findViewById(R.id.compatGateInfo);
        Button close = (Button) findViewById(R.id.btnCompatClose);
        Button external = (Button) findViewById(R.id.btnCompatExternalPrimary);
        Button internal = (Button) findViewById(R.id.btnCompatInternal);
        Button browser = (Button) findViewById(R.id.btnCompatBrowser);
        Button retry = (Button) findViewById(R.id.btnCompatRetry);
        Button errorExternal = (Button) findViewById(R.id.btnCompatExternal);

        name = getIntent().getStringExtra("name");
        url = getIntent().getStringExtra("url");
        source = getIntent().getStringExtra("source");
        Bundle b = getIntent().getBundleExtra("headers");
        if (b != null) {
            for (String key : b.keySet()) {
                String value = b.getString(key);
                if (value != null && value.trim().length() > 0) headers.put(key, value);
            }
        }

        String channelName = name == null ? "Canal" : name;
        title.setText(channelName);
        gateTitle.setText(channelName);
        gateInfo.setText("MX10 / API " + android.os.Build.VERSION.SDK_INT
                + " · Fuente: " + (source == null ? "desconocida" : source)
                + "\nModo externo recomendado: evita inicializar el decodificador Rockchip dentro de esta app.");

        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        external.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openExternal(); }
        });
        internal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startNativePlayback(); }
        });
        browser.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openBrowser(); }
        });
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startNativePlayback(); }
        });
        errorExternal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openExternal(); }
        });

        loading.setVisibility(View.GONE);
        overlay.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        gatePanel.setVisibility(View.VISIBLE);
        external.requestFocus();
        DiagnosticStore.savePlayerLog(this, name, url, "PLAYER GATE abierto | sin decoder inicializado | fuente=" + safe(source));
    }

    private void startNativePlayback() {
        prepared = false;
        gatePanel.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        ensureVideoView();
        showOverlay();

        if (url == null || url.trim().length() == 0) {
            showError("URL de canal vacía.");
            return;
        }

        DiagnosticStore.savePlayerLog(this, name, url, "Inicio VideoView / MediaPlayer nativo bajo demanda");
        try {
            videoView.stopPlayback();
            if (android.os.Build.VERSION.SDK_INT >= 21 && !headers.isEmpty()) {
                videoView.setVideoURI(Uri.parse(url), headers);
            } else {
                videoView.setVideoURI(Uri.parse(url));
            }
            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(final MediaPlayer mp) {
                    prepared = true;
                    loading.setVisibility(View.GONE);
                    try {
                        mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                            @Override public boolean onInfo(MediaPlayer player, int what, int extra) {
                                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.setVisibility(View.VISIBLE);
                                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) loading.setVisibility(View.GONE);
                                return false;
                            }
                        });
                    } catch (Throwable ignored) {}
                    DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, url,
                            "PREPARED native | video=" + mp.getVideoWidth() + "x" + mp.getVideoHeight());
                    videoView.start();
                    showOverlay();
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    loading.setVisibility(View.GONE);
                    DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, url,
                            "ERROR MediaPlayer nativo | what=" + what + " extra=" + extra);
                    showError("El reproductor nativo del MX10 no pudo abrir esta señal.\nCódigo: "
                            + what + " / " + extra
                            + "\n\nUsa 'Abrir externo' con VLC/MX Player o prueba otra fuente.");
                    return true;
                }
            });
            videoView.start();
        } catch (Throwable t) {
            DiagnosticStore.savePlayerLog(this, name, url,
                    "EXCEPCION VideoView | " + t.getClass().getName() + ": " + safe(t.getMessage()));
            showError("Error al iniciar el motor nativo: " + t.getClass().getSimpleName());
        }
    }

    private void ensureVideoView() {
        if (videoView != null) return;
        videoView = new VideoView(this);
        videoView.setFocusable(true);
        videoView.setFocusableInTouchMode(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        playerRoot.addView(videoView, 0, lp);
        videoView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showOverlay(); }
        });
        videoView.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                showOverlay();
                return false;
            }
        });
    }

    private void openExternal() {
        if (url == null || url.trim().length() == 0) return;
        DiagnosticStore.savePlayerLog(this, name, url, "Intent ACTION_VIEW externo solicitado");
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(url), guessMime(url));
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(Intent.createChooser(i, "Abrir canal con"));
        } catch (ActivityNotFoundException e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } catch (Throwable t) {
                Toast.makeText(this,
                        "No hay reproductor externo. Instala VLC o MX Player en el stick.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this,
                    "No se pudo abrir externamente: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openBrowser() {
        if (url == null || url.trim().length() == 0) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!headers.isEmpty()) {
                Bundle extraHeaders = new Bundle();
                for (Map.Entry<String, String> e : headers.entrySet()) extraHeaders.putString(e.getKey(), e.getValue());
                i.putExtra(Browser.EXTRA_HEADERS, extraHeaders);
            }
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "No se pudo abrir el enlace.", Toast.LENGTH_LONG).show();
        }
    }

    private String guessMime(String value) {
        String u = value == null ? "" : value.toLowerCase();
        if (u.contains(".m3u8")) return "application/vnd.apple.mpegurl";
        if (u.contains(".mpd")) return "application/dash+xml";
        return "video/*";
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
        findViewById(R.id.btnCompatRetry).requestFocus();
    }

    private void showOverlay() {
        if (overlay == null || gatePanel.getVisibility() == View.VISIBLE) return;
        handler.removeCallbacks(hideOverlay);
        overlay.setVisibility(View.VISIBLE);
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

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) showOverlay();
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        showOverlay();
        return super.dispatchTouchEvent(ev);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (gatePanel.getVisibility() != View.VISIBLE && videoView != null) {
                try { videoView.stopPlayback(); } catch (Throwable ignored) {}
                playerRoot.removeView(videoView);
                videoView = null;
                prepared = false;
                loading.setVisibility(View.GONE);
                overlay.setVisibility(View.GONE);
                errorPanel.setVisibility(View.GONE);
                gatePanel.setVisibility(View.VISIBLE);
                findViewById(R.id.btnCompatExternalPrimary).requestFocus();
                return true;
            }
            finish();
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) && prepared && videoView != null) {
            try {
                if (videoView.isPlaying()) videoView.pause(); else videoView.start();
            } catch (Throwable ignored) {}
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) {
            try { videoView.stopPlayback(); } catch (Throwable ignored) {}
        }
        super.onStop();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveLegacy();
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
