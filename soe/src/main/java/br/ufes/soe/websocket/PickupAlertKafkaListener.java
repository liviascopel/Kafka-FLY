package br.ufes.soe.websocket;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PickupAlertKafkaListener {

    private static final String PICKUP_ALERTS_TOPIC = "pickup-alerts";

    private final PickupAlertWebSocketHandler webSocketHandler;

    public PickupAlertKafkaListener(PickupAlertWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(topics = PICKUP_ALERTS_TOPIC, groupId = "pickup-alert-websocket-bridge")
    public void onPickupAlert(String jsonAlert) {
        webSocketHandler.broadcast(jsonAlert);
    }
}