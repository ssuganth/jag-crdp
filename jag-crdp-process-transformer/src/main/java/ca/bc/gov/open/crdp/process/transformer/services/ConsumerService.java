package ca.bc.gov.open.crdp.process.transformer.services;

import ca.bc.gov.open.crdp.process.models.ScannerPub;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.ws.server.endpoint.annotation.Endpoint;

@Endpoint
@Slf4j
public class ConsumerService {

    private final TransformerService transformerService;

    private final ObjectMapper objectMapper;

    @Autowired
    public ConsumerService(ObjectMapper objectMapper, TransformerService senderService) {
        this.objectMapper = objectMapper;
        this.transformerService = senderService;
    }

    @RabbitListener(queues = "${crdp.scanner-queue}")
    public void receiveScannerPubMessage(@Payload Message<ScannerPub> message) {
        transformerService.processFileService(message.getPayload());
    }
}
