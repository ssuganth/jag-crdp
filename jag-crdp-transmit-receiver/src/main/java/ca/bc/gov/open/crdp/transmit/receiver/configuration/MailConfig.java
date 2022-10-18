package ca.bc.gov.open.crdp.transmit.receiver.configuration;

import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.ws.config.annotation.EnableWs;

@EnableWs
@Configuration
@Slf4j
public class MailConfig extends MailSenderAutoConfiguration {

    @Value("${crdp.smtp-username}")
    private String username = "";

    @Value("${crdp.smtp-password}")
    private String password = "";

    @Bean
    public JavaMailSender javaMailSender() {
        String host = "smtp.gmail.com";
        String port = "587";
        String protocol = "SMTP";
        if (!password.isEmpty()
                && !username.isEmpty()
                && !protocol.isEmpty()
                && !port.isEmpty()
                && !host.isEmpty()) {
            JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
            javaMailSender.setHost(host);
            javaMailSender.setPort(Integer.parseInt(port));
            javaMailSender.setProtocol(protocol);
            javaMailSender.setUsername(username);
            javaMailSender.setPassword(password);
            javaMailSender.setJavaMailProperties(getMailProperties());
            return javaMailSender;
        } else {
            log.error("Mail Config Failure");
            return null;
        }
    }

    private Properties getMailProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.quitwait", "false");
        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        properties.setProperty("mail.smtp.socketFactory.fallback", "false");
        properties.setProperty("mail.debug", "true");
        return properties;
    }
}
