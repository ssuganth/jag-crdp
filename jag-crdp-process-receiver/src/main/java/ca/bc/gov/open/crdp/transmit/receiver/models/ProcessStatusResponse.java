package ca.bc.gov.open.crdp.transmit.receiver.models;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStatusResponse implements Serializable {
    private String resultCd;
    private String resultMsg;
}
