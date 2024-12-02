package weatherapp.myweatherapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class WeatherApp extends Application {

    private TextField cityInput;
    private TextArea weatherDisplay;
    private TextArea historyArea;
    private TextArea locationWeatherDisplay;
    private ImageView weatherIcon;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private VBox forecastBox;
    private WebView mapView; // WebView for Google Maps

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Weather App");

        weatherDisplay = new TextArea("Weather will be displayed here.");
        weatherDisplay.setEditable(false);
        weatherDisplay.setWrapText(true);

        cityInput = new TextField();
        cityInput.setPromptText("Enter city");

        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> searchWeather());

        VBox centerBox = new VBox(10, cityInput, searchButton, weatherDisplay);
        centerBox.setPadding(new Insets(10));

        historyArea = new TextArea();
        historyArea.setPrefRowCount(7);
        historyArea.setEditable(false);

        locationWeatherDisplay = new TextArea("Weather for Mekele will be displayed here.");
        locationWeatherDisplay.setEditable(false);
        locationWeatherDisplay.setWrapText(true);

        weatherIcon = new ImageView();
        weatherIcon.setFitHeight(100);
        weatherIcon.setFitWidth(100);

        // Initialize WebView for Google Maps
        mapView = new WebView();
        WebEngine webEngine = mapView.getEngine();
        mapView.setPrefSize(300, 200); // Set preferred size for the map view

        // Create a VBox for forecast display
        forecastBox = new VBox(10);

        // Create a SplitPane for history and map
        SplitPane splitPane = new SplitPane();
        VBox historyBox = new VBox(10, new Label("History:"), historyArea);
        historyBox.setPrefWidth(300); // Set a preferred width for the history box

        // Add history box and map view to the SplitPane
        splitPane.getItems().addAll(historyBox, new VBox(10, new Label("Location on Map:"), mapView));
        splitPane.setDividerPositions(0.3); // Set initial divider position

        // Create a BorderPane layout
        BorderPane layout = new BorderPane();
        layout.setCenter(centerBox);
        layout.setBottom(locationWeatherDisplay);
        layout.setTop(weatherIcon);
        layout.setLeft(splitPane); // Set the SplitPane on the left

        // Set background image
        String imageUrl = "https://images.unsplash.com/photo-1700480555928-198c674a6ab8?w=600&auto=format&fit=crop&q=60&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxzZWFyY2h8NHx8d2VhdGhlciUyMGJhbGxvb258ZW58MHx8MHx8fDA%3D";
        Image backgroundImage = new Image(imageUrl, 800, 600, false, true);
        BackgroundImage myBackground = new BackgroundImage(
                backgroundImage,
                BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT,
                BackgroundSize.DEFAULT);
        layout.setBackground(new Background(myBackground));

        Scene scene = new Scene(layout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Fetch weather for Mekele
        fetchWeatherForMekele();
    }

    private void searchWeather() {
        String city = cityInput.getText().trim();
        if (city.isEmpty()) {
            weatherDisplay.setText("Please enter a city name.");
            return;
        }

        executor.submit(() -> {
            try (Socket socket = new Socket("localhost", 12435); // Ensure this port matches the server
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(city);
                StringBuilder weatherData = new StringBuilder();
                String line;

                // First read the weather data
                while ((line = in.readLine()) != null) {
                    weatherData.append(line).append("\n");
                    if (line.isEmpty()) break; // Break if an empty line is received (if you decide to use this)
                }

                // Process the weather data
                String weatherResponse = weatherData.toString();
                Platform.runLater(() -> {
                    weatherDisplay.setText(weatherResponse);
                    updateHistory(city, weatherResponse);
                    updateMapLocation(city); // Update the map location
                });

                // Next read the forecast data
                StringBuilder forecastData = new StringBuilder();
                while ((line = in.readLine()) != null) {
                    forecastData.append(line).append("\n");
                }

                // Process the forecast data
                parse7DayForecast(forecastData.toString());

            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> weatherDisplay.setText("Error: Unable to connect to server."));
            }
        });
    }

    private void fetchWeatherForMekele() {
        executor.submit(() -> {
            String city = "Mekele"; // Hardcoded city name
            try (Socket socket = new Socket("localhost", 12435);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(city);
                StringBuilder weatherData = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    weatherData.append(line).append("\n");
                }

                Platform.runLater(() -> {
                    locationWeatherDisplay.setText("Weather for Mekele (UTC: " + getCurrentUTCTime() + "):\n" + weatherData.toString());
                    updateWeatherIcon(weatherData.toString());
                    updateMapLocation(city); // Update the map location for Mekele
                    fetch7DayForecast(city); // Fetch the 7-day forecast
                });

            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> locationWeatherDisplay.setText("Error: Unable to connect to server."));
            }
        });
    }

    private String getCurrentUTCTime() {
        return Instant.now().atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private void fetch7DayForecast(String city) {
        executor.submit(() -> {
            try {
                try (Socket socket = new Socket("localhost", 1236);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println(city);
                    StringBuilder forecastData = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        forecastData.append(line).append("\n");
                    }
                    parse7DayForecast(forecastData.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void parse7DayForecast(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            JSONArray daily = json.getJSONArray("daily");
            forecastBox.getChildren().clear(); // Clear previous forecasts

            for (int i = 0; i < daily.length(); i++) {
                JSONObject day = daily.getJSONObject(i);
                long dt = day.getLong("dt");
                String date = Instant.ofEpochSecond(dt).atZone(ZoneId.of("UTC")).toLocalDate().toString();
                JSONObject temp = day.getJSONObject("temp");
                String weatherDescription = day.getJSONArray("weather").getJSONObject(0).getString("description");
                String iconCode = day.getJSONArray("weather").getJSONObject(0).getString("icon");
                String iconUrl = "http://openweathermap.org/img/wn/" + iconCode + "@2x.png";

                HBox card = new HBox(10);
                VBox details = new VBox(5);
                details.getChildren().addAll(
                    new Label("Date: " + date),
                    new Label("Min Temp: " + temp.getDouble("min") + "°C"),
                    new Label("Max Temp: " + temp.getDouble("max") + "°C"),
                    new Label("Weather: " + weatherDescription)
                );

                ImageView icon = new ImageView(new Image(iconUrl));
                icon.setFitHeight(50);
                icon.setFitWidth(50);

                card.getChildren().addAll(icon, details);
                forecastBox.getChildren().add(card);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateWeatherIcon(String weatherData) {
        try {
            JSONObject json = new JSONObject(weatherData);
            String iconCode = json.getJSONArray("weather").getJSONObject(0).getString("icon");
            String iconUrl = "http://openweathermap.org/img/wn/" + iconCode + "@2x.png";
            Image image = new Image(iconUrl);
            weatherIcon.setImage(image);
        } catch (Exception e) {
            e.printStackTrace();
            weatherIcon.setImage(null);
        }
    }

    private void updateHistory(String city, String weatherData) {
        String entry = city + ": " + weatherData + "\n";
        historyArea.appendText(entry);
    }

    private void updateMapLocation(String city) {
        String url = "https://www.google.com/maps/search/?api=1&query=" + city.replace(" ", "+");
        mapView.getEngine().load(url); // Load the Google Maps URL
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}