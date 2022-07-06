package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProcessXMLRequest {
    private byte[] ccDocument;
    private GuidMapDocument map;
}
