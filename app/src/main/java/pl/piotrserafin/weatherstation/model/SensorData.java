package pl.piotrserafin.weatherstation.model;

public class SensorData {
    private long timestamp;
    private String sensorName;
    private float value;

    public SensorData(String sensorName, float value) {
        this(System.currentTimeMillis(), sensorName, value);
    }

    public SensorData(long timestamp, String sensorName, float value) {
        this.timestamp = timestamp;
        this.sensorName = sensorName;
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSensorName() {
        return sensorName;
    }

    public float getValue() {
        return value;
    }

    @Override
    public String toString() {
        return sensorName + " [" + timestamp + "] " + value;
    }
}
