package br.ufes.soe.domain;

public record AircraftData(
    String registration,
    String iata,
    String icao,
    String icao24
) {}
