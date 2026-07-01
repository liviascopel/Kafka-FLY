package br.ufes.soe.service.kafka;

import java.util.Properties;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.flight.OpenSkyState;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FlightProducer {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9094,localhost:9096";
    private static final String TOPIC = "flight-events"; 
    private static final int TOPIC_PARTITIONS = 3;
    private static final short TOPIC_REPLICATION_FACTOR = 3;
    
    private static final String AVIATIONSTACK_API_KEY = "4350cb53c2fd888a11e776203109b540"; 
    private static final String AVIATIONSTACK_URL = "http://api.aviationstack.com/v1/flights";
    
    private static final String OPENSKY_STATES_URL = "https://opensky-network.org/api/states/all";
    
    private static final int POLLING_INTERVAL_MS = 30000; 

    public static void main(String[] args) throws Exception {
        Properties kafkaProperties = new Properties();
        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, FlightSerializer.class.getName());

        KafkaProducer<String, Flight> kafkaProducer = new KafkaProducer<>(kafkaProperties);
        OkHttpClient httpClient = new OkHttpClient();

        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            ensureTopic(kafkaProperties, TOPIC);

            while (true) {
                System.out.println("log: buscando dados de voos");
                long roundStartTime = System.currentTimeMillis();

                try {
                    Map<String, OpenSkyState> openSkyState = fetchOpenSkyState(httpClient, jsonMapper);
                    String aviationStackUrl = AVIATIONSTACK_URL + "?access_key=" + AVIATIONSTACK_API_KEY;
                    Request aviationStackRequest = new Request.Builder().url(aviationStackUrl).build();

                    try (Response aviationStackResponse = httpClient.newCall(aviationStackRequest).execute()) {
                        if (!aviationStackResponse.isSuccessful()) {
                            System.out.println("erro: falha ao contactar AviationStack: " + aviationStackResponse);
                            continue;
                        }

                        String aviationStackRaw = aviationStackResponse.body().string();
                        JsonNode jsonRootNode = jsonMapper.readTree(aviationStackRaw);
                        JsonNode jsonFlightsArray = jsonRootNode.get("data");

                        if (jsonFlightsArray != null && jsonFlightsArray.isArray()) {
                            System.out.println("Voos da AviationStack: " + jsonFlightsArray.size());
                            jsonFlightsArray.forEach(jsonFlightNode -> {

                                try {
                                    Flight rawFlight = jsonMapper.treeToValue(jsonFlightNode, Flight.class);

                                    // ja descarta voos sem icao
                                    if (rawFlight.flight() == null || rawFlight.flight().icao() == null) {
                                        return; 
                                    }

                                    String flightIcaoKey = rawFlight.flight().icao();
                                    OpenSkyState flightLiveState = findOpenSkyState(rawFlight, openSkyState);

                                    if (flightLiveState != null) {
                                        String resolvedStatus = flightLiveState.flightStatus(rawFlight.flight_status());

                                        // adiciona os dados da OpenSky em LiveTelemetry e status
                                        Flight completeFlight = new Flight(
                                                    rawFlight.flight_date(),
                                                    resolvedStatus, 
                                                    rawFlight.departure(),
                                                    rawFlight.arrival(),
                                                    rawFlight.airline(),
                                                    rawFlight.flight(),
                                                    rawFlight.aircraft(),
                                                    flightLiveState.toLiveTelemetry() 
                                        );
                                        
                                        ProducerRecord<String, Flight> kafkaRecord = new ProducerRecord<>(TOPIC, flightIcaoKey, completeFlight);
                                        kafkaProducer.send(kafkaRecord, (metadata, exception) -> {
                                            if (exception == null) {
                                                System.out.println("log: enviado com sucesso -> ICAO: " + flightIcaoKey 
                                                        + " | status: " + resolvedStatus 
                                                        + " | partição: " + metadata.partition());
                                            } else {
                                                System.err.println("erro: erro ao publicar voo " + flightIcaoKey + ": " + exception.getMessage());
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    System.err.println("erro: erro ao processar voo: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            });
                            kafkaProducer.flush();
                        }
                    } 
                    long executionTime = System.currentTimeMillis() - roundStartTime;
                    System.out.println("log: sincronização concluída em " + executionTime + "ms. aguardando próxima rodada...");
                    Thread.sleep(POLLING_INTERVAL_MS);

                }  catch (Exception e) {
                    System.err.println("erro: erro na rodada de sincronização: " + e.getMessage());
                }
            }
            
        } finally {
            kafkaProducer.close();
        }
    } 


    // garante que o topico está criado
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

    // limpa o texto da key e salva no mapa
    private static void putWithNormalizedKey(Map<String, OpenSkyState> states, String key, OpenSkyState state) {
        String normalizedKey = OpenSkyState.normalizeCallsign(key);
        if (normalizedKey != null) states.put(normalizedKey, state);
    }

    // busca o mapa pela chave
    private static OpenSkyState getUsingNormalizedKey(Map<String, OpenSkyState> states, String key) {
        return states.get(OpenSkyState.normalizeCallsign(key));
    }

    // return the current open sky flights data as OpenSkyState map 
    private static Map<String, OpenSkyState> fetchOpenSkyState(OkHttpClient client, ObjectMapper mapper) throws IOException {
        Request request = new Request.Builder().url(OPENSKY_STATES_URL).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erro ao consultar OpenSky: " + response);

            String json = response.body().string();
            JsonNode states = mapper.readTree(json).get("states");
            Map<String, OpenSkyState> liveFlights = new HashMap<>();

            if (states == null || !states.isArray()) return liveFlights;

            // para cada voo, ele insere 2 vezes. 1 com o icao e 1 com o callsign
            // (alguns voos podem vir com um deles null)
            states.forEach(stateNode -> {
                OpenSkyState state = OpenSkyState.fromJson(stateNode);
                putWithNormalizedKey(liveFlights, state.icao24(), state);
                putWithNormalizedKey(liveFlights, state.callsign(), state);
            });

            System.out.println("log: voos OpenSky carregados: " + liveFlights.size());
            return liveFlights;
        }
    }

    private static OpenSkyState findOpenSkyState(Flight flight, Map<String, OpenSkyState> openSkyStates) {
        if (flight.aircraft() != null && flight.aircraft().icao24() != null) {
            OpenSkyState byIcao24 = getUsingNormalizedKey(openSkyStates, flight.aircraft().icao24());
            if (byIcao24 != null) return byIcao24;
        }
        if (flight.flight() != null) {
            if (flight.flight().icao() != null) {
                OpenSkyState byIcaoCallsign = getUsingNormalizedKey(openSkyStates, flight.flight().icao());
                if (byIcaoCallsign != null) return byIcaoCallsign;
            }
            if (flight.flight().iata() != null) {
                return getUsingNormalizedKey(openSkyStates, flight.flight().iata());
            }
        }
        return null;
    }
}
