package ca.bc.gov.open.crdp.models;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetDocumentProcessStatusRequest implements Serializable {
    private String processedDate;
    private String xmlFileShortName;
}
