package weatherapp.myweatherapp;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherServer {

    private static final int PORT = 12435;
    private static final String API_KEY = "f2a5588ab5a3a1bd1ea8e26dfc33805e"; 
    private static final String WEATHER_API_URL = "http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric";
    private static final String FORECAST_API_URL = "http://api.openweathermap.org/data/2.5/onecall?lat=%f&lon=%f&exclude=current,minutely,hourly,alerts&appid=%s&units=metric";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("WeatherServer is running ...at port " + PORT);
            ExecutorService pool = Executors.newFixedThreadPool(10);
            while (true) {
                pool.execute(new WeatherClientHandler(serverSocket.accept()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class WeatherClientHandler implements Runnable {
        private final Socket socket;

        public WeatherClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String city = in.readLine();
                String weatherResponse = fetchWeatherData(city);
                if (weatherResponse.startsWith("Error:")) {
                    out.println(weatherResponse);
                    return; // Return early if there's an error in fetching weather data
                }
                String forecastResponse = fetchForecastData(weatherResponse);
                out.println(weatherResponse);
                out.println(forecastResponse);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String fetchWeatherData(String city) {
            try {
                String urlString = String.format(WEATHER_API_URL, city, API_KEY);
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) {
                    return "Error: Could not retrieve weather data for " + city;
                }

                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        content.append(line);
                    }
                    return parseWeatherData(content.toString(), city);
                }
            } catch (Exception e) {
                return "Error: Could not retrieve data for " + city;
            }
        }

        private String fetchForecastData(String weatherJsonString) {
            try {
                JSONObject json = new JSONObject(weatherJsonString);
                if (json.has("cod") && json.getInt("cod") != 200) {
                    return "Error: " + json.getString("message");
                }
                JSONObject coord = json.getJSONObject("coord");
                double lat = coord.getDouble("lat");
                double lon = coord.getDouble("lon");

                String urlString = String.format(FORECAST_API_URL, lat, lon, API_KEY);
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) {
                    return "Error: Could not retrieve forecast data.";
                }

                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        content.append(line);
                    }
                    return content.toString(); // Return the JSON forecast data
                }
            } catch (Exception e) {
                return "Error: Could not retrieve forecast data.";
            }
        }

        private String parseWeatherData(String jsonString, String city) {
            JSONObject json = new JSONObject(jsonString);
            JSONObject main = json.getJSONObject("main");
            JSONObject clouds = json.getJSONObject("clouds");
            JSONObject coord = json.getJSONObject("coord");
            JSONObject wind = json.getJSONObject("wind");
            JSONObject weather = json.getJSONArray("weather").getJSONObject(0);

            return String.format("City: %s\nTemperature: %.1f°C\nHumidity: %d%%\nCloudiness: %d%% ☁️\n" +
                            "Latitude: %.2f, Longitude: %.2f\nWind Speed: %.1f m/s 🌬️\nPressure: %d hPa\n" +
                            "Weather: %s 🌤️",
                    city,
                    main.getDouble("temp"),
                    main.getInt("humidity"),
                    clouds.getInt("all"),
                    coord.getDouble("lat"),
                    coord.getDouble("lon"),
                    wind.getDouble("speed"),
                    main.getInt("pressure"),
                    weather.getString("description"));
        }
    }
}