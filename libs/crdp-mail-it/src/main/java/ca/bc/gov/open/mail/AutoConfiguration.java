package ca.bc.gov.open.mail;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ca.bc.gov.open.mail.api.MailSendApi;
import ca.bc.gov.open.mail.api.handler.ApiClient;

@Configuration
@ComponentScan
@EnableConfigurationProperties(MailSendProperties.class)
public class AutoConfiguration {

    @Bean({"mailItApiClient"})
    public ApiClient apiClient(MailSendProperties mailSendProperties)  {
        ApiClient apiClient = new ApiClient();
        //Setting this to null will make it use the baseUrl instead
        apiClient.setServerIndex(null);
        apiClient.setBasePath(mailSendProperties.getBasePath());
        return apiClient;
    }
    
    @Bean
    public MailSendApi mailApi(@Qualifier("mailItApiClient") ApiClient apiClient) {
        return new MailSendApi(apiClient);
    }
    
}
