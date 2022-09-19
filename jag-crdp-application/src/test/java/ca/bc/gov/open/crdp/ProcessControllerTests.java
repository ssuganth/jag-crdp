package ca.bc.gov.open.crdp;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import ca.bc.gov.open.crdp.controllers.ProcessController;
import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProcessControllerTests {

    private String inputDir;

    @Mock private ObjectMapper objectMapper;
    @Mock private JavaMailSender emailSender;
    @Mock private RestTemplate restTemplate;

    @Mock private ProcessController controller;

    @BeforeEach
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        String appPath = new File("").getCanonicalPath();
        inputDir = appPath + "/src/test/resources/processingIncoming/";
    }

    @Test
    public void processAuditSvcTest() throws IOException {
        var fileName = inputDir + "dummy.xml";
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
        var fileName = inputDir + "dummy.xml";

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
        var fileName = inputDir + "dummy.xml";
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
        var fileName = inputDir + "dummy.xml";

        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessStatusResponse>>any()))
                .thenThrow(ORDSException.class);

        ProcessController processController =
                new ProcessController(emailSender, restTemplate, objectMapper);

        Assertions.assertThrows(
                ORDSException.class, () -> processController.processStatusSvc(fileName));
    }

    @Test
    public void processDocumentsSvcTest() throws IOException {
        var folderName = inputDir + "Processed_2020-03-24/CCs/";
        var folderShortName = "CCs";
        var processedDate = "2020-03-24";

        //     Set up to mock ords response
        Map<String, String> m = new HashMap<>();
        ResponseEntity<Map<String, String>> responseEntity = new ResponseEntity<>(m, HttpStatus.OK);
        m.put("status", "N");

        // Set up to mock ords response
        when(restTemplate.exchange(
                        AdditionalMatchers.not(contains("tokenvalue")),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenReturn(responseEntity);

        var savePDFDocumentResponse = new SavePDFDocumentResponse();
        ResponseEntity<SavePDFDocumentResponse> responseEntity1 =
                new ResponseEntity<>(savePDFDocumentResponse, HttpStatus.OK);
        responseEntity1.getBody().setResultCd("0");

        //     Set up to mock ords response
        when(restTemplate.exchange(
                        contains("doc/save"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<SavePDFDocumentResponse>>any()))
                .thenReturn(responseEntity1);

        var processCCsResponse = new ProcessCCsResponse();
        ResponseEntity<ProcessCCsResponse> responseEntity2 =
                new ResponseEntity<>(processCCsResponse, HttpStatus.OK);

        //     Set up to mock ords response
        when(restTemplate.exchange(
                        contains("doc/processCCs"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessCCsResponse>>any()))
                .thenReturn(responseEntity2);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);
        controller.processDocumentsSvc(folderName, folderShortName, processedDate);
    }

    @Test
    public void processReportsSvcTest() throws IOException {
        var folderName = inputDir + "Processed_2020-03-24/R-Lists/";
        var folderShortName = "R-Lists";
        var processedDate = "2020-03-24";

        //     Set up to mock ords response
        Map<String, String> m = new HashMap<>();
        ResponseEntity<Map<String, String>> responseEntity = new ResponseEntity<>(m, HttpStatus.OK);

        // Set up to mock ords response
        when(restTemplate.exchange(
                        AdditionalMatchers.not(contains("tokenvalue")),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenReturn(responseEntity);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);
        controller.processReportsSvc(folderName, processedDate);
    }

    @Test
    public void processReportsSvcTestFail() throws IOException {
        var folderName = inputDir + "Processed_2020-03-24/R-Lists/";
        var folderShortName = "R-Lists";
        var processedDate = "2020-03-24";

        ProcessController processController =
                new ProcessController(emailSender, restTemplate, objectMapper);

        // Set up to mock ords response
        when(restTemplate.exchange(
                        AdditionalMatchers.not(contains("tokenvalue")),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenThrow(ORDSException.class);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);

        Assertions.assertThrows(
                ORDSException.class, () -> controller.processReportsSvc(folderName, processedDate));
    }

    @Test
    public void saveErrorTest() throws IOException {
        String errMsg = "AA";
        String date = "AA";
        String fileName = "AA";
        String fileContentXml = "AA";

        Map<String, String> m = new HashMap<>();
        ResponseEntity<Map<String, String>> responseEntity = new ResponseEntity<>(m, HttpStatus.OK);

        // Set up to mock ords response
        when(restTemplate.exchange(
                        AdditionalMatchers.not(contains("tokenvalue")),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenReturn(responseEntity);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);
        controller.saveError(
                errMsg, date, fileName, fileContentXml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void saveErrorTestFail() throws IOException {

        String errMsg = "AA";
        String date = "AA";
        String fileName = "AA";
        String fileContentXml = "AA";

        // Set up to mock ords response
        when(restTemplate.exchange(
                        AdditionalMatchers.not(contains("tokenvalue")),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenThrow(ORDSException.class);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);

        Assertions.assertThrows(
                ORDSException.class,
                () ->
                        controller.saveError(
                                errMsg,
                                date,
                                fileName,
                                fileContentXml.getBytes(StandardCharsets.UTF_8)));
    }
}
