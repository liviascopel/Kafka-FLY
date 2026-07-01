package br.ufes.soe.service.weather;

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

import br.ufes.soe.service.weather.AirportWeatherSerializer; // Certifique-se de importar o seu serializer customizado
import br.ufes.soe.domain.flight.AirportCoords;
import br.ufes.soe.domain.weather.AirportWeather;
import br.ufes.soe.domain.weather.WeatherResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class MeteoProducer {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9094,localhost:9096";
    private static final String TOPIC = "meteo-raw";
    private static final int TOPIC_PARTITIONS = 3;
    private static final short TOPIC_REPLICATION_FACTOR = 3;

    private static final String AVIATIONSTACK_API_KEY = "4350cb53c2fd888a11e776203109b540";
    private static final String AVIATIONSTACK_AIRPORTS_URL = "http://api.aviationstack.com/v1/airports";
    
    private static final int POLLING_INTERVAL_MS = 30000;
    private static final int AIRPORT_REQUEST_PAUSE_MS = 150;

    public static void main(String[] args) throws Exception {
        Properties kafkaProperties = new Properties();
        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // CORREÇÃO: Alterado de StringSerializer para o seu Serializer do objeto customizado
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AirportWeatherSerializer.class.getName());

        KafkaProducer<String, AirportWeather> kafkaProducer = new KafkaProducer<>(kafkaProperties);
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.registerModule(new JavaTimeModule());
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Map<String, AirportCoords> airportCoordinatesCache = fetchAirportCoordinates(httpClient, jsonMapper);

        System.out.println("Iniciando motor meteorológico de polling contínuo para o Kafka...");

        try {
            ensureTopic(kafkaProperties, TOPIC);
            while (true) {
                long roundStartTime = System.currentTimeMillis();
                System.out.println("\n=== Coletando Clima Atual dos Aeroportos ===");

                if (airportCoordinatesCache.isEmpty()) {
                    System.out.println("Cache de aeroportos está vazio. Nenhuma requisição climática será feita.");
                }

                airportCoordinatesCache.forEach((iata, coords) -> {
                    if (coords == null || coords.latitude() == null || coords.longitude() == null) return;

                    // faz uma requisicao usando a latitude e longitude do aeroporto
                    String url = String.format(
                            "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current_weather=true",
                            coords.latitude(), coords.longitude());
                    Request request = new Request.Builder().url(url).build();

                    try (Response response = httpClient.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String jsonWeatherRaw = response.body().string();
                            WeatherResponse weather = jsonMapper.readValue(jsonWeatherRaw, WeatherResponse.class);
                            
                            if (weather != null && weather.currentWeather() != null) {
                                AirportWeather weatherEvent = new AirportWeather(
                                    coords.iata_code(),
                                    coords.airport_name(),
                                    coords.country_name(),
                                    weather.currentWeather().temperature(),
                                    weather.currentWeather().windspeed(),
                                    weather.currentWeather().weatherCode()
                                );

                                ProducerRecord<String, AirportWeather> record = new ProducerRecord<>(TOPIC, iata, weatherEvent);
                                
                                kafkaProducer.send(record, (metadata, exception) -> {
                                    if (exception == null) {
                                        System.out.println("Enviado -> Clima de " + iata);
                                    } else {
                                        System.err.println("Erro Kafka em " + iata + ": " + exception.getMessage());
                                    }
                                });
                            }
                        } else {
                            System.err.println("Open-Meteo recusou requisição para " + iata + " Código: " + response.code());
                        }
                    } catch (Exception e) {
                        System.err.println("Timeout ou Erro de conexão ao buscar clima de " + iata + ": " + e.getMessage());
                    }
                    try { 
                        Thread.sleep(AIRPORT_REQUEST_PAUSE_MS); 
                    } catch (InterruptedException e) { 
                        Thread.currentThread().interrupt(); 
                    }
                });

                kafkaProducer.flush();
                long executionTime = System.currentTimeMillis() - roundStartTime;
                System.out.println("Carga climática enviada ao Kafka em " + executionTime + "ms. Aguardando próxima rodada...");
                Thread.sleep(POLLING_INTERVAL_MS);
            }
        } finally {
            kafkaProducer.close();
        }
    }

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

                    if (iata != null && lat != null && lon != null) {
                        airportMap.put(iata.toUpperCase().trim(), new AirportCoords(airportName, iata, country, lat, lon));
                    }
                });
            }
        }

        System.out.println("Coordenadas de " + airportMap.size() + " aeroportos carregadas com sucesso.");
        return airportMap;
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
}