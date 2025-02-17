package ca.bc.gov.open.crdp.process.transformer.services;

import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.OrdsErrorLog;
import ca.bc.gov.open.crdp.models.RequestSuccessLog;
import ca.bc.gov.open.crdp.process.models.*;
import ca.bc.gov.open.sftp.starter.*;
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
import javax.xml.XMLConstants;
import javax.xml.bind.JAXB;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import lombok.extern.slf4j.Slf4j;
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

    @Autowired JschSessionProvider jschSessionProvider;
    private FileService fileService;
    private final SftpProperties sftpProperties;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static TreeMap<String, String> completedFilesToMove =
            new TreeMap<String, String>(); // completed files.
    private static TreeMap<String, String> erredFilesToMove =
            new TreeMap<String, String>(); // erred files.

    private static TreeMap<String, String> completedFoldersToMove =
            new TreeMap<String, String>(); // completed folders.
    private static TreeMap<String, String> erredFoldersToMove =
            new TreeMap<String, String>(); // erred folders.

    private static String auditSchemaPath =
            "jag-crdp-process-transformer/src/main/resources/xsdSchemas/outgoingAudit.xsd";
    private static String ccSchemaPath =
            "jag-crdp-process-transformer/src/main/resources/xsdSchemas/outgoingCCs.xsd";
    private static String lettersSchemaPath =
            "jag-crdp-process-transformer/src/main/resources/xsdSchemas/outgoingLetters.xsd";
    private static String statusSchemaPath =
            "jag-crdp-process-transformer/src/main/resources/xsdSchemas/outgoingStatus.xsd";

    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");

    @Autowired
    public TransformerService(
            RestTemplate restTemplate, ObjectMapper objectMapper, SftpProperties sftpProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.sftpProperties = sftpProperties;
    }

    public void processFileService(ScannerPub pub) {
        fileService =
                false
                        ? new SftpServiceImpl(jschSessionProvider, sftpProperties)
                        : new LocalFileImpl();

        // re-initialize arrays. Failing to do this can result in unpredictable results.
        completedFilesToMove = new TreeMap<String, String>(); // completed files.
        erredFilesToMove = new TreeMap<String, String>(); // erred files.
        completedFoldersToMove = new TreeMap<String, String>(); // completed folders.
        erredFoldersToMove = new TreeMap<String, String>(); // erred folders.

        this.timestamp = pub.getDateTime();

        // File object
        fileService.makeFolder(inProgressDir);

        if (fileService.exists(inProgressDir) && fileService.isDirectory(inProgressDir)) {
            // create Completed folder
            if (!fileService.exists(completedDir)) {
                fileService.makeFolder(completedDir);
            }
            // create completed folder with last scanning timestamp
            if (!fileService.exists(completedDir + timestamp)) {
                fileService.makeFolder(completedDir + timestamp);
            }
            // create Errors folder
            if (!fileService.exists(errorsDir)) {
                fileService.makeFolder(errorsDir);
            }
            // create errors folder with last scanning timestamp
            if (!fileService.exists(errorsDir + timestamp)) {
                fileService.makeFolder(errorsDir + timestamp);
            }

            if (!fileService.isDirectory(pub.getFilePath())) {
                // process files
                processFile(pub.getFilePath());
            } else {
                // process folders
                processFolder(pub.getFilePath());
            }

            try {
                for (Map.Entry<String, String> m : completedFilesToMove.entrySet()) {
                    fileService.moveFile(m.getKey(), m.getValue());
                }

                for (Map.Entry<String, String> m : completedFoldersToMove.entrySet()) {
                    fileService.moveFile(m.getKey(), m.getValue());
                }

                for (Map.Entry<String, String> m : erredFilesToMove.entrySet()) {
                    fileService.moveFile(m.getKey(), m.getValue());
                }

                for (Map.Entry<String, String> m : erredFoldersToMove.entrySet()) {
                    fileService.moveFile(m.getKey(), m.getValue());
                }
                cleanUp(inProgressDir);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        } else {
            log.error("InProgress directory \"" + inProgressDir + "\" does not exist");
        }
    }

    private void cleanUp(String inProgressDir) {
        for (var f : fileService.listFiles(inProgressDir)) {
            if (fileService.isDirectory(f) && fileService.listFiles(f).size() == 0) {
                fileService.removeFolder(f);
            }
        }
    }

    private void processFile(String filePath) {
        String auditRegex = "^[A-Za-z]{4}O_Audit.\\d{6}.XML"; // ^[A-Z]{4}O_Audit.\d{6}.XML
        String statusRegex = "^[A-Za-z]{4}O_Status.\\d{6}.XML"; // ^[A-Z]{4}O_Status.\d{6}.XML
        boolean move = false;

        try {
            if (Pattern.matches(auditRegex, getFileName(filePath))) {
                processAuditSvc(filePath);
                move = true;

            } else if (Pattern.matches(statusRegex, getFileName(filePath))) {
                processStatusSvc(filePath);
                move = true;
            }

            // Move file to 'completed' folder on success (status or audit only)
            if (move) {
                completedFilesToMove.put(
                        filePath, completedDir + timestamp + "/" + getFileName(filePath));
            }

        } catch (Exception e) {
            erredFilesToMove.put(filePath, errorsDir + timestamp + "/" + getFileName(filePath));
        }
    }

    public void processAuditSvc(String fileName) throws IOException {
        String shortFileName = FilenameUtils.getName(fileName); // Extract file name from full path
        File xmlFile = new File(fileName);
        if (!validateXml(auditSchemaPath, new File(fileName))) {
            throw new IOException("XML file schema validation failed. fileName : " + xmlFile);
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "process-audit");

        byte[] file = readFile(xmlFile);

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
        File xmlFile = new File(fileName);
        if (!validateXml(statusSchemaPath, xmlFile)) {
            throw new IOException("XML file schema validation failed. fileName : " + xmlFile);
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "process-status");

        byte[] file = readFile(xmlFile);

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

    private void processFolder(String folderPath) {
        // Extract date from Processed folderName to pass service as 'processedDate'.
        Pattern p = Pattern.compile("\\bProcessed_\\w+[-][0-9][0-9][-][0-9][0-9]");
        Matcher m = p.matcher(folderPath);
        String processedDate = null;
        if (m.find()) {
            processedDate = m.group().substring("Processed_".length());
        }

        try {
            switch (getFileName(folderPath)) {
                case "Letters":
                case "CCs":
                    processDocumentsSvc(folderPath, getFileName(folderPath), processedDate);
                    break;
                case "JUS178s":
                case "R-Lists":
                    // folderShortName is not used in processReports
                    processReportsSvc(folderPath, processedDate);
                    break;
                default:
            }

            // Add the processed folder and its target location to the processedFolders map
            // dealt with at the end of processing.
            completedFoldersToMove.put(
                    folderPath, completedDir + timestamp + "/" + getFileName(folderPath));

        } catch (Exception e) {
            // Add the erred folder path and its target location to the erred folders map
            // dealt with at the end of processing.
            erredFoldersToMove.put(
                    folderPath, errorsDir + timestamp + "/" + getFileName(folderPath));
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
        boolean isValid = false;
        File xmlFile = null;
        if (folderShortName.equals("CCs")) {
            fileName = extractXMLFileName(fileList, "^[A-Z]{4}O_CCs.XML");
            xmlFile = new File(folderName + "/" + fileName);
            isValid = validateXml(ccSchemaPath, xmlFile);
        } else if (folderShortName.equals("Letters")) {
            fileName = extractXMLFileName(fileList, "^[A-Z]{4}O_Letters.XML");
            xmlFile = new File(folderName + "/" + fileName);
            isValid = validateXml(lettersSchemaPath, xmlFile);
        } else {
            log.error("Unexpected folder short name: " + folderShortName);
            return;
        }

        if (!isValid) {
            throw new IOException(
                    "XML file schema validation failed. fileName : " + folderName + fileName);
        }

        byte[] document = readFile(xmlFile);

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
                            document);

                    throw new ORDSException();
                }
            }

            StringWriter sw = new StringWriter();
            JAXB.marshal(guidMapDocument, sw);
            String xml = sw.toString();

            ProcessXMLRequest req =
                    new ProcessXMLRequest(document, xml.getBytes(StandardCharsets.UTF_8));
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
                            document);

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
                        document);
                throw new ORDSException();
            }

        } else {
            // do nothing
            log.info(
                    "Document already processed. Response from GetDocumentProcessStatus: "
                            + resp.getBody());
            return;
        }
    }

    public void processReportsSvc(String folderName, String processedDate) throws IOException {
        List<String> pdfs = extractPDFFileNames(folderName);
        for (String pdf : pdfs) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "rpt");

            File reqPDF = new File(pdf);
            byte[] file = readFile(reqPDF);

            ProcessReportRequest req =
                    new ProcessReportRequest(reqPDF.getName(), processedDate, file);
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

    public boolean validateXml(String xsdPath, File xmlFile) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Source schemaFile = new StreamSource(xsdPath);
        try {
            Schema schema = factory.newSchema(schemaFile);
            schema.newValidator().validate(new StreamSource(xmlFile));
            return true;
        } catch (Exception e) {
            log.error("validateXml error: " + e.getMessage());
            return false;
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

    private static String getFileName(String filePath) {
        if (filePath.contains("\\") && !filePath.contains("/")) {
            // Windows path
            return filePath.substring(filePath.lastIndexOf("\\") + 1);
        } else if (filePath.contains("/") && !filePath.contains("\\")) {
            // Linux path
            return filePath.substring(filePath.lastIndexOf("/") + 1);
        } else {
            log.warn("Invalid file path: " + filePath);
            return filePath;
        }
    }
}
