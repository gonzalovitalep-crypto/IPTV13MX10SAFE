package cl.chiletv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChannelRepository {
    public static final String CHILE_PLAYLIST = "https://iptv-org.github.io/iptv/countries/cl.m3u";
    private static final String CACHE_FILE = "iptv_chile.m3u";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public ChannelRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public interface Callback {
        void onLoaded(List<Channel> channels, boolean fromCache);
        void onError(String message);
    }

    public void load(Callback callback) {
        executor.execute(() -> {
            try {
                String text = download(CHILE_PLAYLIST);
                List<Channel> channels = M3UParser.parse(text);
                if (channels.isEmpty()) throw new IllegalStateException("La lista llegó vacía.");
                saveCache(text);
                main.post(() -> callback.onLoaded(channels, false));
            } catch (Exception networkError) {
                try {
                    String cached = readCache();
                    List<Channel> channels = M3UParser.parse(cached);
                    if (channels.isEmpty()) throw new IllegalStateException("No existe una caché válida.");
                    main.post(() -> callback.onLoaded(channels, true));
                } catch (Exception cacheError) {
                    String message = "No fue posible descargar la lista de Chile. Revisa tu conexión e inténtalo nuevamente.";
                    main.post(() -> callback.onError(message));
                }
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private String download(String source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "ChileTVIPTV/1.0 Android");
        connection.setRequestProperty("Accept", "application/x-mpegURL,text/plain,*/*");

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }

        try (InputStream in = connection.getInputStream()) {
            return readFully(in);
        } finally {
            connection.disconnect();
        }
    }

    private void saveCache(String text) throws Exception {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readCache() throws Exception {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        if (!file.exists()) throw new IllegalStateException("Sin caché");
        try (FileInputStream in = new FileInputStream(file)) {
            return readFully(in);
        }
    }

    private static String readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
