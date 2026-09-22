package cl.chiletv.app;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashLogger {
    private static boolean installed = false;
    private static Thread.UncaughtExceptionHandler previous;

    private CrashLogger() {}

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        Context appContext = context.getApplicationContext();
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrash(appContext, thread, throwable);
            } catch (Throwable ignored) {
                // Nunca interferir con el manejador normal de Android.
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    private static void writeCrash(Context context, Thread thread, Throwable throwable) throws Exception {
        StringWriter stack = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stack));

        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        StringBuilder out = new StringBuilder();
        out.append("CHILE TV IPTV - ULTIMO CRASH JAVA\n");
        out.append("Fecha: ").append(now).append('\n');
        out.append("Thread: ").append(thread == null ? "?" : thread.getName()).append('\n');
        out.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("Fabricante: ").append(Build.MANUFACTURER).append('\n');
        out.append("Modelo: ").append(Build.MODEL).append('\n');
        out.append("Device: ").append(Build.DEVICE).append('\n');
        out.append("Hardware: ").append(Build.HARDWARE).append("\n\n");
        out.append(stack);

        File file = new File(context.getFilesDir(), "last_crash.txt");
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(out.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
