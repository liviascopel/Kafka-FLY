package br.ufes.soe.service.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class RouteService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public int calculateTravelTimeMinutes(
            double originLat,
            double originLon,
            double destinationLat,
            double destinationLon
    ) {
        try {
            String url = String.format(
                    "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=false",
                    originLon,
                    originLat,
                    destinationLon,
                    destinationLat
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("User-Agent", "Kafka-FLY")
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("Erro ao buscar rota. Status: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());

            JsonNode routes = root.get("routes");

            if (routes == null || !routes.isArray() || routes.isEmpty()) {
                throw new RuntimeException("Nenhuma rota encontrada.");
            }

            double durationSeconds = routes.get(0).get("duration").asDouble();

            return (int) Math.ceil(durationSeconds / 60.0);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular tempo de deslocamento.", e);
        }
    }
}