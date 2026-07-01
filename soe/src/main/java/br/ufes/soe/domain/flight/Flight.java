package br.ufes.soe.domain.flight;

import java.io.Serializable;

public record Flight(
    String flight_date,
    String flight_status,
    AirportData departure,
    AirportData arrival,
    AirlineData airline,
    FlightInfo flight,
    AircraftData aircraft,
    LiveTelemetry live
) implements Serializable {}