package pl.piotrserafin.weatherstation;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import pl.piotrserafin.weatherstation.model.SensorData;
import pl.piotrserafin.weatherstation.sensor.Sensor;
import pl.piotrserafin.weatherstation.utils.TimerHelper;
import timber.log.Timber;

public class SensorHub {

    private static final int DEFAULT_TELEMETRY_PER_HOUR = 60*3; // every 20 seconds

    private HandlerThread backgroundThread;
    private Handler recurrentTasksHandler;

    private int telemetryEventsPerHour;

    private long lastTelemetryRun;

    private Sensor sensor;

    private AtomicBoolean ready;

    public SensorHub(Sensor sensor) {
        this.ready =  new AtomicBoolean(false);
        this.telemetryEventsPerHour = DEFAULT_TELEMETRY_PER_HOUR;
        this.sensor = sensor;
    }

    public void start() {

        backgroundThread = new HandlerThread("SensorHub");
        backgroundThread.start();

        recurrentTasksHandler = new Handler(backgroundThread.getLooper());
        recurrentTasksHandler.post(recurrentSensorDataCollector);
    }

    public void stop() {
        Timber.d("Stop SensorHub");
        backgroundThread.quitSafely();
        closeSensors();
    }

    private List<SensorData> collectCurrentSensorsReadings() {
        List<SensorData> sensorsData = new ArrayList<>();

        try {
            sensor.collectData(sensorsData);
        } catch (Throwable t) {
            Timber.e(t);
        }

        Timber.d("collected sensor data: %s", sensorsData);
        return sensorsData;
    }

    private void closeSensors() {
        try {
            sensor.close();
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    private void scheduleNextSensorCollection() {
        long nextRun = TimerHelper.calculateNextRun(telemetryEventsPerHour, lastTelemetryRun);
        recurrentTasksHandler.postAtTime(recurrentSensorDataCollector, nextRun);
    }

    private final Runnable recurrentSensorDataCollector = new Runnable() {
        @Override
        public void run() {
            lastTelemetryRun = SystemClock.uptimeMillis();
            if (TimerHelper.canExecute("Telemetry loop", ready.get())) {
                try {
                    List<SensorData> currentReadings = collectCurrentSensorsReadings();
                    Timber.d(currentReadings.toString());
                } catch (Throwable t) {
                    Timber.e(t);
                }
            }
            scheduleNextSensorCollection();
        }
    };
}
