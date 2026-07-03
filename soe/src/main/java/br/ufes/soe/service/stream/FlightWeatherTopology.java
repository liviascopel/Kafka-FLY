package br.ufes.soe.service.stream;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufes.soe.config.JsonSerdes;
import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.weather.AirportWeather;
import br.ufes.soe.domain.weather.WeatherAlert;

@Component
public class FlightWeatherTopology {

    private static final String METEO_RAW_TOPIC = "meteo-raw";
    private static final String FLIGHT_EVENTS_TOPIC = "complete-flights";
    private static final String CLIMATE_ALERT_TOPIC = "climate-alert";

    @Autowired
    public void buildTopology(StreamsBuilder streamsBuilder) {

        // usa mapvalues ao inves do filter para que os aeroportos que saem do estado de clima severo sumam da tabela
        KTable<String, AirportWeather> severeWeatherTable = streamsBuilder
                .stream(METEO_RAW_TOPIC, Consumed.with(Serdes.String(), JsonSerdes.airportWeather()))
                .mapValues(weather -> isClimaSevero(weather.weatherCode()) ? weather : null)
                .toTable(Materialized.with(Serdes.String(), JsonSerdes.airportWeather()));

        // tream para consumir um fluxo continuo de flight-events
        KStream<String, Flight> flightStream = streamsBuilder
                .stream(FLIGHT_EVENTS_TOPIC, Consumed.with(Serdes.String(), JsonSerdes.flight()));

        // join com a severeWeatherTable pelo aeroporto de origem, e manda pro topico climate-alert
        flightStream
                .selectKey((icao, flight) -> flight.departure() != null ? flight.departure().iata() : null)
                .filter((iata, flight) -> iata != null)
                .join(severeWeatherTable, (flight, weather) -> new WeatherAlert(
                        flight.flight() != null ? flight.flight().icao() : "DESCONHECIDO",
                        traduzirCodigoWmo(weather.weatherCode()),
                        weather.iataCode(),
                        weather.airportName(),
                        "ORIGEM",
                        flight
                ))
                .to(CLIMATE_ALERT_TOPIC, Produced.with(Serdes.String(), JsonSerdes.weatherAlert()));

        // join com a severeWeatherTable pelo aeroporto de destino, e manda pro topico climate-alert
        flightStream
                .selectKey((icao, flight) -> flight.arrival() != null ? flight.arrival().iata() : null)
                .filter((iata, flight) -> iata != null)
                .join(severeWeatherTable, (flight, weather) -> new WeatherAlert(
                        flight.flight() != null ? flight.flight().icao() : "DESCONHECIDO",
                        traduzirCodigoWmo(weather.weatherCode()),
                        weather.iataCode(),
                        weather.airportName(),
                        "DESTINO",
                        flight
                ))
                .to(CLIMATE_ALERT_TOPIC, Produced.with(Serdes.String(), JsonSerdes.weatherAlert()));
    }

    private boolean isClimaSevero(int code) {
        // codigos WMO de neblina, chuva forte, neve e tempestades
        return code == 45 || code == 48 || code == 65 || code == 75 || code == 95 || code == 96 || code == 99;
    }

    private String traduzirCodigoWmo(int code) {
        return switch (code) {
            case 45, 48 -> "NEBLINA DENSA";
            case 65 -> "CHUVA FORTE";
            case 75 -> "NEVE SEVERA";
            case 95, 96, 99 -> "TEMPESTADE ELÉTRICA";
            case 1 -> "MAJORITARIAMENTE LIMPO";
            default -> "CONDIÇÃO SEVERA DESCONHECIDA";
        };
    }
}