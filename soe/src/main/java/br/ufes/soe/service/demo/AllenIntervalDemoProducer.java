package br.ufes.soe.service.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.time.LocalDate;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;

import br.ufes.soe.domain.flight.AirlineData;
import br.ufes.soe.domain.flight.AirportData;
import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.flight.FlightInfo;
import br.ufes.soe.domain.weather.AirportWeather;
import br.ufes.soe.service.flight.FlightSerializer;
import br.ufes.soe.service.stream.AllenIntervalRelation;
import br.ufes.soe.service.weather.AirportWeatherSerializer;

// producer de demonstracao: publica cenarios fabricados direto em meteo-raw e complete-flights,
// com timestamps calculados pra forcar cada relacao de allen
// clima severo real coincidindo com um voo e raro, entao isso existe so pra apresentacao/teste -
// nao faz parte do pipeline normal (FlightProducer).
public class AllenIntervalDemoProducer {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9094,localhost:9096";
    private static final String METEO_RAW_TOPIC = "meteo-raw";
    private static final String COMPLETE_FLIGHT_TOPIC = "complete-flights";
    private static final String CLIMATE_EXPOSURE_ALERT_TOPIC = "climate-exposure-alert";
    private static final int TOPIC_PARTITIONS = 3;
    private static final short TOPIC_REPLICATION_FACTOR = 3;
    private static final int READING_STEP_SECONDS = 60;

    public static void main(String[] args) throws Exception {
        Properties flightProps = new Properties();
        flightProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        flightProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        flightProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, FlightSerializer.class.getName());

        Properties weatherProps = (Properties) flightProps.clone();
        weatherProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AirportWeatherSerializer.class.getName());

        KafkaProducer<String, Flight> flightProducer = new KafkaProducer<>(flightProps);
        KafkaProducer<String, AirportWeather> weatherProducer = new KafkaProducer<>(weatherProps);

        ensureTopic(flightProps, CLIMATE_EXPOSURE_ALERT_TOPIC);
        ensureTopic(flightProps, COMPLETE_FLIGHT_TOPIC);
        ensureTopic(flightProps, METEO_RAW_TOPIC);

        sleep(3000);

        // ancora 1h no passado, pra sobrar bastante folga dentro da janela de 6h de "tempestade nao esta velha" do join
        Instant t0 = Instant.now().minus(Duration.ofHours(1));

        List<Scenario> scenarios = List.of(
                // 1. PRECEDES: voo termina bem antes da tempestade comecar
                new Scenario("ZZ1", "Mock Precedes",
                        t0.plus(Duration.ofHours(2)), t0.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(5)),
                        false, t0, AllenIntervalRelation.PRECEDES),

                // 2. MEETS: fim da janela do voo == inicio exato da tempestade
                new Scenario("ZZ2", "Mock Meets",
                        t0.plus(Duration.ofHours(1)), t0.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(5)),
                        true, t0.plus(Duration.ofHours(1)), AllenIntervalRelation.MEETS),

                // 3. OVERLAPS: voo comeca antes da tempestade, tempestade continua depois do voo acabar
                new Scenario("ZZ3", "Mock Overlaps",
                        t0.plus(Duration.ofMinutes(40)), t0.plus(Duration.ofMinutes(80)),
                        false, t0.plus(Duration.ofMinutes(30)), AllenIntervalRelation.OVERLAPS),

                // 4. DURING: janela do voo inteira dentro da tempestade
                new Scenario("ZZ4", "Mock During",
                        t0, t0.plus(Duration.ofMinutes(40)),
                        true, t0.plus(Duration.ofMinutes(35)), AllenIntervalRelation.DURING),

                // 5. EQUALS: janelas identicas
                new Scenario("ZZ5", "Mock Equals",
                        t0, t0.plus(Duration.ofMinutes(30)),
                        true, t0.plus(Duration.ofMinutes(30)), AllenIntervalRelation.EQUALS),

                // 6. STARTS: comecam juntas, tempestade continua depois do voo acabar
                new Scenario("ZZ6", "Mock Starts",
                        t0, t0.plus(Duration.ofMinutes(50)),
                        true, t0.plus(Duration.ofMinutes(30)), AllenIntervalRelation.STARTS),

                // 7. FINISHES: terminam juntas, voo comeca depois da tempestade ja ter comecado
                new Scenario("ZZ7", "Mock Finishes",
                        t0, t0.plus(Duration.ofMinutes(50)),
                        false, t0.plus(Duration.ofMinutes(20)), AllenIntervalRelation.FINISHES)
        );

        try {
            // publica todas as sessoes de clima primeiro
            for (Scenario scenario : scenarios) {
                publishStormSession(weatherProducer, scenario.iata(), scenario.airportName(), scenario.stormStart(), scenario.stormEnd());
            }
            weatherProducer.flush();

            System.out.println("Sessoes de clima publicadas, aguardando o pipeline processar antes de enviar os voos...");
            sleep(15000);

            // com a tabela de tempestades atualizada, publica os voos de uma vez
            for (Scenario scenario : scenarios) {
                Flight flight = buildScenarioFlight(scenario.iata(), scenario.airportName(), scenario.best(), scenario.isDeparture());
                flightProducer.send(new ProducerRecord<>(COMPLETE_FLIGHT_TOPIC, flight.flight().icao(), flight));
            }
            flightProducer.flush();

            for (Scenario scenario : scenarios) {
                Instant flightStart = scenario.isDeparture() ? scenario.best().minus(Duration.ofMinutes(30)) : scenario.best();
                Instant flightEnd = scenario.isDeparture() ? scenario.best() : scenario.best().plus(Duration.ofMinutes(30));
                AllenIntervalRelation computed = AllenIntervalRelation.classify(flightStart, flightEnd, scenario.stormStart(), scenario.stormEnd());
                System.out.printf("[%s] esperado=%s | classificado localmente=%s | %s%n",
                        scenario.iata(), scenario.expectedRelation(), computed,
                        computed == scenario.expectedRelation() ? "OK" : "DIVERGIU - confira a construcao do cenario");
            }

            System.out.println("Cenarios publicados. Acompanhe o topico " + CLIMATE_EXPOSURE_ALERT_TOPIC + " (Kafka UI ou console-consumer).");
        } finally {
            flightProducer.close();
            weatherProducer.close();
        }
    }

    private record Scenario(
            String iata, String airportName,
            Instant stormStart, Instant stormEnd,
            boolean isDeparture, Instant best,
            AllenIntervalRelation expectedRelation
    ) {}

    // publica leituras severas espacadas a cada READING_STEP_SECONDS entre start e end (inclusive),
    // formando uma unica sessao continua com essas bordas exatas
    private static void publishStormSession(KafkaProducer<String, AirportWeather> producer, String iata, String airportName, Instant start, Instant end) {
        AirportWeather severeReading = new AirportWeather(iata, airportName, "Mockland", 0.0, 0.0, 95);
        Instant t = start;
        while (t.isBefore(end)) {
            producer.send(new ProducerRecord<>(METEO_RAW_TOPIC, null, t.toEpochMilli(), iata, severeReading));
            t = t.plusSeconds(READING_STEP_SECONDS);
        }
        producer.send(new ProducerRecord<>(METEO_RAW_TOPIC, null, end.toEpochMilli(), iata, severeReading));
    }

    private static Flight buildScenarioFlight(String iata, String airportName, Instant scheduledTime, boolean isDeparture) {
    AirportData leg = new AirportData(airportName, null, iata, iata + "X", null, null, 0, scheduledTime, null, null, null);
    FlightInfo flightInfo = new FlightInfo("ALLEN1", iata + "-DEMO", iata + "DEMO");
    AirlineData airline = new AirlineData("Allen Airlines", "AL", "ALN");
    
    // pega a data dinamicamente com base no dia de hoje
    String todayStr = LocalDate.now().toString(); 

    return new Flight(
            todayStr, "scheduled",
            isDeparture ? leg : null,
            isDeparture ? null : leg,
            airline, flightInfo, null, null, null
    );
}

    private static void ensureTopic(Properties props, String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(props)) {
            try {
                admin.createTopics(Collections.singletonList(
                        new NewTopic(topic, TOPIC_PARTITIONS, TOPIC_REPLICATION_FACTOR))).all().get();
                System.out.println("Topico verificado com sucesso: " + topic);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) throw e;
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
