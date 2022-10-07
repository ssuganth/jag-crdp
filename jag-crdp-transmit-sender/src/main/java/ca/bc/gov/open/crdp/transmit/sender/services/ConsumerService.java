package ca.bc.gov.open.crdp.transmit.sender.services;

import ca.bc.gov.open.crdp.transmit.models.ReceiverPub;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.ws.server.endpoint.annotation.Endpoint;

@Endpoint
@Slf4j
public class ConsumerService {

    private final SenderService senderService;

    private final ObjectMapper objectMapper;

    @Autowired
    public ConsumerService(ObjectMapper objectMapper, SenderService hsrService) {
        this.objectMapper = objectMapper;
        this.senderService = hsrService;
    }

    @RabbitListener(queues = "${crdp.receiver-queue}")
    public void receiveXmlMessage(@Payload Message<ReceiverPub> message)
            throws IOException, InterruptedException {
        try {
            senderService.update(message.getPayload());
        } catch (Exception ignored) {
            log.error("ERROR: " + message + " not processed successfully");
        }

        try {
            senderService.send(message.getPayload());
        } catch (Exception ignored) {
            log.error("ERROR: " + message + " not processed successfully");
        }
        System.out.println(objectMapper.writeValueAsString(message.getPayload()));
    }
}
