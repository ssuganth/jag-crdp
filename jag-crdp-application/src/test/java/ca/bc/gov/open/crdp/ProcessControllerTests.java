package ca.bc.gov.open.crdp;

import static org.mockito.Mockito.when;

import ca.bc.gov.open.crdp.controllers.ProcessController;
import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.ProcessAuditResponse;
import ca.bc.gov.open.crdp.models.ProcessStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProcessControllerTests {

    @Mock private ObjectMapper objectMapper;
    @Mock private JavaMailSender emailSender;
    @Mock private RestTemplate restTemplate;

    @Mock private ProcessController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void processAuditSvcTest() throws IOException {
        String appPath = new File("").getCanonicalPath();
        var fileName = appPath + "/src/test/resources/dummy.xml";
        var processAuditResponse = new ProcessAuditResponse();

        ResponseEntity<ProcessAuditResponse> responseEntity =
                new ResponseEntity<>(processAuditResponse, HttpStatus.OK);

        //     Set up to mock ords response
        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessAuditResponse>>any()))
                .thenReturn(responseEntity);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);
        controller.processAuditSvc(fileName);
    }

    @Test
    public void processAuditSvcTestFail() throws IOException {

        String appPath = new File("").getCanonicalPath();
        var fileName = appPath + "/src/test/resources/dummy.xml";

        ProcessController processController =
                new ProcessController(emailSender, restTemplate, objectMapper);

        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessAuditResponse>>any()))
                .thenThrow(ORDSException.class);

        Assertions.assertThrows(
                ORDSException.class, () -> processController.processAuditSvc(fileName));
    }

    @Test
    public void processStatusSvcTest() throws IOException {
        String appPath = new File("").getCanonicalPath();
        var fileName = appPath + "/src/test/resources/dummy.xml";
        var processStatusResponse = new ProcessStatusResponse();

        ResponseEntity<ProcessStatusResponse> responseEntity =
                new ResponseEntity<>(processStatusResponse, HttpStatus.OK);

        //     Set up to mock ords response
        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessStatusResponse>>any()))
                .thenReturn(responseEntity);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);
        controller.processAuditSvc(fileName);
    }

    @Test
    public void processStatusSvcTestFail() throws IOException {

        String appPath = new File("").getCanonicalPath();
        var fileName = appPath + "/src/test/resources/dummy.xml";

        ProcessController processController =
                new ProcessController(emailSender, restTemplate, objectMapper);

        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessStatusResponse>>any()))
                .thenThrow(ORDSException.class);

        Assertions.assertThrows(
                ORDSException.class, () -> processController.processStatusSvc(fileName));
    }
}
