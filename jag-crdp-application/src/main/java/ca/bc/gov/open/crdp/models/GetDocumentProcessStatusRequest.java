package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetDocumentProcessStatusRequest implements Serializable {
    private String processedDate;
    private String xmlFileShortName;
}
