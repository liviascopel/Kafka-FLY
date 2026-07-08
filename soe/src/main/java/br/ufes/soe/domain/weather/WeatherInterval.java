package br.ufes.soe.domain.weather;

import java.io.Serializable;
import java.time.Instant;

public record WeatherInterval(
    String iataCode,
    String airportName,
    Instant start,
    Instant end,
    int weatherCode
) implements Serializable {}
