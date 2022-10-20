package ca.bc.gov.open.crdp.process.transformer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAutoConfiguration
@SpringBootApplication
@EnableScheduling
public class ProcessTransformerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessTransformerApplication.class, args);
    }
}
