package ca.bc.gov.open.crdp.process.scanner.services;

import ca.bc.gov.open.crdp.models.MqErrorLog;
import ca.bc.gov.open.crdp.process.scanner.configuration.QueueConfig;
import ca.bc.gov.open.sftp.starter.JschSessionProvider;
import ca.bc.gov.open.sftp.starter.SftpProperties;
import ca.bc.gov.open.sftp.starter.SftpServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.server.endpoint.annotation.Endpoint;

@Endpoint
@Slf4j
public class ScannerService {

    @Value("${crdp.in-file-dir}")
    private String inFileDir = "/";

    @Value("${crdp.in-progress-dir}")
    private String inProgressDir = "/";

    @Value("${crdp.record-ttl-hour}")
    private int recordTTLHour = 24;

    @Autowired JschSessionProvider jschSessionProvider;
    private SftpServiceImpl sftpService;
    private final SftpProperties sftpProperties;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    private final AmqpAdmin amqpAdmin;
    private final Queue scannerQueue;
    private final QueueConfig queueConfig;

    private static String
            processFolderName; // current "Processed_yyyy_nn" folder name (not full path).

    private static TreeMap<String, String> inProgressFilesToMove = new TreeMap<String, String>();
    private static TreeMap<String, String> inProgressFoldersToMove =
            new TreeMap<String, String>(); // completed files.

    DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");

    @Autowired
    public ScannerService(
            @Qualifier("scanner-queue") Queue scannerQueue,
            AmqpAdmin amqpAdmin,
            QueueConfig queueConfig,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            SftpProperties sftpProperties) {
        this.scannerQueue = scannerQueue;
        this.amqpAdmin = amqpAdmin;
        this.queueConfig = queueConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.sftpProperties = sftpProperties;
    }

    /** The primary method for the Java service to scan CRDP directory */
    @Scheduled(cron = "${crdp.cron-job-incoming-file}")
    public void CRDPScanner() {
        sftpService = new SftpServiceImpl(jschSessionProvider, sftpProperties);

        // re-initialize arrays
        inProgressFilesToMove = new TreeMap<String, String>();
        inProgressFoldersToMove = new TreeMap<String, String>();

        LocalDateTime scanDateTime = LocalDateTime.now();

        // File object
        sftpService.makeFolder(inFileDir);

        if (sftpService.exists(inFileDir)) {
            // create inProgress folder
            if (!sftpService.exists(inProgressDir)) {
                sftpService.makeFolder(inProgressDir);
            }

            String[] arr = sftpService.listFiles(inFileDir).toArray(new String[0]);

            // Calling recursive method
            try {
                recursiveScan(arr, 0, 0);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (inProgressFilesToMove.isEmpty() && inProgressFoldersToMove.isEmpty()) {
                log.info("No file/fold found, end current scan session: " + scanDateTime);
                return;
            }

            try {
                // enqueue a timestamp of current scan
                enQueue("scanning time:" + customFormatter.format(scanDateTime));

                // move files into in-progress folder
                for (Entry<String, String> m : inProgressFilesToMove.entrySet()) {
                    sftpService.moveFile(m.getKey(), m.getValue());
                    enQueue(m.getValue());
                }

                for (Entry<String, String> m : inProgressFoldersToMove.entrySet()) {
                    sftpService.put(new FileInputStream(m.getKey()), m.getValue());
                    enQueue(m.getValue());
                }
                cleanUp(inFileDir);
                log.info("Scan Complete");
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    private void cleanUp(String headFolderPath) {
        // delete processed folders (delivered from Ottawa).
        for (var folder : sftpService.listFiles(headFolderPath)) {
            if (getFileName(folder).equals("inProgress")) {
                continue;
            }

            if (getFileName(folder).equals("Errors") || getFileName(folder).equals("Completed")) {
                for (var f : sftpService.listFiles(folder)) {
                    if (new Date().getTime() - sftpService.lastModify(f)
                            > recordTTLHour * 60 * 60 * 1000) {
                        sftpService.removeFolder(f);
                    }
                }
                continue;
            }
            sftpService.removeFolder(folder);
        }
    }

    private void recursiveScan(String[] arr, int index, int level) throws IOException {
        // terminate condition
        if (index == arr.length) return;
        try {
            // for root folder files (Audit and Status).
            if (!sftpService.isDirectory(arr[index])) {
                inProgressFilesToMove.put(
                        arr[index], inProgressDir + Paths.get(arr[index]).getFileName().toString());
            }

            // for sub-directories
            if (sftpService.isDirectory(arr[index])) {
                // Retain the name of the current process folder short name
                // and add to list for deletion at the end of processing.
                if (isProcessedFolder(getFileName(arr[index]))) {
                    processFolderName = getFileName(arr[index]);
                }
                if (isProcessedFolder(getFileName(arr[index]))
                        || isProcessedSubFolder(getFileName(arr[index]))) {
                    if ("CCs".equals(getFileName(arr[index]))
                            || "Letters".equals(getFileName(arr[index]))
                            || "R-Lists".equals(getFileName(arr[index]))
                            || "JUS178s".equals(getFileName(arr[index]))) {
                        inProgressFoldersToMove.put(
                                arr[index],
                                inProgressDir + processFolderName + "/" + getFileName(arr[index]));
                    } else {
                        // recursion for sub-directories
                        recursiveScan(
                                sftpService.listFiles(arr[index]).toArray(new String[0]),
                                0,
                                level + 1);
                    }
                }
            }

        } catch (Exception ex) {
            log.error(
                    "An error was captured from the CRDP Scanner. Message: "
                            + ex.getLocalizedMessage());
        }

        // recursion for main directory
        recursiveScan(arr, ++index, level);
    }

    private static boolean isProcessedFolder(String name) {
        String processedRegex =
                "\\bProcessed_\\w+[-][0-9][0-9][-][0-9][0-9]"; // \bProcessed_\w+[-][0-9][0-9][-][0-9][0-9]
        return Pattern.matches(processedRegex, name);
    }

    private static boolean isProcessedSubFolder(String name) {
        if ("CCs".equals(name)
                || "JUS178s".equals(name)
                || "Letters".equals(name)
                || "R-Lists".equals(name)) return true;
        else return false;
    }

    private void enQueue(String filePath) throws JsonProcessingException {
        try {
            this.rabbitTemplate.convertAndSend(
                    queueConfig.getTopicExchangeName(),
                    queueConfig.getScannerRoutingkey(),
                    filePath);
        } catch (Exception ex) {
            log.error(
                    objectMapper.writeValueAsString(
                            new MqErrorLog(
                                    "Enqueue failed", "RecursiveScan", ex.getMessage(), filePath)));
        }
    }

    private String getFileName(String filePath) {
        return filePath.substring(filePath.lastIndexOf("/") + 1);
    }
}
