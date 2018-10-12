package pl.piotrserafin.pmt.gps;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;

import com.google.android.things.userdriver.UserDriverManager;
import com.google.android.things.userdriver.location.GnssDriver;

import java.io.IOException;

;
/**
 * Created by pserafin on 10.04.2018.
 */

public class Gps implements AutoCloseable {

    private static final String TAG = Gps.class.getSimpleName();

    private Context context;
    private GnssDriver gnssDriver;
    private GpsSensor gpsSensor;

    public Gps(Context context, String uartName, int baudRate,
                         float accuracy) throws IOException {
        this(context, uartName, baudRate, accuracy, null);
    }

    public Gps(Context context, String uartName, int baudRate,
                         float accuracy, Handler handler) throws IOException {
        GpsSensor gpsSensor = new GpsSensor(uartName, baudRate, handler);
        init(context, gpsSensor, accuracy);
    }

    Gps(Context context, GpsSensor gpsSensor) throws IOException {
        init(context, gpsSensor, gpsSensor.getAccuracy());
    }

    private void init(Context context, GpsSensor sensor, float accuracy) {
        this.context = context.getApplicationContext();
        gpsSensor = sensor;
        gpsSensor.setAccuracy(accuracy);
        gpsSensor.setGpsCallback(gpsCallback);
    }

    private Location lastLocation = new Location(LocationManager.GPS_PROVIDER);
    private GpsCallback gpsCallback = new GpsCallback() {

        @Override
        public void onSatelliteStatusUpdate(boolean active, int satellites) { }

        @Override
        public void onPositionUpdate(long timestamp,
                                        double latitude, double longitude) {
            if (gnssDriver != null) {
                lastLocation.setTime(timestamp);

                // We cannot compute accuracy from NMEA data alone.
                // Assume that a valid fix has the quoted accuracy of the module.
                // Framework requires accuracy in DRMS.
                lastLocation.setAccuracy(gpsSensor.getAccuracy() * 1.2f);
                lastLocation.setLatitude(latitude);
                lastLocation.setLongitude(longitude);

                // Is the lastLocation update ready to send?
                if (lastLocation.hasAccuracy() && lastLocation.getTime() != 0) {
                    gnssDriver.reportLocation(lastLocation);
                }
            }
        }
    };

    public void register() {
        if (gnssDriver == null) {
            UserDriverManager manager = UserDriverManager.getInstance();
            gnssDriver = new GnssDriver();
            manager.registerGnssDriver(gnssDriver);
        }
    }

    public void unregister() {
        if (gnssDriver != null) {
            UserDriverManager manager = UserDriverManager.getInstance();
            manager.unregisterGnssDriver();
            gnssDriver = null;
        }
    }

    @Override
    public void close() throws IOException {
        unregister();
        if (gpsSensor != null) {
            gpsSensor.setGpsCallback(null);
            try {
                gpsSensor.close();
            } finally {
                gpsSensor = null;
            }
        }
    }
}