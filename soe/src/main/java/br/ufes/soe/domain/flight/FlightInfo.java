package br.ufes.soe.domain.flight;

public record FlightInfo(
    String number, 
    String iata, 
    String icao
) {}
