package ca.bc.gov.open.crdp.process.models;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScannerPub implements Serializable {
    private String filePath;
    private String dateTime;
}
