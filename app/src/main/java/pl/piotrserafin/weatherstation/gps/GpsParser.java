package pl.piotrserafin.weatherstation.gps;

import android.text.format.DateFormat;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by pserafin on 10.04.2018.
 */

class GpsParser {

    private static final String TAG = GpsParser.class.getSimpleName();

    // From https://en.wikipedia.org/wiki/NMEA_0183
    private static final byte START_DELIMITER = 0x24;
    private static final byte END_DELIMITER = 0x0D;
    private static final byte CHECKSUM_DELIMITER = 0x2A;
    private static final String FIELD_DELIMITER = ",";

    private static final String GGA = "GPGGA";
    private static final String GLL = "GPGLL";
    private static final String RMC = "GPRMC";

    private Calendar timestampCalendar;
    private GpsCallback gpsCallback;

    GpsParser() {
        timestampCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    byte getStartDelimiter() {
        return START_DELIMITER;
    }

    byte getEndDelimiter() {
        return END_DELIMITER;
    }

    void setGpsCallback(GpsCallback callback) {
        gpsCallback = callback;
    }

    void parseMessage(byte[] buffer) {

        // Check buffer
        if (buffer == null || buffer.length < 1) {
            Log.w(TAG, "Buffer invalid");
            return;
        }

        // Check checksum
        int index = validateChecksum(buffer);
        if (index < 0) {
            Log.w(TAG, "Checksum invalid");
            return;
        }

        // Parse the message based on type
        String nmea = new String(buffer, 0, index);

        //Log.d(TAG, "NMEA MSG: " + nmea);

        String[] tokens = nmea.split(FIELD_DELIMITER);
        switch (tokens[0]) {
            case GGA:
                handleGgaMsg(tokens);
                break;
            case GLL:
                handleGllMsg(tokens);
                break;
            case RMC:
                handleRmcMsg(tokens);
                break;
            default:
                // Ignore the message
        }
    }

    private int validateChecksum(byte[] buffer) {
        int index = 0;
        int messageSum = buffer[index++];
        while (index < buffer.length) {
            if (buffer[index] == CHECKSUM_DELIMITER) {
                break;
            }

            messageSum ^= buffer[index++];
        }

        // Index is pointing to checksum start
        if (index >= (buffer.length - 2)) {
            Log.w(TAG, "Checksum missing from incoming message");
            return -1;
        }

        int checkSum = convertAsciiByte(buffer[index+1], buffer[index+2]);
        if (messageSum != checkSum) {
            Log.w(TAG, "Checksum invalid (" + messageSum + "), expected " + checkSum);;
            return -1;
        }

        return index;
    }

    private void handleGgaMsg(String[] nmea) {

        if (nmea.length < 12) {
            Log.w(TAG, "Invalid GGA_MSG length");
            return;
        }

        int quality = Integer.parseInt(nmea[6]);
        int satelliteCount = Integer.parseInt(nmea[7]);
        postSatelliteStatus(quality > 0, satelliteCount);

        if (quality < 1) {
            // No valid fix
            return;
        }

        long timestamp = getUpdatedTimestamp(nmea[1], null);
        double latitude = parseCoordinate(nmea[2], nmea[3]);
        double longitude = parseCoordinate(nmea[4], nmea[5]);
        postPosition(timestamp, latitude, longitude);
    }

    private void handleGllMsg(String[] nmea) {

        if (nmea.length < 7) {
            Log.w(TAG, "Invalid GLL_MSG length");
            return;
        }

        String status = nmea[6];
        if (status.contains("V")) {
            // No valid fix
            return;
        }

        long timestamp = getUpdatedTimestamp(nmea[5], null);
        double latitude = parseCoordinate(nmea[1], nmea[2]);
        double longitude = parseCoordinate(nmea[3], nmea[4]);
        postPosition(timestamp, latitude, longitude);
    }

    private void handleRmcMsg(String[] nmea) {

        if (nmea.length < 11) {
            Log.w(TAG, "Invalid RMC_MSG length");
            return;
        }

        String status = nmea[2];
        if (status.contains("V")) {
            // No valid fix
            return;
        }

        long timestamp = getUpdatedTimestamp(nmea[1], nmea[9]);
        double latitude = parseCoordinate(nmea[3], nmea[4]);
        double longitude = parseCoordinate(nmea[5], nmea[6]);
        postPosition(timestamp, latitude, longitude);
    }

    private void postSatelliteStatus(boolean active, int satellites) {
        if (gpsCallback != null) {
            gpsCallback.onSatelliteStatusUpdate(active, satellites);
        }
    }

    private void postPosition(long timestamp, double latitude, double longitude) {
        if (gpsCallback != null) {
            gpsCallback.onPositionUpdate(timestamp, latitude, longitude);
        }
    }

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("ddMMyyHHmmss", Locale.US);
    private long getUpdatedTimestamp(String timeString, String dateString) {
        if (timeString.length() < 6) {
            // Invalid time
            return -1;
        }
        if (dateString != null && dateString.length() < 6) {
            // Invalid date
            return -1;
        }


        try {
            // Use last known date if not supplied
            if (dateString == null) {
                dateString = DateFormat.format("ddMMyy", timestampCalendar).toString();
            }
            // Truncate milliseconds
            int pointIndex = timeString.indexOf('.');
            if (pointIndex != -1) {
                timeString = timeString.substring(0, pointIndex);
            }

            Date date = FORMAT.parse(dateString+timeString);
            timestampCalendar.setTime(date);
            return timestampCalendar.getTimeInMillis();
        } catch (ParseException e) {
            // Default to current time
            return System.currentTimeMillis();
        }
    }

    private double parseCoordinate(String degreeString, String hemisphere) {
        if (degreeString.isEmpty() || hemisphere.isEmpty()) {
            // No data
            return -1;
        }

        // Two digits left of decimal to the end are the minutes
        int index = degreeString.indexOf('.') - 2;
        if (index < 0) {
            // Invalid string
            return -1;
        }

        // Parse full degrees
        try {
            double value = Double.parseDouble(degreeString.substring(0, index));
            // Append the minutes
            value += Double.parseDouble(degreeString.substring(index)) / 60.0;

            // Compensate for the hemisphere
            if (hemisphere.contains("W") || hemisphere.contains("S")) {
                value *= -1;
            }

            return value;
        } catch (NumberFormatException e) {
            // Invalid value
            return -1;
        }
    }

    private int convertAsciiByte(byte msb, byte lsb) {
        return (getHexDigit(msb) << 4) | getHexDigit(lsb);
    }

    private byte getHexDigit(byte b) {
        if (b >= 0x30 && b <= 0x39) { // 0-9
            return (byte) (b - 0x30);
        }
        if (b >= 0x41 && b <= 0x46) { // A-F
            return (byte) (b - 0x37);
        }
        return (byte) 0x00; //NULL
    }
}