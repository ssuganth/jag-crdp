package ca.bc.gov.open.crdp.process.transformer.services;

import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.OrdsErrorLog;
import ca.bc.gov.open.crdp.models.RequestSuccessLog;
import ca.bc.gov.open.crdp.process.models.*;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.ws.server.endpoint.annotation.Endpoint;

@Endpoint
@Slf4j
public class TransformerService {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.in-progress-dir}")
    private String inProgressDir = "/";

    @Value("${crdp.completed-dir}")
    private String completedDir = "/";

    @Value("${crdp.errors-dir}")
    private String errorsDir = "/";

    private String timestamp = null;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static String
            processFolderName; // current "Processed_yyyy_nn" folder name (not full path).

    private static TreeMap<String, String> completedFilesToMove =
            new TreeMap<String, String>(); // completed files.
    private static TreeMap<String, String> erredFilesToMove =
            new TreeMap<String, String>(); // erred files.

    private static TreeMap<String, String> completedFoldersToMove =
            new TreeMap<String, String>(); // completed folders.
    private static TreeMap<String, String> erredFoldersToMove =
            new TreeMap<String, String>(); // erred folders.

    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");

    @Autowired
    public TransformerService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordScanningTime(String timestamp) {
        this.timestamp = timestamp;
    }

    public void processFile(String fileName) {
        // re-initialize arrays. Failing to do this can result in unpredictable results.
        completedFilesToMove = new TreeMap<String, String>(); // completed files.
        erredFilesToMove = new TreeMap<String, String>(); // erred files.
        completedFoldersToMove = new TreeMap<String, String>(); // completed folders.
        erredFoldersToMove = new TreeMap<String, String>(); // erred folders.

        // File object
        File mainDir = new File(inProgressDir);

        if (mainDir.exists() && mainDir.isDirectory()) {
            // create Completed folder
            File compDir = new File(completedDir);
            if (!compDir.exists()) {
                compDir.mkdir();
            }
            File compTimestampDir = new File(completedDir + timestamp);
            if (!compTimestampDir.exists()) {
                compTimestampDir.mkdir();
            }

            // create Errors folder
            File errDir = new File(errorsDir);
            if (!errDir.exists()) {
                errDir.mkdir();
            }
            File errTimestampDir = new File(errorsDir + timestamp);
            if (!errTimestampDir.exists()) {
                errTimestampDir.mkdir();
            }

            File file = new File(fileName);
            if (file.isFile()) {
                // process files
                processFile(file);
            } else {
                // process folders
                processFolder(file);
            }

            try {
                for (Map.Entry<String, String> m : completedFilesToMove.entrySet()) {
                    File f = new File(m.getKey());
                    move(f, m.getValue());
                }

                for (Map.Entry<String, String> m : completedFoldersToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }

                for (Map.Entry<String, String> m : erredFilesToMove.entrySet()) {
                    File f = new File(m.getKey());
                    move(f, m.getValue());
                }

                for (Map.Entry<String, String> m : erredFoldersToMove.entrySet()) {
                    move(new File(m.getKey()), m.getValue());
                }
                cleanUp(inProgressDir);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        } else {
            log.error("InProgress directory \"" + mainDir + "\" does not exist");
        }
    }

    private static void cleanUp(String inProgressDir) {
        File dir = new File(inProgressDir);
        File[] fileList = dir.listFiles();
        for (var f : fileList) {
            if (f.listFiles() != null && f.listFiles().length == 0) {
                removeFolderSvc(f.getAbsolutePath());
            }
        }
    }

    private void processFile(File file) {
        String fileName = null;
        try {
            fileName = file.getCanonicalPath();
        } catch (IOException e1) {
            log.error(e1.getMessage());
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

            // Move file to 'completed' folder on success (status or audit only)
            if (move) {
                completedFilesToMove.put(file.getAbsolutePath(), completedDir + timestamp + "\\");
            }

        } catch (Exception e) {
            erredFilesToMove.put(file.getAbsolutePath(), errorsDir + timestamp + "\\");
        }
    }

    public void processAuditSvc(String fileName) throws IOException {
        String shortFileName = FilenameUtils.getName(fileName); // Extract file name from full path
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "process-audit");

        byte[] file = readFile(new File(fileName));

        ProcessAuditRequest req = new ProcessAuditRequest(shortFileName, file);
        // Send ORDS request
        try {

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
            if (!resp.getBody().getResultCd().equals("0")) {
                throw new ORDSException(resp.getBody().getResultMsg());
            }

        } catch (Exception e) {
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS",
                                    "processAuditSvc",
                                    e.getMessage(),
                                    fileName)));
            saveError(
                    e.getMessage(),
                    dateFormat.format(Calendar.getInstance().getTime()),
                    fileName,
                    file);

            throw new ORDSException();
        }
    }

    public void processStatusSvc(String fileName) throws IOException {
        String shortFileName = FilenameUtils.getName(fileName); // Extract file name from full path

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "process-status");

        byte[] file = readFile(new File(fileName));

        ProcessStatusRequest req = new ProcessStatusRequest(shortFileName, file);
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

            if (!resp.getBody().getResultCd().equals("0")) {
                throw new ORDSException(resp.getBody().getResultMsg());
            }
        } catch (Exception e) {
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS",
                                    "processStatusSvc",
                                    e.getMessage(),
                                    fileName)));

            saveError(
                    e.getMessage(),
                    dateFormat.format(Calendar.getInstance().getTime()),
                    fileName,
                    file);

            throw new ORDSException();
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

    private void processFolder(File folderPath) {
        // Extract date from Processed folderName to pass service as 'processedDate'.
        Pattern p = Pattern.compile("\\bProcessed_\\w+[-][0-9][0-9][-][0-9][0-9]");
        Matcher m = p.matcher(folderPath.toString());
        String processedDate = null;
        if (m.find()) {
            processedDate = m.group().substring("Processed_".length());
        }

        try {
            switch (folderPath.getName()) {
                case "Letters":
                case "CCs":
                    processDocumentsSvc(
                            folderPath.getCanonicalPath(), folderPath.getName(), processedDate);
                    break;
                case "JUS178s":
                case "R-Lists":
                    // folderShortName is not used in processReports
                    processReportsSvc(folderPath.getCanonicalPath(), processedDate);
                    break;
                default:
            }

            // Add the processed folder and its target location to the processedFolders map
            // dealt with at the end of processing.
            completedFoldersToMove.put(
                    folderPath.getAbsolutePath(), completedDir + timestamp + "\\" + folderPath.getName());

        } catch (Exception e) {
            // Add the erred folder path and its target location to the erred folders map
            // dealt with at the end of processing.
            erredFoldersToMove.put(
                    folderPath.getAbsolutePath(), errorsDir + timestamp + "\\" + folderPath.getName());
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
        byte[] ccDocument = readFile(new File(folderName + "\\" + fileName));

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
                                    "processDocumentsSvc - GetDocumentProcessStatus ("
                                            + folderShortName
                                            + ")")));

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
                                            "processDocumentsSvc - SavePDFDocument ("
                                                    + folderShortName
                                                    + ")")));
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
                                            "processDocumentsSvc - SavePDFDocument (\" + folderShortName + \")\"",
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
                        throw new ORDSException(response.getBody().getResultMsg());
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

            File reqPDF = new File(pdf);
            byte[] file = readFile(reqPDF);

            ProcessReportRequest req = new ProcessReportRequest(reqPDF.getName(), processedDate, file);
            HttpEntity<ProcessReportRequest> payload = new HttpEntity<>(req, new HttpHeaders());
            try {
                HttpEntity<ProcessReportResponse> response =
                        restTemplate.exchange(
                                builder.toUriString(),
                                HttpMethod.POST,
                                payload,
                                ProcessReportResponse.class);
                log.info(
                        objectMapper.writeValueAsString(
                                new RequestSuccessLog("Request Success", "processReportSvc")));

                if (!response.getBody().getResultCd().equals("0")) {
                    throw new ORDSException(response.getBody().getResultMsg());
                }
            } catch (Exception e) {
                log.error(
                        objectMapper.writeValueAsString(
                                new OrdsErrorLog(
                                        "Error received from ORDS",
                                        "processReportSvc",
                                        e.getMessage(),
                                        req)));

                saveError(
                        e.getMessage(),
                        dateFormat.format(Calendar.getInstance().getTime()),
                        pdf,
                        file);

                throw new ORDSException();
            }
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
