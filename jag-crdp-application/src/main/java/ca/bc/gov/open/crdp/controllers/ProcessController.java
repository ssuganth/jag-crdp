package ca.bc.gov.open.crdp.controllers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.GenerateIncomingReqFileResponse;
import ca.bc.gov.open.crdp.models.ProcessAuditResponse;
import ca.bc.gov.open.crdp.models.ProcessStatusResponse;
import ca.bc.gov.open.crdp.models.RequestSuccessLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Endpoint
@Slf4j
public class ProcessController {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.in-file-dir}")
    private static String inFileDir = "/";

    @Value("${notification-addresses}")
    private static String errNotificationAddresses = "";

    private static JavaMailSender emailSender;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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

    @Autowired
    public ProcessController(JavaMailSender emailSender, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.emailSender = emailSender;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // -- START OF PRIMARY SERVICES

    /** The primary method for the Java service to scan CRDP directory */
    @Scheduled(cron = "0 1 1 * * ?")
    private void CRDPScanner() {
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
                // move the successfully processed files
                // (key is fileName, value is target filePath)
                for (Entry<String, String> m : processedFilesToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }

                // move the files that failed during processing to the errors' folder
                // (key is fileName, value is target filePath
                for (Entry<String, String> m : erredFilesToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }

                // move the successfully processed folders
                // (key is folderName, value is target folder)
                for (Entry<String, String> m : processedFoldersToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }

                // move the folders that failed during processing to the errors' folder
                // (key is folderName, value is target folder)
                for (Entry<String, String> m : erredFoldersToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }

                cleanUp(inFileDir, headFolderList);

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

    private static final void sendErrorNotificationSvc(String errorMsg) {
        String subject = "An error was received from the CRDP System";
        try {
            // delimiter - " ,"
            String[] addresses = errNotificationAddresses.split(" ,");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(""); // to be changed?
            message.setSubject(subject);
            message.setText(errorMsg);
            message.setTo(addresses);
            emailSender.send(message);
        } catch (MailSendException ex) {
            throw new MailSendException(ex.getMessage());
        }
    }

    private final void processAuditSvc(String fileName) throws JsonProcessingException {
        fileName = FilenameUtils.getName(fileName); // Extract file name from full path

        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(host + "process-audit")
                        .queryParam("fileName", fileName);
        // Send ORDS request
        try {
            HttpEntity<ProcessAuditResponse> resp =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            ProcessAuditResponse.class);
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog(
                                    "Request Success", "processAuditSvc")));

        } catch (ORDSException e) {
            e.printStackTrace();
        }
    }

    private final void processStatusSvc(String fileName) throws JsonProcessingException {
        fileName = FilenameUtils.getName(fileName); // Extract file name from full path

        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(host + "process-status")
                        .queryParam("fileName", fileName);
        // Send ORDS request
        try {
            HttpEntity<ProcessStatusResponse> resp =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            ProcessStatusResponse.class);
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog(
                                    "Request Success", "processStatusSvc")));

        } catch (ORDSException e) {
            e.printStackTrace();
        }
    }

    /**
     * The primary method for the Java service
     */
    public static final byte[] readFile(String fileName) throws IOException {
        try {
            byte[] data = readFile(new File(fileName));
            return data;
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException("Failed to read file: " + fileName);
        }
    }

    // -- END OF PRIMARY SERVICES

    private static void cleanUp(String headFolderPath, List<String> headFolderList) {
        // delete processed folders (delivered from Ottawa).
        for (int i = 0; i < headFolderList.size(); i++) {
            removeFolderSvc(headFolderPath + "/" + headFolderList.get(i));
        }
        // clean out 'processed' folder.
        removeFolderSvc(headFolderPath + "/processed");
        makeFolderSvc(headFolderPath + "/processed");
    }

    private void recursiveScan(File[] arr, int index, int level) throws IOException {
        // terminate condition
        if (index == arr.length) return;
        try {
            // for root folder files (Audit and Status).
            if (arr[index].isFile()) processFile(arr[index]);

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
                    if ("CCs".equals(arr[index].getName())) {
                        processFolder(
                                arr[index].getCanonicalPath() + "/", "CCs", "ProcessDocuments");
                    }
                    if ("JUS178s".equals(arr[index].getName())) {
                        processFolder(
                                arr[index].getCanonicalPath() + "/", "JUS178s", "ProcessReports");
                    }
                    if ("Letters".equals(arr[index].getName())) {
                        processFolder(
                                arr[index].getCanonicalPath() + "/", "Letters", "ProcessDocuments");
                    }
                    if ("R-Lists".equals(arr[index].getName())) {
                        processFolder(
                                arr[index].getCanonicalPath() + "/", "R-Lists", "ProcessReports");
                    }
                    // recursion for sub-directories
                    recursiveScan(arr[index].listFiles(), 0, level + 1);
                }
            }

        } catch (Exception ex) {
            sendErrorNotificationSvc(
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

    static byte[] readFile(File file) throws IOException {
        // Open file
        RandomAccessFile f = new RandomAccessFile(file, "r");
        try {
            // Get and check length
            long longlength = f.length();
            int length = (int) longlength;
            if (length != longlength) throw new IOException("File size >= 2 GB");
            // Read file and return data
            byte[] data = new byte[length];
            f.readFully(data);
            return data;
        } finally {
            f.close();
        }
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

    private static int getErrorCount() {
        return erredFilesToMove.size() + erredFoldersToMove.size();
    }

    /**
     * processFolder - handles subfolders of folder type 'Processed_yyyy_dd' (e.g. CCs, Letters,
     * etc).
     *
     * @throws Exception
     */
    private static void processFolder(String folderName, String folderShortName, String serviceName)
            throws Exception {
        // Extract date from Processed folderName to pass service as 'processedDate'.
        Pattern p = Pattern.compile("\\bProcessed_\\w+[-][0-9][0-9][-][0-9][0-9]");
        Matcher m = p.matcher(folderName);
        String processedDate = null;
        if (m.find()) {
            processedDate = m.group().substring("Processed_".length());
        }

        try {
            // callService("CRDP.Source.ProcessIncomingFile.Services", serviceName, input);

            // Add the processed folder and it's target location to the processedFolders map dealt
            // with at the end of processing.
            processedFoldersToMove.put(
                    folderName,
                    inFileDir + "/processed/" + processFolderName + "/" + folderShortName);

        } catch (Exception e) {
            // Add the errored folder path and it's target location to the erroredFolders map dealt
            // with at the end of processing.
            erredFoldersToMove.put(
                    folderName, inFileDir + "/errors/" + processFolderName + "/" + folderShortName);

            // inform parent of error to be sent via email.
            throw new Exception(e.getMessage());
        }
    }

    /**
     * processFile - handles root folder status and audit files.
     *
     * @throws Exception
     */
    private void processFile(File file) throws Exception {
        String fileName = null;
        try {
            fileName = file.getCanonicalPath();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        String auditRegex = "^[A-Za-z]{4}O_Audit.\\d{6}.XML"; // ^[A-Z]{4}O_Audit.\d{6}.XML
        String statusRegex = "^[A-Za-z]{4}O_Status.\\d{6}.XML"; // ^[A-Z]{4}O_Status.\d{6}.XML
        boolean move = false;

        try {
            if (Pattern.matches(auditRegex, file.getName())) {
                processAuditSvc(fileName);
                move = true;

            } else if (Pattern.matches(statusRegex, file.getName())) {
                processStatusSvc(fileName);
                move = true;
            }

            // Move file to 'processed' folder on success (if status or audit only)
            if (move) {
                processedFilesToMove.put(file.getCanonicalPath(), inFileDir + "/processed");
            }

        } catch (Exception e) {
            erredFilesToMove.put(file.getAbsolutePath(), inFileDir + "/errors");

            // inform parent of error to be sent via email.
            throw new Exception(e.getMessage(), e);
        }
    }
}
