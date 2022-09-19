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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mail.SimpleMailMessage;
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

    @Value("${crdp.notification-addresses}")
    public void setErrNotificationAddresses(String addresses) {
        TransmitController.errNotificationAddresses = addresses;
    }

    private static String errNotificationAddresses;

    @Value("${crdp.smtp-from}")
    public void setDefaultSmtpFrom(String from) {
        TransmitController.defaultSmtpFrom = from;
    }

    private static String defaultSmtpFrom = "";

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

    // CRON Job Name:   CRDP Transmit Outgoing File
    //                  2020/04/14 14:44:14 600s
    // Pattern      :   "0/10 * * * * *"
    // Interval     :   Every 10 minutes
    @PayloadRoot(localPart = "generateIncomingRequestFile")
    @ResponsePayload
    @Scheduled(cron = "${crdp.cron-job-incomming-file}")
    public void GenerateIncomingRequestFile() throws JsonProcessingException {
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
                processError(resp.getBody().getErrMsg());
            }

            // Create file to outgoing file directory
            File outgoingFile = new File(outFileDir + "/" + resp.getBody().getFileName());
            try (FileOutputStream outputStream = new FileOutputStream(outgoingFile)) {
                outputStream.write(resp.getBody().getFile().getBytes(StandardCharsets.UTF_8));
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

    private void processError(String errMsg) {
        String integrationNameMsg = "CRDP";
        String errorTypeMsg = "NA";
        String errorSubtypeMsg = "NA";
        SimpleDateFormat milliFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String additionalInformation = milliFormatter.format(new Date());
        SimpleDateFormat curDtmFormatter = new SimpleDateFormat("MMM-dd-yyyy HH:mm:ss");
        String curDtm = curDtmFormatter.format(new Date());
        String subjectMsg =
                "Exception: "
                        + integrationNameMsg
                        + " had error "
                        + errorTypeMsg
                        + " at "
                        + errorTypeMsg;
        int notificationFailure = 0;
        int haveVerboseNotification = 0;
        int haveNonVerboseNotification = 0;
        List<String> verboseSendToList = null;
        List<String> verboseCopyToList = null;
        List<String> nonVerboseSendToList = null;
        List<String> nonVerboseCopyToList = null;

        // TO BE CONT..
        String subject = "An error was received from the CRDP System";

        String[] addresses = errNotificationAddresses.split(" ,");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(defaultSmtpFrom);
        message.setSubject(subject);
        message.setText(errMsg);
        message.setTo(addresses);
        emailSender.send(message);
    }

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
