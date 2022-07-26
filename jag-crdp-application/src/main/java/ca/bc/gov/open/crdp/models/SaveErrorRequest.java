package ca.bc.gov.open.crdp.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SaveErrorRequest {
    private String errMsg;
    private String date;
    private String fileName;
    private byte[] fileContentXml;
}
