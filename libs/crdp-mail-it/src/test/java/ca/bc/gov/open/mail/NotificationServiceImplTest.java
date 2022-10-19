package ca.bc.gov.open.mail;

import ca.bc.gov.open.mail.api.MailSendApi;
import ca.bc.gov.open.mail.api.handler.ApiException;
import ca.bc.gov.open.mail.api.model.EmailRequest;
import ca.bc.gov.open.mail.api.model.EmailResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class NotificationServiceImplTest {
    @Mock
    public MailSendApi mailSendApiMock;

    public NotificationServiceImpl sut;

    @Before
    public void init() throws ApiException {
        MockitoAnnotations.openMocks(this);
        EmailResponse emailResponseMock = new EmailResponse();
        emailResponseMock.setAcknowledge(true);
        Mockito.when(mailSendApiMock.mailSend(Mockito.any(EmailRequest.class))).thenReturn(emailResponseMock);
        sut = new NotificationServiceImpl(mailSendApiMock);
    }

    @Test
    public void notifyShouldNotThrowException() throws ApiException {
       Assertions.assertDoesNotThrow(() ->  sut.notify("subject", "message", "from", "to"));
    }
}
