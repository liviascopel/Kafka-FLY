package br.ufes.soe.service.weather;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.ufes.soe.domain.flight.AirportCoords;
import br.ufes.soe.domain.weather.AirportWeather;
import br.ufes.soe.domain.weather.WeatherResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MeteoProducer {
    private static final String METEO_RAW_TOPIC = "meteo-raw";

    // ja pesquisa o clima pela coordenada do aeroporto
    public static void processAndSendWeather(
            String iata, 
            AirportCoords coords, 
            OkHttpClient httpClient, 
            ObjectMapper jsonMapper, 
            KafkaProducer<String, AirportWeather> kafkaProducer) {
        
        if (coords == null || coords.latitude() == null || coords.longitude() == null) return;

        // faz a requisição
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current_weather=true",
                coords.latitude(), coords.longitude());
        Request request = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String jsonWeatherRaw = response.body().string();
                WeatherResponse weather = jsonMapper.readValue(jsonWeatherRaw, WeatherResponse.class);
                
                // cria o objeto AirportWeather
                if (weather != null && weather.currentWeather() != null) {
                    AirportWeather weatherEvent = new AirportWeather(
                        coords.iata_code(),
                        coords.airport_name(),
                        coords.country_name(),
                        weather.currentWeather().temperature(),
                        weather.currentWeather().windspeed(),
                        weather.currentWeather().weatherCode()
                    );

                    // manda para o topico meteo-airport
                    ProducerRecord<String, AirportWeather> record = new ProducerRecord<>(METEO_RAW_TOPIC, iata, weatherEvent);
                    
                    kafkaProducer.send(record, (metadata, exception) -> {
                        if (exception == null) {
                            System.out.println("[Open-Meteo] Clima enviado para KTable -> " + iata + " (WMO: " + weatherEvent.weatherCode() + ")");
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar clima de " + iata + ": " + e.getMessage());
        }
    }
}