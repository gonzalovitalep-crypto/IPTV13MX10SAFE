package cl.chiletv.app;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class M3UParser {
    private static final Pattern ATTR = Pattern.compile("([A-Za-z0-9_-]+)=\\\"([^\\\"]*)\\\"");
    private static final Pattern JSON_HEADER = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private M3UParser() {}

    public static List<Channel> parse(String text) {
        List<Channel> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;

        String pendingInfo = null;
        Map<String, String> pendingHeaders = new HashMap<>();

        String[] lines = text.replace("\r", "").split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF:")) {
                pendingInfo = line;
                pendingHeaders = new HashMap<>();
                continue;
            }

            if (line.regionMatches(true, 0, "#EXTVLCOPT:http-referrer=", 0, 25)) {
                pendingHeaders.put("Referer", line.substring(line.indexOf('=') + 1).trim());
                continue;
            }

            if (line.regionMatches(true, 0, "#EXTVLCOPT:http-user-agent=", 0, 27)) {
                pendingHeaders.put("User-Agent", line.substring(line.indexOf('=') + 1).trim());
                continue;
            }

            if (line.startsWith("#EXTHTTP:")) {
                Matcher hm = JSON_HEADER.matcher(line.substring(9));
                while (hm.find()) pendingHeaders.put(hm.group(1), hm.group(2));
                continue;
            }

            if (line.startsWith("#")) continue;
            if (pendingInfo == null) continue;

            Map<String, String> attrs = parseAttrs(pendingInfo);
            String name = parseName(pendingInfo);
            String url = line;
            Map<String, String> headers = new HashMap<>(pendingHeaders);

            int pipe = url.indexOf('|');
            if (pipe > 0) {
                String extra = url.substring(pipe + 1);
                url = url.substring(0, pipe);
                parsePipeHeaders(extra, headers);
            }

            if (url.startsWith("http://") || url.startsWith("https://")) {
                result.add(new Channel(
                        attrs.get("tvg-id"),
                        attrs.containsKey("tvg-name") && !attrs.get("tvg-name").isEmpty() ? attrs.get("tvg-name") : name,
                        attrs.get("tvg-logo"),
                        attrs.get("group-title"),
                        firstNonEmpty(attrs.get("tvg-country"), attrs.get("country")),
                        firstNonEmpty(attrs.get("tvg-language"), attrs.get("language")),
                        url,
                        headers
                ));
            }

            pendingInfo = null;
            pendingHeaders = new HashMap<>();
        }
        return result;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        return b == null ? "" : b.trim();
    }

    private static Map<String, String> parseAttrs(String info) {
        Map<String, String> attrs = new HashMap<>();
        Matcher m = ATTR.matcher(info);
        while (m.find()) attrs.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2).trim());
        return attrs;
    }

    private static String parseName(String info) {
        int comma = info.lastIndexOf(',');
        if (comma < 0 || comma + 1 >= info.length()) return "Canal sin nombre";
        return info.substring(comma + 1).trim();
    }

    private static void parsePipeHeaders(String extra, Map<String, String> headers) {
        for (String part : extra.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = decode(part.substring(0, eq));
            String value = decode(part.substring(eq + 1));
            if (key.equalsIgnoreCase("referrer") || key.equalsIgnoreCase("referer")) key = "Referer";
            if (key.equalsIgnoreCase("user-agent")) key = "User-Agent";
            headers.put(key, value);
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return s;
        }
    }
}
