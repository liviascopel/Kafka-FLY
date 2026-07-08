package br.ufes.soe.service.stream;

import java.time.Duration;
import java.time.Instant;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufes.soe.config.JsonSerdes;
import br.ufes.soe.domain.flight.AirportData;
import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.weather.ClimateExposureAlert;
import br.ufes.soe.domain.weather.WeatherInterval;

@Component
public class ClimateExposureTopology {

    private static final String METEO_RAW_TOPIC = "meteo-raw";
    private static final String FLIGHT_EVENTS_TOPIC = "complete-flights";
    private static final String CLIMATE_EXPOSURE_ALERT_TOPIC = "climate-exposure-alert";

    private static final Duration SESSION_INACTIVITY_GAP = Duration.ofSeconds(90);
    private static final Duration EXPOSURE_WINDOW = Duration.ofMinutes(30);
    private static final long STALE_STORM_HOURS = 6;

    @Autowired
    public void buildExposureTopology(StreamsBuilder streamsBuilder) {

        // agrupa leituras severas consecutivas de cada aeroporto numa sessao: a "duracao" da tempestade.
        // cada atualizacao propaga na hora, entao o
        // fim do intervalo fica "em aberto" ate a tempestade realmente acabar (gap > 90s sem leitura severa)
        KTable<String, WeatherInterval> stormIntervalTable = streamsBuilder
                .stream(METEO_RAW_TOPIC, Consumed.with(Serdes.String(), JsonSerdes.airportWeather()))
                .filter((iata, weather) -> isClimaSevero(weather.weatherCode()))
                .groupByKey(Grouped.with(Serdes.String(), JsonSerdes.airportWeather()))
                .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(SESSION_INACTIVITY_GAP))
                .aggregate(
                        () -> null,
                        (iata, weather, aggregate) -> weather,
                        (iata, aggregate1, aggregate2) -> aggregate1,
                        Materialized.with(Serdes.String(), JsonSerdes.airportWeather())
                )
                .toStream()
                // quando duas sessoes se fundem, a janela antiga vira null antes da nova
                // janela mesclada chegar - descarta esse tombstone em vez de tentar montar um WeatherInterval dele
                .filter((windowedKey, weather) -> weather != null)
                .map((windowedKey, weather) -> KeyValue.pair(
                        windowedKey.key(),
                        new WeatherInterval(
                                windowedKey.key(),
                                weather.airportName(),
                                windowedKey.window().startTime(),
                                windowedKey.window().endTime(),
                                weather.weatherCode()
                        )
                ))
                .toTable(Materialized.with(Serdes.String(), JsonSerdes.weatherInterval()));

        // fluxo continuo de voos enriquecidos
        KStream<String, Flight> flightStream = streamsBuilder
                .stream(FLIGHT_EVENTS_TOPIC, Consumed.with(Serdes.String(), JsonSerdes.flight()));

        // exposicao na ORIGEM: janela do voo = [partida - 30min, partida]
        flightStream
                .selectKey((icao, flight) -> flight.departure() != null ? flight.departure().iata() : null)
                .filter((iata, flight) -> iata != null && bestAvailable(flight.departure()) != null)
                .join(stormIntervalTable, (flight, storm) -> {
                    Instant best = bestAvailable(flight.departure());
                    return buildAlert(flight, storm, best.minus(EXPOSURE_WINDOW), best, "ORIGEM");
                })
                .filter((iata, alert) -> alert != null)
                .to(CLIMATE_EXPOSURE_ALERT_TOPIC, Produced.with(Serdes.String(), JsonSerdes.climateExposureAlert()));

        // exposicao no DESTINO: janela do voo = [chegada, chegada + 30min]
        flightStream
                .selectKey((icao, flight) -> flight.arrival() != null ? flight.arrival().iata() : null)
                .filter((iata, flight) -> iata != null && bestAvailable(flight.arrival()) != null)
                .join(stormIntervalTable, (flight, storm) -> {
                    Instant best = bestAvailable(flight.arrival());
                    return buildAlert(flight, storm, best, best.plus(EXPOSURE_WINDOW), "DESTINO");
                })
                .filter((iata, alert) -> alert != null)
                .to(CLIMATE_EXPOSURE_ALERT_TOPIC, Produced.with(Serdes.String(), JsonSerdes.climateExposureAlert()));
    }

    private ClimateExposureAlert buildAlert(Flight flight, WeatherInterval storm, Instant flightStart, Instant flightEnd, String type) {
        // a tabela de tempestades nao tem tombstone por idade - ignora tempestades muito antigas
        // em vez de gerar alerta contra um evento irrelevante que nunca saiu da tabela
        if (Duration.between(storm.end(), Instant.now()).toHours() > STALE_STORM_HOURS) {
            return null;
        }

        AllenIntervalRelation relation = AllenIntervalRelation.classify(flightStart, flightEnd, storm.start(), storm.end());

        return new ClimateExposureAlert(
                flight.flight() != null ? flight.flight().icao() : "DESCONHECIDO",
                relation.name(),
                relation.isRisky(),
                storm.iataCode(),
                storm.airportName(),
                type,
                flightStart,
                flightEnd,
                storm.start(),
                storm.end(),
                flight
        );
    }

    private Instant bestAvailable(AirportData data) {
        if (data == null) return null;
        if (data.actual() != null) return data.actual();
        if (data.estimated() != null) return data.estimated();
        return data.scheduled();
    }

    private boolean isClimaSevero(int code) {
        // codigos WMO de neblina, chuva forte, neve e tempestades
        return code == 45 || code == 48 || code == 65 || code == 75 || code == 95 || code == 96 || code == 99;
    }
}
