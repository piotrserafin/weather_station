package pl.piotrserafin.pmt.sensor;

import com.google.android.things.contrib.driver.bmx280.Bmx280;

import java.io.IOException;
import java.util.List;

import timber.log.Timber;

public class EnvironmentalSensor implements AutoCloseable {

    private static final String TEMPERATURE = "temperature";
    private static final String PRESSURE = "pressure";
    private static final String HUMIDITY = "humidity";

    private String i2cBus;
    private Bmx280 bmx280;

    public EnvironmentalSensor(String i2cBus) {
        this.i2cBus = i2cBus;

        try {
            init();
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    private void init() throws IOException {
        if (bmx280 != null) {
            return;
        }
        bmx280 = new Bmx280(i2cBus);
        bmx280.setTemperatureOversampling(Bmx280.OVERSAMPLING_1X);
        bmx280.setPressureOversampling(Bmx280.OVERSAMPLING_1X);
        bmx280.setHumidityOversampling(Bmx280.OVERSAMPLING_1X);
        bmx280.setMode(Bmx280.MODE_NORMAL);
    }

    public void collectData(List<SensorData> sensorData) {
        if (bmx280 == null) {
            return;
        }
        try {
            long timestamp = System.currentTimeMillis();
            float[] data = bmx280.readTemperatureAndPressure();
            sensorData.add(new SensorData(timestamp, TEMPERATURE, data[0]));
            sensorData.add(new SensorData(timestamp, PRESSURE, data[1]));
            sensorData.add(new SensorData(HUMIDITY, bmx280.readHumidity()));

        } catch (Throwable t) {
            Timber.e(t);
        }
    }

    @Override
    public void close() throws Exception {
        if (bmx280 != null) {
            try {
                bmx280.close();
            } finally {
                bmx280 = null;
            }
        }
    }
}
