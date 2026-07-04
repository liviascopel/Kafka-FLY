package br.ufes.soe.websocket;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AirlineRankingKafkaListener {

    private static final String AIRLINE_RANKING_TOPIC = "airline-ranking-output";

    private final AirlineRankingWebSocketHandler webSocketHandler;

    public AirlineRankingKafkaListener(AirlineRankingWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(topics = AIRLINE_RANKING_TOPIC, groupId = "airline-ranking-websocket-bridge")
    public void onRankingUpdate(String jsonMetrics) {
        webSocketHandler.broadcast(jsonMetrics);
    }
}
