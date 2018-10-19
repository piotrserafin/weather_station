package pl.piotrserafin.weatherstation.iotcore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

import pl.piotrserafin.weatherstation.model.SensorData;

public class MessagePayload {

    /**
     * Serialize a List of SensorData objects into a JSON string, for sending to the cloud
     * @param data List of SensorData objects to serialize
     * @return JSON String
     */
    public static String createTelemetryMessagePayload(List<SensorData> data) {
        try {
            JSONObject messagePayload = new JSONObject();
            JSONArray dataArray = new JSONArray();
            for (SensorData el : data) {
                JSONObject sensor = new JSONObject();
                sensor.put("timestamp_" + el.getSensorName(),
                        el.getTimestamp());
                sensor.put(el.getSensorName(), el.getValue());
                dataArray.put(sensor);
            }
            messagePayload.put("data", dataArray);
            return messagePayload.toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid message", e);
        }
    }

    /**
     * Compose and serialize some parameters as a JSON string, for sending to the IotCore as a
     * device state update
     * @return JSON String
     */
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

    /**
     * De-serialize IotCore device configuration message payload as a JSON string.
     *
     * Format of the message should be similar to:
     * <pre>
     * {
     *      "version": 1,
     *      "telemetry-events-per-hour": 20,
     *      "state-updates-per-hour": 10,
     *      "active-sensors": ["motion", "temperature"]
     * }
     * </pre>
     *
     * @param jsonPayload JSON of the device config message
     * @return JSON String
     */
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
