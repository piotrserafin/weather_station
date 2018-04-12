package pl.piotrserafin.pmt.gps;

/**
 * Created by pserafin on 10.04.2018.
 */

public abstract class GpsCallback {

    public abstract void onSatelliteStatusUpdate(boolean active, int satellites);

    public abstract void onPositionUpdate(long timestamp,
                                          double latitude,
                                          double longitude);
}
