package ca.bc.gov.open.crdp.transmit.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAutoConfiguration
@SpringBootApplication
@EnableScheduling
public class ProcessReceiverApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessReceiverApplication.class, args);
    }
}
