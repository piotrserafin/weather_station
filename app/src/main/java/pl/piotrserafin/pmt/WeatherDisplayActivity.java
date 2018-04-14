package pl.piotrserafin.pmt;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;

import pl.piotrserafin.pmt.api.OpenWeatherApiClient;
import pl.piotrserafin.pmt.gps.Gps;
import pl.piotrserafin.pmt.lcd.Lcd;
import pl.piotrserafin.pmt.model.WeatherData;
import pl.piotrserafin.pmt.utils.RpiSettings;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by pserafin on 20.03.2018.
 */

public class WeatherDisplayActivity extends Activity {

    private static final String TAG = WeatherDisplayActivity.class.getSimpleName();

    public static final int UART_BAUD = 9600;
    public static final float ACCURACY = 2.5f;

    //Test Data
    public static final String WROCLAW_CITY_ID = "3081368"; //Wroclaw OW Id
    public static final String WROCLAW_LATITUDE = "51.099998";
    public static final String WROCLAW_LONGITUDE = "17.033331";

    private Lcd lcd;
    private Gps gps;

    private ButtonInputDriver button;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Call<WeatherData> openWeatherCall = OpenWeatherApiClient.getInstance().getCurrentWeather(WROCLAW_CITY_ID);
        Callback<WeatherData> moviesCallback = new Callback<WeatherData>() {
            @Override
            public void onResponse(Call<WeatherData> openWeatherCall, Response<WeatherData> response) {
                if (!response.isSuccessful()) {
                    Log.d(TAG, "onResponse: Failure");
                    return;
                }
                //TODO: Handel response
                Log.d(TAG,response.body().getWeather().get(0).getDescription());
            }

            @Override
            public void onFailure(Call<WeatherData> openWeatherCall, Throwable t) {
                //TODO: Handle Failure
                Log.d(TAG, "Failure");
            }
        };
        //asynchronous call
        openWeatherCall.enqueue(moviesCallback);

        initLcd();
        //initGps();
        initButton();
    }

    private void initButton() {
        try {
            Log.i(TAG, "Registering button driver " + RpiSettings.getButtonGpioName());

            button = new ButtonInputDriver(
                    RpiSettings.getButtonGpioName(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_SPACE);

            button.register();

        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {

            Log.d(TAG, "onKeyDown");
            // Turn on the LED
            setLcdMessage("Key Pressed");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {

            Log.d(TAG, "onKeyUp");
            // Turn off the LED
            setLcdMessage("Key Released");
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    private void setLcdMessage(String message) {
        try {
            lcd.clearDisplay();
            lcd.returnHome();
            lcd.setText(message);

        } catch (IOException e) {
            Log.e(TAG, "Exception: " + e.getMessage());
        }
    }

    private void initGps() {

        Log.d(TAG, "initGps");

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // We need permission to get location updates
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.d(TAG, "No permission");
            return;
        }

        try {
            gps = new Gps (this, RpiSettings.getUartName(), UART_BAUD, ACCURACY);
        } catch (IOException e) {
            Log.w(TAG, "Unable to open Gps", e);
        }

        gps.register();

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                1000, 0, locationListener);
    }

    private void initLcd() {

        Log.d(TAG, "initLcd");

        try {

            lcd = new Lcd();
           // lcd.clearDisplay();

        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            Log.d(TAG, "GPS: (" + String.valueOf(latitude) + " " + String.valueOf(longitude) + ")");

            try {
                lcd.clearDisplay();
                lcd.setPosition(1, 1);
                lcd.setText(String.valueOf(latitude));
                lcd.setPosition(2, 1);
                lcd.setText(String.valueOf(longitude));
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(TAG, provider);
            Log.d(TAG, "Status: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG, provider);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG, provider);
        }
    };

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop called.");
        if (button != null) {
            button.unregister();
            try {
                Log.d(TAG, "Unregistering button");
                button.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing Button driver", e);
            } finally{
                button = null;
            }
        }
        // Verify permission was granted
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No permission");
            return;
        }

        if (gps != null) {
            // Unregister components
            gps.unregister();
            locationManager.removeUpdates(locationListener);

            try {
                gps.close();
            } catch (IOException e) {
                Log.w(TAG, "Unable to close GPS driver", e);
            }
        }

        try {
            if (lcd != null) {
                lcd.close();
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
