package pl.piotrserafin.weatherstation;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.KeyEvent;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;

import pl.piotrserafin.weatherstation.api.OpenWeatherApiClient;
import pl.piotrserafin.weatherstation.fsm.State;
import pl.piotrserafin.weatherstation.fsm.StateContext;
import pl.piotrserafin.weatherstation.gps.Gps;
import pl.piotrserafin.weatherstation.lcd.Lcd;
import pl.piotrserafin.weatherstation.model.WeatherData;
import pl.piotrserafin.weatherstation.sensor.EnvironmentalSensor;
import pl.piotrserafin.weatherstation.utils.RpiSettings;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Created by pserafin on 20.03.2018.
 */

public class WeatherDisplayActivity extends Activity {

    public static final int UART_BAUD = 9600;
    public static final float ACCURACY = 2.5f;

    //Test Data
    public static final String WROCLAW_CITY_ID = "3081368"; //Wroclaw OW Id
    public static final double WROCLAW_LATITUDE = 51.099998;
    public static final double WROCLAW_LONGITUDE = 17.033331;

    private Lcd lcd;
    private Gps gps;
    private EnvironmentalSensor sensor;

    private Call<WeatherData> openWeatherCall;
    private Callback<WeatherData> moviesCallback;

    private ButtonInputDriver button;
    private LocationManager locationManager;

    private double latitude;
    private double longitude;

    private WeatherData weatherData;

    private StateContext stateContext;

    private boolean testData = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        stateContext = new StateContext(new StateStart());

        //StateStart -> StateInit
        stateContext.takeAction();
    }

    private void initLcd() {

        Timber.d("initLcd");

        try {

            lcd = new Lcd();

        } catch (IOException e) {
            Timber.e(e);
        }
    }

    private void initGps() {

        Timber.d("initGps");

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            gps = new Gps(this, RpiSettings.getUartName(), UART_BAUD, ACCURACY);
        } catch (IOException e) {
            Timber.e(e);
        }

        //StateInit -> StateFetchGpsData
        stateContext.takeAction();
    }

    private void initButton() {
        try {
            Timber.d("Registering button driver %s", RpiSettings.getButtonGpioName());

            button = new ButtonInputDriver(
                    RpiSettings.getButtonGpioName(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_SPACE);

        } catch (IOException e) {
            Timber.d(e);
        }
    }

    private void initWeatherApiClient() {

        moviesCallback = new Callback<WeatherData>() {

            @Override
            public void onResponse(Call<WeatherData> openWeatherCall, Response<WeatherData> response) {
                if (!response.isSuccessful()) {
                    Timber.d("onResponse: Failure");
                    return;
                }
                weatherData = response.body();

                stateContext.takeAction();
            }

            @Override
            public void onFailure(Call<WeatherData> openWeatherCall, Throwable t) {
                setLcdClear();
                setLcdPosition(0,0);
                setLcdMessage("Failed to fetch");
                setLcdPosition(1,0);
                setLcdMessage("Weather Data...");

                stateContext.setState(new StateStop());
                stateContext.takeAction();
            }
        };
    }

    private void startFetchingGpsData() {
        gps.register();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                5000, 0, locationListener);
    }

    private void stopFetchingGpsData() {
        locationManager.removeUpdates(locationListener);
        gps.unregister();
    }

    private void startFetchingWeatherData() {

        openWeatherCall = OpenWeatherApiClient
                .getInstance()
                .getCurrentWeather(
                        Double.toString(latitude),
                        Double.toString(longitude));

        //asynchronous call
        openWeatherCall.enqueue(moviesCallback);
    }

    private void stopFetchingWeatherData() {
        openWeatherCall.cancel();
    }

    private void startButtonListener() {
        button.register();
    }

    private void stopButtonListener() {
        button.unregister();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {

            if(stateContext.getState().getClass() == StateFetchGpsData.class) {
                latitude = WROCLAW_LATITUDE;
                longitude = WROCLAW_LONGITUDE;
                testData = true;
            }

            stateContext.takeAction();

            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    private LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();

            // StateFetchGpsData -> StateFetchWeatherData
            stateContext.takeAction();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    };

    /////////////////////////////LCD stuff/////////////////////////
    private void setLcdClear() {
        try {
            lcd.clearDisplay();
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    private void setLcdPosition(int row, int col) {
        try {
            lcd.setPosition(row, col);
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    private void setLcdMessage(String message) {
        try {
            lcd.setText(message);
        } catch (IOException e) {
            Timber.e(e);
        }
    }
    ///////////////////////////////////////////////////////////////

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Timber.e(e);
        }
    }

    ///////////////////////FSM States!!!///////////////////////////
    public class StateStart extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateInit());
            initWeatherApiClient();
            initButton();
            initLcd();
            initGps();
        }
    }

    public class StateInit extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateFetchGpsData());

            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage("Waiting for");
            setLcdPosition(1,0);
            setLcdMessage("GPS Data...");

            sleep(3000);

            startFetchingGpsData();
            startButtonListener();
        }
    }

    public class StateFetchGpsData extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateFetchWeatherData());

            stopFetchingGpsData();
            stopButtonListener();

            if(testData) {
                setLcdClear();
                setLcdPosition(0,0);
                setLcdMessage("Test Data Used");
                sleep(2000);
            }

            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage("Waiting for");
            setLcdPosition(1,0);
            setLcdMessage("Weather Data...");

            sleep(3000);

            startFetchingWeatherData();
        }
    }

    public class StateFetchWeatherData extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateShowWeatherDataName());
            stopFetchingWeatherData();

            sleep(2000);

            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage("Weather Ready");
            setLcdPosition(1,0);
            setLcdMessage("Press Button");
            startButtonListener();
        }
    }

    public class StateShowWeatherDataName extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateShowWeatherDataCoord());
            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage("City:");
            setLcdPosition(1,0);
            setLcdMessage(weatherData.getName());
        }
    }

    public class StateShowWeatherDataCoord extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateShowWeatherDataDesc());
            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage("Coordinates:");
            setLcdPosition(1,0);
            setLcdMessage(String.format("%5.2f %5.2f",
                    weatherData.getCoord().getLat(),
                    weatherData.getCoord().getLon()));
        }
    }

    public class StateShowWeatherDataDesc extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateShowWeatherTemp());
            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage("Description:");
            setLcdPosition(1,0);
            setLcdMessage(weatherData.getWeather().get(0).getDescription());
        }
    }

    public class StateShowWeatherTemp extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateShowWeatherDataPressureAndHumidity());
            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage("Temp: ");
            setLcdPosition(1,0);
            setLcdMessage(String.format("%5.2f C",weatherData.getMain().getTemp() - 273.0));
        }
    }

    public class StateShowWeatherDataPressureAndHumidity extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stateContext.setState(new StateShowWeatherDataName());
            setLcdClear();
            setLcdPosition(0,0);
            setLcdMessage(String.format("P: %6.2f hPa", weatherData.getMain().getPressure()));
            setLcdPosition(1,0);
            setLcdMessage(String.format("H: %5.2f %%",weatherData.getMain().getHumidity()));
        }
    }

    public class StateStop extends State {

        @Override
        public void takeAction(StateContext stateContext) {
            stopButtonListener();
            closeButton();
            closeGps();
            closeLcd();
        }
    }

    ///////////////////////////////////////////////////////////////

    private void closeLcd() {

        try {
            if (lcd != null) {
                lcd.clearDisplay();
                lcd.close();
            }
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    private void closeGps() {

        if (gps != null) {
            // Unregister components
            gps.unregister();
            locationManager.removeUpdates(locationListener);

            try {
                gps.close();
            } catch (IOException e) {
                Timber.e(e);
            }
        }
    }

    private void closeButton() {
        if (button != null) {
            try {
                Timber.d("Unregistering button");
                button.close();
            } catch (IOException e) {
                Timber.e(e);
            } finally {
                button = null;
            }
        }
    }

    @Override
    protected void onStop() {
        Timber.d("onStop called.");

        stateContext.setState(new StateStop());
        stateContext.takeAction();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
