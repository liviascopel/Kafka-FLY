package br.ufes.soe.domain.flight;

public record AirportCoords(
    String airport_name,
    String iata_code,
    String country_name,
    Double latitude,
    Double longitude
) {}
