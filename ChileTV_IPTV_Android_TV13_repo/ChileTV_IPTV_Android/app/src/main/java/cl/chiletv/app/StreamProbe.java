package cl.chiletv.app;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comprobación ligera antes de entregar una señal al MediaPlayer del firmware.
 * Detecta streams caídos y, en HLS master, elige una variante <=720p/2.5Mbps.
 */
public final class StreamProbe {
    private static final int MAX_PLAYLIST_BYTES = 384 * 1024;
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;
    private static final long MAX_BANDWIDTH = 2500000L;

    private StreamProbe() {}

    public static final class Result {
        public final boolean ok;
        public final String url;
        public final String detail;
        public final boolean variantSelected;

        Result(boolean ok, String url, String detail, boolean variantSelected) {
            this.ok = ok;
            this.url = url;
            this.detail = detail;
            this.variantSelected = variantSelected;
        }
    }

    public static Result resolve(String inputUrl, Map<String, String> headers) {
        if (inputUrl == null || inputUrl.trim().isEmpty()) {
            return new Result(false, inputUrl, "URL vacía", false);
        }
        HttpURLConnection connection = null;
        try {
            connection = open(inputUrl, headers);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new Result(false, inputUrl, "HTTP " + code, false);
            }

            String finalUrl = connection.getURL().toString();
            String contentType = safe(connection.getContentType()).toLowerCase(Locale.ROOT);
            boolean likelyHls = finalUrl.toLowerCase(Locale.ROOT).contains(".m3u8")
                    || contentType.contains("mpegurl") || contentType.contains("m3u8");
            if (!likelyHls) {
                return new Result(true, finalUrl, "HTTP " + code + " · stream directo", false);
            }

            String text = readLimited(connection.getInputStream(), MAX_PLAYLIST_BYTES);
            if (!text.startsWith("#EXTM3U") && !text.contains("#EXTM3U")) {
                return new Result(false, finalUrl, "La URL no devolvió una playlist HLS válida", false);
            }

            if (!text.contains("#EXT-X-STREAM-INF")) {
                return new Result(true, finalUrl, "HLS media playlist accesible", false);
            }

            Variant best = chooseVariant(text, finalUrl);
            if (best == null) {
                return new Result(true, finalUrl, "HLS master accesible; sin variante seleccionable", false);
            }

            // Verificar que la variante elegida también responde antes de usar MediaPlayer.
            HttpURLConnection variantConnection = null;
            try {
                variantConnection = open(best.url, headers);
                int variantCode = variantConnection.getResponseCode();
                if (variantCode < 200 || variantCode >= 300) {
                    return new Result(false, best.url, "Variante HLS respondió HTTP " + variantCode, true);
                }
                String resolvedVariant = variantConnection.getURL().toString();
                return new Result(true, resolvedVariant,
                        "HLS master → " + best.width + "x" + best.height + " · " + best.bandwidth + " bps", true);
            } finally {
                if (variantConnection != null) variantConnection.disconnect();
            }
        } catch (Throwable t) {
            return new Result(false, inputUrl,
                    t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()), false);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection open(String value, Map<String, String> headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(value).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 7.1.2; MX10 Build/N2G47H) "
                        + "AppleWebKit/537.36 Chrome/88.0 Mobile Safari/537.36 TVHispana/1.6");
        c.setRequestProperty("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,video/*,*/*");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Connection", "close");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().trim().isEmpty()) {
                    c.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }
        return c;
    }

    private static String readLimited(InputStream raw, int maxBytes) throws Exception {
        BufferedInputStream in = new BufferedInputStream(raw, 8192);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 32768));
        try {
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                int allowed = Math.min(n, maxBytes - total);
                if (allowed > 0) out.write(buffer, 0, allowed);
                total += allowed;
                if (total >= maxBytes) break;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static Variant chooseVariant(String playlist, String baseUrl) {
        String[] lines = playlist.replace("\r", "").split("\n");
        List<Variant> variants = new ArrayList<Variant>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue;
            long bandwidth = longAttr(line, "BANDWIDTH", Long.MAX_VALUE);
            int[] resolution = resolutionAttr(line);
            String uri = null;
            for (int j = i + 1; j < lines.length; j++) {
                String next = lines[j].trim();
                if (next.isEmpty()) continue;
                if (next.startsWith("#")) continue;
                uri = next;
                i = j;
                break;
            }
            if (uri == null) continue;
            try {
                String absolute = new URL(new URL(baseUrl), uri).toString();
                variants.add(new Variant(absolute, bandwidth, resolution[0], resolution[1]));
            } catch (Throwable ignored) {}
        }
        if (variants.isEmpty()) return null;

        Variant bestUnderLimit = null;
        Variant lowest = null;
        for (Variant v : variants) {
            if (lowest == null || v.bandwidth < lowest.bandwidth) lowest = v;
            boolean dimensionsOk = v.width <= 0 || v.height <= 0 || (v.width <= MAX_WIDTH && v.height <= MAX_HEIGHT);
            boolean bitrateOk = v.bandwidth <= MAX_BANDWIDTH;
            if (dimensionsOk && bitrateOk) {
                if (bestUnderLimit == null || v.bandwidth > bestUnderLimit.bandwidth) bestUnderLimit = v;
            }
        }
        return bestUnderLimit != null ? bestUnderLimit : lowest;
    }

    private static long longAttr(String line, String key, long fallback) {
        String value = stringAttr(line, key);
        if (value.isEmpty()) return fallback;
        try { return Long.parseLong(value); } catch (Throwable ignored) { return fallback; }
    }

    private static int[] resolutionAttr(String line) {
        String value = stringAttr(line, "RESOLUTION");
        if (value.isEmpty()) return new int[]{0, 0};
        int x = value.toLowerCase(Locale.ROOT).indexOf('x');
        if (x <= 0) return new int[]{0, 0};
        try {
            return new int[]{Integer.parseInt(value.substring(0, x)), Integer.parseInt(value.substring(x + 1))};
        } catch (Throwable ignored) {
            return new int[]{0, 0};
        }
    }

    private static String stringAttr(String line, String key) {
        String token = key + "=";
        int start = line.indexOf(token);
        if (start < 0) return "";
        start += token.length();
        int end = line.indexOf(',', start);
        if (end < 0) end = line.length();
        String value = line.substring(start, end).trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static final class Variant {
        final String url;
        final long bandwidth;
        final int width;
        final int height;
        Variant(String url, long bandwidth, int width, int height) {
            this.url = url;
            this.bandwidth = bandwidth;
            this.width = width;
            this.height = height;
        }
    }
}
