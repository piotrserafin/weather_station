package pl.piotrserafin.pmt;

import android.app.Application;

import timber.log.Timber;

public class PmtApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}
