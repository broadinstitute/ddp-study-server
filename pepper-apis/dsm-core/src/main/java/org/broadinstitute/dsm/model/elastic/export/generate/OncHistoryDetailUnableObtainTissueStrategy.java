package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;

import java.util.HashMap;
import java.util.Map;

public class OncHistoryDetailUnableObtainTissueStrategy implements Generator {

    private final GeneratorPayload generatorPayload;
    OncHistoryDetailDao<OncHistoryDetailDto> oncHistoryDetailDao;

    public OncHistoryDetailUnableObtainTissueStrategy(GeneratorPayload generatorPayload) {
        oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
        this.generatorPayload = generatorPayload;
    }

    @Override
    public Map<String, Object> generate() {
        if (isUnableToObtain()) {
            return Map.of();
        }
        boolean hasReceivedDate = oncHistoryDetailDao.hasReceivedDate(generatorPayload.getRecordId());
        Map<String, Object> resultMap = new HashMap<>();
        if (hasReceivedDate) {
            resultMap.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_RECEIVED);
        } else {
            resultMap.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_SENT);
        }
        return resultMap;
    }

    private boolean isUnableToObtain() {
        return (boolean) generatorPayload.getValue();
    }
}
