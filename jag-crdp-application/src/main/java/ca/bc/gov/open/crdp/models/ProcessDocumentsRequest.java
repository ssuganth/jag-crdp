package ca.bc.gov.open.crdp.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProcessDocumentsRequest {
    private String processedDate;
    private String xmlFileShortName;
    private List<String> pdfs;
}
