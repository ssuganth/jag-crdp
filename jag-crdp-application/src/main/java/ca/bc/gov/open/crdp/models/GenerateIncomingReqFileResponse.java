package ca.bc.gov.open.crdp.models;

import lombok.Data;

@Data
public class GenerateIncomingReqFileResponse {
    private String status;
    private String fileName;
    private String file;
}
