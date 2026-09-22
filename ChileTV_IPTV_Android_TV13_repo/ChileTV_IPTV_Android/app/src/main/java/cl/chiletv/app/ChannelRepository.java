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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChannelRepository {

    public enum Source {
        VERIFIED(
                "Verificados",
                "https://dearbulut.github.io/iptv/playlists/country/cl.m3u",
                "iptv_verified_cl.m3u",
                false
        ),
        IPTV_ORG(
                "IPTV-org",
                "https://iptv-org.github.io/iptv/countries/cl.m3u",
                "iptv_org_cl.m3u",
                false
        ),
        M3U_CL(
                "M3U.CL",
                "https://m3u.cl/lista/CL.m3u",
                "m3ucl_cl.m3u",
                false
        ),
        FREE_TV(
                "Free-TV",
                "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8",
                "freetv_cl.m3u",
                true
        );

        public final String label;
        public final String url;
        public final String cacheFile;
        public final boolean filterChile;

        Source(String label, String url, String cacheFile, boolean filterChile) {
            this.label = label;
            this.url = url;
            this.cacheFile = cacheFile;
            this.filterChile = filterChile;
        }

        public Source next() {
            Source[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public ChannelRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public interface Callback {
        void onLoaded(List<Channel> channels, boolean fromCache, Source source);
        void onError(String message, Source source);
    }

    public void load(final Source source, final Callback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String text = download(source.url);
                    List<Channel> channels = prepareChannels(M3UParser.parse(text), source);
                    if (channels.isEmpty()) throw new IllegalStateException("La lista llegó vacía.");
                    saveCache(source.cacheFile, text);
                    final List<Channel> loaded = channels;
                    main.post(new Runnable() {
                        @Override public void run() { callback.onLoaded(loaded, false, source); }
                    });
                } catch (Exception networkError) {
                    try {
                        String cached = readCache(source.cacheFile);
                        List<Channel> channels = prepareChannels(M3UParser.parse(cached), source);
                        if (channels.isEmpty()) throw new IllegalStateException("No existe una caché válida.");
                        final List<Channel> loaded = channels;
                        main.post(new Runnable() {
                            @Override public void run() { callback.onLoaded(loaded, true, source); }
                        });
                    } catch (Exception cacheError) {
                        final String message = "No fue posible descargar la fuente " + source.label
                                + ". Prueba otra fuente o revisa la conexión.";
                        main.post(new Runnable() {
                            @Override public void run() { callback.onError(message, source); }
                        });
                    }
                }
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private List<Channel> prepareChannels(List<Channel> channels, Source source) {
        if (!source.filterChile) return channels;
        List<Channel> filtered = new ArrayList<>();
        for (Channel channel : channels) {
            if (looksChilean(channel)) filtered.add(channel);
        }
        return filtered;
    }

    private boolean looksChilean(Channel channel) {
        String id = safe(channel.getId()).toLowerCase(Locale.ROOT);
        String group = safe(channel.getGroup()).toLowerCase(Locale.ROOT);
        String name = safe(channel.getName()).toLowerCase(Locale.ROOT);
        return id.contains(".cl")
                || id.endsWith("cl")
                || group.equals("chile")
                || group.contains("chile")
                || isKnownNational(name);
    }

    public static boolean isKnownNational(String value) {
        String s = safe(value).toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n");
        return s.equals("tvn") || s.startsWith("tvn ") || s.contains("tvn3")
                || s.contains("24 horas") || s.contains("24horas") || s.equals("ntv")
                || s.contains("tv chile") || s.equals("mega") || s.startsWith("mega ")
                || s.contains("meganoticias") || s.equals("chv") || s.contains("chilevision")
                || s.contains("canal 13") || s.startsWith("13 ") || s.equals("13c")
                || s.contains("t13") || s.contains("la red") || s.equals("tv+")
                || s.startsWith("tv+ ") || s.contains("cnn chile") || s.contains("telecanal")
                || s.contains("chv noticias") || s.contains("chv deportes");
    }

    private String download(String source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 7.1.2; MX10 Build/N2G47H) "
                        + "AppleWebKit/537.36 Chrome/88.0 Mobile Safari/537.36 ChileTVIPTV/1.4");
        connection.setRequestProperty("Accept", "application/x-mpegURL,application/vnd.apple.mpegurl,text/plain,*/*");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Connection", "close");

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

    private void saveCache(String cacheName, String text) throws Exception {
        File file = new File(context.getFilesDir(), cacheName);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readCache(String cacheName) throws Exception {
        File file = new File(context.getFilesDir(), cacheName);
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
