package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;

public class OncHistoryDetailSourceGenerator extends CollectionSourceGenerator {

    OncHistoryDetailDao<OncHistoryDetailDto> oncHistoryDetailDao;

    public OncHistoryDetailSourceGenerator() {
        oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
    }
    
    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        Map<String, Object> resultMap = new HashMap<>();
        if ("faxSent".equals(getFieldName())) {
            resultMap = addFaxSentData("faxConfirmed");
        } else if ("faxSent2".equals(getFieldName())) {
            resultMap = addFaxSentData("faxConfirmed2");
        } else if ("faxSent3".equals(getFieldName())) {
            resultMap = addFaxSentData("faxConfirmed3");
        } else if ("tissueReceived".equals(getFieldName())) {
            Map<String, Object> rm = new HashMap<>();
            rm.put("request", "received");
            resultMap = rm;
        } else if (isAbleToObtain()) {
            resultMap = addRequestBasedOnReceivedDate();
        } 
        return Optional.of(resultMap);
    }

    private void addRequestBasedOnReceivedDate() {
        boolean hasReceivedDate = oncHistoryDetailDao.hasReceivedDate(generatorPayload.getRecordId());
        if (hasReceivedDate) {
            resultMap.put("request", "received");
        } else {
            resultMap.put("request", "sent");
        }
    }

    private boolean isAbleToObtain() {
        return "unableObtainTissue".equals(getFieldName()) && !(boolean) generatorPayload.getValue();
    }

    private Map<String, Object> addFaxSentData(String faxConfirmed) {
        Map<String, Object> map = new HashMap<>();
        map.put(faxConfirmed, generatorPayload.getValue());
        map.put("request", "sent");
        return map;
    }
}
