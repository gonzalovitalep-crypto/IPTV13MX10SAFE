package cl.chiletv.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompatMainActivity extends Activity {
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> visibleChannels = new ArrayList<>();
    private ChannelRepository repository;
    private FavoritesStore favorites;
    private CompatAdapter adapter;
    private ListView listView;
    private EditText searchInput;
    private TextView statusText;
    private ProgressBar loadingBar;
    private Button favoriteFilter;
    private boolean onlyFavorites = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compat_main);

        repository = new ChannelRepository(this);
        favorites = new FavoritesStore(this);
        listView = findViewById(R.id.compatChannelList);
        searchInput = findViewById(R.id.compatSearchInput);
        statusText = findViewById(R.id.compatStatusText);
        loadingBar = findViewById(R.id.compatLoadingBar);
        favoriteFilter = findViewById(R.id.btnCompatFavorites);
        Button refresh = findViewById(R.id.btnCompatRefresh);
        Button diagnostics = findViewById(R.id.btnCompatDiagnostics);
        Button web = findViewById(R.id.btnCompatWeb);
        Button home = findViewById(R.id.btnCompatHome);

        adapter = new CompatAdapter();
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener((parent, view, position, id) -> openChannel(visibleChannels.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Channel c = visibleChannels.get(position);
            boolean nowFavorite = favorites.toggle(c);
            Toast.makeText(this, nowFavorite ? "Agregado a favoritos" : "Quitado de favoritos", Toast.LENGTH_SHORT).show();
            applyFilter();
            return true;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        refresh.setOnClickListener(v -> loadChannels());
        favoriteFilter.setOnClickListener(v -> {
            onlyFavorites = !onlyFavorites;
            favoriteFilter.setText(onlyFavorites ? "★ Favoritos" : "☆ Favoritos");
            applyFilter();
        });
        diagnostics.setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        web.setOnClickListener(v -> startActivity(new Intent(this, OfficialWebActivity.class)));
        home.setOnClickListener(v -> finish());

        searchInput.clearFocus();
        loadChannels();
    }

    private void loadChannels() {
        loadingBar.setVisibility(View.VISIBLE);
        statusText.setText("Cargando canales de Chile…");
        repository.load(new ChannelRepository.Callback() {
            @Override
            public void onLoaded(List<Channel> channels, boolean fromCache) {
                allChannels.clear();
                allChannels.addAll(channels);
                loadingBar.setVisibility(View.GONE);
                applyFilter();
                if (fromCache) Toast.makeText(CompatMainActivity.this, "Usando lista guardada.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                loadingBar.setVisibility(View.GONE);
                statusText.setText("No se pudo cargar la lista.");
                Toast.makeText(CompatMainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter() {
        String q = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleChannels.clear();
        for (Channel c : allChannels) {
            boolean textOk = q.isEmpty()
                    || c.getName().toLowerCase(Locale.ROOT).contains(q)
                    || c.getGroup().toLowerCase(Locale.ROOT).contains(q);
            boolean favOk = !onlyFavorites || favorites.isFavorite(c);
            if (textOk && favOk) visibleChannels.add(c);
        }
        adapter.notifyDataSetChanged();
        statusText.setText(visibleChannels.size() + " canales" + (onlyFavorites ? " favoritos" : ""));
        if (!visibleChannels.isEmpty()) {
            listView.postDelayed(() -> {
                try {
                    listView.setSelection(0);
                    listView.requestFocus();
                } catch (Throwable ignored) {}
            }, 150);
        }
    }

    private void openChannel(Channel channel) {
        Intent i = new Intent(this, CompatPlayerActivity.class);
        i.putExtra("name", channel.getName());
        i.putExtra("url", channel.getUrl());
        Bundle headers = new Bundle();
        for (Map.Entry<String, String> e : channel.getHeaders().entrySet()) headers.putString(e.getKey(), e.getValue());
        i.putExtra("headers", headers);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        if (repository != null) repository.shutdown();
        super.onDestroy();
    }

    private final class CompatAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleChannels.size(); }
        @Override public Object getItem(int position) { return visibleChannels.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(CompatMainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(12), dp(18), dp(12));
                row.setMinimumHeight(dp(66));
                row.setBackgroundResource(R.drawable.bg_channel);
                row.setFocusable(true);

                LinearLayout textBox = new LinearLayout(CompatMainActivity.this);
                textBox.setOrientation(LinearLayout.VERTICAL);
                textBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView title = new TextView(CompatMainActivity.this);
                title.setTextColor(Color.WHITE);
                title.setTextSize(18);
                title.setSingleLine(true);

                TextView subtitle = new TextView(CompatMainActivity.this);
                subtitle.setTextColor(Color.rgb(160, 170, 180));
                subtitle.setTextSize(12);
                subtitle.setSingleLine(true);

                TextView star = new TextView(CompatMainActivity.this);
                star.setTextColor(Color.rgb(255, 203, 5));
                star.setTextSize(24);
                star.setPadding(dp(14), 0, 0, 0);

                textBox.addView(title);
                textBox.addView(subtitle);
                row.addView(textBox);
                row.addView(star);

                holder = new Holder(title, subtitle, star);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (Holder) convertView.getTag();
            }

            Channel c = visibleChannels.get(position);
            holder.title.setText(c.getName());
            holder.subtitle.setText(c.getGroup().isEmpty() ? "Chile" : c.getGroup());
            holder.star.setText(favorites.isFavorite(c) ? "★" : "☆");
            return convertView;
        }
    }

    private static final class Holder {
        final TextView title;
        final TextView subtitle;
        final TextView star;
        Holder(TextView title, TextView subtitle, TextView star) {
            this.title = title;
            this.subtitle = subtitle;
            this.star = star;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
