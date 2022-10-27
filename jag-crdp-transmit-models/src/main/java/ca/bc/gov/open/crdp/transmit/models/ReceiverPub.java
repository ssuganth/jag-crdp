package ca.bc.gov.open.crdp.transmit.models;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiverPub implements Serializable {
    private String fileName;
    private String xmlString;
    private String dataExchangeFileSeqNo;
    private List<String> partOneFileIds;
    private List<String> regModFileIds;
    private List<String> partTwoFileIds;
}
