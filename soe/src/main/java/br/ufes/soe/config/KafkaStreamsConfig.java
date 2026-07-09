package br.ufes.soe.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    public static class RecordTimestampExtractor implements TimestampExtractor {
        @Override
        public long extract(ConsumerRecord<Object, Object> record, long previousTimestamp) {
            return record.timestamp(); // captura o tempo exato enviado pelo DemoProducer
        }
    }

    @Bean
    public NewTopic meteoRawTopic() {
        return TopicBuilder.name("meteo-raw")
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic completeFlightsTopic() {
        return TopicBuilder.name("complete-flights")
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic aviationstackFlightsTopic() {
        return TopicBuilder.name("aviationstack-flights")
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic pickupRequestsTopic() {
        return TopicBuilder.name("pickup-requests")
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic pickupAlertsTopic() {
        return TopicBuilder.name("pickup-alerts")
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();

        // id unico no cluster para gerenciar os estados locais
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-fly-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092,localhost:9094,localhost:9096");
        
        // serdes (serializadores/deserializadores) padrão como string
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        
        // desativa o cache de buffer para os alertas serem gerados instantaneamente
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        // paraleliza o processamento em 3 threads, uma por partição dos tópicos de entrada
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 3);

        props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, RecordTimestampExtractor.class);

        return new KafkaStreamsConfiguration(props);
    }
}