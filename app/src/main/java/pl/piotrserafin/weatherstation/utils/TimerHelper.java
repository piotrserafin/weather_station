package pl.piotrserafin.weatherstation.utils;

import java.util.Calendar;

import timber.log.Timber;

public class TimerHelper {

    private static final long INITIAL_VALID_TIMESTAMP;
    static {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2016, 1, 1);
        INITIAL_VALID_TIMESTAMP = calendar.getTimeInMillis();
    }

    public static long calculateNextRun(long eventsPerHour, long lastRun) {
        return lastRun + 60*60*1000L/eventsPerHour;
    }

    public static boolean canExecute(String loopType, boolean isReady) {
        long clockTime = System.currentTimeMillis();
        if (clockTime < INITIAL_VALID_TIMESTAMP) {
            Timber.d(loopType + " ignored because timestamp is invalid. " +
                    "Please, set the device's date/time");
            return false;
        } else if (!isReady) {
            Timber.d("%s ignored because IotCoreClient is not yet connected", loopType);
            return false;
        }
        return true;
    }
}
