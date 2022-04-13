package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OncHistoryDetailSourceGenerator extends CollectionSourceGenerator {

    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        Map<String, Object> resultMap = new HashMap<>();
        if ("faxSent".equals(getFieldName())) {
            addFaxSentData(resultMap, "faxConfirmed");
        } else if ("faxSent2".equals(getFieldName())) {
            addFaxSentData(resultMap, "faxConfirmed2");
        } else if ("faxSent3".equals(getFieldName())) {
            addFaxSentData(resultMap, "faxConfirmed3");
        } else if ("tissueReceived".equals(getFieldName())) {
            resultMap.put("request", "received");
        }
        return super.getAdditionalData();
    }

    private void addFaxSentData(Map<String, Object> resultMap, String faxConfirmed) {
        resultMap.put(faxConfirmed, generatorPayload.getValue());
        resultMap.put("request", "sent");
    }
}
