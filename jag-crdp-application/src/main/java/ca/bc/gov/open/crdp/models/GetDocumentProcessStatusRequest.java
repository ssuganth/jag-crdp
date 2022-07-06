package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GetDocumentProcessStatusRequest {
    private String processedDate;
    private String xmlFileShortName;
}
