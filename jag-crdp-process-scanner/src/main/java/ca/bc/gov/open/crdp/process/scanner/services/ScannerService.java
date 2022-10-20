package ca.bc.gov.open.crdp.process.scanner.services;

import ca.bc.gov.open.crdp.process.models.*;
import ca.bc.gov.open.crdp.process.scanner.configuration.QueueConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.in-file-dir}")
    private String inFileDir = "/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    private final AmqpAdmin amqpAdmin;
    private final Queue scannerQueue;
    private final QueueConfig queueConfig;

    private static List<String> headFolderList = new ArrayList<String>();
    private static String
            processFolderName; // current "Processed_yyyy_nn" folder name (not full path).

    private static TreeMap<String, String> processedFilesToMove =
            new TreeMap<String, String>(); // completed files.
    private static TreeMap<String, String> erredFilesToMove =
            new TreeMap<String, String>(); // erred files.

    private static TreeMap<String, String> processedFoldersToMove =
            new TreeMap<String, String>(); // completed folders.
    private static TreeMap<String, String> erredFoldersToMove =
            new TreeMap<String, String>(); // erred folders.

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
    @Scheduled(cron = "0/2 * * * * *") // Every 2 sec - for testing purpose
    public void CRDPScanner() {
        // re-initialize arrays. Failing to do this can result in unpredictable results.
        headFolderList = new ArrayList<String>();
        processedFilesToMove = new TreeMap<String, String>(); // completed files.
        erredFilesToMove = new TreeMap<String, String>(); // erred files.
        processedFoldersToMove = new TreeMap<String, String>(); // completed folders.
        erredFoldersToMove = new TreeMap<String, String>(); // erred folders.

        // File object
        File mainDir = new File(inFileDir);

        if (mainDir.exists() && mainDir.isDirectory()) {
            // array for files and sub-directories of directory pointed by mainDir
            File arr[] = mainDir.listFiles();

            // Calling recursive method
            try {
                recursiveScan(arr, 0, 0);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                // move files into in-progress folder
                for (Entry<String, String> m : processedFilesToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }

                // move folders into in-progress folder
                for (Entry<String, String> m : processedFoldersToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
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

    public static final List<String> extractPDFFileNames(String folderName) throws IOException {
        /** Purpose of this service is to extract a list of file names from a given folder */
        List<String> pdfs = new ArrayList<>();

        try {
            File file = new File(folderName);
            File[] files = file.listFiles();
            for (File f : files) {
                if (FilenameUtils.getExtension(f.getName()).equalsIgnoreCase("pdf")) {
                    pdfs.add(f.getCanonicalPath());
                }
            }
            return pdfs;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public static final String extractXMLFileName(String[] fileList, String regex)
            throws IOException {
        /**
         * Purpose of this service is to extract a file from a list of file names given a specific
         * regex.
         */
        String result = null;
        if (fileList == null || fileList.length == 0 || regex == null) {
            throw new IOException(
                    "Unsatisfied parameter requirement(s) at CRDP.Source.ProcessIncomingFile.Java:extractXMLFileName");
        }
        try {
            for (int i = 0; i < fileList.length; i++) {
                if (Pattern.matches(regex, fileList[i])) {
                    if (result != null)
                        throw new IOException(
                                "Multiple files found satisfying regex at CRDP.Source.ProcessIncomingFile.Java:extractXMLFileName. Should only be one.");
                    result = fileList[i];
                }
            }
            return result;
        } catch (IOException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    private void recursiveScan(File[] arr, int index, int level) throws IOException {
        // terminate condition
        if (index == arr.length) return;
        try {
            // for root folder files (Audit and Status).
            if (arr[index].isFile()) {
                // processFile(arr[index]);
                log.info("Found file: " + arr[index].getName());
            }

            // for sub-directories
            //            if (arr[index].isDirectory()) {
            //
            //                // Retain the name of the current process folder short name
            //                // and add to list for deletion at the end of processing.
            //                if (isProcessedFolder(arr[index].getName())) {
            //                    processFolderName = arr[index].getName();
            //                    headFolderList.add(processFolderName);
            //                }
            //
            //                if (isProcessedFolder(arr[index].getName())
            //                        || isProcessedSubFolder(arr[index].getName())) {
            //                    if ("CCs".equals(arr[index].getName())) {
            //                        processFolder(
            //                                arr[index].getCanonicalPath() + "/", "CCs",
            // "ProcessDocuments");
            //                    }
            //                    if ("JUS178s".equals(arr[index].getName())) {
            //                        processFolder(
            //                                arr[index].getCanonicalPath() + "/", "JUS178s",
            // "ProcessReports");
            //                    }
            //                    if ("Letters".equals(arr[index].getName())) {
            //                        processFolder(
            //                                arr[index].getCanonicalPath() + "/", "Letters",
            // "ProcessDocuments");
            //                    }
            //                    if ("R-Lists".equals(arr[index].getName())) {
            //                        processFolder(
            //                                arr[index].getCanonicalPath() + "/", "R-Lists",
            // "ProcessReports");
            //                    }
            //                    // recursion for sub-directories
            //                    recursiveScan(arr[index].listFiles(), 0, level + 1);
            //                }
            //            }

        } catch (Exception ex) {
            log.error(
                    "An error was captured from the CRDP Scanner. Message: "
                            + ex.getLocalizedMessage());
        }

        // recursion for main directory
        recursiveScan(arr, ++index, level);
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
}
