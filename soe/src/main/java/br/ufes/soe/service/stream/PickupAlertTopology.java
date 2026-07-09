package br.ufes.soe.service.stream;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;

import br.ufes.soe.config.JsonSerdes;
import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.pickup.PickupAlert;
import br.ufes.soe.domain.pickup.PickupRequest;
import br.ufes.soe.service.user.TimeDistanceService;

@Component
public class PickupAlertTopology {
    private static final String PICKUP_REQUESTS_TOPIC = "pickup-requests";
    private static final String COMPLETE_FLIGHTS_TOPIC = "complete-flights";
    private static final String PICKUP_ALERTS_TOPIC = "pickup-alerts";

    @Autowired
    private TimeDistanceService timeDistanceService;

    @Autowired
    public void buildPickupTopology(StreamsBuilder streamsBuilder) {

        // Por padrão, o Kafka Streams não permite que um 
        // tópico seja registrado como uma KTable direta se ele já tiver sido registrado em outro lugar da topologia
        // Le como Stream 
        System.out.println("===== PICKUP TOPOLOGY INICIADA =====");
        KStream<String, Flight> flightStream = streamsBuilder
                .stream(COMPLETE_FLIGHTS_TOPIC, Consumed.with(Serdes.String(), JsonSerdes.flight()))
                .peek((key, flight) -> System.out.println(
                    "KSTREAM RECEBEU VOO: key=" + key + 
                    " | icao=" + flight.flight().icao()
                ));

        // Converte para KTable explicitamente, dando um nome para o estado interno (Store)
        // Isso evita o erro de "already been registered by another source"
        KTable<String, Flight> flightTable = flightStream
                        .toTable(Materialized.<String, Flight, KeyValueStore<Bytes, byte[]>>as("flights-state-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(JsonSerdes.flight()));
        
        flightStream.peek((key, flight) -> {
        System.out.println("\n========== FLIGHT KTABLE ==========");
        System.out.println("Key: " + key);
        System.out.println("ICAO: " + (flight.flight() != null ? flight.flight().icao() : "null"));
        System.out.println("===================================\n");
        });

        // Le os pedidos de buscar alguem
        KStream<String, PickupRequest> pickupStream = streamsBuilder
                .stream(PICKUP_REQUESTS_TOPIC, Consumed.with(Serdes.String(), JsonSerdes.pickupRequest()))
                .peek((key, request) -> {
                System.out.println("\n========== REQUEST STREAM ==========");
                System.out.println("Key: " + key);
                System.out.println("ICAO: " + request.flightIcao());
                System.out.println("Usuário: " + request.userName());
                System.out.println("====================================\n");
        });

        // Faz o Join orientado a eventos (Request consulta o histórico de Flights)
        pickupStream
                .peek((key, request) -> System.out.println("STREAM RECEBEU REQUISIÇÃO! Chave: " + key + " | ICAO do Request: " + request.flightIcao()))
                .selectKey((key, request) -> request.flightIcao().trim().toUpperCase())
                .filter((flightIcao, request) -> flightIcao != null)
                .join(flightTable, this::createAlertIfNeeded)
                .filter((flightIcao, alert) -> alert != null)
                .to(PICKUP_ALERTS_TOPIC, Produced.with(Serdes.String(), JsonSerdes.pickupAlert()));
    }

    private PickupAlert createAlertIfNeeded(PickupRequest pickupRequest, Flight flight) {
        
        System.out.println("Processando request do usuário: " + pickupRequest.userName());
        
        if (flight == null) {
            System.out.println("Voo não encontrado na KTable para o ICAO solicitado: " + pickupRequest.flightIcao());
            return null; // Se o voo não existe (ainda), não gera alerta
        }
        
        if(flight.coords() == null || flight.coords().latitude() == null || flight.coords().longitude() == null){
            System.out.println("Não é possível calcular sua distância até o aeroporto solicitado, pois as coordenadas deste não estão disponíveis");
            return null;
        }
        else{
            System.out.println("LATITUDE E LONGITUDE: " + flight.coords().latitude() + flight.coords().longitude());
            try {
                Thread.sleep(10000); // espera 10 segundos
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }        
        }


        Integer tempoAteAeroporto = timeDistanceService.calculaTempoEnderecoAteAeroporto(pickupRequest.userId(), flight.coords().latitude(), flight.coords().longitude());

        Integer etaMinutes = calculateETAMinutes(flight);
        if (etaMinutes == null) {
            etaMinutes = 200; // Seu fallback padrão
        }


        Integer pickupTime = etaMinutes - tempoAteAeroporto;
        if(pickupTime < 0){
            pickupTime = 0;
        }

        String msg = "O voo solicitado, pousará em " + etaMinutes + 
        " minutos, no aeroporto " + flight.arrival().iata() + 
        ". O tempo de seu endereço até o aeroporto de destino é " + tempoAteAeroporto +
        " minutos. Vá para o aeroporto em " + pickupTime + " minutos.";

        System.out.println(msg);

        return new PickupAlert(
                pickupRequest.userId(),
                pickupRequest.userName(),
                pickupRequest.flightIcao(),
                flight.arrival().iata(),
                tempoAteAeroporto,
                etaMinutes,
                msg
        );
    }

    private Integer calculateETAMinutes(Flight flight) {
        if (flight.arrival() == null) {
            return null;
        }

        Instant arrivalTime = flight.arrival().estimated() != null
                ? flight.arrival().estimated()
                : flight.arrival().scheduled();


        if (arrivalTime == null) {
            return null;
        }

        long minutes = Duration.between(Instant.now(), arrivalTime).toMinutes();

        return minutes < 0 ? 0 : (int) minutes;
    }
}


