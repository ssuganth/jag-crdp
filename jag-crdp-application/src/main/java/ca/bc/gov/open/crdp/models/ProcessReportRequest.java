package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessReportRequest implements Serializable {
    private String reportFileName;
    private String processedDate;
    private byte[] data;
}
