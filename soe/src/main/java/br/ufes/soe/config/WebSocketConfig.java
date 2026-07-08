package br.ufes.soe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import br.ufes.soe.websocket.AirlineRankingWebSocketHandler;
import br.ufes.soe.websocket.ClimateAlertWebSocketHandler;
import br.ufes.soe.websocket.ClimateExposureWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClimateAlertWebSocketHandler alertaHandler;
    private final AirlineRankingWebSocketHandler rankingHandler;
    private final ClimateExposureWebSocketHandler exposureHandler;

    public WebSocketConfig(ClimateAlertWebSocketHandler alertaHandler,
                            AirlineRankingWebSocketHandler rankingHandler,
                            ClimateExposureWebSocketHandler exposureHandler) {
        this.alertaHandler = alertaHandler;
        this.rankingHandler = rankingHandler;
        this.exposureHandler = exposureHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertaHandler, "/ws/alertas-climaticos")
                .setAllowedOrigins("*");
        registry.addHandler(rankingHandler, "/ws/airline-ranking")
                .setAllowedOrigins("*");
        registry.addHandler(exposureHandler, "/ws/exposicao-climatica")
                .setAllowedOrigins("*");
    }
}
