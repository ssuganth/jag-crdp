package ca.bc.gov.open.crdp.transmit.models;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartOneData implements Serializable {
    private String recordType;
    private String courtNumber;
    private String divorceRegistryNumber;
    private String feeCode;
    private String filingDate;
    private String marriageDate;
    private String jointApplication;
    private String applicantSurname;
    private String applicantGivenName;
    private String applicantBirthDate;
    private String applicantGender;
    private String respondentSurname;
    private String respondentGivenName;
    private String respondentBirthDate;
    private String respondentGender;
    private String originalCourtNumber;
    private String originalDivorceRegNumber;
    private String petitionSignedDate;
    private String physicalFileId;
}
