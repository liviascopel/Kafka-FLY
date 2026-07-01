package br.ufes.soe.domain.weather;

import java.io.Serializable;

import br.ufes.soe.domain.flight.Flight;

public record WeatherAlert(
    String flightIcao,
    String condition,       // chuva, neblina, raio, nevasca
    String airportIata,
    String airportName,
    String type,            // origem ou destino
    Flight flightDetails    // objeto completo do voo para o frontend ter tudo na tela
) implements Serializable {}