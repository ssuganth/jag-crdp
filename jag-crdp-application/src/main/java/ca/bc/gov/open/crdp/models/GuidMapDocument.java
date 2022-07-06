package ca.bc.gov.open.crdp.models;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuidMapDocument {
    private String version;
    private Map<String, String> mapping;
}
