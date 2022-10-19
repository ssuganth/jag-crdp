package ca.bc.gov.open.mail;

import ca.bc.gov.open.mail.api.handler.ApiException;

public interface NotificationService {
    void notify(String subject, String message, String from, String to) throws ApiException;
}
