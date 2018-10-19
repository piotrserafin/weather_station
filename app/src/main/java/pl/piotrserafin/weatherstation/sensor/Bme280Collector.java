package pl.piotrserafin.weatherstation.sensor;

import com.google.android.things.contrib.driver.bmx280.Bmx280;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pl.piotrserafin.weatherstation.model.SensorData;
import timber.log.Timber;

public class Bme280Collector implements SensorCollector {

    private static final String SENSOR_TEMPERATURE = "temperature";
    private static final String SENSOR_HUMIDITY = "humidity";
    private static final String SENSOR_PRESSURE = "pressure";

    private boolean isTemperatureEnabled;
    private boolean isPressureEnabled;
    private boolean isHumidityEnabled;

    private boolean isHumidityAvailable;

    private String i2cBus;
    private Bmx280 bme280;

    public Bme280Collector(String i2cBus) {
        this.i2cBus = i2cBus;
        this.isTemperatureEnabled = true;
        this.isPressureEnabled = true;
        this.isHumidityEnabled = true;
    }

    @Override
    public boolean activate() {
        if (bme280 != null) {
            return true;
        }
        try {
            bme280 = new Bmx280(i2cBus);
            isHumidityAvailable = bme280.hasHumiditySensor();
            setEnabled(SENSOR_TEMPERATURE, isTemperatureEnabled);
            setEnabled(SENSOR_PRESSURE, isPressureEnabled);
            setEnabled(SENSOR_HUMIDITY, isHumidityAvailable && isHumidityEnabled);
            bme280.setMode(Bmx280.MODE_NORMAL);
            Timber.d("BME280 initialized");
            return true;
        } catch (Throwable t) {
            Timber.e(t);
        }
        return false;
    }

    @Override
    public void setEnabled(String sensor, boolean enabled) {
        try {
            int overSampling = enabled ? Bmx280.OVERSAMPLING_1X : Bmx280.OVERSAMPLING_SKIPPED;
            switch (sensor) {
                case SENSOR_TEMPERATURE:
                    if (bme280 != null) {
                        bme280.setTemperatureOversampling(overSampling);
                    }
                    isTemperatureEnabled = enabled;
                    break;
                case SENSOR_PRESSURE:
                    if (bme280 != null) {
                        bme280.setPressureOversampling(overSampling);
                    }
                    isPressureEnabled = enabled;
                    break;
                case SENSOR_HUMIDITY:
                    if (enabled && !isHumidityAvailable) {
                        Timber.i("Humidity sensor not available. Ignoring request to enable it");
                    } else {
                        if (bme280 != null && isHumidityAvailable) {
                            bme280.setHumidityOversampling(overSampling);
                        }
                        isHumidityEnabled = enabled;
                    }
                    break;
                default:
                    Timber.w("Unknown sensor " + sensor + ". Ignoring request");
            }
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    @Override
    public boolean isEnabled(String sensor) {
        switch (sensor) {
            case SENSOR_TEMPERATURE:
                return isTemperatureEnabled;
            case SENSOR_PRESSURE:
                return isPressureEnabled;
            case SENSOR_HUMIDITY:
                return isHumidityAvailable && isHumidityEnabled;
            default:
                Timber.w("Unknown sensor " + sensor + ". Ignoring request");
        }
        return false;
    }

    @Override
    public List<String> getAvailableSensors() {
        List<String> sensors = new ArrayList<>();
        sensors.add(SENSOR_TEMPERATURE);
        sensors.add(SENSOR_PRESSURE);
        if (isHumidityAvailable) {
            sensors.add(SENSOR_HUMIDITY);
        }
        return sensors;
    }

    @Override
    public List<String> getEnabledSensors() {
        List<String> sensors = new ArrayList<>();
        if (isEnabled(SENSOR_TEMPERATURE)) {
            sensors.add(SENSOR_TEMPERATURE);
        }
        if (isEnabled(SENSOR_PRESSURE)) {
            sensors.add(SENSOR_PRESSURE);
        }
        if (isEnabled(SENSOR_HUMIDITY)) {
            sensors.add(SENSOR_HUMIDITY);
        }
        return sensors;
    }

    @Override
    public void collectRecentReadings(List<SensorData> output) {
        if (bme280 == null) {
            return;
        }
        try {
            if (isEnabled(SENSOR_TEMPERATURE) && isEnabled(SENSOR_PRESSURE)) {
                // If both temperature and pressure are enabled, we can read both with a single
                // I2C read, so we will report both values with the same timestamp
                long now = System.currentTimeMillis();
                float[] data = bme280.readTemperatureAndPressure();
                output.add(new SensorData(now, SENSOR_TEMPERATURE, data[0]));
                output.add(new SensorData(now, SENSOR_PRESSURE, data[1]));
            } else if (isEnabled(SENSOR_TEMPERATURE)) {
                float data = bme280.readTemperature();
                output.add(new SensorData(SENSOR_TEMPERATURE, data));
            } else if (isEnabled(SENSOR_PRESSURE)) {
                float data = bme280.readPressure();
                output.add(new SensorData(SENSOR_PRESSURE, data));
            }
            if (isEnabled(SENSOR_HUMIDITY)) {
                output.add(new SensorData(SENSOR_HUMIDITY, bme280.readHumidity()));
            }
        } catch (Throwable t) {
            Timber.e(t);
        }
    }

    @Override
    public void closeQuietly() {
        if (bme280 != null) {
            try {
                bme280.close();
            } catch (IOException e) {
                // close quietly
            }
            bme280 = null;
        }
    }
}
