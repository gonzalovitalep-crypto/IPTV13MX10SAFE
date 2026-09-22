package cl.chiletv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class OfficialWebActivity extends Activity {
    private static final String[][] SITES = new String[][]{
            {"Chilevisión", "Señal online oficial", "https://www.chilevision.cl/senal-online"},
            {"TVN", "TVN en vivo", "https://www.tvn.cl/en-vivo"},
            {"Mega", "Señal en vivo oficial", "https://www.mega.cl/senal-en-vivo/"},
            {"Canal 13", "Señal principal en 13Go", "https://www.13.cl/13go-live-c13"},
            {"TV+", "Señal online oficial", "https://www.tvmas.tv/page/en-vivo/"},
            {"La Red", "Sitio oficial; la señal online puede variar", "https://www.lared.cl/"},
            {"Telecanal", "Sitio oficial; señal online según disponibilidad", "https://telecanal.cl/"},
            {"24 Horas", "Señal informativa y señales TVN", "https://www.24horas.cl/envivo"},
            {"T13", "T13 en vivo", "https://www.t13.cl/en-vivo"},
            {"Meganoticias Ahora", "Señal informativa de Megamedia", "https://www.meganoticias.cl/senal-en-vivo/meganoticias/"},
            {"Mega 2", "Segunda señal oficial de Mega", "https://www.mega.cl/senal-en-vivo/senal-mega-2"},
            {"Canal 9 Bío Bío TV", "Señal regional oficial", "https://www.canal9.cl/"},
            {"UCV TV", "TV en directo", "https://ucvtv.cl/"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_official_web);
        LinearLayout list = findViewById(R.id.webChannelList);
        Button back = findViewById(R.id.btnBackWeb);
        back.setOnClickListener(v -> finish());

        for (String[] site : SITES) addSite(list, site[0], site[1], site[2]);
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private void addSite(LinearLayout parent, String name, String description, String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setBackgroundResource(R.drawable.bg_channel);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);
        row.setFocusable(true);
        row.setClickable(true);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(title.getTypeface(), 1);

        TextView subtitle = new TextView(this);
        subtitle.setText(description + " · abre el sitio del canal");
        subtitle.setTextColor(Color.rgb(160, 170, 180));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(3), dp(12), 0);

        textBox.addView(title);
        textBox.addView(subtitle);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(30);

        row.addView(textBox);
        row.addView(arrow);
        row.setOnClickListener(v -> openUrl(url));
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.02f : 1f).scaleY(hasFocus ? 1.02f : 1f).setDuration(100).start();
        });
        parent.addView(row);
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Este TV Stick no tiene un navegador compatible instalado.", Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
