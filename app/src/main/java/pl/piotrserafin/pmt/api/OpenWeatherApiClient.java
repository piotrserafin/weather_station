package pl.piotrserafin.pmt.api;

import okhttp3.OkHttpClient;
import pl.piotrserafin.pmt.model.WeatherData;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class OpenWeatherApiClient {

    private final static String BASE_URL = "http://api.openweathermap.org/";
    private static final String API_KEY = "bbaf7f9fd2fef4c6e0f822c0b36a1a34";

    private static OpenWeatherApiClient instance = null;

    private OpenWeatherApiClient.Api api;

    public interface Api {

        @GET("/data/2.5/weather")
        Call<WeatherData> getCurrentWeather(@Query("lat") String latitude,
                                            @Query("lon") String longitude,
                                            @Query("appid") String appId);

        @GET("/data/2.5/weather")
        Call<WeatherData> getCurrentWeather(@Query("id") String cityId,
                                            @Query("appid") String appId);
    }

    private OpenWeatherApiClient() {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient())
                .build();

        api = retrofit.create(OpenWeatherApiClient.Api.class);
    }

    public static OpenWeatherApiClient getInstance() {
        if (instance == null) {
            instance = new OpenWeatherApiClient();
        }
        return instance;
    }

    public Call<WeatherData> getCurrentWeather(String latitude, String longitude) {
        return api.getCurrentWeather(latitude, longitude, API_KEY);
    }

    public Call<WeatherData> getCurrentWeather(String cityId) {
        return api.getCurrentWeather(cityId, API_KEY);
    }
}
