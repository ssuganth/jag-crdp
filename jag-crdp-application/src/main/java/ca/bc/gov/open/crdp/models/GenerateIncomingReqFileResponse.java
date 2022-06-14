package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenerateIncomingReqFileResponse {
    private String status;
    private String fileName;
    private byte[] file;
}
