package ca.bc.gov.open.crdp.process.transformer.services;

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

    private final TransformerService transformerService;

    private final ObjectMapper objectMapper;

    @Autowired
    public ConsumerService(ObjectMapper objectMapper, TransformerService senderService) {
        this.objectMapper = objectMapper;
        this.transformerService = senderService;
    }

    @RabbitListener(queues = "${crdp.scanner-queue}")
    public void receiveScannerPubMessage(@Payload Message<String> message) {
        if (message.getPayload().contains("scanning time:")) {
            transformerService.recordScanningTime(
                    message.getPayload()
                            .substring(
                                    message.getPayload().lastIndexOf("scanning time:")
                                            + "scanning time:".length()));
        } else {
            transformerService.processFile(message.getPayload());
        }
    }
}
