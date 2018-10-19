package pl.piotrserafin.weatherstation.sensor;

import java.util.List;

import pl.piotrserafin.weatherstation.model.SensorData;

public interface SensorCollector {
    boolean activate();
    void setEnabled(String sensor, boolean enabled);
    boolean isEnabled(String sensor);
    List<String> getAvailableSensors();
    List<String> getEnabledSensors();
    void collectRecentReadings(List<SensorData> output);
    void closeQuietly();
}
