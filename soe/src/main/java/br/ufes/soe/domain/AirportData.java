package br.ufes.soe.domain;

import java.time.Instant;

public record AirportData(
    String airport,
    String timezone,
    String iata,
    String icao,
    String terminal,
    String gate,
    int delay,
    Instant scheduled,
    Instant estimated,
    Instant actual,
    String baggage
) {}
