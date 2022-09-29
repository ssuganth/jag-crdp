package ca.bc.gov.open.crdp.transmit.receiver.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    private final QueueConfig queueConfig;

    @Autowired
    public RabbitMqConfig(QueueConfig queueConfig) {
        this.queueConfig = queueConfig;
    }

    @Bean(name = "ca.bc.gov.open.crdp.transmit.receiver-queue")
    public Queue receiverQueue() {
        return new Queue(queueConfig.getReceiverQueueName(), false);
    }


    @Bean(name = "ping-queue")
    public Queue testQueue() {
        return new Queue(queueConfig.getPingQueueName(), false);
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
            @Qualifier("ca.bc.gov.open.crdp.transmit.receiver-queue") Queue receiverQueue,
            @Qualifier("ping-queue") Queue testQueue,
            DirectExchange exchange) {
        return new Declarables(
                BindingBuilder.bind(receiverQueue).to(exchange).with(queueConfig.getReceiverRoutingkey()),
                BindingBuilder.bind(testQueue).to(exchange).with(queueConfig.getPingRoutingKey()));
    }
}
