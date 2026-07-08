package br.ufes.soe.websocket;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClimateExposureKafkaListener {

    private static final String CLIMATE_EXPOSURE_ALERT_TOPIC = "climate-exposure-alert";

    private final ClimateExposureWebSocketHandler webSocketHandler;

    public ClimateExposureKafkaListener(ClimateExposureWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(topics = CLIMATE_EXPOSURE_ALERT_TOPIC, groupId = "climate-exposure-websocket-bridge")
    public void onExposureAlert(String jsonAlert) {
        webSocketHandler.broadcast(jsonAlert);
    }
}
