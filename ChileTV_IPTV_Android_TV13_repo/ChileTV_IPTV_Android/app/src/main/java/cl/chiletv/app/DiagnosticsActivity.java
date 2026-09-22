package cl.chiletv.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class DiagnosticsActivity extends Activity {
    private TextView reportText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        reportText = findViewById(R.id.debugReport);
        Button copy = findViewById(R.id.btnCopyDebug);
        Button clear = findViewById(R.id.btnClearDebug);
        Button back = findViewById(R.id.btnBackDebug);

        refresh();

        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Chile TV IPTV Debug", reportText.getText()));
                Toast.makeText(this, "Debug copiado.", Toast.LENGTH_SHORT).show();
            }
        });

        clear.setOnClickListener(v -> {
            DiagnosticStore.clear(this);
            refresh();
            Toast.makeText(this, "Registro limpiado.", Toast.LENGTH_SHORT).show();
        });

        back.setOnClickListener(v -> finish());
        copy.requestFocus();
    }

    private void refresh() {
        reportText.setText(DiagnosticStore.buildReport(this));
    }
}
