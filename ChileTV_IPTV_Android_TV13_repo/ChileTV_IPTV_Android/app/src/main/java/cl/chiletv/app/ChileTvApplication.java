package cl.chiletv.app;

import android.app.Application;

public class ChileTvApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
    }
}
