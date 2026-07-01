package br.ufes.soe.domain.weather;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WeatherResponse(
    @JsonProperty("current_weather") CurrentWeather currentWeather
) {
    public record CurrentWeather(
        double temperature,
        double windspeed,
        @JsonProperty("weathercode") int weatherCode // WMO
    ) {}
}