package br.ufes.soe.domain.flight;

public record AircraftData(
    String registration,
    String iata,
    String icao,
    String icao24
) {}
