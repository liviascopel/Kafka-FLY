package br.ufes.soe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import br.ufes.soe.websocket.AirlineRankingWebSocketHandler;
import br.ufes.soe.websocket.ClimateAlertWebSocketHandler;
import br.ufes.soe.websocket.ClimateExposureWebSocketHandler;
import br.ufes.soe.websocket.PickupAlertWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClimateAlertWebSocketHandler alertaHandler;
    private final AirlineRankingWebSocketHandler rankingHandler;
    private final ClimateExposureWebSocketHandler exposureHandler;
    private final PickupAlertWebSocketHandler pickupAlertHandler;

    public WebSocketConfig(ClimateAlertWebSocketHandler alertaHandler,
                            AirlineRankingWebSocketHandler rankingHandler,
                            ClimateExposureWebSocketHandler exposureHandler,
                             PickupAlertWebSocketHandler pickupAlertHandler) {
        this.alertaHandler = alertaHandler;
        this.rankingHandler = rankingHandler;
        this.exposureHandler = exposureHandler;
        this.pickupAlertHandler = pickupAlertHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertaHandler, "/ws/alertas-climaticos")
                .setAllowedOrigins("*");
        registry.addHandler(rankingHandler, "/ws/airline-ranking")
                .setAllowedOrigins("*");
        registry.addHandler(exposureHandler, "/ws/exposicao-climatica")
                .setAllowedOrigins("*");
        registry.addHandler(pickupAlertHandler, "/ws/pickup-alerts")
                .setAllowedOrigins("*");
    }
}
