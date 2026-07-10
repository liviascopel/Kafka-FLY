package br.ufes.soe.service.demo;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import br.ufes.soe.domain.flight.AirlineData;
import br.ufes.soe.domain.flight.AirportData;
import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.flight.FlightInfo;
import br.ufes.soe.service.flight.FlightSerializer;

public class AirlineRankingDynamicProducer {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9094,localhost:9096";
    private static final String AVIATIONSTACK_FLIGHTS_TOPIC = "aviationstack-flights";

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, FlightSerializer.class.getName());

        KafkaProducer<String, Flight> producer = new KafkaProducer<>(props);

        System.out.println("⏳ [LIVE] Iniciando Simulação Dinâmica da Janela Deslizante...");
        System.out.println("Deixe o terminal/Kafka UI aberto para ver as flutuações de Score a cada minuto!\n");

        try {
            // =========================================================================
            // 🕐 MINUTO 1: "DelayAir" começa a operar com problemas
            // =========================================================================
            System.out.println("[Minuto 1] 🟢 'DelayAir' decola seu primeiro voo (no prazo)...");
            sendLiveFlight(producer, "DelayAir", "DA-101", "active", 0);
            
            System.out.println("[Minuto 1] ⏳ Aguardando 20 segundos para a próxima decolagem...");
            Thread.sleep(10000);

            System.out.println("[Minuto 1] 🛑 Ops! 'DelayAir' teve um problema e gerou um GRANDE ATRASO...");
            sendLiveFlight(producer, "DelayAir", "DA-102", "active", 40); // > 15 min (Penalizado)

            System.out.println("[Minuto 1] ⏳ Aguardando 40 segundos para fechar o primeiro minuto...\n");
            Thread.sleep(10000);

            // =========================================================================
            // 🕑 MINUTO 2: "FlyEco" entra rasgando com voos perfeitos
            // =========================================================================
            System.out.println("[Minuto 2] ✨ 'FlyEco' entra no ar com um voo perfeito!");
            sendLiveFlight(producer, "FlyEco", "FE-201", "scheduled", 0);

            Thread.sleep(10000);

            System.out.println("[Minuto 2] ✨ 'FlyEco' lança mais um voo no prazo. Eficiência máxima!");
            sendLiveFlight(producer, "FlyEco", "FE-202", "active", 0);
            
            System.out.println("[Minuto 2] 🛑 Enquanto isso, 'DelayAir' sofre um cancelamento crítico...");
            sendLiveFlight(producer, "DelayAir", "DA-103", "cancelled", 0); // Cancelado (Penalidade máxima)

            System.out.println("[Minuto 2] ⏳ Aguardando 30 segundos para avançar...\n");
            Thread.sleep(10000);

            // =========================================================================
            // 🕒 MINUTO 3: O fluxo continua em tempo real
            // =========================================================================
            System.out.println("[Minuto 3] 🟢 'DelayAir' tenta se recuperar com um voo limpo...");
            sendLiveFlight(producer, "DelayAir", "DA-104", "landed", 0);

            System.out.println("[Minuto 3] ✨ 'FlyEco' se consolida com seu 3º voo impecável!");
            sendLiveFlight(producer, "FlyEco", "FE-203", "landed", 0);

            System.out.println("\n🏁 Fim do script de injeção contínua.");
            System.out.println("💡 AGORA ASSISTA: Nos próximos minutos, conforme o tempo físico passa,");
            System.out.println("os voos do [Minuto 1] vão expirar e sumir da janela de 5 minutos automaticamente,");
            System.out.println("fazendo o Score da 'DelayAir' subir de volta para 100 de forma reativa!");

        } finally {
            producer.close();
        }
    }

    private static void sendLiveFlight(KafkaProducer<String, Flight> producer, String airlineName, String flightIcao, String status, int delayMinutes) {
        AirportData departureData = new AirportData(null, null, "VIX", null, null, null, delayMinutes, null, null, null, null);
        AirlineData airlineData = new AirlineData(airlineName, null, null);
        FlightInfo flightInfo = new FlightInfo(null, null, flightIcao);

        // Deixamos a data fixa do dia, mas o Kafka vai datar o registro com o milissegundo EXATO do System.currentTimeMillis()
        Flight mockFlight = new Flight(
                java.time.LocalDate.now().toString(),
                status,
                departureData,
                null,
                airlineData,
                flightInfo,
                null,
                null,
                null
        );

        producer.send(new ProducerRecord<>(AVIATIONSTACK_FLIGHTS_TOPIC, flightIcao, mockFlight));
        producer.flush();
    }
}