package ca.bc.gov.open.crdp.transmit.models;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegModData implements Serializable {
    private String recordType;
    private String courtNumber;
    private String divorceRegistryNumber;
    private String sourceCaseNumber;
    private String originalDivorceRegNumber;
    private String physicalFileId;
}
