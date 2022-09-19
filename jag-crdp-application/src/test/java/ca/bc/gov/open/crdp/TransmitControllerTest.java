package ca.bc.gov.open.crdp;

import static org.mockito.Mockito.when;

import ca.bc.gov.open.crdp.controllers.TransmitController;
import ca.bc.gov.open.crdp.models.GenerateIncomingReqFileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransmitControllerTest {
    private String outputDir;
    @Mock private ObjectMapper objectMapper;
    @Mock private JavaMailSender emailSender;
    @Mock private RestTemplate restTemplate;

    @Mock private TransmitController controller;

    @BeforeEach
    public void setUp() throws IOException {

        MockitoAnnotations.openMocks(this);

        String appPath = new File("").getCanonicalPath();
        outputDir = appPath + "/src/test/resources/transimitoutgoing/";
    }

    @Test
    public void GenerateIncomingRequestFileTest() throws IOException {
        var generateIncomingReqFileResponse = new GenerateIncomingReqFileResponse();

        generateIncomingReqFileResponse.setFile("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        generateIncomingReqFileResponse.setFileName("dummy.xml");
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

        controller = new TransmitController(emailSender, restTemplate, objectMapper);
        ReflectionTestUtils.setField(controller, "outFileDir", outputDir, String.class);
        controller.GenerateIncomingRequestFile();
    }
}
