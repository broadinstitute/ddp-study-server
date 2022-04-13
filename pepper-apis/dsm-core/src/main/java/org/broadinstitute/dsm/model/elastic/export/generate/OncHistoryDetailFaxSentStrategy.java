package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.db.OncHistoryDetail;

import java.util.HashMap;
import java.util.Map;

public class OncHistoryDetailFaxSentStrategy implements Generator {

    private final String faxConfirmed;
    private final Object value;

    public OncHistoryDetailFaxSentStrategy(String faxConfirmed, Object value) {
        this.faxConfirmed = faxConfirmed;
        this.value = value;
    }

    @Override
    public Map<String, Object> generate() {
        Map<String, Object> map = new HashMap<>();
        map.put(faxConfirmed, value);
        map.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_SENT);
        return map;
    }
}
