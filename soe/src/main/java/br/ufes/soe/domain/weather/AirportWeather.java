package br.ufes.soe.domain.weather;

import java.io.Serializable;

public record AirportWeather(
    String iataCode,
    String airportName,
    String countryName,
    double temperature,
    double windspeed,
    int weatherCode
) implements Serializable {}