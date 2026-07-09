package br.ufes.soe.controller;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/monitoramento")
@CrossOrigin(origins = "*")
public class StreamingController {

    private final CopyOnWriteArrayList<SseEmitter> conexoesAtivas = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter estabelecerConexao() {
        SseEmitter emissor = new SseEmitter(60000L);
        conexoesAtivas.add(emissor);
        
        emissor.onCompletion(() -> conexoesAtivas.remove(emissor));
        emissor.onTimeout(() -> conexoesAtivas.remove(emissor));
        emissor.onError((erro) -> conexoesAtivas.remove(emissor));
        
        return emissor;
    }

    @KafkaListener(topics = "climate-alert", groupId = "grupo-front")
    public void consumirTopicoAlertaClimatico(String jsonMensagem) {
        despacharParaClientes("climate-alert", jsonMensagem);
    }

    @KafkaListener(topics = "airline-ranking-output", groupId = "grupo-front")
    public void consumirTopicoRanking(String jsonMensagem) {
        despacharParaClientes("airline-ranking-output", jsonMensagem);
    }

    @KafkaListener(topics = "complete-flights", groupId = "grupo-front")
    public void consumirTopicoVoo(String jsonMensagem) {
        despacharParaClientes("complete-flights", jsonMensagem);
    }

    @KafkaListener(topics = "meteo-raw", groupId = "grupo-front")
    public void consumirTopicoMeteoRaw(String jsonMensagem) {
        despacharParaClientes("meteo-raw", jsonMensagem);
    }

    @KafkaListener(topics = "climate-exposure-alert", groupId = "grupo-front")
    public void consumirTopicoAlertaExposicaoClimatica(String jsonMensagem) {
        despacharParaClientes("climate-exposure-alert", jsonMensagem);
    }

    @KafkaListener(topics = "pickup-alerts", groupId = "grupo-front")
    public void consumirTopicoAlertaCarona(String jsonMensagem) {
        despacharParaClientes("pickup-alerts", jsonMensagem);
    }

    private void despacharParaClientes(String nomeEvento, String payload) {
        for (SseEmitter emissor : conexoesAtivas) {
            try {
                emissor.send(SseEmitter.event().name(nomeEvento).data(payload));
            } catch (IOException e) {
                conexoesAtivas.remove(emissor);
            }
        }
    }

    
}
