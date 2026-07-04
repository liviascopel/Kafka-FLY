package br.ufes.soe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import br.ufes.soe.websocket.AirlineRankingWebSocketHandler;
import br.ufes.soe.websocket.ClimateAlertWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClimateAlertWebSocketHandler alertaHandler;
    private final AirlineRankingWebSocketHandler rankingHandler;

    public WebSocketConfig(ClimateAlertWebSocketHandler alertaHandler, AirlineRankingWebSocketHandler rankingHandler) {
        this.alertaHandler = alertaHandler;
        this.rankingHandler = rankingHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertaHandler, "/ws/alertas-climaticos")
                .setAllowedOrigins("*");
        registry.addHandler(rankingHandler, "/ws/airline-ranking")
                .setAllowedOrigins("*");
    }
}
