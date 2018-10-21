package pl.piotrserafin.weatherstation;


import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.google.android.things.iotcore.ConnectionCallback;
import com.google.android.things.iotcore.IotCoreClient;
import com.google.android.things.iotcore.TelemetryEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import pl.piotrserafin.weatherstation.iotcore.MessagePayload;
import pl.piotrserafin.weatherstation.iotcore.Parameters;
import pl.piotrserafin.weatherstation.model.SensorData;
import pl.piotrserafin.weatherstation.sensor.SensorCollector;
import pl.piotrserafin.weatherstation.utils.AuthKeyGenerator;
import pl.piotrserafin.weatherstation.utils.TimerHelper;
import timber.log.Timber;

public class SensorHub {
    private static final String TAG = "sensorhub";

    private static final int DEFAULT_TELEMETRY_PER_HOUR = 60*6; // every 10 seconds
    private static final int DEFAULT_STATE_UPDATES_PER_HOUR = 60; // every minute

    private HandlerThread backgroundThread;
    private Handler eventsHandler;
    private Handler recurrentTasksHandler;

    private int configurationVersion;

    private int telemetryEventsPerHour;
    private int stateUpdatesPerHour;

    private long lastTelemetryRun;
    private long lastStateUpdateRun;

    private List<SensorCollector> collectors;

    private Parameters params;
    private IotCoreClient iotCoreClient;

    private AtomicBoolean ready;

    public SensorHub(Parameters params) {
        this.ready =  new AtomicBoolean(false);
        this.configurationVersion = 0;
        this.telemetryEventsPerHour = DEFAULT_TELEMETRY_PER_HOUR;
        this.stateUpdatesPerHour = DEFAULT_STATE_UPDATES_PER_HOUR;
        this.params = params;
        this.collectors = new ArrayList<>();
    }

    public void registerSensorCollector(@NonNull SensorCollector collector) {
        collectors.add(collector);
    }

    public void start() throws GeneralSecurityException, IOException {
        initializeIfNeeded();

        backgroundThread = new HandlerThread("IotCoreThread");
        backgroundThread.start();
        eventsHandler = new Handler(backgroundThread.getLooper());
        recurrentTasksHandler = new Handler(backgroundThread.getLooper());

        recurrentTasksHandler.post(recurrentTelemetryPublisher);
        recurrentTasksHandler.post(stateUpdatePublisher);
    }

    public void stop() {
        Timber.d("Stop SensorHub");
        backgroundThread.quitSafely();
        closeCollectors();
        iotCoreClient.disconnect();
    }

    private void initializeIfNeeded() {
        ready.set(false);
        AuthKeyGenerator keyGenerator = null;
        try {
            keyGenerator = new AuthKeyGenerator(params.getKeyAlgorithm());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Cannot create a key generator", e);
        }
        iotCoreClient = new IotCoreClient.Builder()
                .setConnectionParams(params.getConnectionParams())
                .setKeyPair(keyGenerator.getKeyPair())
                .setConnectionCallback(new ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        Timber.d("Connected to IoT Core");
                        ready.set(true);
                    }

                    @Override
                    public void onDisconnected(int i) {
                        Timber.d("Disconnected from IoT Core");
                    }
                })
                .setOnConfigurationListener(this::onConfigurationReceived)
                .build();
        connectIfNeeded();
    }

    private void connectIfNeeded() {
        if (iotCoreClient != null && !iotCoreClient.isConnected()) {
            iotCoreClient.connect();
        }
    }

    private void onConfigurationReceived(byte[] bytes) {
        if (bytes.length == 0) {
            Timber.w("Ignoring empty device config event");
            return;
        }
        MessagePayload.DeviceConfig deviceConfig = MessagePayload.parseDeviceConfigPayload(
                new String(bytes));
        if (deviceConfig.version <= configurationVersion) {
            Timber.w("Ignoring device config message with old version. Current version: " +
                    configurationVersion + ", Version received: " + deviceConfig.version);
            return;
        }
        Timber.i("Applying device config: %s", deviceConfig);
        configurationVersion = deviceConfig.version;

        recurrentTasksHandler.post(() -> {
            reconfigure(deviceConfig);
        });
    }

    private void reconfigure(MessagePayload.DeviceConfig deviceConfig) {
        telemetryEventsPerHour = deviceConfig.telemetryEventsPerHour;
        stateUpdatesPerHour = deviceConfig.stateUpdatesPerHour;

        HashSet<String> toEnable = new HashSet<>(Arrays.asList(deviceConfig.activeSensors));

        for (SensorCollector collector: collectors) {
            for (String sensor: collector.getAvailableSensors()) {
                boolean enable = toEnable.remove(sensor);
                collector.setEnabled(sensor, enable);
            }
        }

        if (!toEnable.isEmpty()) {
            Timber.w("Ignoring unknown sensors in device config active-sensors: %s", toEnable);
        }

        // reconfigure recurrent tasks:
        recurrentTasksHandler.removeCallbacks(recurrentTelemetryPublisher);
        recurrentTasksHandler.removeCallbacks(stateUpdatePublisher);
        scheduleNextSensorCollection();
        scheduleNextStatusUpdate();
    }

    private void processSensorEvent(SensorData event) {
        if (eventsHandler == null) {
            Timber.i("Ignoring event because the background handler is " +
                    "not running (has the event thread been initiated yet?). Event: " +
                    event);
            return;
        }
        eventsHandler.post(() -> publishTelemetry(Collections.singletonList(event)));
    }

    private void publishTelemetry(List<SensorData> currentReadings) {
        String payload = MessagePayload.createTelemetryMessagePayload(currentReadings);
        Timber.d("Publishing telemetry: %s", payload);
        if (iotCoreClient == null) {
            Timber.w("Ignoring sensor readings because IotCoreClient is not yet active.");
            return;
        }

        TelemetryEvent event = new TelemetryEvent(payload.getBytes(),
                null, TelemetryEvent.QOS_AT_LEAST_ONCE);
        iotCoreClient.publishTelemetry(event);
    }

    private void publishDeviceState() {
        List<String> activeSensors = new ArrayList<>();
        List<String> allSensors = new ArrayList<>();
        for (SensorCollector collector: collectors) {
            allSensors.addAll(collector.getAvailableSensors());
            activeSensors.addAll(collector.getEnabledSensors());
        }
        String payload = MessagePayload.createDeviceStateUpdatePayload(
                configurationVersion, telemetryEventsPerHour, stateUpdatesPerHour,
                allSensors, activeSensors);
        Timber.d("Publishing device state: %s", payload);
        if (iotCoreClient == null) {
            Timber.w("Refusing to publishTelemetry device state because IotCoreClient is not yet active.");
            return;
        }
        iotCoreClient.publishDeviceState(payload.getBytes());
    }

    private List<SensorData> collectCurrentSensorsReadings() {
        List<SensorData> sensorsData = new ArrayList<>();
        for (SensorCollector collector: collectors) {
            try {
                collector.activate();
                collector.collectRecentReadings(sensorsData);
            } catch (Throwable t) {
                Timber.e(t,"Cannot collect recent readings of " +
                        collector.getAvailableSensors() + ", will try again in the next run.");
            }
        }
        Timber.d("collected sensor data: %s", sensorsData);
        return sensorsData;
    }

    private void closeCollectors() {
        for (SensorCollector collector: collectors) {
            collector.closeQuietly();
        }
    }

    private void scheduleNextSensorCollection() {
        long nextRun = TimerHelper.calculateNextRun(telemetryEventsPerHour, lastTelemetryRun);
        recurrentTasksHandler.postAtTime(recurrentTelemetryPublisher, nextRun);
    }

    private void scheduleNextStatusUpdate() {
        long nextRun = TimerHelper.calculateNextRun(stateUpdatesPerHour, lastStateUpdateRun);
        recurrentTasksHandler.postAtTime(stateUpdatePublisher, nextRun);
    }

    private final Runnable recurrentTelemetryPublisher = new Runnable() {
        @Override
        public void run() {
            lastTelemetryRun = SystemClock.uptimeMillis();
            connectIfNeeded();
            if (TimerHelper.canExecute("Telemetry loop", ready.get())) {
                try {
                    List<SensorData> currentReadings = collectCurrentSensorsReadings();
                    publishTelemetry(currentReadings);
                } catch (Throwable t) {
                    Timber.e(t, "Cannot publish recurrent telemetry events, will try again later");
                }
            }
            scheduleNextSensorCollection();
        }
    };

    private final Runnable stateUpdatePublisher = new Runnable() {
        @Override
        public void run() {
            lastStateUpdateRun = SystemClock.uptimeMillis();
            connectIfNeeded();
            if (TimerHelper.canExecute("State update loop", ready.get())) {
                try {
                    publishDeviceState();
                } catch (Throwable t) {
                    Timber.e(t,"Cannot publish device state, will try again later");
                }
            }
            scheduleNextStatusUpdate();
        }
    };
}
