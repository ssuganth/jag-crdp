package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProcessReportRequest {
    private String reportFileName;
    private String processedDate;
    private byte[] data;
}
