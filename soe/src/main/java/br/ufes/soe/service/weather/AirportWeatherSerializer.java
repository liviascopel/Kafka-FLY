package br.ufes.soe.service.weather;

import java.util.Map;

import org.apache.kafka.common.serialization.Serializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.ufes.soe.domain.weather.AirportWeather;



public class AirportWeatherSerializer implements Serializer<AirportWeather> {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topico, AirportWeather dados) {
        try {
            if (dados == null) {
                return null;
            }
            return objectMapper.writeValueAsBytes(dados);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao serializar voo", e);
        }
    }

    @Override
    public void close() {
    }
}