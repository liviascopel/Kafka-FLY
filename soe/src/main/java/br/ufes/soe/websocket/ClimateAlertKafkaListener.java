package br.ufes.soe.websocket;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClimateAlertKafkaListener {

    private static final String CLIMATE_ALERT_TOPIC = "climate-alert";

    private final ClimateAlertWebSocketHandler webSocketHandler;

    public ClimateAlertKafkaListener(ClimateAlertWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(topics = CLIMATE_ALERT_TOPIC, groupId = "climate-alert-websocket-bridge")
    public void onAlerta(String jsonAlert) {
        webSocketHandler.broadcast(jsonAlert);
    }
}
