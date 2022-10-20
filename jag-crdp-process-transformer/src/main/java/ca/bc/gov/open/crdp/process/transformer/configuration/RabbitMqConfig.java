package ca.bc.gov.open.crdp.process.transformer.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class RabbitMqConfig {

    private final QueueConfig queueConfig;

    @Autowired
    public RabbitMqConfig(QueueConfig queueConfig) {
        this.queueConfig = queueConfig;
    }

    @Bean(name = "receiver-queue")
    public Queue receiverQueue() {
        return new Queue(queueConfig.getReceiverQueueName(), false);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(queueConfig.getTopicExchangeName());
    }

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public Declarables binding(
            @Qualifier("receiver-queue") Queue receiverQueue, DirectExchange exchange) {
        return new Declarables(
                BindingBuilder.bind(receiverQueue)
                        .to(exchange)
                        .with(queueConfig.getReceiverRoutingkey()));
    }
}
