package ca.bc.gov.open.crdp.transmit.models;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartTwoData implements Serializable {
    private String recordType;
    private String courtNumber;
    private String divorceRegistryNumber;
    private String sourceCaseNumber;
    private String dispositionCode;
    private String dispositionDate;
    private String transferredCourtNumber;
    private String dispositionSignedDate;
    private String physicalFileId;
}
