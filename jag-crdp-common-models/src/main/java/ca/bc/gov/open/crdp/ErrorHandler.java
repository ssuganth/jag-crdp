package ca.bc.gov.open.crdp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;

@Slf4j
public class ErrorHandler {
    public static void processError(String targetAddresses, String defaultSmtpFrom, String errMsg) {
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

        String subject = "An error was received from the CRDP System";

        try {
            String[] addresses = targetAddresses.split(" ,");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(defaultSmtpFrom); // watt.common.defaultSmtpFrom
            message.setSubject(subject);
            message.setText(subjectMsg + "\n" + errMsg);
            message.setTo(addresses);
            //            emailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send email notification to: " + targetAddresses);
        }
    }
}
