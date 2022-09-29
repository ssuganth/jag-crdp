package ca.bc.gov.open.crdp.transmit.receiver.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class QueueConfig {

    @Value("${crdp.rabbitmq.exchange-name}")
    private String topicExchangeName;

    @Value("${crdp.rabbitmq.ca.bc.gov.open.crdp.transmit.receiver-queue}")
    private String receiverQueueName;

    @Value("${crdp.rabbitmq.ping-queue}")
    private String pingQueueName;

    @Value("${crdp.rabbitmq.ca.bc.gov.open.crdp.transmit.receiver-routing-key}")
    private String receiverRoutingkey;

    @Value("${crdp.rabbitmq.ping-routing-key}")
    private String pingRoutingKey;
}
