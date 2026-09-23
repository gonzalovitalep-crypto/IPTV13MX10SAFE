package cl.chiletv.app;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Modelo deliberadamente liviano para el MX10. Los mapas de cabeceras vacíos
 * comparten una instancia inmutable en vez de reservar un HashMap por canal.
 */
public class Channel {
    private final String id;
    private final String name;
    private final String logo;
    private final String group;
    private final String country;
    private final String language;
    private final String url;
    private final Map<String, String> headers;

    public Channel(String id, String name, String logo, String group, String country,
                   String language, String url, Map<String, String> headers) {
        this.id = value(id);
        this.name = value(name).isEmpty() ? "Canal sin nombre" : value(name);
        this.logo = value(logo);
        this.group = value(group).isEmpty() ? "TV" : value(group);
        this.country = value(country);
        this.language = value(language);
        this.url = value(url);
        if (headers == null || headers.isEmpty()) {
            this.headers = Collections.emptyMap();
        } else {
            this.headers = Collections.unmodifiableMap(new HashMap<String, String>(headers));
        }
    }

    private static String value(String s) {
        return s == null ? "" : s.trim();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLogo() { return logo; }
    public String getGroup() { return group; }
    public String getCountry() { return country; }
    public String getLanguage() { return language; }
    public String getUrl() { return url; }
    public Map<String, String> getHeaders() { return headers; }

    public String favoriteKey() {
        if (!id.isEmpty()) return "id:" + id;
        return "url:" + url;
    }
}
