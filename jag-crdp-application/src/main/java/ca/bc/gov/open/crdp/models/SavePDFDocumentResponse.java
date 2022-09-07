package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavePDFDocumentResponse implements Serializable {
    private String objectGuid;
    private String resultCd;
    private String resultMsg;
}
