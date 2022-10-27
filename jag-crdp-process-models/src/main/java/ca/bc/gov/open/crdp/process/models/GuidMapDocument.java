package ca.bc.gov.open.crdp.process.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuidMapDocument {
    private String version;
    private List<GuidDocumentMapping> mappings;
}
