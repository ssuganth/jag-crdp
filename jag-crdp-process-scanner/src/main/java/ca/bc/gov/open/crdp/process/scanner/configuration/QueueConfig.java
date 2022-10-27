package ca.bc.gov.open.crdp.process.scanner.configuration;

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

    @Value("${crdp.rabbitmq.scanner-queue}")
    private String scannerQueueName;

    @Value("${crdp.rabbitmq.scanner-routing-key}")
    private String scannerRoutingkey;
}
