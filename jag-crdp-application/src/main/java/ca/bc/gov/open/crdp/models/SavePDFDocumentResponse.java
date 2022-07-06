package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SavePDFDocumentResponse {
    private String objectGuid;
    private String resultCd;
    private String resultMsg;
}
