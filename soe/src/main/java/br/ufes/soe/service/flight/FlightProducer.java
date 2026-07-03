package br.ufes.soe.service.flight;

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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import br.ufes.soe.domain.flight.AirportCoords;
import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.flight.OpenSkyState;
import br.ufes.soe.domain.weather.AirportWeather;


public class FlightProducer {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9094,localhost:9096";
    private static final String FLIGHT_TOPIC = "complete-flights"; 
    private static final int TOPIC_PARTITIONS = 3;
    private static final short TOPIC_REPLICATION_FACTOR = 3;
    
    private static final String AVIATIONSTACK_API_KEY = "4350cb53c2fd888a11e776203109b540"; 
    private static final String AVIATIONSTACK_FLIGHTS_URL = "http://api.aviationstack.com/v1/flights";
    private static final String AVIATIONSTACK_AIRPORTS_URL = "http://api.aviationstack.com/v1/airports";
    
    private static final String OPENSKY_STATES_URL = "https://opensky-network.org/api/states/all";
    
    private static final int POLLING_INTERVAL_MS = 30000; 

    public static void main(String[] args) throws Exception {
        Properties kafkaProperties = new Properties();
        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, FlightSerializer.class.getName());

        KafkaProducer<String, Flight> flightKafkaProducer = new KafkaProducer<>(kafkaProperties);
        OkHttpClient httpClient = new OkHttpClient();

        Properties weatherProps = (Properties) kafkaProperties.clone();
        weatherProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, br.ufes.soe.service.weather.AirportWeatherSerializer.class.getName());
        KafkaProducer<String, AirportWeather> weatherKafkaProducer = new KafkaProducer<>(weatherProps);

        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // carrega as coordenadas dos aeroportos 
        Map<String, AirportCoords> airportCoordinatesCache = fetchAirportCoordinates(httpClient, jsonMapper);

        try {
            ensureTopic(kafkaProperties, FLIGHT_TOPIC);

            while (true) {
                System.out.println("log: buscando dados de voos");
                long roundStartTime = System.currentTimeMillis();
                
                // vamos buscar pela meteorologia apenas dos aeroportos com voos ativos na api
                java.util.Set<String> activeAirportsInThisRound = new java.util.HashSet<>();

                try {
                    Map<String, OpenSkyState> openSkyState = fetchOpenSkyState(httpClient, jsonMapper);
                    String aviationStackUrl = AVIATIONSTACK_FLIGHTS_URL + "?access_key=" + AVIATIONSTACK_API_KEY;
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
                                    // voo da aviationstack
                                    Flight rawFlight = jsonMapper.treeToValue(jsonFlightNode, Flight.class);

                                    if (rawFlight.flight() == null || rawFlight.flight().icao() == null) {
                                        return; 
                                    }

                                    String flightIcaoKey = rawFlight.flight().icao();
                                    OpenSkyState flightLiveState = findOpenSkyState(rawFlight, openSkyState);

                                    if (flightLiveState != null) {
                                        // pega os dados de telemetria e status da opensky e adiciona no objeto
                                        String resolvedStatus = flightLiveState.flightStatus(rawFlight.flight_status());

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

                                        // adiciona os aeroportos à lista de ativos nessa rodada
                                        if (completeFlight.departure() != null && completeFlight.departure().iata() != null) {
                                            activeAirportsInThisRound.add(completeFlight.departure().iata().toUpperCase().trim());
                                        }
                                        if (completeFlight.arrival() != null && completeFlight.arrival().iata() != null) {
                                            activeAirportsInThisRound.add(completeFlight.arrival().iata().toUpperCase().trim());
                                        }
                                        
                                        // envia os Flights para o FLIGHT_TOPIC
                                        ProducerRecord<String, Flight> kafkaRecord = new ProducerRecord<>(FLIGHT_TOPIC, flightIcaoKey, completeFlight);
                                        flightKafkaProducer.send(kafkaRecord, (metadata, exception) -> {
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
                            flightKafkaProducer.flush();

                            // quantidade de aeroportos ativos
                            System.out.println("Buscando clima para " + activeAirportsInThisRound.size() + " aeroportos em uso...");
                            activeAirportsInThisRound.forEach(iata -> {
                                AirportCoords coords = airportCoordinatesCache.get(iata);
                                // pega a meteorologia do aeroporto
                                if (coords != null) {
                                    br.ufes.soe.service.weather.MeteoProducer.processAndSendWeather(
                                        iata, coords, httpClient, jsonMapper, weatherKafkaProducer
                                    );
                                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                                }
                            });
                            weatherKafkaProducer.flush();
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
            flightKafkaProducer.close();
            weatherKafkaProducer.close();
        }
    } 

    // metodo para buscar as coordenadas dos aeroportos na aviationstack
    private static Map<String, AirportCoords> fetchAirportCoordinates(OkHttpClient client, ObjectMapper mapper) throws IOException {
        System.out.println("Carregando coordenadas dos aeroportos da AviationStack...");
        String url = AVIATIONSTACK_AIRPORTS_URL + "?access_key=" + AVIATIONSTACK_API_KEY + "&limit=1000";
        Request request = new Request.Builder().url(url).build();
        Map<String, AirportCoords> airportMap = new HashMap<>();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Falha ao carregar aeroportos.");
                return airportMap;
            }

            String json = response.body().string();
            JsonNode rootNode = mapper.readTree(json);
            JsonNode dataArray = rootNode.get("data");

            if (dataArray != null && dataArray.isArray()) {
                // para cada nó de aeroporto, pega o iata, nome, país, latitude e longitude do aeroporto
                dataArray.forEach(airportNode -> {
                    String iata = airportNode.has("iata_code") && !airportNode.get("iata_code").isNull()
                            ? airportNode.get("iata_code").asText()
                            : null;
                    String airportName = airportNode.has("airport_name") && !airportNode.get("airport_name").isNull()
                            ? airportNode.get("airport_name").asText()
                            : iata;
                    String country = airportNode.has("country_name") && !airportNode.get("country_name").isNull()
                            ? airportNode.get("country_name").asText()
                            : iata;
                    Double lat = airportNode.has("latitude") && !airportNode.get("latitude").isNull()
                            ? airportNode.get("latitude").asDouble()
                            : null;
                    Double lon = airportNode.has("longitude") && !airportNode.get("longitude").isNull()
                            ? airportNode.get("longitude").asDouble()
                            : null;

                    // adiciona ao airportMap se os atributos nao forem nulos
                    if (iata != null && lat != null && lon != null) {
                        airportMap.put(iata.toUpperCase().trim(), new AirportCoords(airportName, iata, country, lat, lon));
                    }
                });
            }
        }
        System.out.println("Coordenadas de " + airportMap.size() + " aeroportos carregadas com sucesso.");
        return airportMap;
    }

    // garante que o tóico existe
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

    // normaliza a chave (trim e uppercase)
    private static void putWithNormalizedKey(Map<String, OpenSkyState> states, String key, OpenSkyState state) {
        String normalizedKey = OpenSkyState.normalizeCallsign(key);
        if (normalizedKey != null) states.put(normalizedKey, state);
    }

    // pega a chave normalizada
    private static OpenSkyState getUsingNormalizedKey(Map<String, OpenSkyState> states, String key) {
        return states.get(OpenSkyState.normalizeCallsign(key));
    }

    // pega os voos da opensky
    private static Map<String, OpenSkyState> fetchOpenSkyState(OkHttpClient client, ObjectMapper mapper) throws IOException {
        Request request = new Request.Builder().url(OPENSKY_STATES_URL).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erro ao consultar OpenSky: " + response);

            String json = response.body().string();
            JsonNode states = mapper.readTree(json).get("states");
            Map<String, OpenSkyState> liveFlights = new HashMap<>();

            if (states == null || !states.isArray()) return liveFlights;

            // salva no liveFlights com a chave normalizada
            states.forEach(stateNode -> {
                OpenSkyState state = OpenSkyState.fromJson(stateNode);
                putWithNormalizedKey(liveFlights, state.icao24(), state);
                putWithNormalizedKey(liveFlights, state.callsign(), state);
            });

            System.out.println("log: voos OpenSky carregados: " + liveFlights.size());
            return liveFlights;
        }
    }

    // busca algum código correspondente entre as duas apis
    private static OpenSkyState findOpenSkyState(Flight flight, Map<String, OpenSkyState> openSkyStates) {
        if (flight.aircraft() != null && flight.aircraft().icao24() != null) {
            OpenSkyState byIcao24 = getUsingNormalizedKey(openSkyStates, flight.aircraft().icao24());
            // procura o voo pelo icao24
            if (byIcao24 != null) return byIcao24;
        }
        if (flight.flight() != null) {
            // se der errado, pelo icao
            if (flight.flight().icao() != null) {
                OpenSkyState byIcaoCallsign = getUsingNormalizedKey(openSkyStates, flight.flight().icao());
                if (byIcaoCallsign != null) return byIcaoCallsign;
            }
            // se der errado, pelo iata
            if (flight.flight().iata() != null) {
                return getUsingNormalizedKey(openSkyStates, flight.flight().iata());
            }
        }
        return null;
    }
}