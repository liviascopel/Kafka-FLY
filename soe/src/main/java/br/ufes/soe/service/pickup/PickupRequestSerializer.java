package br.ufes.soe.service.pickup;

import org.apache.kafka.common.serialization.Serializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufes.soe.domain.pickup.PickupRequest;


public class PickupRequestSerializer implements Serializer<PickupRequest>{
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public byte[] serialize(String topic, PickupRequest dados){
        try {
            if (dados == null) {
                return null;
            }
            return objectMapper.writeValueAsBytes(dados);
        } 
        catch (Exception e) {
            throw new RuntimeException("Erro ao serializar PickupRequest", e);
        }
    }
}
