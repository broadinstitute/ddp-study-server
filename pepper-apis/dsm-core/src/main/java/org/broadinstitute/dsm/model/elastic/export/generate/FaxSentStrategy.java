package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

public class FaxSentStrategy implements Generator {
    protected String faxConfirmed;
    protected Object value;

    public FaxSentStrategy(String faxConfirmed, Object value) {
        this.faxConfirmed = faxConfirmed;
        this.value = value;
    }

    @Override
    public Map<String, Object> generate() {
        Map<String, Object> map = new HashMap<>();
        map.put(faxConfirmed, value);
        return map;
    }
}
