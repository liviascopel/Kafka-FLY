package br.ufes.soe.service.stream;

import java.time.Duration;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.ufes.soe.config.JsonSerdes;
import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.ranking.AirlineMetrics;

@Component
public class AirlineRankingTopology {

    private static final String AVIATIONSTACK_FLIGHTS_TOPIC = "aviationstack-flights";
    private static final String AIRLINE_RANKING_TOPIC = "airline-ranking-output";

    @Autowired
    public void buildRankingTopology(StreamsBuilder streamsBuilder) {

        // consome do aviationstack-flights
        KStream<String, Flight> flightStream = streamsBuilder
                .stream(AVIATIONSTACK_FLIGHTS_TOPIC, Consumed.with(Serdes.String(), JsonSerdes.flight()));

        // chave é o nome da companhia
        KTable<Windowed<String>, AirlineMetrics> rankingTable = flightStream
                .selectKey((icao, flight) -> 
                    flight.airline() != null && flight.airline().name() != null 
                        ? flight.airline().name().trim() 
                        : "DESCONHECIDA"
                )
                // agrupa pelo nome da companhia
                .groupByKey(org.apache.kafka.streams.kstream.Grouped.with(Serdes.String(), JsonSerdes.flight()))
                
                // janela de 5 minutos, deslizando a cada 1 minuto
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5)).advanceBy(Duration.ofMinutes(1)))
                
                // computa o score
                .aggregate(
                        // inicializador da janela
                        () -> AirlineMetrics.empty(""),
                        
                        // agregador
                        (airlineName, flight, currentMetrics) -> {
                            String name = currentMetrics.airlineName().isEmpty() ? airlineName : currentMetrics.airlineName();
                            
                            // atraso em minutos se houver
                            int delay = (flight.departure() != null) ? flight.departure().delay() : 0;
                            
                            // reaproveita o record para calcular o novo score internamente
                            return new AirlineMetrics(
                                name, 
                                currentMetrics.totalFlights(), 
                                currentMetrics.delayedFlights(), 
                                currentMetrics.cancelledFlights(), 
                                currentMetrics.score()
                            ).increment(flight.flight_status(), delay);
                        },
                        
                        Materialized.with(Serdes.String(), JsonSerdes.airlineMetrics())
                );

        // envia o resultado da tabela para o tópico de saída
        rankingTable
                .toStream()
                // como a chave virou windowed<string>, convertemos p string antes de enviar para o tópico
                .selectKey((windowedKey, metrics) -> windowedKey.key())
                .to(AIRLINE_RANKING_TOPIC, Produced.with(Serdes.String(), JsonSerdes.airlineMetrics()));
    }
}