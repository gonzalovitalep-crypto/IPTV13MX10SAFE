package cl.chiletv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * Player de compatibilidad para RK322x/API 25.
 * - Se ejecuta en un proceso separado para que la memoria del player no se acumule
 *   en el catálogo principal.
 * - Comprueba la URL antes de crear MediaPlayer.
 * - Para HLS master intenta elegir una variante <=720p/2.5Mbps.
 * - VLC se ofrece como motor externo recomendado cuando está instalado.
 */
public class CompatPlayerActivity extends Activity {
    private static final long OVERLAY_TIMEOUT = 4500L;
    private static final String VLC_PACKAGE = "org.videolan.vlc";
    private static final String VLC_DOWNLOAD_PAGE = "https://www.videolan.org/vlc/download-android.html";

    private FrameLayout playerRoot;
    private LinearLayout gatePanel;
    private View overlay;
    private View errorPanel;
    private TextView errorText;
    private TextView gateInfo;
    private ProgressBar loading;
    private Button externalPrimary;
    private Button errorExternal;
    private Button otherPlayer;
    private VideoView videoView;
    private boolean prepared;
    private String name;
    private String originalUrl;
    private String resolvedUrl;
    private String source;
    private Thread probeThread;
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
        gateInfo = (TextView) findViewById(R.id.compatGateInfo);
        Button close = (Button) findViewById(R.id.btnCompatClose);
        externalPrimary = (Button) findViewById(R.id.btnCompatExternalPrimary);
        Button internal = (Button) findViewById(R.id.btnCompatInternal);
        otherPlayer = (Button) findViewById(R.id.btnCompatBrowser);
        Button retry = (Button) findViewById(R.id.btnCompatRetry);
        errorExternal = (Button) findViewById(R.id.btnCompatExternal);

        name = getIntent().getStringExtra("name");
        originalUrl = getIntent().getStringExtra("url");
        resolvedUrl = originalUrl;
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
        updateGateInfo("Listo. El modo interno comprobará primero la señal y reducirá HLS master a una variante compatible.");
        updateExternalButtons();
        otherPlayer.setText("Abrir con otro reproductor");

        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        externalPrimary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openVlcOrInstall(); }
        });
        internal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startNativePlayback(); }
        });
        otherPlayer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openExternalChooser(); }
        });
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startNativePlayback(); }
        });
        errorExternal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openVlcOrInstall(); }
        });

        loading.setVisibility(View.GONE);
        overlay.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        gatePanel.setVisibility(View.VISIBLE);
        externalPrimary.requestFocus();
        DiagnosticStore.savePlayerLog(this, name, originalUrl,
                "PLAYER GATE abierto | proceso player separado | sin decoder inicializado | fuente=" + safe(source));
    }

    private void updateGateInfo(String extra) {
        gateInfo.setText("MX10 / API " + android.os.Build.VERSION.SDK_INT
                + " · Fuente: " + (source == null ? "desconocida" : source)
                + "\n" + extra);
    }

    private void updateExternalButtons() {
        boolean hasVlc = isPackageInstalled(VLC_PACKAGE);
        String text = hasVlc ? "Reproducir con VLC · recomendado" : "Instalar VLC compatible · recomendado";
        externalPrimary.setText(text);
        errorExternal.setText(hasVlc ? "Abrir con VLC" : "Instalar VLC");
    }

    private void startNativePlayback() {
        prepared = false;
        cancelProbe();
        releaseNativePlayer();
        gatePanel.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        overlay.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);

        if (originalUrl == null || originalUrl.trim().length() == 0) {
            showError("URL de canal vacía.");
            return;
        }

        DiagnosticStore.savePlayerLog(this, name, originalUrl,
                "PROBE inicio | headers=" + headers.keySet());
        probeThread = new Thread(new Runnable() {
            @Override public void run() {
                final StreamProbe.Result result = StreamProbe.resolve(originalUrl, headers);
                if (Thread.currentThread().isInterrupted() || isFinishing()) return;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (!result.ok) {
                            DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, originalUrl,
                                    "PROBE ERROR | " + result.detail);
                            showError("La señal no respondió correctamente antes de abrir el player.\n\n"
                                    + result.detail
                                    + "\n\nPrueba otra fuente o usa VLC.");
                            return;
                        }
                        resolvedUrl = result.url;
                        DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, resolvedUrl,
                                "PROBE OK | " + result.detail);
                        startNativeResolved(result);
                    }
                });
            }
        }, "stream-probe");
        probeThread.start();
    }

    private void startNativeResolved(StreamProbe.Result result) {
        prepared = false;
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        ensureVideoView();
        showOverlay();
        updateGateInfo(result.detail);

        try {
            videoView.stopPlayback();
            if (android.os.Build.VERSION.SDK_INT >= 21 && !headers.isEmpty()) {
                videoView.setVideoURI(Uri.parse(resolvedUrl), headers);
            } else {
                videoView.setVideoURI(Uri.parse(resolvedUrl));
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
                    DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, resolvedUrl,
                            "PREPARED native | video=" + mp.getVideoWidth() + "x" + mp.getVideoHeight());
                    videoView.start();
                    showOverlay();
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    loading.setVisibility(View.GONE);
                    DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, resolvedUrl,
                            "ERROR MediaPlayer nativo | what=" + what + " extra=" + extra);
                    if (what == MediaPlayer.MEDIA_ERROR_UNKNOWN && extra == Integer.MIN_VALUE) {
                        showError("El firmware Rockchip devolvió un error de sistema de bajo nivel.\n"
                                + "Código: 1 / -2147483648\n\n"
                                + "La URL respondió, pero MediaPlayer del MX10 no pudo decodificarla. "
                                + "Usa VLC o prueba otra fuente/calidad.");
                    } else {
                        showError("El reproductor nativo del MX10 no pudo abrir esta señal.\nCódigo: "
                                + what + " / " + extra
                                + "\n\nPrueba VLC o cambia de fuente.");
                    }
                    return true;
                }
            });
            videoView.start();
        } catch (Throwable t) {
            DiagnosticStore.savePlayerLog(this, name, resolvedUrl,
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

    private void openVlcOrInstall() {
        if (isPackageInstalled(VLC_PACKAGE)) {
            openWithPackage(VLC_PACKAGE);
        } else {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(VLC_DOWNLOAD_PAGE));
                startActivity(i);
            } catch (Throwable t) {
                Toast.makeText(this, "No se pudo abrir la página oficial de VLC.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openWithPackage(String packageName) {
        String u = resolvedUrl == null ? originalUrl : resolvedUrl;
        if (u == null || u.trim().length() == 0) return;
        DiagnosticStore.savePlayerLog(this, name, u, "Intent externo paquete=" + packageName);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(u), guessMime(u));
            i.setPackage(packageName);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Throwable t) {
            openExternalChooser();
        }
    }

    private void openExternalChooser() {
        String u = resolvedUrl == null ? originalUrl : resolvedUrl;
        if (u == null || u.trim().length() == 0) return;
        DiagnosticStore.savePlayerLog(this, name, u, "Intent ACTION_VIEW chooser solicitado");
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(u), guessMime(u));
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(Intent.createChooser(i, "Abrir canal con"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No hay reproductor externo. Instala VLC para Android ARMv7.",
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                    "No se pudo abrir externamente: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
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
        updateExternalButtons();
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

    private void cancelProbe() {
        if (probeThread != null) {
            try { probeThread.interrupt(); } catch (Throwable ignored) {}
            probeThread = null;
        }
    }

    private void releaseNativePlayer() {
        prepared = false;
        if (videoView != null) {
            try { videoView.setOnPreparedListener(null); } catch (Throwable ignored) {}
            try { videoView.setOnErrorListener(null); } catch (Throwable ignored) {}
            try { videoView.stopPlayback(); } catch (Throwable ignored) {}
            try { playerRoot.removeView(videoView); } catch (Throwable ignored) {}
            videoView = null;
        }
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
            if (gatePanel.getVisibility() != View.VISIBLE && (videoView != null || errorPanel.getVisibility() == View.VISIBLE)) {
                cancelProbe();
                releaseNativePlayer();
                loading.setVisibility(View.GONE);
                overlay.setVisibility(View.GONE);
                errorPanel.setVisibility(View.GONE);
                gatePanel.setVisibility(View.VISIBLE);
                updateExternalButtons();
                externalPrimary.requestFocus();
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
        cancelProbe();
        releaseNativePlayer();
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cancelProbe();
        releaseNativePlayer();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveLegacy();
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
