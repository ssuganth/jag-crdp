package ca.bc.gov.open.crdp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.server.endpoint.annotation.Endpoint;

@Endpoint
@Slf4j
public class ProcessController {

    @Value("${crdp.host}")
    private String host = "https://127.0.0.1/";

    @Value("${crdp.in-file-dir}")
    private String inFileDir = "/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private List<String> headFolderList = new ArrayList<String>();
    private static String
            processFolderName; // current "Processed_yyyy_nn" folder name (not full path).

    private TreeMap<String, String> processedFilesToMove =
            new TreeMap<String, String>(); // completed files.
    private TreeMap<String, String> erredFilesToMove =
            new TreeMap<String, String>(); // erred files.

    private TreeMap<String, String> processedFoldersToMove =
            new TreeMap<String, String>(); // completed folders.
    private TreeMap<String, String> erredFoldersToMove =
            new TreeMap<String, String>(); // erred folders.

    @Autowired
    public ProcessController(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private void CRDPScanner() {}
}
