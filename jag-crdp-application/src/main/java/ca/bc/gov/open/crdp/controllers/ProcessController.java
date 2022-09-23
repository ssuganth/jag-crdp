package ca.bc.gov.open.crdp.controllers;

import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXB;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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

@Endpoint
@Slf4j
public class ProcessController {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.in-file-dir}")
    private String inFileDir = "/";

    @Value("${crdp.notification-addresses}")
    public void setNameStatic(String addresses) {
        ProcessController.errNotificationAddresses = addresses;
    }

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

    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");

    @Autowired
    public ProcessController(
            JavaMailSender emailSender, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.emailSender = emailSender;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // CRON Job Name:   CRDP Incoming File Processor
    //                  2020/04/22 16:30:00 86400s
    // Pattern      :   "* 0/24 * * * *"
    // Interval     :   Every 24hours
    /** The primary method for the Java service to scan CRDP directory */
    @Scheduled(cron = "${crdp.cron-job-outgoing-file}")
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

    public void processAuditSvc(String fileName) throws IOException {

        // Send ORDS request
        try {
            String shortFileName =
                    FilenameUtils.getName(fileName); // Extract file name from full path
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "process-audit");

            ProcessAuditRequest req =
                    new ProcessAuditRequest(shortFileName, readFile(new File(fileName)));
            HttpEntity<ProcessAuditRequest> payload = new HttpEntity<>(req, new HttpHeaders());

            HttpEntity<ProcessAuditResponse> resp =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.POST,
                            payload,
                            ProcessAuditResponse.class);
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog("Request Success", "processAuditSvc")));

        } catch (Exception e) {
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS",
                                    "processAuditSvc",
                                    e.getMessage(),
                                    fileName)));
            throw new ORDSException();
        }
    }

    public void processStatusSvc(String fileName) throws IOException {
        String shortFileName = FilenameUtils.getName(fileName); // Extract file name from full path

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "process-status");

        ProcessStatusRequest req =
                new ProcessStatusRequest(shortFileName, readFile(new File(fileName)));
        HttpEntity<ProcessStatusRequest> payload = new HttpEntity<>(req, new HttpHeaders());
        // Send ORDS request
        try {
            HttpEntity<ProcessStatusResponse> resp =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.POST,
                            payload,
                            ProcessStatusResponse.class);
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog("Request Success", "processStatusSvc")));

        } catch (Exception e) {
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS",
                                    "processStatusSvc",
                                    e.getMessage(),
                                    fileName)));
            throw new ORDSException();
        }
    }

    public void processDocumentsSvc(String folderName, String folderShortName, String processedDate)
            throws IOException {
        String[] fileList;

        // Creates a new File instance by converting the given pathname string
        // into an abstract pathname
        File f = new File(folderName);

        // Populates the array with names of files and directories
        fileList = f.list();
        String fileName = "";
        if (folderShortName.equals("CCs")) {
            fileName = extractXMLFileName(fileList, "^[A-Z]{4}O_CCs.XML");
        } else if (folderShortName.equals("Letters")) {
            fileName = extractXMLFileName(fileList, "^[A-Z]{4}O_Letters.XML");
        } else {
            log.error("Unexpected folder short name: " + folderShortName);
        }
        byte[] ccDocument = readFile(new File(folderName + fileName));

        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(host + "doc/status")
                        .queryParam("processedDate", processedDate)
                        .queryParam("fileName", fileName);

        HttpEntity<Map<String, String>> resp = null;
        try {
            resp =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            new ParameterizedTypeReference<>() {});
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog(
                                    "Request Success",
                                    "processDocumentsSvc - GetDocumentProcessStatus")));

        } catch (Exception e) {
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS",
                                    "processDocumentsSvc - GetDocumentProcessStatusRequest",
                                    e.getMessage(),
                                    fileName + " " + processedDate)));
            throw new ORDSException();
        }

        GuidMapDocument guidMapDocument = new GuidMapDocument("1", new ArrayList<>());
        if (resp.getBody().get("status").equals("N")) {
            List<String> pdfs = extractPDFFileNames(folderName);
            UriComponentsBuilder builder2 = UriComponentsBuilder.fromHttpUrl(host + "doc/save");
            for (String pdf : pdfs) {
                SavePDFDocumentRequest req = new SavePDFDocumentRequest(readFile(new File(pdf)));
                HttpEntity<SavePDFDocumentRequest> payload =
                        new HttpEntity<>(req, new HttpHeaders());
                try {
                    HttpEntity<SavePDFDocumentResponse> response =
                            restTemplate.exchange(
                                    builder2.toUriString(),
                                    HttpMethod.POST,
                                    payload,
                                    SavePDFDocumentResponse.class);
                    log.info(
                            objectMapper.writeValueAsString(
                                    new RequestSuccessLog(
                                            "Request Success",
                                            "processDocumentsSvc - SavePDFDocument")));
                    if (response.getBody().getResultCd().equals("0")) {
                        // map file name and guid
                        guidMapDocument
                                .getMappings()
                                .add(
                                        new GuidDocumentMapping(
                                                FilenameUtils.getName(pdf),
                                                response.getBody().getObjectGuid()));
                    } else {
                        throw new ORDSException(response.getBody().getResultMsg());
                    }
                } catch (Exception e) {
                    log.error(
                            objectMapper.writeValueAsString(
                                    new OrdsErrorLog(
                                            "Error received from ORDS",
                                            "processDocumentsSvc - SavePDFDocument",
                                            e.getMessage(),
                                            req)));
                    saveError(
                            e.getMessage(),
                            dateFormat.format(Calendar.getInstance().getTime()),
                            fileName,
                            ccDocument);

                    throw new ORDSException();
                }
            }

            StringWriter sw = new StringWriter();
            JAXB.marshal(guidMapDocument, sw);
            String xml = sw.toString();

            ProcessXMLRequest req =
                    new ProcessXMLRequest(ccDocument, xml.getBytes(StandardCharsets.UTF_8));
            if (folderShortName.equals("CCs")) {
                UriComponentsBuilder builder3 =
                        UriComponentsBuilder.fromHttpUrl(host + "doc/processCCs");
                HttpEntity<ProcessXMLRequest> payload = new HttpEntity<>(req, new HttpHeaders());
                try {
                    HttpEntity<ProcessCCsResponse> response =
                            restTemplate.exchange(
                                    builder3.toUriString(),
                                    HttpMethod.POST,
                                    payload,
                                    ProcessCCsResponse.class);
                    log.info(
                            objectMapper.writeValueAsString(
                                    new RequestSuccessLog(
                                            "Request Success",
                                            "processDocumentsSvc - ProcessCCsXML")));

                    if (!response.getBody().getResultCd().equals("0")) {
                        throw new ORDSException(response.getBody().getResultMsg());
                    }
                } catch (Exception e) {
                    log.error(
                            objectMapper.writeValueAsString(
                                    new OrdsErrorLog(
                                            "Error received from ORDS",
                                            "processDocumentsSvc - ProcessCCsXML",
                                            e.getMessage(),
                                            req)));

                    saveError(
                            e.getMessage(),
                            dateFormat.format(Calendar.getInstance().getTime()),
                            fileName,
                            ccDocument);

                    throw new ORDSException();
                }
            } else if (folderShortName.equals("Letters")) {
                UriComponentsBuilder builder3 =
                        UriComponentsBuilder.fromHttpUrl(host + "doc/processLetters");
                HttpEntity<ProcessXMLRequest> payload = new HttpEntity<>(req, new HttpHeaders());
                try {
                    HttpEntity<ProcessLettersResponse> response =
                            restTemplate.exchange(
                                    builder3.toUriString(),
                                    HttpMethod.POST,
                                    payload,
                                    ProcessLettersResponse.class);
                    log.info(
                            objectMapper.writeValueAsString(
                                    new RequestSuccessLog(
                                            "Request Success",
                                            "processDocumentsSvc - ProcessLettersXML")));

                    if (!response.getBody().getResultCd().equals("0")) {
                        saveError(
                                response.getBody().getResultMsg(),
                                dateFormat.format(Calendar.getInstance().getTime()),
                                fileName,
                                ccDocument);
                        throw new ORDSException();
                    }
                } catch (Exception e) {
                    log.error(
                            objectMapper.writeValueAsString(
                                    new OrdsErrorLog(
                                            "Error received from ORDS",
                                            "processDocumentsSvc - ProcessLettersXML",
                                            e.getMessage(),
                                            req)));
                    throw new ORDSException();
                }
            } else {
                saveError(
                        "Unexpected folder short name: " + folderShortName,
                        dateFormat.format(Calendar.getInstance().getTime()),
                        fileName,
                        ccDocument);
                throw new ORDSException();
            }

        } else {
            // do nothing
            return;
        }
    }

    public void processReportsSvc(String folderName, String processedDate) throws IOException {
        List<String> pdfs = extractPDFFileNames(folderName);
        for (String pdf : pdfs) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "rpt");
            ProcessReportRequest req =
                    new ProcessReportRequest(pdf, processedDate, readFile(new File(pdf)));
            HttpEntity<ProcessReportRequest> payload = new HttpEntity<>(req, new HttpHeaders());
            try {
                HttpEntity<Map<String, String>> response =
                        restTemplate.exchange(
                                builder.toUriString(),
                                HttpMethod.POST,
                                payload,
                                new ParameterizedTypeReference<>() {});
                log.info(
                        objectMapper.writeValueAsString(
                                new RequestSuccessLog("Request Success", "processReportSvc")));
            } catch (Exception e) {
                log.error(
                        objectMapper.writeValueAsString(
                                new OrdsErrorLog(
                                        "Error received from ORDS",
                                        "processReportSvc",
                                        e.getMessage(),
                                        req)));
                throw new ORDSException();
            }
        }
    }

    public void saveError(String errMsg, String date, String fileName, byte[] fileContentXml)
            throws JsonProcessingException {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "err/save");
        SaveErrorRequest req = new SaveErrorRequest(errMsg, date, fileName, fileContentXml);
        HttpEntity<SaveErrorRequest> payload = new HttpEntity<>(req, new HttpHeaders());
        try {
            HttpEntity<Map<String, String>> response =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.POST,
                            payload,
                            new ParameterizedTypeReference<>() {});
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog("Request Success", "SaveError")));
        } catch (Exception e) {
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS", "SaveError", e.getMessage(), req)));
            throw new ORDSException();
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
            long longLength = f.length();
            int length = (int) longLength;
            if (length != longLength) throw new IOException("File size >= 2 GB");
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
     */
    private void processFolder(String folderName, String folderShortName, String serviceName)
            throws Exception {
        // Extract date from Processed folderName to pass service as 'processedDate'.
        Pattern p = Pattern.compile("\\bProcessed_\\w+[-][0-9][0-9][-][0-9][0-9]");
        Matcher m = p.matcher(folderName);
        String processedDate = null;
        if (m.find()) {
            processedDate = m.group().substring("Processed_".length());
        }

        try {
            switch (serviceName) {
                case "ProcessDocuments":
                    processDocumentsSvc(folderName, folderShortName, processedDate);
                    break;
                case "ProcessReports":
                    // folderShortName is not used in processReports
                    processReportsSvc(folderName, processedDate);
                    break;
                default:
            }

            // Add the processed folder and its target location to the processedFolders map
            // dealt with at the end of processing.
            processedFoldersToMove.put(
                    folderName,
                    inFileDir + "/processed/" + processFolderName + "/" + folderShortName);

        } catch (Exception e) {
            // Add the erred folder path and its target location to the erred folders map
            // dealt with at the end of processing.
            erredFoldersToMove.put(
                    folderName, inFileDir + "/errors/" + processFolderName + "/" + folderShortName);

            // inform parent of error to be sent via email, but wM does not do anything sending
            // email
            // sendErrorNotificationSvc(e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    /** processFile - handles root folder status and audit files. */
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
