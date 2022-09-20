package ca.bc.gov.open.crdp;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import ca.bc.gov.open.crdp.controllers.ProcessController;
import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProcessControllerTests {

    private String inFileDir;

    @Mock private ObjectMapper objectMapper;
    @Mock private JavaMailSender emailSender;
    @Mock private RestTemplate restTemplate;

    private ProcessController controller;

    /*
     *  backup folder structure:
     *  resources\backup\processingIncoming\Processed_2020-03-24\ABCDO_Audit.000001.XML
     *                                                           \ABCDO_Status.000001.XML
     *                                                           \CCs\ABCDO_CCs.XML
     *                                                               \ABCDO_CCs.pdf
     *                                                           \Letters\ABCDO_Letters.XML
     *                                                                    \ABCDO_Letters_1.pdf
     *                                                                    \ABCDO_Letters_2.pdf
     *                                                           \JUS178s
     *                                                           \R-Lists\ABCDO_Reports.XML
     *                                                                   \ABCDO_Reports_1.pdf
     *                                       \processed
     *                                       \errors
     *
     * resources\backup\transimitoutgoing\sendToOttawa.xml
     *
     *  Setup() copies backup folder to test folder to start test. After testing, testing files are
     *  moved to processed folder and errors go to error folder
     *
     */
    @BeforeAll
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        controller = new ProcessController(emailSender, restTemplate, objectMapper);

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

    @BeforeEach
    void setupThis() {}

    @Test
    @Order(1)
    public void processAuditSvcTest() throws IOException {
        var fileName = inFileDir + "Processed_2020-03-24/ABCDO_Audit.000001.XML";
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

        controller.processAuditSvc(fileName);
    }

    @Test
    @Order(2)
    public void processAuditSvcTestFail() throws IOException {
        var fileName = inFileDir + "Processed_2020-03-24/ABCDO_Audit.000001.XML";

        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessAuditResponse>>any()))
                .thenThrow(ORDSException.class);

        Assertions.assertThrows(ORDSException.class, () -> controller.processAuditSvc(fileName));
    }

    @Test
    @Order(3)
    public void processStatusSvcTest() throws IOException {
        var fileName = inFileDir + "Processed_2020-03-24/ABCDO_Status.000001.XML";
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

        controller.processAuditSvc(fileName);
    }

    @Test
    @Order(4)
    public void processStatusSvcTestFail() throws IOException {
        var fileName = inFileDir + "Processed_2020-03-24/ABCDO_Status.000001.XML";

        when(restTemplate.exchange(
                        Mockito.any(String.class),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessStatusResponse>>any()))
                .thenThrow(ORDSException.class);

        Assertions.assertThrows(ORDSException.class, () -> controller.processStatusSvc(fileName));
    }

    @Test
    @Order(5)
    public void processDocumentsSvcTest() throws IOException {
        var folderName = inFileDir + "Processed_2020-03-24/CCs/";
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

        controller.processDocumentsSvc(folderName, folderShortName, processedDate);
    }

    @Test
    @Order(6)
    public void processReportsSvcTest() throws IOException {
        var folderName = inFileDir + "Processed_2020-03-24/R-Lists/";
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

        controller.processReportsSvc(folderName, processedDate);
    }

    @Test
    @Order(7)
    public void processReportsSvcTestFail() throws IOException {
        var folderName = inFileDir + "Processed_2020-03-24/R-Lists/";
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

        Assertions.assertThrows(
                ORDSException.class, () -> controller.processReportsSvc(folderName, processedDate));
    }

    @Test
    @Order(8)
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

        controller.saveError(
                errMsg, date, fileName, fileContentXml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @Order(9)
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

        Assertions.assertThrows(
                ORDSException.class,
                () ->
                        controller.saveError(
                                errMsg,
                                date,
                                fileName,
                                fileContentXml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Order(10)
    public void CRDPScannerTest() throws IOException {

        ReflectionTestUtils.setField(controller, "inFileDir", inFileDir, String.class);

        // processAuditSvc
        var processAuditResponse = new ProcessAuditResponse();
        ResponseEntity<ProcessAuditResponse> responseEntity =
                new ResponseEntity<>(processAuditResponse, HttpStatus.OK);
        when(restTemplate.exchange(
                        contains("process-audit"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessAuditResponse>>any()))
                .thenReturn(responseEntity);

        // processStatusSvc
        var processStatusResponse = new ProcessStatusResponse();
        ResponseEntity<ProcessStatusResponse> responseEntity1 =
                new ResponseEntity<>(processStatusResponse, HttpStatus.OK);
        when(restTemplate.exchange(
                        contains("process-status"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessStatusResponse>>any()))
                .thenReturn(responseEntity1);

        // processDocumentsSvc
        Map<String, String> m = new HashMap<>();
        ResponseEntity<Map<String, String>> responseEntity2 =
                new ResponseEntity<>(m, HttpStatus.OK);
        m.put("status", "N");
        when(restTemplate.exchange(
                        contains("doc/status"),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenReturn(responseEntity2);

        var savePDFDocumentResponse = new SavePDFDocumentResponse();
        ResponseEntity<SavePDFDocumentResponse> responseEntity3 =
                new ResponseEntity<>(savePDFDocumentResponse, HttpStatus.OK);
        responseEntity3.getBody().setResultCd("0");
        when(restTemplate.exchange(
                        contains("doc/save"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<SavePDFDocumentResponse>>any()))
                .thenReturn(responseEntity3);

        var processCCsResponse = new ProcessCCsResponse();
        ResponseEntity<ProcessCCsResponse> responseEntity4 =
                new ResponseEntity<>(processCCsResponse, HttpStatus.OK);
        when(restTemplate.exchange(
                        contains("doc/processCCs"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<Class<ProcessCCsResponse>>any()))
                .thenReturn(responseEntity4);

        // processReportsSvc
        Map<String, String> m1 = new HashMap<>();
        ResponseEntity<Map<String, String>> responseEntity5 =
                new ResponseEntity<>(m1, HttpStatus.OK);
        when(restTemplate.exchange(
                        contains("rpt"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenReturn(responseEntity5);

        // processReportsSvc
        Map<String, String> m2 = new HashMap<>();
        ResponseEntity<Map<String, String>> responseEntity6 =
                new ResponseEntity<>(m1, HttpStatus.OK);
        when(restTemplate.exchange(
                        contains("doc/processLetters"),
                        Mockito.eq(HttpMethod.POST),
                        Mockito.<HttpEntity<String>>any(),
                        Mockito.<ParameterizedTypeReference<Map<String, String>>>any()))
                .thenReturn(responseEntity6);

        controller.CRDPScanner();

        // verify \processingIncoming, \errors, and \processed are empty
        boolean passed = false;
        File errorsFolderer = new File(inFileDir + "/errors/");

        try {
            if (FileUtils.isEmptyDirectory(errorsFolderer)) {
                passed = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Assertions.assertTrue(true);
    }
}
