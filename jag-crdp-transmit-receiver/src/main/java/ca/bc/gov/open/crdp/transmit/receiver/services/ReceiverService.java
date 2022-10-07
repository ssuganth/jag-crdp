package ca.bc.gov.open.crdp.transmit.receiver.services;

import ca.bc.gov.open.crdp.ErrorHandler;
import ca.bc.gov.open.crdp.exceptions.ORDSException;
import ca.bc.gov.open.crdp.models.MqErrorLog;
import ca.bc.gov.open.crdp.models.OrdsErrorLog;
import ca.bc.gov.open.crdp.models.RequestSuccessLog;
import ca.bc.gov.open.crdp.transmit.models.*;
import ca.bc.gov.open.crdp.transmit.receiver.configuration.QueueConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Endpoint
@Slf4j
public class ReceiverService {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.out-file-dir}")
    private String outFileDir = "/";

    @Value("${crdp.notification-addresses}")
    public void setErrNotificationAddresses(String addresses) {
        ReceiverService.errNotificationAddresses = addresses;
    }

    private static String errNotificationAddresses = "";

    @Value("${crdp.smtp-from}")
    public void setDefaultSmtpFrom(String from) {
        ReceiverService.defaultSmtpFrom = from;
    }

    private static String defaultSmtpFrom = "";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final JavaMailSender emailSender;
    private final AmqpAdmin amqpAdmin;
    private final Queue receiverQueue;
    private final QueueConfig queueConfig;

    @Autowired
    public ReceiverService(
            @Qualifier("receiver-queue") Queue receiverQueue,
            AmqpAdmin amqpAdmin,
            QueueConfig queueConfig,
            JavaMailSender emailSender,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate) {
        this.receiverQueue = receiverQueue;
        this.amqpAdmin = amqpAdmin;
        this.emailSender = emailSender;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.queueConfig = queueConfig;
    }

    // CRON Job Name:   CRDP Transmit Outgoing File
    //                  2020/04/14 14:44:14 600s
    // Pattern      :   "0/10 * * * * *"
    // Interval     :   Every 10 minutes
    @PayloadRoot(localPart = "generateIncomingRequestFile")
    @ResponsePayload
    //    @Scheduled(cron = "${crdp.cron-job-incomming-file}")
    @Scheduled(cron = "0/2 * * * * *") // Every 2 sec - for testing purpose
    public void GenerateIncomingRequestFile() throws JsonProcessingException {
        UriComponentsBuilder reqFileBuilder = UriComponentsBuilder.fromHttpUrl(host + "req-file");

        HttpEntity<GenerateIncomingReqFileResponse> reqFileResp = null;
        // Request part one and part two files
        try {
            reqFileResp =
                    restTemplate.exchange(
                            reqFileBuilder.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            GenerateIncomingReqFileResponse.class);
            log.info(
                    objectMapper.writeValueAsString(
                            new RequestSuccessLog(
                                    "Request Success", "generateIncomingRequestFile")));

        } catch (Exception ex) {
            ErrorHandler.processError(); // TO BE COMPLETED
            log.error(
                    objectMapper.writeValueAsString(
                            new OrdsErrorLog(
                                    "Error received from ORDS",
                                    "generateIncomingRequestFile",
                                    ex.getMessage(),
                                    null)));
            return;
        }

        String xmlString = xmlBuilder(reqFileResp.getBody());
        // Save Data Exchange File
        if (xmlString != null) {
            UriComponentsBuilder saveFileBuilder =
                    UriComponentsBuilder.fromHttpUrl(host + "save-file");

            HttpEntity<SaveDataExchangeFileRequest> payload =
                    new HttpEntity<>(
                            new SaveDataExchangeFileRequest(
                                    reqFileResp.getBody().getFileName(),
                                    xmlString,
                                    reqFileResp.getBody().getDataExchangeFileSeqNo()),
                            new HttpHeaders());

            HttpEntity<Map<String, String>> saveFileResp = null;
            try {
                saveFileResp =
                        restTemplate.exchange(
                                saveFileBuilder.toUriString(),
                                HttpMethod.POST,
                                payload,
                                new ParameterizedTypeReference<>() {});
                if (saveFileResp.getBody().get("status").equals("0")) {
                    throw new ORDSException("Save failed with status 0");
                }
                log.info(
                        objectMapper.writeValueAsString(
                                new RequestSuccessLog("Request Success", "saveDataExchangeFile")));
            } catch (Exception ex) {
                ErrorHandler.processError(); // TO BE COMPLETED
                log.error(
                        objectMapper.writeValueAsString(
                                new OrdsErrorLog(
                                        "Error received from ORDS",
                                        "saveDataExchangeFile",
                                        ex.getMessage(),
                                        payload)));
                return;
            }
        }

        // Public xml (in ReceiverPub) for sender
        ReceiverPub pub =
                new ReceiverPub(
                        reqFileResp.getBody().getFileName(),
                        xmlString,
                        reqFileResp.getBody().getDataExchangeFileSeqNo());
        try {
            this.rabbitTemplate.convertAndSend(
                    queueConfig.getTopicExchangeName(),
                    queueConfig.getReceiverRoutingkey(),
                    reqFileResp.getBody());
        } catch (Exception ex) {
            ErrorHandler.processError(); // TO BE COMPLETED
            log.error(
                    objectMapper.writeValueAsString(
                            new MqErrorLog(
                                    "Enqueue failed",
                                    "generateIncomingRequestFile",
                                    ex.getMessage(),
                                    reqFileResp.getBody())));
            return;
        }
    }

    private String xmlBuilder(GenerateIncomingReqFileResponse fileComponent) {
        StringWriter writer = new StringWriter();
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            // CRDPFORMS (root element)
            Element rootElement = doc.createElement("CRDPFORMS");
            doc.appendChild(rootElement);

            // CRDPAPPIN99
            Element crdpappin99 = doc.createElement("CRDPAPPIN99");
            createXmlNode("Record_Type", "99", crdpappin99, doc);
            createXmlNode(
                    "Part1_RecordCount",
                    String.format("%06d", fileComponent.getPartOneCount()),
                    crdpappin99,
                    doc);
            createXmlNode(
                    "Part2_RecordCount",
                    String.format("%06d", fileComponent.getPartTwoCount()),
                    crdpappin99,
                    doc);
            rootElement.appendChild(crdpappin99);
            // END CRDPAPPIN99

            // CRDPAPPINPART1
            if (fileComponent.getPartOneData() != null) {
                Element crdpappinPart1 = doc.createElement("CRDPAPPINPART1");
                for (PartOneData one : fileComponent.getPartOneData()) {
                    createXmlNode("Record_Type", one.getRecordType(), crdpappinPart1, doc);
                    createXmlNode("Court_Number", one.getCourtNumber(), crdpappinPart1, doc);
                    createXmlNode(
                            "Divorce_Registry_Number",
                            one.getDivorceRegistryNumber(),
                            crdpappinPart1,
                            doc);
                    createXmlNode("Fee_Code", one.getFeeCode(), crdpappinPart1, doc);
                    createXmlNode("Filing_Date", one.getFilingDate(), crdpappinPart1, doc);
                    createXmlNode("Marriage_Date", one.getMarriageDate(), crdpappinPart1, doc);
                    createXmlNode(
                            "Joint_Application", one.getJointApplication(), crdpappinPart1, doc);
                    createXmlNode(
                            "Applicant_Surname", one.getApplicantSurname(), crdpappinPart1, doc);
                    createXmlNode(
                            "Applicant_Given_Name",
                            one.getApplicantGivenName(),
                            crdpappinPart1,
                            doc);
                    createXmlNode(
                            "Applicant_Birth_Date",
                            one.getApplicantBirthDate(),
                            crdpappinPart1,
                            doc);
                    createXmlNode(
                            "Applicant_Gender", one.getApplicantGender(), crdpappinPart1, doc);
                    createXmlNode(
                            "Respondent_Surname", one.getRespondentSurname(), crdpappinPart1, doc);
                    createXmlNode(
                            "Respondent_Given_Name",
                            one.getRespondentGivenName(),
                            crdpappinPart1,
                            doc);
                    createXmlNode(
                            "Respondent_Birth_Date",
                            one.getRespondentBirthDate(),
                            crdpappinPart1,
                            doc);
                    createXmlNode(
                            "Respondent_Gender", one.getRespondentGender(), crdpappinPart1, doc);
                    createXmlNode(
                            "Original_Court_Number",
                            one.getOriginalCourtNumber(),
                            crdpappinPart1,
                            doc);
                    createXmlNode(
                            "Original_Divorce_Reg_Number",
                            one.getOriginalDivorceRegNumber(),
                            crdpappinPart1,
                            doc);
                    createXmlNode(
                            "Petition_Signed_Date",
                            one.getPetitionSignedDate(),
                            crdpappinPart1,
                            doc);
                }
                rootElement.appendChild(crdpappinPart1);
            }
            // END CRDPAPPINPART1

            // CRDPAPPINPART1-REGNUMBER_MOD
            if (fileComponent.getRegModData() != null) {
                Element crdpappinMod = doc.createElement("CRDPAPPINPART1-REGNUMBER_MOD");
                for (RegModData mod : fileComponent.getRegModData()) {
                    createXmlNode("Record_Type", mod.getRecordType(), crdpappinMod, doc);
                    createXmlNode("Court_Number", mod.getCourtNumber(), crdpappinMod, doc);
                    createXmlNode(
                            "Divorce_Registry_Number",
                            mod.getDivorceRegistryNumber(),
                            crdpappinMod,
                            doc);
                    createXmlNode(
                            "Source_Case_Number",
                            mod.getSourceCaseNumber(),
                            crdpappinMod,
                            doc); // No data from DB
                    createXmlNode(
                            "Original_Divorce_Reg_Number",
                            mod.getOriginalDivorceRegNumber(),
                            crdpappinMod,
                            doc);
                }
                rootElement.appendChild(crdpappinMod);
            }
            // END CRDPAPPINPART1-REGNUMBER_MOD

            // CRDPAPPINPART2
            if (fileComponent.getPartTwoData() != null) {
                Element crdpappinPart2 = doc.createElement("CRDPAPPINPART2");
                for (PartTwoData two : fileComponent.getPartTwoData()) {
                    createXmlNode("Record_Type", two.getRecordType(), crdpappinPart2, doc);
                    createXmlNode("Court_Number", two.getCourtNumber(), crdpappinPart2, doc);
                    createXmlNode(
                            "Divorce_Registry_Number",
                            two.getDivorceRegistryNumber(),
                            crdpappinPart2,
                            doc);
                    createXmlNode(
                            "Source_Case_Number",
                            two.getSourceCaseNumber(),
                            crdpappinPart2,
                            doc); // No data from DB
                    createXmlNode(
                            "Disposition_Code", two.getDispositionCode(), crdpappinPart2, doc);
                    createXmlNode(
                            "Disposition_Date", two.getDispositionDate(), crdpappinPart2, doc);
                    createXmlNode(
                            "Transferred_Court_Number",
                            two.getTransferredCourtNumber(),
                            crdpappinPart2,
                            doc);
                    createXmlNode(
                            "Disposition_Signed_Date",
                            two.getDispositionSignedDate(),
                            crdpappinPart2,
                            doc);
                }
                rootElement.appendChild(crdpappinPart2);
            }
            // END CRDPAPPINPART2

            // CRDPAPPIN01
            Element crdpappin01 = doc.createElement("CRDPAPPIN01");
            createXmlNode("Record_Type", "01", crdpappin01, doc);
            createXmlNode("File_Name", fileComponent.getFileName(), crdpappin01, doc);
            // UTC or local date?
            createXmlNode("File_Date", LocalDate.now().toString(), crdpappin01, doc);
            createXmlNode(
                    "Terms_Accepted",
                    "The Province or Territory of British Columbia hereby confirms that it agrees to comply with the terms and conditions of use of the FTP process",
                    crdpappin01,
                    doc);
            rootElement.appendChild(crdpappin01);
            // END CRDPAPPIN01

            // Write the content into XML file
            DOMSource source = new DOMSource(doc);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            // Beautify the format of the resulted XML
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(source, new StreamResult(writer));

            // if output file to local dir
            // StreamResult result = new StreamResult(new File(fileComponent.getFileName()));
            // transformer.transform(source, result);
        } catch (Exception ex) {
            log.error("Error creating XML File:" + ex.getMessage());
        }
        return writer.getBuffer().toString();
    }

    private void createXmlNode(String node, String value, Element parentNode, Document doc) {
        Element e = doc.createElement(node);
        e.setTextContent(value);
        parentNode.appendChild(e);
    }
}
