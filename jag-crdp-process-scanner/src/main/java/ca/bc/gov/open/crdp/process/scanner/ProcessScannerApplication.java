package ca.bc.gov.open.crdp.process.scanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProcessScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessScannerApplication.class, args);
    }
}
