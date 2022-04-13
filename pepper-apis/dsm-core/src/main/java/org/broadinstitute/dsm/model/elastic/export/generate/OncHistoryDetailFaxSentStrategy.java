package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.db.OncHistoryDetail;

public class OncHistoryDetailFaxSentStrategy implements Generator {

    Generator baseStrategy;

    public OncHistoryDetailFaxSentStrategy(Generator strategy) {
        this.baseStrategy = strategy;
    }

    @Override
    public Map<String, Object> generate() {
        Map<String, Object> baseMap = baseStrategy.generate();
        baseMap.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_SENT);
        return baseMap;
    }
}
