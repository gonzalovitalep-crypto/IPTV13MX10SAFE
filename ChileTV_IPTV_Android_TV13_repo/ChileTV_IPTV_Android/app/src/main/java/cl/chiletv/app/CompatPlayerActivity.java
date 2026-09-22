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

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.HashMap;
import java.util.Map;

public class CompatPlayerActivity extends Activity {
    private static final long OVERLAY_TIMEOUT = 4500L;

    private PlayerView exoView;
    private VideoView nativeVideoView;
    private ExoPlayer exoPlayer;
    private View overlay;
    private View errorPanel;
    private TextView errorText;
    private TextView engineText;
    private ProgressBar loading;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlay = new Runnable() {
        @Override public void run() {
            if (overlay != null && errorPanel != null && errorPanel.getVisibility() != View.VISIBLE) {
                overlay.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
                    @Override public void run() {
                        if (overlay != null) overlay.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    };

    private String name;
    private String url;
    private String source;
    private final Map<String, String> headers = new HashMap<>();
    private boolean exoReady = false;
    private boolean nativePrepared = false;
    private boolean usingNative = false;
    private boolean nativeFallbackAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compat_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveLegacy();

        exoView = findViewById(R.id.compatExoPlayerView);
        nativeVideoView = findViewById(R.id.compatVideoView);
        overlay = findViewById(R.id.compatPlayerOverlay);
        errorPanel = findViewById(R.id.compatErrorPanel);
        errorText = findViewById(R.id.compatErrorText);
        engineText = findViewById(R.id.compatEngineText);
        loading = findViewById(R.id.compatPlayerLoading);
        TextView title = findViewById(R.id.compatPlayerTitle);
        Button close = findViewById(R.id.btnCompatClose);
        Button retry = findViewById(R.id.btnCompatRetry);
        Button nativeButton = findViewById(R.id.btnCompatNative);
        Button external = findViewById(R.id.btnCompatExternal);

        name = getIntent().getStringExtra("name");
        url = getIntent().getStringExtra("url");
        source = getIntent().getStringExtra("source");
        Bundle b = getIntent().getBundleExtra("headers");
        if (b != null) {
            for (String key : b.keySet()) {
                String value = b.getString(key);
                if (value != null && !value.trim().isEmpty()) headers.put(key, value);
            }
        }

        title.setText(name == null ? "Canal" : name);
        close.setOnClickListener(v -> finish());
        retry.setOnClickListener(v -> startExoPlayback());
        nativeButton.setOnClickListener(v -> startNativePlayback("Motor nativo solicitado"));
        external.setOnClickListener(v -> openExternal());
        exoView.setOnClickListener(v -> showOverlay());
        exoView.setOnTouchListener((v, event) -> { showOverlay(); return false; });
        nativeVideoView.setOnClickListener(v -> showOverlay());
        nativeVideoView.setOnTouchListener((v, event) -> { showOverlay(); return false; });

        showOverlay();
        startExoPlayback();
    }

    private void startExoPlayback() {
        releasePlayers();
        usingNative = false;
        nativeFallbackAttempted = false;
        exoReady = false;
        nativePrepared = false;
        handler.removeCallbacks(hideOverlay);
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        exoView.setVisibility(View.VISIBLE);
        nativeVideoView.setVisibility(View.GONE);
        engineText.setText("EN VIVO · ExoPlayer Legacy · máx. 720p");

        if (url == null || url.trim().isEmpty()) {
            showError("URL de canal vacía.");
            return;
        }

        DiagnosticStore.savePlayerLog(this, name, url,
                "Inicio ExoPlayer 2.18.7 | fuente=" + safe(source) + " | headers=" + headers.keySet());

        try {
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setMaxVideoSize(1280, 720)
                    .setMaxVideoBitrate(4000000)
                    .setMaxVideoFrameRate(30));

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(5000, 15000, 1500, 2500)
                    .setTargetBufferBytes(8 * 1024 * 1024)
                    .build();

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true);

            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 7.1.2; MX10 Build/N2G47H) "
                            + "AppleWebKit/537.36 Chrome/88.0 Mobile Safari/537.36 ChileTVIPTV/1.4")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(25000);
            if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(httpFactory);
            exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build();
            exoView.setPlayer(exoPlayer);
            exoView.setUseController(false);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        exoReady = true;
                        loading.setVisibility(View.GONE);
                        DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, url,
                                "READY ExoPlayer | fuente=" + safe(source));
                        showOverlay();
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        loading.setVisibility(View.VISIBLE);
                    } else if (playbackState == Player.STATE_ENDED) {
                        loading.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    String detail = error.getErrorCodeName() + " | " + safe(error.getMessage());
                    DiagnosticStore.savePlayerLog(CompatPlayerActivity.this, name, url,
                            "ERROR ExoPlayer | " + detail);
                    if (!nativeFallbackAttempted) {
                        nativeFallbackAttempted = true;
                        Toast.makeText(CompatPlayerActivity.this,
                                "ExoPlayer no pudo abrir la señal. Probando motor nativo…",
                                Toast.LENGTH_SHORT).show();
                        startNativePlayback("Fallback automático tras " + detail);
                    } else {
                        showError("ExoPlayer no pudo abrir esta señal.\n" + detail);
                    }
                }
            });

            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
        } catch (Throwable t) {
            DiagnosticStore.savePlayerLog(this, name, url,
                    "EXCEPCION ExoPlayer | " + t.getClass().getName() + ": " + safe(t.getMessage()));
            nativeFallbackAttempted = true;
            startNativePlayback("Fallback por excepción ExoPlayer");
        }
    }

    private void startNativePlayback(String reason) {
        releaseExoOnly();
        usingNative = true;
        nativePrepared = false;
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        exoView.setVisibility(View.GONE);
        nativeVideoView.setVisibility(View.VISIBLE);
        engineText.setText("EN VIVO · MediaPlayer nativo · fallback");
        DiagnosticStore.savePlayerLog(this, name, url, reason + " | VideoView nativo");

        try {
            nativeVideoView.stopPlayback();
            nativeVideoView.setVideoURI(Uri.parse(url), headers);
            nativeVideoView.setOnPreparedListener(mp -> {
                nativePrepared = true;
                loading.setVisibility(View.GONE);
                try {
                    mp.setOnInfoListener((player, what, extra) -> {
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.setVisibility(View.VISIBLE);
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) loading.setVisibility(View.GONE);
                        return false;
                    });
                } catch (Throwable ignored) {}
                DiagnosticStore.savePlayerLog(this, name, url,
                        "PREPARED nativo | video=" + mp.getVideoWidth() + "x" + mp.getVideoHeight());
                nativeVideoView.start();
                showOverlay();
            });
            nativeVideoView.setOnErrorListener((mp, what, extra) -> {
                loading.setVisibility(View.GONE);
                DiagnosticStore.savePlayerLog(this, name, url,
                        "ERROR MediaPlayer nativo | what=" + what + " extra=" + extra);
                showError("Ningún motor interno pudo abrir esta señal.\n"
                        + "MediaPlayer: " + what + " / " + extra
                        + "\n\nPrueba 'Abrir externo' o cambia de fuente en la lista.");
                return true;
            });
            nativeVideoView.start();
        } catch (Throwable t) {
            DiagnosticStore.savePlayerLog(this, name, url,
                    "EXCEPCION VideoView | " + t.getClass().getName() + ": " + safe(t.getMessage()));
            showError("Error del motor nativo: " + t.getClass().getSimpleName());
        }
    }

    private void openExternal() {
        if (url == null || url.trim().isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(url), "video/*");
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } catch (Throwable t) {
                Toast.makeText(this, "No hay un reproductor externo compatible instalado.", Toast.LENGTH_LONG).show();
            }
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
            togglePlayback();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            play();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            pause();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void togglePlayback() {
        try {
            if (usingNative) {
                if (nativePrepared) {
                    if (nativeVideoView.isPlaying()) nativeVideoView.pause(); else nativeVideoView.start();
                }
            } else if (exoPlayer != null && exoReady) {
                if (exoPlayer.isPlaying()) exoPlayer.pause(); else exoPlayer.play();
            }
        } catch (Throwable ignored) {}
    }

    private void play() {
        try {
            if (usingNative && nativePrepared) nativeVideoView.start();
            else if (exoPlayer != null) exoPlayer.play();
        } catch (Throwable ignored) {}
    }

    private void pause() {
        try {
            if (usingNative && nativePrepared) nativeVideoView.pause();
            else if (exoPlayer != null) exoPlayer.pause();
        } catch (Throwable ignored) {}
    }

    private void releaseExoOnly() {
        try {
            if (exoView != null) exoView.setPlayer(null);
            if (exoPlayer != null) exoPlayer.release();
        } catch (Throwable ignored) {}
        exoPlayer = null;
        exoReady = false;
    }

    private void releasePlayers() {
        releaseExoOnly();
        try { if (nativeVideoView != null) nativeVideoView.stopPlayback(); } catch (Throwable ignored) {}
        nativePrepared = false;
    }

    @Override
    protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        releasePlayers();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveLegacy();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
