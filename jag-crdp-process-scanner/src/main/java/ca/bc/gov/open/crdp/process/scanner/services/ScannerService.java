package ca.bc.gov.open.crdp.process.scanner.services;

import ca.bc.gov.open.crdp.models.MqErrorLog;
import ca.bc.gov.open.crdp.process.scanner.configuration.QueueConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    private final AmqpAdmin amqpAdmin;
    private final Queue scannerQueue;
    private final QueueConfig queueConfig;

    private static List<String> headFolderList = new ArrayList<String>();
    private static String
            processFolderName; // current "Processed_yyyy_nn" folder name (not full path).

    private static TreeMap<String, String> inProgressFilesToMove = new TreeMap<String, String>();
    private static TreeMap<String, String> inProgressFoldersToMove =
            new TreeMap<String, String>(); // completed files.

    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");

    @Autowired
    public ScannerService(
            @Qualifier("scanner-queue") Queue scannerQueue,
            AmqpAdmin amqpAdmin,
            QueueConfig queueConfig,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate) {
        this.scannerQueue = scannerQueue;
        this.amqpAdmin = amqpAdmin;
        this.queueConfig = queueConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    // CRON Job Name:   CRDP Incoming File Processor
    //                  2020/04/14 14:44:14 600s
    // Pattern      :   "0/10 * * * * *"
    // Interval     :   Every 10 minutes
    /** The primary method for the Java service to scan CRDP directory */
    //    @Scheduled(cron = "${crdp.cron-job-outgoing-file}")
    @Scheduled(cron = "0/5 * * * * *") // Every 5 sec - for testing purpose
    public void CRDPScanner() {
        // re-initialize arrays. Failing to do this can result in unpredictable results.
        headFolderList = new ArrayList<String>();
        inProgressFilesToMove = new TreeMap<String, String>();
        inProgressFoldersToMove = new TreeMap<String, String>(); // completed files.

        // File object
        File mainDir = new File(inFileDir);

        if (mainDir.exists() && mainDir.isDirectory()) {

            // create inProgress folder
            File inProDir = new File(inProgressDir);
            if (!inProDir.exists()) {
                inProDir.mkdir();
            }

            // array for files and sub-directories of directory pointed by mainDir
            File arr[] = mainDir.listFiles();

            // Calling recursive method
            try {
                recursiveScan(arr, 0, 0);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (inProgressFilesToMove.isEmpty() && inProgressFoldersToMove.isEmpty()) {
                log.info("No file/fold found, end current scan session");
                return;
            }

            try {
                // enqueue a timestamp of current scan
                enQueue("scanning time:" + new Timestamp(System.currentTimeMillis()).toString());

                // move files into in-progress folder
                for (Entry<String, String> m : inProgressFilesToMove.entrySet()) {
                    File f = new File(m.getKey());
                    move(f, m.getValue());
                    enQueue(m.getValue() + f.getName());
                }

                for (Entry<String, String> m : inProgressFoldersToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                    enQueue(m.getValue());
                }
                cleanUp(inFileDir, headFolderList);
                log.info("Scan Complete");
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    private static void cleanUp(String headFolderPath, List<String> headFolderList) {
        // delete processed folders (delivered from Ottawa).
        for (int i = 0; i < headFolderList.size(); i++) {
            if (headFolderList.get(i).equals("inProgress")
                    || headFolderList.get(i).equals("Errors")
                    || headFolderList.get(i).equals("Completed")) {
                continue;
            }
            removeFolderSvc(headFolderPath + "/" + headFolderList.get(i));
        }
        // Todo:
        // clean out 'Completed' and 'Errors' folder.
        // removeFolderSvc(headFolderPath + "/processed");
        // makeFolderSvc(headFolderPath + "/processed");
    }

    /** The primary method for the Java service to create a folder */
    public static final void makeFolderSvc(String folderPath) {
        File target = new File(folderPath);
        // The folderPath must IN a folder owned by wmadmin OR be in a folder with o-rwx permissions
        // set.
        try {
            FileUtils.forceMkdir(target);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** The primary method for the Java service to delete a folder */
    public static final void removeFolderSvc(String folderPath) {
        // The folderPath must be owned by wmadmin OR have o-rwx permissions set.
        File target = new File(folderPath);
        try {
            if (target.exists()) {
                FileUtils.deleteDirectory(target);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void recursiveScan(File[] arr, int index, int level) throws IOException {
        // terminate condition
        if (index == arr.length) return;
        try {
            // for root folder files (Audit and Status).
            if (arr[index].isFile()) {
                inProgressFilesToMove.put(arr[index].getCanonicalPath(), inProgressDir);
                log.info("Found file: " + arr[index].getName());
            }

            // for sub-directories
            if (arr[index].isDirectory()) {
                // Retain the name of the current process folder short name
                // and add to list for deletion at the end of processing.
                if (isProcessedFolder(arr[index].getName())) {
                    processFolderName = arr[index].getName();
                    headFolderList.add(processFolderName);
                }
                if (isProcessedFolder(arr[index].getName())
                        || isProcessedSubFolder(arr[index].getName())) {
                    if ("CCs".equals(arr[index].getName())
                            || "Letters".equals(arr[index].getName())
                            || "R-Lists".equals(arr[index].getName())
                            || "JUS178s".equals(arr[index].getName())) {
                        inProgressFoldersToMove.put(
                                arr[index].getCanonicalPath(),
                                inProgressDir + processFolderName + "/" + arr[index].getName());
                    } else {
                        // recursion for sub-directories
                        recursiveScan(arr[index].listFiles(), 0, level + 1);
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

    private static void move(File file, String targetFolder) throws Exception {
        try {
            if (file.isFile()) {
                // move single file
                moveFileSvc(file.getCanonicalPath(), targetFolder);
            } else {
                // move single folder
                moveFolderSvc(file.getCanonicalPath(), targetFolder);
            }
        } catch (Exception e) {
            throw new Exception("An error was caught moving a file or folder to " + targetFolder);
        }
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

    /** The primary method for the Java service to move a single file */
    public static final void moveFileSvc(String filePath, String targetFolder) {
        // The filePath and targetFolder must be owned by wmadmin
        // OR have o-rwx permissions set.
        File source = new File(filePath);
        File destFolder = new File(targetFolder);

        try {
            FileUtils.moveFileToDirectory(source, destFolder, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** The primary method for the Java service to move a single folder */
    public static final void moveFolderSvc(String folderPath, String targetFolderPath) {
        // The folderPath must be owned by wmadmin OR have o-rwx permissions set.
        File source = new File(folderPath);
        File dest = new File(targetFolderPath);
        try {
            FileUtils.copyDirectory(source, dest);
            FileUtils.deleteDirectory(source);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
