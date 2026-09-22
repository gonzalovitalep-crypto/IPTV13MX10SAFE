package cl.chiletv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class FavoritesStore {
    private static final String PREFS = "favorites";
    private static final String KEY = "channel_keys";
    private final SharedPreferences prefs;

    public FavoritesStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(Channel channel) {
        return prefs.getStringSet(KEY, new HashSet<>()).contains(channel.favoriteKey());
    }

    public boolean toggle(Channel channel) {
        Set<String> copy = new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
        boolean nowFavorite;
        if (copy.contains(channel.favoriteKey())) {
            copy.remove(channel.favoriteKey());
            nowFavorite = false;
        } else {
            copy.add(channel.favoriteKey());
            nowFavorite = true;
        }
        prefs.edit().putStringSet(KEY, copy).apply();
        return nowFavorite;
    }
}
