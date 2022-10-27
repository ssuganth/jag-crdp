package ca.bc.gov.open.crdp.process.scanner;

import static org.mockito.Mockito.*;

import ca.bc.gov.open.crdp.process.scanner.configuration.QueueConfig;
import ca.bc.gov.open.crdp.process.scanner.services.ScannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScannerServiceTests {

    private String inFileDir;

    @Mock private ObjectMapper objectMapper;
    @Mock private RestTemplate restTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private AmqpAdmin amqpAdmin;
    @Mock private QueueConfig queueConfig;

    @Qualifier("scanner-queue")
    private org.springframework.amqp.core.Queue scannerQueue;

    @Mock private ScannerService controller;

    @BeforeAll
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        controller =
                Mockito.spy(
                        new ScannerService(
                                scannerQueue,
                                amqpAdmin,
                                queueConfig,
                                restTemplate,
                                objectMapper,
                                rabbitTemplate));
    }

    @Test
    public void scannerTest() {
        controller.CRDPScanner();
    }
}
