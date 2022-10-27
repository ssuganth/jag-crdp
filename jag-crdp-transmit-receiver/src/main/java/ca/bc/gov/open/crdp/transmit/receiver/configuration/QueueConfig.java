package ca.bc.gov.open.crdp.transmit.receiver.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@ComponentScan
public class QueueConfig {
    @Value("${crdp.rabbitmq.exchange-name}")
    private String topicExchangeName;

    @Value("${crdp.rabbitmq.receiver-queue}")
    private String receiverQueueName;

    @Value("${crdp.rabbitmq.receiver-routing-key}")
    private String receiverRoutingkey;
}
