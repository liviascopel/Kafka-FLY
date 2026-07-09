package br.ufes.soe.service.pickup;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.Collections;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

import br.ufes.soe.domain.pickup.PickupRequest;
import jakarta.annotation.PostConstruct;



@Component
public class PickupProducer{
    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9094,localhost:9096";

    private static final String PICKUP_REQUESTS_TOPIC = "pickup-requests";
    private static final String PICKUP_ALERTS_TOPIC = "pickup-alerts";

    private static final int TOPIC_PARTITIONS = 3;
    private static final short TOPIC_REPLICATION_FACTOR = 3;

    private KafkaProducer<String, PickupRequest> pickupProducer;

    @PostConstruct
    public void init() throws Exception {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, PickupRequestSerializer.class.getName());

        ensureTopic(props, PICKUP_REQUESTS_TOPIC);
        ensureTopic(props, PICKUP_ALERTS_TOPIC);

        this.pickupProducer = new KafkaProducer<>(props);
    }

    public void send(PickupRequest request){
        pickupProducer.send(
        new ProducerRecord<>(
                PICKUP_REQUESTS_TOPIC,
                request.flightIcao(),
                request
        ),
        (metadata, exception) -> {
            if (exception == null) {
                System.out.println("pickupRequest mandado para o tópico");
            } else {
                exception.printStackTrace();
            }
        }
);
    }


    private static void ensureTopic(Properties props, String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(props)) {
            try {
                admin.createTopics(Collections.singletonList(
                        new NewTopic(topic, TOPIC_PARTITIONS, TOPIC_REPLICATION_FACTOR))).all().get();
                System.out.println("Topico verificado com sucesso: " + topic);
            } 
            catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) throw e;
            }
        }
    }
}