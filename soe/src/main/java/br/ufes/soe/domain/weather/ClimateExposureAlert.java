package br.ufes.soe.domain.weather;

import java.io.Serializable;
import java.time.Instant;

import br.ufes.soe.domain.flight.Flight;

public record ClimateExposureAlert(
    String flightIcao,
    String allenRelation,        // nome da relacao de Allen, ex: "OVERLAPS"
    boolean risky,               // false so para PRECEDES/PRECEDED_BY (sem exposicao)
    String airportIata,
    String airportName,
    String type,                 // origem ou destino
    Instant flightWindowStart,
    Instant flightWindowEnd,
    Instant stormWindowStart,
    Instant stormWindowEnd,
    Flight flightDetails
) implements Serializable {}
