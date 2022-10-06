package ca.bc.gov.open.tmp;

import static org.mockito.Mockito.*;

import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.transmit.models.GenerateIncomingReqFileResponse;
import ca.bc.gov.open.crdp.transmit.receiver.configuration.QueueConfig;
import ca.bc.gov.open.crdp.transmit.receiver.controllers.ReceivingController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.*;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReceivingControllerTests {
    private String outputDir;
    @Mock private ObjectMapper objectMapper;
    @Mock private JavaMailSender emailSender;
    @Mock private RestTemplate restTemplate;
    @MockBean private RabbitTemplate rabbitTemplate;
    @MockBean private AmqpAdmin amqpAdmin;

    @Qualifier("receiver-queue")
    private org.springframework.amqp.core.Queue receiverQueue;

    private QueueConfig queueConfig;

    @Mock private ReceivingController controller;

    @BeforeEach
    public void setUp() throws IOException {

        MockitoAnnotations.openMocks(this);

        controller =
                new ReceivingController(
                        receiverQueue,
                        amqpAdmin,
                        queueConfig,
                        emailSender,
                        restTemplate,
                        objectMapper,
                        rabbitTemplate);

        String appPath = new File("").getCanonicalPath();
        outputDir = appPath + "/src/test/resources/test/transimitoutgoing/";
    }

    @Test
    public void GenerateIncomingRequestFileTest() throws IOException {
        var generateIncomingReqFileResponse = new GenerateIncomingReqFileResponse();

        //        generateIncomingReqFileResponse.setFile("<?xml version=\"1.0\"
        // encoding=\"utf-8\"?>");
        generateIncomingReqFileResponse.setFileName("sendToOttawa.xml");
        generateIncomingReqFileResponse.setStatus("1");

        ResponseEntity<GenerateIncomingReqFileResponse> responseEntity =
                new ResponseEntity<>(generateIncomingReqFileResponse, HttpStatus.OK);

        //     Set up to mock ords response
        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<GenerateIncomingReqFileResponse>>any()))
                .thenReturn(responseEntity);

        controller =
                new ReceivingController(
                        receiverQueue,
                        amqpAdmin,
                        queueConfig,
                        emailSender,
                        restTemplate,
                        objectMapper,
                        rabbitTemplate);

        ReflectionTestUtils.setField(controller, "outFileDir", outputDir, String.class);
        controller.GenerateIncomingRequestFile();
    }

    @Test
    public void generateIncomingRequestFileFail1() throws IOException {

        // Set up to mock ords response
        when(restTemplate.exchange(
                        Mockito.<String>any(),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<GenerateIncomingReqFileResponse>>any()))
                .thenThrow(ORDSException.class);

        Assertions.assertThrows(
                ORDSException.class, () -> controller.GenerateIncomingRequestFile());
    }

    @Test
    public void generateIncomingRequestFileFail2() throws IOException {

        var generateIncomingReqFileResponse = new GenerateIncomingReqFileResponse();

        //        generateIncomingReqFileResponse.setFile("<?xml version=\"1.0\"
        // encoding=\"utf-8\"?>");
        generateIncomingReqFileResponse.setFileName("sendToOttawa.xml");
        generateIncomingReqFileResponse.setStatus("0");

        ResponseEntity<GenerateIncomingReqFileResponse> responseEntity =
                new ResponseEntity<>(generateIncomingReqFileResponse, HttpStatus.OK);

        //     Set up to mock ords response
        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<GenerateIncomingReqFileResponse>>any()))
                .thenReturn(responseEntity);

        ReflectionTestUtils.setField(controller, "outFileDir", outputDir, String.class);

        doThrow(new RuntimeException()).when(emailSender).send(any(SimpleMailMessage.class));

        Assertions.assertThrows(
                ORDSException.class, () -> controller.GenerateIncomingRequestFile());
    }

    @Test
    public void generateIncomingRequestFileFail3() throws IOException {

        var generateIncomingReqFileResponse = new GenerateIncomingReqFileResponse();

        //        generateIncomingReqFileResponse.setFile("<?xml version=\"1.0\"
        // encoding=\"utf-8\"?>");
        generateIncomingReqFileResponse.setFileName("sendToOttawa.xml");
        generateIncomingReqFileResponse.setStatus("1");

        ResponseEntity<GenerateIncomingReqFileResponse> responseEntity =
                new ResponseEntity<>(generateIncomingReqFileResponse, HttpStatus.OK);

        //     Set up to mock ords response
        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<GenerateIncomingReqFileResponse>>any()))
                .thenReturn(responseEntity);

        controller =
                new ReceivingController(
                        receiverQueue,
                        amqpAdmin,
                        queueConfig,
                        emailSender,
                        restTemplate,
                        objectMapper,
                        rabbitTemplate);

        Assertions.assertThrows(
                ORDSException.class, () -> controller.GenerateIncomingRequestFile());
    }
}
