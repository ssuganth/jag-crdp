package ca.bc.gov.open.sftp.starter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bcgov")
public class SftpProperties {

    @Value("${bcgov.sftp.host}")
    private String host;

    @Value("${bcgov.sftp.port}")
    private String port;

    @Value("${bcgov.sftp.username}")
    private String username;

    @Value("${bcgov.sftp.remote-location}")
    private String remoteLocation;

    @Value("${bcgov.sftp.known-hosts-file-name}")
    private String knownHostsFileName;

    @Value("${bcgov.sftp.ssh-private-key}")
    private String sshPrivateKey;

    // Not Apply in ACS-SFEG
    private String sshPrivatePassphrase;
    private String password;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRemoteLocation() {
        return remoteLocation;
    }

    public void setRemoteLocation(String remoteLocation) {
        this.remoteLocation = remoteLocation;
    }

    public String getKnownHostsFileName() {
        return knownHostsFileName;
    }

    public void setKnownHostsFileName(String knownHostsFileName) {
        this.knownHostsFileName = knownHostsFileName;
    }

    public String getSshPrivateKey() {
        return sshPrivateKey;
    }

    public void setSshPrivateKey(String sshPrivateKey) {
        this.sshPrivateKey = sshPrivateKey;
    }

    public String getSshPrivatePassphrase() {
        return sshPrivatePassphrase;
    }

    public void setSshPrivatePassphrase(String sshPrivatePassphrase) {
        this.sshPrivatePassphrase = sshPrivatePassphrase;
    }

    public SftpProperties() {}
}
