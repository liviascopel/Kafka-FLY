package br.ufes.soe.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.ufes.soe.domain.flight.Flight;

import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class FlightDeserializer implements Deserializer<Flight> {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public Flight deserialize(String topico, byte[] dados) {
        try {
            if (dados == null) {
                return null;
            }
            return objectMapper.readValue(dados, Flight.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao desserializar", e);
        }
    }

    @Override
    public void close() {
    }
}
