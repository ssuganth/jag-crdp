package ca.bc.gov.open.crdp.models;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidMapDocument implements Serializable {
    private String version;
    private Map<String, String> mapping;
}
