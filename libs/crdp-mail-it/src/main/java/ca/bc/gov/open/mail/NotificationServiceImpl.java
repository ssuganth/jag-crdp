package ca.bc.gov.open.mail;

import ca.bc.gov.open.mail.api.MailSendApi;
import ca.bc.gov.open.mail.api.handler.ApiException;
import ca.bc.gov.open.mail.api.model.EmailObject;
import ca.bc.gov.open.mail.api.model.EmailRequest;
import ca.bc.gov.open.mail.api.model.EmailRequestContent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class NotificationServiceImpl implements NotificationService {

    Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final MailSendApi mailSendApi;

    public NotificationServiceImpl(MailSendApi mailSendApi) {
        this.mailSendApi = mailSendApi;
    }

    @Override
    public void notify(String subject, String message, String from, String to) {
        try {
            EmailRequest emailRequest = new EmailRequest();
            EmailObject emailFrom = new EmailObject();
            emailFrom.email(from);
            EmailObject emailTo = new EmailObject();
            emailTo.email(to);
            EmailRequestContent content = new EmailRequestContent();
            content.setValue(message);

            emailRequest.setFrom(emailFrom);
            emailRequest.setTo(Collections.singletonList(emailTo));
            emailRequest.setSubject(subject);
            emailRequest.setContent(content);

            logger.info("Sending email message");
            mailSendApi.mailSend(emailRequest);
        } catch (ApiException e) {
            logger.error(e.getMessage());
        }
    }
}
