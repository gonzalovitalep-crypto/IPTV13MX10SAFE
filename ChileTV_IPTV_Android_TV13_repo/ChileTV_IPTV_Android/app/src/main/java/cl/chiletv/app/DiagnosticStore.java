package cl.chiletv.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DiagnosticStore {
    private static final String PREFS = "diagnostics";
    private static final String KEY_PLAYER = "last_player_log";

    private DiagnosticStore() {}

    public static void savePlayerLog(Context context, String channel, String url, String message) {
        try {
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String value = now + "\nCanal: " + safe(channel) + "\nURL: " + safe(url) + "\n" + safe(message);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PLAYER, value).apply();
            File file = new File(context.getFilesDir(), "last_player.txt");
            FileOutputStream out = new FileOutputStream(file, false);
            try { out.write(value.getBytes(StandardCharsets.UTF_8)); } finally { try { out.close(); } catch (Throwable ignored) {} }
        } catch (Throwable ignored) {}
    }

    public static void clear(Context context) {
        try { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply(); } catch (Throwable ignored) {}
        try {
            File crash = new File(context.getFilesDir(), "last_crash.txt");
            if (crash.exists()) crash.delete();
            File player = new File(context.getFilesDir(), "last_player.txt");
            if (player.exists()) player.delete();
        } catch (Throwable ignored) {}
    }

    public static String buildReport(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("=== CHILE TV IPTV DEBUG MX10 ULTRA SAFE ===\n");
        out.append("Fecha: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n');
        out.append("App: ").append(appVersion(context)).append('\n');
        out.append("Android reportado: ").append(safe(Build.VERSION.RELEASE)).append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("Security patch: ").append(readSecurityPatch()).append('\n');
        out.append("Fabricante: ").append(safe(Build.MANUFACTURER)).append('\n');
        out.append("Marca: ").append(safe(Build.BRAND)).append('\n');
        out.append("Modelo: ").append(safe(Build.MODEL)).append('\n');
        out.append("Device: ").append(safe(Build.DEVICE)).append('\n');
        out.append("Product: ").append(safe(Build.PRODUCT)).append('\n');
        out.append("Board: ").append(safe(Build.BOARD)).append('\n');
        out.append("Hardware: ").append(safe(Build.HARDWARE)).append('\n');
        out.append("Display: ").append(safe(Build.DISPLAY)).append('\n');
        out.append("Fingerprint: ").append(safe(Build.FINGERPRINT)).append('\n');
        out.append("ABIs: ").append(abis()).append('\n');
        out.append("Red: ").append(networkSummary(context)).append('\n');
        out.append("Memoria app: ").append(memoryClass(context)).append(" MB\n");
        out.append("Heap Java: ").append(heapSummary()).append("\n");
        out.append("Caché app: ").append(cacheSummary(context)).append("\n");
        out.append("Decodificadores: ").append(codecSummary()).append("\n\n");

        out.append("--- ULTIMAS SALIDAS DEL PROCESO ---\n");
        out.append(exitHistoryReflective(context)).append("\n\n");

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        out.append("--- ULTIMO EVENTO DEL PLAYER ---\n");
        String playerFile = readFile(new File(context.getFilesDir(), "last_player.txt"), "");
        if (playerFile == null || playerFile.trim().isEmpty()) {
            playerFile = prefs.getString(KEY_PLAYER, "Sin eventos guardados.");
        }
        out.append(playerFile).append("\n\n");

        out.append("--- ULTIMO CRASH JAVA ---\n");
        out.append(readFile(new File(context.getFilesDir(), "last_crash.txt"), "Sin crash Java guardado."));
        return out.toString();
    }

    private static String exitHistoryReflective(Context context) {
        if (Build.VERSION.SDK_INT < 30) return "No disponible antes de Android 11.";
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "ActivityManager no disponible.";
            Method method = ActivityManager.class.getMethod("getHistoricalProcessExitReasons", String.class, int.class, int.class);
            Object value = method.invoke(am, context.getPackageName(), 0, 5);
            if (!(value instanceof List)) return "Historial no disponible.";
            List<?> list = (List<?>) value;
            if (list.isEmpty()) return "Sin salidas registradas.";
            StringBuilder result = new StringBuilder();
            for (Object info : list) {
                try {
                    Class<?> c = info.getClass();
                    long timestamp = ((Number) c.getMethod("getTimestamp").invoke(info)).longValue();
                    int reason = ((Number) c.getMethod("getReason").invoke(info)).intValue();
                    int status = ((Number) c.getMethod("getStatus").invoke(info)).intValue();
                    Object desc = c.getMethod("getDescription").invoke(info);
                    result.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(timestamp)))
                            .append(" | reason=").append(reason)
                            .append(" | status=").append(status);
                    if (desc != null) result.append(" | ").append(desc);
                    result.append('\n');
                } catch (Throwable ignored) {}
            }
            return result.length() == 0 ? "Sin detalles legibles." : result.toString().trim();
        } catch (Throwable t) {
            return "No disponible en este firmware (" + t.getClass().getSimpleName() + ").";
        }
    }

    private static String codecSummary() {
        try {
            MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
            StringBuilder out = new StringBuilder();
            int shown = 0;
            for (MediaCodecInfo info : codecs) {
                if (info.isEncoder()) continue;
                boolean video = false;
                String[] types = info.getSupportedTypes();
                for (String type : types) if (type != null && type.startsWith("video/")) { video = true; break; }
                if (!video) continue;
                if (shown++ > 0) out.append(", ");
                out.append(info.getName());
                if (shown >= 12) { out.append(", …"); break; }
            }
            return shown == 0 ? "sin decodificadores de video detectados" : out.toString();
        } catch (Throwable t) {
            return "no disponible (" + t.getClass().getSimpleName() + ")";
        }
    }

    @SuppressWarnings("deprecation")
    private static String networkSummary(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "desconocida";
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info == null) return "sin red activa";
            return info.getTypeName() + (info.isConnected() ? " conectada" : " no conectada");
        } catch (Throwable t) {
            return "desconocida";
        }
    }

    private static String heapSummary() {
        try {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            return (used / (1024 * 1024)) + " MB usados / "
                    + (rt.maxMemory() / (1024 * 1024)) + " MB máx.";
        } catch (Throwable t) { return "no disponible"; }
    }

    private static String cacheSummary(Context context) {
        try {
            long bytes = dirSize(context.getCacheDir());
            return (bytes / 1024) + " KB";
        } catch (Throwable t) { return "no disponible"; }
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0L;
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File f : files) {
            if (f.isDirectory()) total += dirSize(f); else total += f.length();
        }
        return total;
    }

    private static int memoryClass(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return am == null ? 0 : am.getMemoryClass();
        } catch (Throwable t) { return 0; }
    }

    @SuppressWarnings("deprecation")
    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Throwable e) {
            return "desconocida";
        }
    }

    private static String abis() {
        try { return Arrays.toString(Build.SUPPORTED_ABIS); }
        catch (Throwable t) { return safe(Build.CPU_ABI); }
    }

    private static String readSecurityPatch() {
        try { return Build.VERSION.SECURITY_PATCH; }
        catch (Throwable t) { return "no disponible"; }
    }

    private static String readFile(File file, String fallback) {
        if (!file.exists()) return fallback;
        try (FileInputStream in = new FileInputStream(file)) { return readFully(in); }
        catch (Throwable e) { return fallback + " (" + e.getClass().getSimpleName() + ")"; }
    }

    private static String readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
