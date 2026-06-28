package br.ufes.soe.domain;

public record FlightInfo(
    String number, 
    String iata, 
    String icao
) {}
