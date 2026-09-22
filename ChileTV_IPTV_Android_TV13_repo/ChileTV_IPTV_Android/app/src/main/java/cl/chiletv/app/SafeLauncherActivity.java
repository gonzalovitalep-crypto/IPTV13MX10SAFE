package cl.chiletv.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class SafeLauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_launcher);

        TextView device = findViewById(R.id.deviceSummary);
        Button openTv = findViewById(R.id.btnOpenCompatTv);
        Button diagnostics = findViewById(R.id.btnOpenDiagnostics);
        Button web = findViewById(R.id.btnOpenWeb);

        String abi = "?";
        try {
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) abi = Build.SUPPORTED_ABIS[0];
        } catch (Throwable ignored) {}

        device.setText("Modelo: " + safe(Build.MODEL)
                + "  ·  Android: " + safe(Build.VERSION.RELEASE)
                + "  ·  API: " + Build.VERSION.SDK_INT
                + "  ·  ABI: " + abi
                + "\nBuild: " + safe(Build.DISPLAY));

        openTv.setOnClickListener(v -> safeStart(CompatMainActivity.class));
        diagnostics.setOnClickListener(v -> safeStart(DiagnosticsActivity.class));
        web.setOnClickListener(v -> safeStart(OfficialWebActivity.class));

        openTv.requestFocus();
    }

    private void safeStart(Class<?> activityClass) {
        try {
            startActivity(new Intent(this, activityClass));
        } catch (Throwable t) {
            Toast.makeText(this, "No se pudo abrir: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private static String safe(String value) {
        return value == null ? "?" : value;
    }
}
