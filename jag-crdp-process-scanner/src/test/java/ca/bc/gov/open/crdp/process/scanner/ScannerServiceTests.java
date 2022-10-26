package ca.bc.gov.open.crdp.process.scanner;

import ca.bc.gov.open.crdp.process.models.*;
import ca.bc.gov.open.crdp.process.scanner.configuration.QueueConfig;
import ca.bc.gov.open.crdp.process.scanner.services.ScannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
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
    public void setUp() throws IOException {
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

        String appPath = new File("").getCanonicalPath();
        inFileDir = appPath + "/src/test/resources/test/processingIncoming/";

        File backupFolder = new File(appPath + "/src/test/resources/backup/");
        File testFolder = new File(appPath + "/src/test/resources/test/");

        try {
            FileUtils.deleteDirectory(testFolder);
            FileUtils.copyDirectory(backupFolder, testFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void scannerTest() throws IOException {
        //        var fileName = inFileDir + "Processed_2020-03-24/ABCDO_Audit.000001.XML";
        //        var processAuditResponse = new ProcessAuditResponse();
        //        processAuditResponse.setResultCd("0");
        //
        //        ResponseEntity<ProcessAuditResponse> responseEntity =
        //                new ResponseEntity<>(processAuditResponse, HttpStatus.OK);
        //
        //        //     Set up to mock ords response
        //        when(restTemplate.exchange(
        //                        Mockito.any(String.class),
        //                        Mockito.eq(HttpMethod.POST),
        //                        Mockito.<HttpEntity<String>>any(),
        //                        Mockito.<Class<ProcessAuditResponse>>any()))
        //                .thenReturn(responseEntity);
        //
        //        when(controller.validateXml(Mockito.any(String.class), Mockito.any(File.class)))
        //                .thenReturn(true);
        //        controller.processAuditSvc(fileName);
    }
}
