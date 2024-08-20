package org.broadinstitute.dsm.model.elastic.export.generate;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.OncHistoryDetail;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OncHistoryDetailUnableObtainTissueStrategy extends UnableObtainTissueStrategy {

    public OncHistoryDetailUnableObtainTissueStrategy(GeneratorPayload generatorPayload) {
        super(generatorPayload);
    }

    @Override
    public Map<String, Object> generate() {
        if (isUnableToObtain()) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.UNABLE_OBTAIN_TISSUE);
            return resultMap;
        }
        return super.generate();
    }

    private boolean isUnableToObtain() {
        return (boolean) generatorPayload.getValue();
    }
}
