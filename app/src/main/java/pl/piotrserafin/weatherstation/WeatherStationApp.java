package pl.piotrserafin.weatherstation;

import android.app.Application;

import timber.log.Timber;

public class WeatherStationApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}
