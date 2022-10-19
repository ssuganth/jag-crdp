package ca.bc.gov.open.crdp.transmit.receiver.configuration;

import java.util.Properties;

import ca.bc.gov.open.mail.MailSendProperties;
import ca.bc.gov.open.mail.NotificationService;
import ca.bc.gov.open.mail.NotificationServiceImpl;
import ca.bc.gov.open.mail.api.MailSendApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.ws.config.annotation.EnableWs;

@Configuration
@Slf4j
@EnableConfigurationProperties({MailSendProperties.class})
public class MailConfig extends MailSenderAutoConfiguration {

    @Bean
    public NotificationService notificationService(MailSendApi mailSendApi) {
        return new NotificationServiceImpl(mailSendApi);
    }
}
