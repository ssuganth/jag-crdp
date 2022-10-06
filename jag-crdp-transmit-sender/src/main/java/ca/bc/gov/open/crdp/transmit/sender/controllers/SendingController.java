package ca.bc.gov.open.crdp.transmit.sender.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@Slf4j
public class SendingController {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.out-file-dir}")
    private String outFileDir = "/";

    @Value("${crdp.notification-addresses}")
    public void setErrNotificationAddresses(String addresses) {
        SendingController.errNotificationAddresses = addresses;
    }

    private static String errNotificationAddresses = "";

    @Value("${crdp.smtp-from}")
    public void setDefaultSmtpFrom(String from) {
        SendingController.defaultSmtpFrom = from;
    }

    private static String defaultSmtpFrom = "";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JavaMailSender emailSender;

    @Autowired
    public SendingController(
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
    //    @Scheduled(cron = "${crdp.cron-job-incomming-file}")
    @Scheduled(cron = "0/2 * * * * *") // Every 2 sec - for testing purpose
    public void GenerateIncomingRequestFile() {
        // Create file to outgoing file directory
        //            File outgoingFile = new File(outFileDir + "/" +
        // resp.getBody().getFileName());
        //            try (FileOutputStream outputStream = new FileOutputStream(outgoingFile)) {
        //
        // outputStream.write(resp.getBody().getFile().getBytes(StandardCharsets.UTF_8));
        //            } catch (IOException e) {
        //                log.error("Fail to create " + outFileDir +
        // resp.getBody().getFileName());
        //                throw e;
        //            }
    }
}
