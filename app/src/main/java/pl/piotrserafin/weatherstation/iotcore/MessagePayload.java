package pl.piotrserafin.weatherstation.iotcore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

import pl.piotrserafin.weatherstation.model.SensorData;

public class MessagePayload {

    public static String createTelemetryMessagePayload(List<SensorData> data) {
        try {
            //JSONObject messagePayload = new JSONObject();
            //JSONArray dataArray = new JSONArray();
            JSONObject sensor = new JSONObject();
            sensor.put("timestamp", data.get(0).getTimestamp());
            for (SensorData el : data) {
                sensor.put(el.getSensorName(), el.getValue());
            }
            //messagePayload.put("data", sensor);
            return sensor.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid message", e);
        }
    }

    public static String createDeviceStateUpdatePayload(int version, int telemetryEventsPerHour,
                                                        int stateUpdatesPerHour, List<String> allSensors, List<String> activeSensors) {
        try {
            JSONObject messagePayload = new JSONObject();
            messagePayload.put("version", version);
            messagePayload.put("telemetry-events-per-hour", telemetryEventsPerHour);
            messagePayload.put("state-updates-per-hour", stateUpdatesPerHour);
            messagePayload.put("sensors", new JSONArray(allSensors));
            messagePayload.put("active-sensors", new JSONArray(activeSensors));
            return messagePayload.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid message", e);
        }
    }

    public static DeviceConfig parseDeviceConfigPayload(String jsonPayload) {
        try {
            JSONObject message = new JSONObject(jsonPayload);
            DeviceConfig deviceConfig = new DeviceConfig();
            deviceConfig.version = message.getInt("version");
            deviceConfig.telemetryEventsPerHour = message.getInt("telemetry-events-per-hour");
            deviceConfig.stateUpdatesPerHour = message.getInt("state-updates-per-hour");
            JSONArray activeSensors = message.getJSONArray("active-sensors");
            deviceConfig.activeSensors = new String[activeSensors.length()];
            for (int i = 0; i < activeSensors.length(); i++) {
                deviceConfig.activeSensors[i] = activeSensors.getString(i);
            }
            return deviceConfig;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid message: \"" + jsonPayload + "\"", e);
        }
    }

    public static class DeviceConfig {
        public int version;
        public int telemetryEventsPerHour;
        public int stateUpdatesPerHour;
        public String[] activeSensors;

        @Override
        public String toString() {
            return "DeviceConfig{" +
                    "version=" + version +
                    ", telemetryEventsPerHour=" + telemetryEventsPerHour +
                    ", stateUpdatesPerHour=" + stateUpdatesPerHour +
                    ", activeSensors=" + Arrays.toString(activeSensors) +
                    '}';
        }
    }
}
