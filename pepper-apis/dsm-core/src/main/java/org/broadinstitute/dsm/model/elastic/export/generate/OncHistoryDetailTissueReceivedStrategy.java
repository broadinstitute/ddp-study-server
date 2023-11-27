package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.db.OncHistoryDetail;

import java.util.HashMap;
import java.util.Map;

public class OncHistoryDetailTissueReceivedStrategy implements Generator {

    @Override
    public Map<String, Object> generate() {
        Map<String, Object> rm = new HashMap<>();
        rm.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_RECEIVED);
        return rm;
    }
}
