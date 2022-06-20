package ca.bc.gov.open.crdp.controllers;

import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.GenerateIncomingReqFileResponse;
import ca.bc.gov.open.crdp.models.OrdsErrorLog;
import ca.bc.gov.open.crdp.models.RequestSuccessLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@Slf4j
public class TransmitController {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.out-file-dir}")
    private String outFileDir = "/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JavaMailSender emailSender;

    @Autowired
    public TransmitController(
            JavaMailSender emailSender, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.emailSender = emailSender;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PayloadRoot(localPart = "generateIncomingRequestFile")
    @ResponsePayload
    @Scheduled(cron = "0 1 1 * * ?")
    private void GenerateIncomingRequestFile() throws JsonProcessingException {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "incoming-file");

        try {
            HttpEntity<GenerateIncomingReqFileResponse> resp =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            GenerateIncomingReqFileResponse.class);
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog(
                                    "Request Success", "generateIncomingRequestFile")));

            // Error handling
            if (resp.getBody().getStatus().equals("0")) {
                processError();
            }

            // Create file to outgoing file directory
            File outgoingFile = new File(outFileDir + resp.getBody().getFileName());
            try (FileOutputStream outputStream = new FileOutputStream(outgoingFile)) {
                outputStream.write(resp.getBody().getFile());
            } catch (IOException e) {
                log.error("Fail to create " + outFileDir + resp.getBody().getFileName());
                throw e;
            }

        } catch (Exception ex) {
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS",
                                    "generateIncomingRequestFile",
                                    ex.getMessage(),
                                    null)));
            throw new ORDSException();
        }
    }

    private void processError() {}

    // Never being used
    private void DeleteFile(String fileName) throws JsonProcessingException {
        File fileToDelete = new File(outFileDir + fileName);
        try {
            fileToDelete.delete();
        } catch (Exception ex) {
            log.error("Fail to delete " + outFileDir + fileName);
            throw ex;
        }
    }
}
