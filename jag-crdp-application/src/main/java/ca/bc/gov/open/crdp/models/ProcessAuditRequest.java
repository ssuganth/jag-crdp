package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProcessAuditRequest {
    private String fileName;
    private byte[] documentXmlString;
}
