package ca.bc.gov.open.crdp.models;

import java.io.Serializable;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateIncomingReqFileResponse implements Serializable {
    private String status;
    private String errMsg;
    private String fileName;
    private String file;
}
