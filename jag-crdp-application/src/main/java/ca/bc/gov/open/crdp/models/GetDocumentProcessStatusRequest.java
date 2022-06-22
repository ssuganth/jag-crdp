package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GetDocumentProcessStatusRequest {
    private String processedDate;
    private String xmlFileShortName;
}
