package ca.bc.gov.open.crdp.transmit.receiver.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessCCsResponse implements Serializable {
    private String resultCd;
    private String resultMsg;
}
