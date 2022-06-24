package ca.bc.gov.open.crdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CrdpApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrdpApplication.class, args);
    }
}
