package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;

public class UnableObtainTissueStrategy implements Generator {

    protected final GeneratorPayload generatorPayload;
    OncHistoryDetailDao<OncHistoryDetailDto> oncHistoryDetailDao;

    public UnableObtainTissueStrategy(GeneratorPayload generatorPayload) {
        this.generatorPayload = generatorPayload;
        oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
    }

    @Override
    public Map<String, Object> generate() {
        boolean hasReceivedDate = oncHistoryDetailDao.hasReceivedDate(generatorPayload.getRecordId());
        Map<String, Object> resultMap = new HashMap<>();
        if (hasReceivedDate) {
            resultMap.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_RECEIVED);
        } else {
            resultMap.put(OncHistoryDetail.STATUS_REQUEST, OncHistoryDetail.STATUS_SENT);
        }
        return resultMap;
    }
}
