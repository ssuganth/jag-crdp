package ca.bc.gov.open.crdp.transmit.sender.services;

import ca.bc.gov.open.crdp.transmit.models.ReceiverPub;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.server.endpoint.annotation.Endpoint;

@Endpoint
@Slf4j
public class SenderService {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.out-file-dir}")
    private String outFileDir = "/";

    @Value("${crdp.notification-addresses}")
    public void setErrNotificationAddresses(String addresses) {
        SenderService.errNotificationAddresses = addresses;
    }

    private static String errNotificationAddresses = "";

    @Value("${crdp.smtp-from}")
    public void setDefaultSmtpFrom(String from) {
        SenderService.defaultSmtpFrom = from;
    }

    private static String defaultSmtpFrom = "";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JavaMailSender emailSender;

    @Autowired
    public SenderService(
            JavaMailSender emailSender, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.emailSender = emailSender;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void update(ReceiverPub xmlPub) {
        // Create file to outgoing file directory
        //  tFileName());
        //         }         File outgoingFile = new File(outFileDir + "/" +
        ////         xmlPub.getFileName());
        ////         try (
        ////            FileOutputStream outputStream = new FileOutputStream(outgoingFile)) {
        ////
        // outputStream.write(resp.getBody().getFile().getBytes(StandardCharsets.UTF_8));
        ////         } catch (IOException e) {
        ////            log.error("Fail to create " + outFileDir +
        ////            resp.getBody().getFileName());
        ////         }
    }

    public void send(ReceiverPub xmlPub) {}
}
