package br.ufes.soe.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import br.ufes.soe.domain.Coordenadas;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GeocodingService {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public Coordenadas calcularCoordenadas(String endereco){
        try{
            String endFormatado = URLEncoder.encode(endereco, StandardCharsets.UTF_8);

            String url = "https://nominatim.openstreetmap.org/search?q=" + endFormatado + "&format=json&limit=1&countrycodes=br";

            HttpRequest request = HttpRequest
                                  .newBuilder()
                                  .uri(URI.create(url))
                                  .header("User-Agent", "Kafka-FLY/1.0 (thaisgomes1585@gmail.com)")
                                  .header("Accept", "application/json")
                                  .GET()
                                  .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

             if (response.statusCode() != 200) {
                throw new RuntimeException("Erro na API de geocoding. Status: " + response.statusCode());
            }
            
            JsonNode json = mapper.readTree(response.body());

            if (json.isEmpty()) {
                throw new RuntimeException("Endereco nao encontrado.");
            }

            Double latitude = json.get(0).get("lat").asDouble();
            Double longitude = json.get(0).get("lon").asDouble();

            return new Coordenadas(latitude, longitude);
        }
        catch(Exception e){
            throw new RuntimeException("Erro ao buscar coordenadas do endereco.", e);
        }
    }
}
