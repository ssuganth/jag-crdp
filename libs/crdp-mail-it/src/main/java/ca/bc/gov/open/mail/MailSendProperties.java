package ca.bc.gov.open.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bcgov")
public class MailSendProperties {

	@Value("${bcgov.mail-it.base-path}")
	private String basePath;

	@Value("${bcgov.mail-it.from-email}")
	private String fromEmail;

	@Value("${bcgov.mail-it.default-email}")
	private String defaultEmail;

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}

	public String getFromEmail() {
		return fromEmail;
	}

	public void setFromEmail(String toEmail) {
		this.fromEmail = toEmail;
	}

	public String getDefaultEmail() { return defaultEmail; }

	public void setDefaultEmail(String defaultEmail) { this.defaultEmail = defaultEmail; }
}
