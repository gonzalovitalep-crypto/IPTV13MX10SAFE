package cl.chiletv.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompatMainActivity extends Activity {
    private static final int PAGE_SIZE = 96;

    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> filteredChannels = new ArrayList<>();
    private final List<Channel> visibleChannels = new ArrayList<>();

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = new Runnable() {
        @Override public void run() { applyFilter(); }
    };

    private ChannelRepository repository;
    private FavoritesStore favorites;
    private CatalogAdapter catalogAdapter;
    private ChannelGridAdapter channelAdapter;

    private GridView catalogGrid;
    private GridView channelGrid;
    private LinearLayout channelArea;
    private EditText searchInput;
    private TextView statusText;
    private TextView sectionTitle;
    private ProgressBar loadingBar;
    private Button favoriteFilter;
    private Button sourceButton;
    private Button backRegions;
    private Button prevPage;
    private Button nextPage;
    private TextView pageText;

    private boolean onlyFavorites = false;
    private int currentPage = 0;
    private boolean needsReloadAfterPlayer = false;
    private ChannelRepository.Catalog currentCatalog = null;
    private ChannelRepository.Source currentSource = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compat_main);

        repository = new ChannelRepository(this);
        favorites = new FavoritesStore(this);

        catalogGrid = findViewById(R.id.compatCatalogGrid);
        channelGrid = findViewById(R.id.compatChannelGrid);
        channelArea = findViewById(R.id.compatChannelArea);
        searchInput = findViewById(R.id.compatSearchInput);
        statusText = findViewById(R.id.compatStatusText);
        sectionTitle = findViewById(R.id.compatSectionTitle);
        loadingBar = findViewById(R.id.compatLoadingBar);
        favoriteFilter = findViewById(R.id.btnCompatFavorites);
        sourceButton = findViewById(R.id.btnCompatSource);
        backRegions = findViewById(R.id.btnCompatBackRegions);
        Button refresh = findViewById(R.id.btnCompatRefresh);
        Button diagnostics = findViewById(R.id.btnCompatDiagnostics);
        Button web = findViewById(R.id.btnCompatWeb);
        Button home = findViewById(R.id.btnCompatHome);
        prevPage = findViewById(R.id.btnCompatPrevPage);
        nextPage = findViewById(R.id.btnCompatNextPage);
        pageText = findViewById(R.id.compatPageText);

        catalogAdapter = new CatalogAdapter();
        channelAdapter = new ChannelGridAdapter();
        catalogGrid.setAdapter(catalogAdapter);
        channelGrid.setAdapter(channelAdapter);

        catalogGrid.setOnItemClickListener((parent, view, position, id) -> {
            ChannelRepository.Catalog[] values = ChannelRepository.Catalog.values();
            if (position >= 0 && position < values.length) openCatalog(values[position]);
        });
        catalogGrid.setOnKeyListener((v, keyCode, event) -> {
            if (!isAcceptKey(keyCode, event)) return false;
            int position = catalogGrid.getSelectedItemPosition();
            ChannelRepository.Catalog[] values = ChannelRepository.Catalog.values();
            if (position >= 0 && position < values.length) {
                openCatalog(values[position]);
                return true;
            }
            return false;
        });

        channelGrid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleChannels.size()) {
                openChannel(visibleChannels.get(position), "grid_onItemClick");
            }
        });
        channelGrid.setOnKeyListener((v, keyCode, event) -> {
            int position = channelGrid.getSelectedItemPosition();
            if (isFavoriteKey(keyCode, event)) {
                if (position >= 0 && position < visibleChannels.size()) {
                    toggleFavorite(visibleChannels.get(position));
                    return true;
                }
                return false;
            }
            if (!isAcceptKey(keyCode, event)) return false;
            if (position >= 0 && position < visibleChannels.size()) {
                openChannel(visibleChannels.get(position), "grid_key_" + keyCode);
                return true;
            }
            return false;
        });
        channelGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleChannels.size()) return false;
            toggleFavorite(visibleChannels.get(position));
            return true;
        });

        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Debounce corto: evita reconstruir la grilla en cada evento del teclado del MX10.
                // Importante: NO mover el foco a la grilla mientras el usuario escribe.
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 180);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean confirm = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getAction() == KeyEvent.ACTION_UP
                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER));
            if (!confirm) return false;
            searchHandler.removeCallbacks(searchRunnable);
            applyFilter();
            hideKeyboardAndFocusGrid();
            return true;
        });

        sourceButton.setOnClickListener(v -> {
            if (currentCatalog == null || currentSource == null) return;
            currentSource = currentSource.nextFor(currentCatalog);
            updateSourceButton();
            loadChannels();
        });
        refresh.setOnClickListener(v -> {
            if (currentSource != null) loadChannels(true);
        });
        prevPage.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                renderPage();
            }
        });
        nextPage.setOnClickListener(v -> {
            int pages = pageCount();
            if (currentPage + 1 < pages) {
                currentPage++;
                renderPage();
            }
        });
        favoriteFilter.setOnClickListener(v -> {
            onlyFavorites = !onlyFavorites;
            favoriteFilter.setText(onlyFavorites ? "★ Favoritos" : "☆ Favoritos");
            applyFilter();
        });
        backRegions.setOnClickListener(v -> showCatalogs());
        diagnostics.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        web.setOnClickListener(v -> startActivity(new Intent(this, OfficialWebActivity.class)));
        home.setOnClickListener(v -> finish());

        showCatalogs();
    }

    private boolean isAcceptKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    }

    private boolean isFavoriteKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        return keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_BOOKMARK
                || keyCode == KeyEvent.KEYCODE_PROG_YELLOW
                || keyCode == KeyEvent.KEYCODE_STAR;
    }

    private void hideKeyboardAndFocusGrid() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && searchInput != null) {
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {}
        try { searchInput.clearFocus(); } catch (Throwable ignored) {}
        if (channelGrid != null && !visibleChannels.isEmpty()) {
            channelGrid.postDelayed(() -> {
                try {
                    channelGrid.setSelection(0);
                    channelGrid.requestFocus();
                } catch (Throwable ignored) {}
            }, 80);
        }
    }

    private void showCatalogs() {
        currentCatalog = null;
        currentSource = null;
        onlyFavorites = false;
        allChannels.clear();
        filteredChannels.clear();
        visibleChannels.clear();
        currentPage = 0;
        channelAdapter.notifyDataSetChanged();
        searchInput.setText("");
        searchInput.clearFocus();

        channelArea.setVisibility(View.GONE);
        catalogGrid.setVisibility(View.VISIBLE);
        loadingBar.setVisibility(View.GONE);
        statusText.setText("Elige una región. Las fichas de canales usan texto compacto para ahorrar memoria en el MX10.");
        catalogGrid.postDelayed(() -> {
            try {
                catalogGrid.setSelection(0);
                catalogGrid.requestFocus();
            } catch (Throwable ignored) {}
        }, 120);
    }

    private void openCatalog(ChannelRepository.Catalog catalog) {
        currentCatalog = catalog;
        currentSource = ChannelRepository.Source.firstFor(catalog);
        onlyFavorites = false;
        favoriteFilter.setText("☆ Favoritos");
        sectionTitle.setText(catalog.label);
        searchInput.setText("");
        updateSourceButton();

        catalogGrid.setVisibility(View.GONE);
        channelArea.setVisibility(View.VISIBLE);
        loadChannels(false);
    }

    private void updateSourceButton() {
        sourceButton.setText(currentSource == null ? "Fuente" : "Fuente: " + currentSource.label);
    }

    private void loadChannels() { loadChannels(false); }

    private void loadChannels(boolean forceRefresh) {
        if (currentSource == null) return;
        final ChannelRepository.Source requested = currentSource;
        loadingBar.setVisibility(View.VISIBLE);
        statusText.setText("Cargando " + requested.label + "…");
        allChannels.clear();
        filteredChannels.clear();
        visibleChannels.clear();
        currentPage = 0;
        channelAdapter.notifyDataSetChanged();

        repository.load(requested, forceRefresh, new ChannelRepository.Callback() {
            @Override
            public void onLoaded(List<Channel> channels, boolean fromCache, ChannelRepository.Source source) {
                if (source != currentSource) return;
                allChannels.clear();
                allChannels.addAll(channels);
                loadingBar.setVisibility(View.GONE);
                applyFilter();
                if (fromCache) {
                    Toast.makeText(CompatMainActivity.this,
                            "Usando caché de " + source.label + ".", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message, ChannelRepository.Source source) {
                if (source != currentSource) return;
                loadingBar.setVisibility(View.GONE);
                statusText.setText("No se pudo cargar " + source.label + ".");
                Toast.makeText(CompatMainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter() {
        if (currentCatalog == null) return;
        String q = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        filteredChannels.clear();
        for (Channel c : allChannels) {
            String country = ChannelRepository.countryLabel(c).toLowerCase(Locale.ROOT);
            boolean textOk = q.isEmpty()
                    || c.getName().toLowerCase(Locale.ROOT).contains(q)
                    || c.getGroup().toLowerCase(Locale.ROOT).contains(q)
                    || country.contains(q);
            boolean favOk = !onlyFavorites || favorites.isFavorite(c);
            if (textOk && favOk) filteredChannels.add(c);
        }
        currentPage = 0;
        renderPage();
    }

    private int pageCount() {
        if (filteredChannels.isEmpty()) return 1;
        return (filteredChannels.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private void renderPage() {
        int pages = pageCount();
        if (currentPage >= pages) currentPage = pages - 1;
        if (currentPage < 0) currentPage = 0;

        final boolean searchHadFocus = searchInput != null && searchInput.hasFocus();
        visibleChannels.clear();
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filteredChannels.size());
        for (int i = start; i < end; i++) visibleChannels.add(filteredChannels.get(i));
        channelAdapter.notifyDataSetChanged();
        if (searchHadFocus) {
            try { searchInput.requestFocus(); } catch (Throwable ignored) {}
        }

        prevPage.setEnabled(currentPage > 0);
        nextPage.setEnabled(currentPage + 1 < pages);
        pageText.setText("Página " + (currentPage + 1) + "/" + pages);

        String sourceLabel = currentSource == null ? "" : currentSource.label;
        statusText.setText(filteredChannels.size() + " canales · " + sourceLabel
                + (onlyFavorites ? " · favoritos" : "")
                + " · " + visibleChannels.size() + " en pantalla");
        final boolean searchHasFocus = searchInput != null && searchInput.hasFocus();
        if (!visibleChannels.isEmpty() && !searchHasFocus) {
            channelGrid.postDelayed(() -> {
                try {
                    channelGrid.setSelection(0);
                    channelGrid.requestFocus();
                } catch (Throwable ignored) {}
            }, 120);
        }
    }

    private void toggleFavorite(Channel channel) {
        boolean nowFavorite = favorites.toggle(channel);
        Toast.makeText(this, nowFavorite ? "★ Agregado a favoritos" : "☆ Quitado de favoritos", Toast.LENGTH_SHORT).show();
        applyFilter();
    }

    private void openChannel(Channel channel, String trigger) {
        if (channel == null) {
            Toast.makeText(this, "Canal no disponible.", Toast.LENGTH_SHORT).show();
            return;
        }
        String channelUrl = channel.getUrl();
        if (channelUrl == null || channelUrl.trim().isEmpty()) {
            DiagnosticStore.savePlayerLog(this, channel.getName(), channelUrl,
                    "SELECCION sin URL | trigger=" + trigger);
            Toast.makeText(this, "Este canal no tiene una URL reproducible.", Toast.LENGTH_LONG).show();
            return;
        }

        String sourceName = currentSource == null ? "desconocida" : currentSource.label;
        DiagnosticStore.savePlayerLog(this, channel.getName(), channelUrl,
                "SELECCION canal | trigger=" + trigger + " | fuente=" + sourceName
                        + " | region=" + (currentCatalog == null ? "" : currentCatalog.label));
        Toast.makeText(this, "Abriendo " + channel.getName() + "…", Toast.LENGTH_SHORT).show();

        try {
            Intent i = new Intent(this, CompatPlayerActivity.class);
            i.putExtra("name", channel.getName());
            i.putExtra("url", channelUrl);
            i.putExtra("source", sourceName);
            Bundle headers = new Bundle();
            for (Map.Entry<String, String> e : channel.getHeaders().entrySet()) {
                headers.putString(e.getKey(), e.getValue());
            }
            i.putExtra("headers", headers);
            startActivity(i);
            needsReloadAfterPlayer = true;
            releaseCatalogMemoryForPlayer();
        } catch (Throwable t) {
            DiagnosticStore.savePlayerLog(this, channel.getName(), channelUrl,
                    "ERROR abriendo player | " + t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
            Toast.makeText(this, "No se pudo abrir el canal: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void releaseCatalogMemoryForPlayer() {
        try { repository.cancelActiveLoad(); } catch (Throwable ignored) {}
        allChannels.clear();
        filteredChannels.clear();
        visibleChannels.clear();
        channelAdapter.notifyDataSetChanged();
        try {
            channelGrid.setDrawingCacheEnabled(false);
            channelGrid.destroyDrawingCache();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsReloadAfterPlayer && currentSource != null && allChannels.isEmpty()) {
            needsReloadAfterPlayer = false;
            loadChannels(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentCatalog != null) {
            showCatalogs();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        try {
            channelGrid.setDrawingCacheEnabled(false);
            channelGrid.destroyDrawingCache();
            catalogGrid.setDrawingCacheEnabled(false);
            catalogGrid.destroyDrawingCache();
        } catch (Throwable ignored) {}
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && currentCatalog == null) {
            allChannels.clear();
            filteredChannels.clear();
            visibleChannels.clear();
            channelAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            channelGrid.setDrawingCacheEnabled(false);
            channelGrid.destroyDrawingCache();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        if (repository != null) repository.shutdown();
        super.onDestroy();
    }

    private final class CatalogAdapter extends BaseAdapter {
        private final ChannelRepository.Catalog[] catalogs = ChannelRepository.Catalog.values();
        @Override public int getCount() { return catalogs.length; }
        @Override public Object getItem(int position) { return catalogs[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CatalogHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(CompatMainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(20), dp(16), dp(20), dp(16));
                card.setMinimumHeight(dp(104));
                card.setBackgroundResource(R.drawable.bg_category);
                card.setFocusable(true);
                card.setFocusableInTouchMode(true);
                card.setClickable(true);

                TextView title = new TextView(CompatMainActivity.this);
                title.setTextColor(Color.WHITE);
                title.setTextSize(21);
                title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                title.setSingleLine(true);

                TextView subtitle = new TextView(CompatMainActivity.this);
                subtitle.setTextColor(Color.rgb(190, 198, 206));
                subtitle.setTextSize(12);
                subtitle.setMaxLines(2);
                subtitle.setEllipsize(TextUtils.TruncateAt.END);
                subtitle.setPadding(0, dp(5), 0, 0);

                card.addView(title);
                card.addView(subtitle);
                holder = new CatalogHolder(title, subtitle);
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (CatalogHolder) convertView.getTag();
            }

            final ChannelRepository.Catalog catalog = catalogs[position];
            holder.title.setText(catalog.label);
            holder.subtitle.setText(catalog.description);
            convertView.setOnClickListener(v -> openCatalog(catalog));
            convertView.setOnKeyListener((v, keyCode, event) -> {
                if (isAcceptKey(keyCode, event)) {
                    openCatalog(catalog);
                    return true;
                }
                return false;
            });
            return convertView;
        }
    }

    private final class ChannelGridAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleChannels.size(); }
        @Override public Object getItem(int position) { return visibleChannels.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ChannelHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(CompatMainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                card.setMinimumHeight(dp(82));
                card.setBackgroundResource(R.drawable.bg_channel);
                card.setFocusable(true);
                card.setFocusableInTouchMode(true);
                card.setClickable(true);
                card.setLongClickable(true);
                card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

                LinearLayout top = new LinearLayout(CompatMainActivity.this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);

                TextView title = new TextView(CompatMainActivity.this);
                title.setTextColor(Color.WHITE);
                title.setTextSize(15);
                title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView star = new TextView(CompatMainActivity.this);
                star.setTextColor(Color.rgb(245, 196, 0));
                star.setTextSize(22);
                star.setGravity(Gravity.CENTER);
                star.setMinWidth(dp(42));
                star.setMinHeight(dp(36));
                star.setPadding(dp(8), 0, dp(8), 0);
                star.setBackgroundResource(R.drawable.bg_button_dark);
                star.setClickable(true);
                star.setFocusable(false);
                star.setContentDescription("Marcar o quitar favorito");

                TextView subtitle = new TextView(CompatMainActivity.this);
                subtitle.setTextColor(Color.rgb(156, 168, 180));
                subtitle.setTextSize(10);
                subtitle.setSingleLine(true);
                subtitle.setEllipsize(TextUtils.TruncateAt.END);
                subtitle.setPadding(0, dp(5), 0, 0);

                top.addView(title);
                top.addView(star);
                card.addView(top);
                card.addView(subtitle);

                holder = new ChannelHolder(title, subtitle, star);
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (ChannelHolder) convertView.getTag();
            }

            final Channel c = visibleChannels.get(position);
            holder.title.setText(c.getName());
            String country = ChannelRepository.countryLabel(c);
            String group = c.getGroup();
            String meta;
            if (!country.isEmpty() && !group.isEmpty() && !group.equalsIgnoreCase("TV")) meta = country + " · " + group;
            else if (!country.isEmpty()) meta = country;
            else meta = group.isEmpty() ? "TV" : group;
            holder.subtitle.setText(meta);
            holder.star.setText(favorites.isFavorite(c) ? "★" : "☆");

            final Channel boundChannel = c;
            holder.star.setOnClickListener(v -> toggleFavorite(boundChannel));
            convertView.setOnClickListener(v -> openChannel(boundChannel, "card_click"));
            convertView.setOnKeyListener((v, keyCode, event) -> {
                if (isFavoriteKey(keyCode, event)) {
                    toggleFavorite(boundChannel);
                    return true;
                }
                if (isAcceptKey(keyCode, event)) {
                    openChannel(boundChannel, "card_key_" + keyCode);
                    return true;
                }
                return false;
            });
            convertView.setOnLongClickListener(v -> {
                toggleFavorite(boundChannel);
                return true;
            });
            return convertView;
        }
    }

    private static final class CatalogHolder {
        final TextView title;
        final TextView subtitle;
        CatalogHolder(TextView title, TextView subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private static final class ChannelHolder {
        final TextView title;
        final TextView subtitle;
        final TextView star;
        ChannelHolder(TextView title, TextView subtitle, TextView star) {
            this.title = title;
            this.subtitle = subtitle;
            this.star = star;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
