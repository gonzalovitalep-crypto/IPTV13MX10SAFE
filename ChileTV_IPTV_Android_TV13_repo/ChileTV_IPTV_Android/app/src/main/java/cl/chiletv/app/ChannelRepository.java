package cl.chiletv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ChannelRepository {

    public enum Catalog {
        NATIONALS("Nacionales", "TV abierta y señales nacionales de Chile"),
        CHILE("Chile", "Canales nacionales, noticias y regionales"),
        LATIN("Latinoamérica", "México, Argentina, Colombia, Perú y más"),
        EUROPE("España / Europa", "Canales españoles y europeos en español"),
        USA_HISPANIC("EE.UU. Hispano", "Canales en español emitidos desde EE.UU."),
        SPANISH_ALL("Todo en español", "Catálogo amplio por idioma español");

        public final String label;
        public final String description;

        Catalog(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    private enum FilterMode { NONE, NATIONALS, CHILE, LATIN_SPANISH, EUROPE_SPANISH, USA_HISPANIC }

    public enum Source {
        NATIONALS_ALPLOX(Catalog.NATIONALS, "Alplox · Nacionales Chile",
                new String[]{"https://raw.githubusercontent.com/Alplox/json-teles/refs/heads/main/m3u-playlists/cl.m3u"},
                "v56_nationals_alplox.m3u", FilterMode.NATIONALS),
        NATIONALS_M3U_CL(Catalog.NATIONALS, "M3U.CL · Nacionales",
                new String[]{"https://m3u.cl/lista/CL.m3u"},
                "v56_nationals_m3ucl.m3u", FilterMode.NATIONALS),
        NATIONALS_IPTV_ORG(Catalog.NATIONALS, "IPTV-org · Nacionales",
                new String[]{"https://iptv-org.github.io/iptv/countries/cl.m3u"},
                "v56_nationals_iptvorg.m3u", FilterMode.NATIONALS),
        NATIONALS_FREE_TV(Catalog.NATIONALS, "Free-TV · Nacionales",
                new String[]{"https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"},
                "v56_nationals_freetv.m3u", FilterMode.NATIONALS),

        CHILE_VERIFIED(Catalog.CHILE, "Verificados Chile",
                new String[]{"https://dearbulut.github.io/iptv/playlists/country/cl.m3u"},
                "v53_chile_verified.m3u", FilterMode.CHILE),
        CHILE_M3U_CL(Catalog.CHILE, "M3U.CL Chile · CL.m3u",
                new String[]{"https://m3u.cl/lista/CL.m3u"},
                "v55_chile_m3ucl.m3u", FilterMode.CHILE),
        CHILE_IPTV_ORG(Catalog.CHILE, "IPTV-org Chile",
                new String[]{"https://iptv-org.github.io/iptv/countries/cl.m3u"},
                "v53_chile_iptvorg.m3u", FilterMode.CHILE),
        CHILE_FREE_TV(Catalog.CHILE, "Free-TV Chile",
                new String[]{"https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"},
                "v53_chile_freetv.m3u", FilterMode.CHILE),

        LATIN_HISPAM(Catalog.LATIN, "IPTV-org Hispanoamérica",
                new String[]{"https://iptv-org.github.io/iptv/regions/hispam.m3u"},
                "v53_latin_hispam.m3u", FilterMode.NONE),
        LATIN_LATAM(Catalog.LATIN, "IPTV-org Latinoamérica",
                new String[]{"https://iptv-org.github.io/iptv/regions/latam.m3u"},
                "v53_latin_latam.m3u", FilterMode.LATIN_SPANISH),
        LATIN_SPANISH(Catalog.LATIN, "IPTV-org Español Latino",
                new String[]{"https://iptv-org.github.io/iptv/languages/spa.m3u"},
                "v53_latin_spa.m3u", FilterMode.LATIN_SPANISH),
        LATIN_DEARBULUT(Catalog.LATIN, "Verificados Español Latino",
                new String[]{"https://dearbulut.github.io/iptv/playlists/language/spa.m3u"},
                "v53_latin_dear_spa.m3u", FilterMode.LATIN_SPANISH),
        LATIN_M3U_CL(Catalog.LATIN, "M3U.CL Latino",
                new String[]{
                        "https://m3u.cl/lista/AR.m3u",
                        "https://m3u.cl/lista/BO.m3u",
                        "https://m3u.cl/lista/CL.m3u",
                        "https://m3u.cl/lista/CO.m3u",
                        "https://m3u.cl/lista/EC.m3u",
                        "https://m3u.cl/lista/MX.m3u"
                },
                "v53_latin_m3ucl.m3u", FilterMode.NONE),

        EUROPE_SPAIN(Catalog.EUROPE, "IPTV-org España",
                new String[]{"https://iptv-org.github.io/iptv/countries/es.m3u"},
                "v53_europe_es.m3u", FilterMode.NONE),
        EUROPE_SPANISH(Catalog.EUROPE, "IPTV-org Europa en español",
                new String[]{"https://iptv-org.github.io/iptv/languages/spa.m3u"},
                "v53_europe_spa.m3u", FilterMode.EUROPE_SPANISH),
        EUROPE_M3U_CL(Catalog.EUROPE, "M3U.CL España",
                new String[]{"https://m3u.cl/lista/ES.m3u"},
                "v53_europe_m3ucl.m3u", FilterMode.NONE),
        EUROPE_FREE_TV(Catalog.EUROPE, "Free-TV España",
                new String[]{"https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"},
                "v53_europe_freetv.m3u", FilterMode.EUROPE_SPANISH),

        USA_SPANISH(Catalog.USA_HISPANIC, "IPTV-org EE.UU. en español",
                new String[]{"https://iptv-org.github.io/iptv/languages/spa.m3u"},
                "v53_usa_spa.m3u", FilterMode.USA_HISPANIC),
        USA_DEARBULUT(Catalog.USA_HISPANIC, "Verificados EE.UU. en español",
                new String[]{"https://dearbulut.github.io/iptv/playlists/language/spa.m3u"},
                "v53_usa_dear_spa.m3u", FilterMode.USA_HISPANIC),

        SPANISH_IPTV_ORG(Catalog.SPANISH_ALL, "IPTV-org Español",
                new String[]{"https://iptv-org.github.io/iptv/languages/spa.m3u"},
                "v53_all_spa.m3u", FilterMode.NONE),
        SPANISH_DEARBULUT(Catalog.SPANISH_ALL, "Verificados Español",
                new String[]{"https://dearbulut.github.io/iptv/playlists/language/spa.m3u"},
                "v53_all_dear_spa.m3u", FilterMode.NONE);

        public final Catalog catalog;
        public final String label;
        public final String[] urls;
        public final String cacheFile;
        private final FilterMode filterMode;

        Source(Catalog catalog, String label, String[] urls, String cacheFile, FilterMode filterMode) {
            this.catalog = catalog;
            this.label = label;
            this.urls = urls;
            this.cacheFile = cacheFile;
            this.filterMode = filterMode;
        }

        public static Source firstFor(Catalog catalog) {
            for (Source source : values()) if (source.catalog == catalog) return source;
            return NATIONALS_ALPLOX;
        }

        public Source nextFor(Catalog catalog) {
            Source[] values = values();
            for (int offset = 1; offset <= values.length; offset++) {
                Source candidate = values[(ordinal() + offset) % values.length];
                if (candidate.catalog == catalog) return candidate;
            }
            return this;
        }
    }

    private static final String[] LATIN_COUNTRIES = {
            "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "SV", "GT",
            "HN", "MX", "NI", "PA", "PY", "PE", "PR", "UY", "VE"
    };
    private static final String[] EUROPE_SPANISH_COUNTRIES = {"ES", "AD"};

    private static final long CACHE_TTL_MS = 30L * 60L * 1000L;
    private static final long MAX_CACHE_BYTES = 8L * 1024L * 1024L;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object taskLock = new Object();
    private Future<?> activeTask;

    public ChannelRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public interface Callback {
        void onLoaded(List<Channel> channels, boolean fromCache, Source source);
        void onError(String message, Source source);
    }

    public void load(final Source source, final Callback callback) {
        load(source, false, callback);
    }

    public void load(final Source source, final boolean forceRefresh, final Callback callback) {
        synchronized (taskLock) {
            if (activeTask != null) activeTask.cancel(true);
            activeTask = executor.submit(new Runnable() {
                @Override
                public void run() {
                    if (Thread.currentThread().isInterrupted()) return;
                    File cache = new File(context.getFilesDir(), source.cacheFile);
                    try {
                        if (!forceRefresh && isFresh(cache)) {
                            List<Channel> channels = parseCache(cache, source);
                            if (!channels.isEmpty()) {
                                postLoaded(callback, channels, true, source);
                                return;
                            }
                        }

                        File temp = new File(context.getCacheDir(), source.cacheFile + ".tmp");
                        if (temp.exists()) temp.delete();
                        downloadAllToFile(source.urls, temp);
                        if (Thread.currentThread().isInterrupted()) {
                            temp.delete();
                            return;
                        }
                        List<Channel> channels = parseCache(temp, source);
                        if (channels.isEmpty()) throw new IllegalStateException("La lista llegó vacía.");
                        replaceFile(temp, cache);
                        postLoaded(callback, channels, false, source);
                    } catch (Exception networkError) {
                        try {
                            if (Thread.currentThread().isInterrupted()) return;
                            List<Channel> channels = parseCache(cache, source);
                            if (channels.isEmpty()) throw new IllegalStateException("No existe una caché válida.");
                            postLoaded(callback, channels, true, source);
                        } catch (Exception cacheError) {
                            final String message = "No fue posible descargar " + source.label
                                    + ". Prueba otra fuente o revisa la conexión.";
                            main.post(new Runnable() {
                                @Override public void run() { callback.onError(message, source); }
                            });
                        }
                    }
                }
            });
        }
    }

    public void cancelActiveLoad() {
        synchronized (taskLock) {
            if (activeTask != null) {
                activeTask.cancel(true);
                activeTask = null;
            }
        }
    }

    private boolean isFresh(File file) {
        return file.exists() && file.length() > 0 && (System.currentTimeMillis() - file.lastModified()) < CACHE_TTL_MS;
    }

    private List<Channel> parseCache(File file, Source source) throws Exception {
        if (file == null || !file.exists() || file.length() <= 0) return new ArrayList<Channel>();
        FileInputStream in = new FileInputStream(file);
        try {
            return prepareChannels(M3UParser.parse(new BufferedInputStream(in, 16384)), source);
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }

    private void postLoaded(final Callback callback, final List<Channel> channels,
                            final boolean fromCache, final Source source) {
        main.post(new Runnable() {
            @Override public void run() { callback.onLoaded(channels, fromCache, source); }
        });
    }

    public void shutdown() {
        cancelActiveLoad();
        executor.shutdownNow();
    }

    private List<Channel> prepareChannels(List<Channel> channels, Source source) {
        LinkedHashMap<String, Channel> unique = new LinkedHashMap<>();
        for (Channel channel : channels) {
            if (!matchesFilter(channel, source.filterMode)) continue;
            String key = !safe(channel.getId()).isEmpty()
                    ? "id:" + safe(channel.getId()).toLowerCase(Locale.ROOT)
                    : "url:" + safe(channel.getUrl());
            if (!unique.containsKey(key)) unique.put(key, channel);
        }
        return new ArrayList<>(unique.values());
    }

    private boolean matchesFilter(Channel channel, FilterMode mode) {
        if (mode == FilterMode.NONE) return true;
        String country = normalizedCountry(channel);
        if (mode == FilterMode.NATIONALS) {
            return isKnownNational(channel.getName()) || isKnownNational(channel.getId());
        }
        if (mode == FilterMode.CHILE) {
            return countryContains(country, "CL") || looksChilean(channel);
        }
        if (mode == FilterMode.LATIN_SPANISH) {
            if (country.contains("LATAM") || country.contains("HISPAM")) return true;
            return countryIn(country, LATIN_COUNTRIES);
        }
        if (mode == FilterMode.EUROPE_SPANISH) {
            return countryIn(country, EUROPE_SPANISH_COUNTRIES) || idEndsWith(channel, ".es") || idEndsWith(channel, ".ad");
        }
        if (mode == FilterMode.USA_HISPANIC) {
            return countryContains(country, "US") || idEndsWith(channel, ".us");
        }
        return true;
    }

    private boolean looksChilean(Channel channel) {
        String id = safe(channel.getId()).toLowerCase(Locale.ROOT);
        String group = safe(channel.getGroup()).toLowerCase(Locale.ROOT);
        String name = safe(channel.getName()).toLowerCase(Locale.ROOT);
        return id.contains(".cl") || id.endsWith("cl") || group.contains("chile") || isKnownNational(name);
    }

    private static String normalizedCountry(Channel channel) {
        String raw = safe(channel.getCountry()).toUpperCase(Locale.ROOT).replace(" ", "");
        if (!raw.isEmpty()) return raw;
        String id = safe(channel.getId());
        int dot = id.lastIndexOf('.');
        if (dot >= 0 && dot + 2 < id.length()) {
            String suffix = id.substring(dot + 1);
            int at = suffix.indexOf('@');
            if (at >= 0) suffix = suffix.substring(0, at);
            if (suffix.length() == 2) return suffix.toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private static boolean idEndsWith(Channel channel, String suffix) {
        String id = safe(channel.getId()).toLowerCase(Locale.ROOT);
        return id.endsWith(suffix) || id.contains(suffix + "@");
    }

    private static boolean countryIn(String raw, String[] values) {
        for (String value : values) if (countryContains(raw, value)) return true;
        return false;
    }

    private static boolean countryContains(String raw, String code) {
        if (raw == null || raw.isEmpty()) return false;
        String[] parts = raw.split("[;,]");
        for (String part : parts) if (part.trim().equalsIgnoreCase(code)) return true;
        return false;
    }

    public static String countryLabel(Channel channel) {
        String raw = normalizedCountry(channel);
        if (raw.isEmpty()) return "";
        String code = raw.split("[;,]")[0].trim();
        if (code.equals("CL")) return "Chile";
        if (code.equals("AR")) return "Argentina";
        if (code.equals("BO")) return "Bolivia";
        if (code.equals("CO")) return "Colombia";
        if (code.equals("CR")) return "Costa Rica";
        if (code.equals("CU")) return "Cuba";
        if (code.equals("DO")) return "R. Dominicana";
        if (code.equals("EC")) return "Ecuador";
        if (code.equals("SV")) return "El Salvador";
        if (code.equals("GT")) return "Guatemala";
        if (code.equals("HN")) return "Honduras";
        if (code.equals("MX")) return "México";
        if (code.equals("NI")) return "Nicaragua";
        if (code.equals("PA")) return "Panamá";
        if (code.equals("PY")) return "Paraguay";
        if (code.equals("PE")) return "Perú";
        if (code.equals("PR")) return "Puerto Rico";
        if (code.equals("UY")) return "Uruguay";
        if (code.equals("VE")) return "Venezuela";
        if (code.equals("US")) return "EE.UU.";
        if (code.equals("ES")) return "España";
        if (code.equals("AD")) return "Andorra";
        if (code.equals("LATAM") || code.equals("HISPAM")) return "Latinoamérica";
        return code;
    }

    public static boolean isKnownNational(String value) {
        String s = safe(value).toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n");
        return s.equals("tvn") || s.startsWith("tvn ") || s.contains("tvn3")
                || s.contains("tvn 3") || s.contains("24 horas") || s.contains("24horas")
                || s.equals("ntv") || s.startsWith("ntv ") || s.contains("tv chile")
                || s.equals("mega") || s.startsWith("mega ") || s.contains("mega 2")
                || s.contains("meganoticias") || s.equals("chv") || s.contains("chilevision")
                || s.contains("chv noticias") || s.contains("chv deportes")
                || s.contains("canal 13") || s.startsWith("13 ") || s.equals("13c")
                || s.contains("13 cultura") || s.contains("13 entret") || s.contains("13e")
                || s.contains("13 internacional") || s.contains("t13")
                || s.contains("la red") || s.equals("tv+") || s.startsWith("tv+ ")
                || s.contains("ucv tv") || s.contains("cnn chile") || s.contains("telecanal");
    }

    private void downloadAllToFile(String[] sources, File output) throws Exception {
        Exception last = null;
        int success = 0;
        long total = 0L;
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output, false), 16384);
        try {
            out.write("#EXTM3U\n".getBytes("UTF-8"));
            total += 8;
            for (String source : sources) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("cancelled");
                HttpURLConnection connection = null;
                try {
                    connection = openConnection(source);
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                    InputStream raw = connection.getInputStream();
                    BufferedInputStream in = new BufferedInputStream(raw, 16384);
                    try {
                        byte[] buffer = new byte[16384];
                        int n;
                        while ((n = in.read(buffer)) >= 0) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("cancelled");
                            total += n;
                            if (total > MAX_CACHE_BYTES) throw new IllegalStateException("Lista demasiado grande");
                            out.write(buffer, 0, n);
                        }
                        out.write('\n');
                    } finally {
                        try { in.close(); } catch (Exception ignored) {}
                    }
                    success++;
                } catch (Exception e) {
                    last = e;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            out.flush();
        } finally {
            try { out.close(); } catch (Exception ignored) {}
        }
        if (success == 0) {
            output.delete();
            if (last != null) throw last;
            throw new IllegalStateException("Sin fuentes disponibles");
        }
    }

    private HttpURLConnection openConnection(String source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 7.1.2; MX10 Build/N2G47H) "
                        + "AppleWebKit/537.36 Chrome/88.0 Mobile Safari/537.36 TVHispana/1.6");
        connection.setRequestProperty("Accept", "application/x-mpegURL,application/vnd.apple.mpegurl,text/plain,*/*");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Connection", "close");
        return connection;
    }

    private void replaceFile(File temp, File cache) throws Exception {
        if (cache.exists() && !cache.delete()) {
            copyFile(temp, cache);
            temp.delete();
            return;
        }
        if (!temp.renameTo(cache)) {
            copyFile(temp, cache);
            temp.delete();
        }
    }

    private void copyFile(File source, File destination) throws Exception {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(source), 16384);
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination, false), 16384);
        try {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Exception ignored) {}
            try { out.close(); } catch (Exception ignored) {}
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
