package org.broadinstitute.dsm.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.tissue.ESTissueRecordsDao;
import org.broadinstitute.dsm.db.dto.ddp.tissue.ESTissueRecordsDto;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Map;

public class TissueRecordExporter implements Exporter {

    private static final Logger logger = LoggerFactory.getLogger(TissueRecordExporter.class);
    private static final ESTissueRecordsDao esTissueRecordsDao = new ESTissueRecordsDao();
    private static final ObjectMapper oMapper = new ObjectMapper();

    @Override
    public void export(DDPInstance instance) {
        int instanceId = instance.getDdpInstanceIdAsInt();
        logger.info("Started exporting tissue records for instance with id " + instanceId);
        ArrayDeque<ESTissueRecordsDto> esTissueRecords = new ArrayDeque<>(TissueRecordExporter.esTissueRecordsDao.getESTissueRecordsByInstanceId(instanceId));
        while (!esTissueRecords.isEmpty()) {
            ESTissueRecordsDto tissueRecord = esTissueRecords.pop();
            Map<String, Object> map = oMapper.convertValue(tissueRecord, Map.class);
            if (tissueRecord.getTissueRecordId() != null && tissueRecord.getDdpParticipantId() != null) {
                ElasticSearchUtil.writeDsmRecord(instance, tissueRecord.getTissueRecordId(), tissueRecord.getDdpParticipantId(),
                        ESObjectConstants.TISSUE_RECORDS, ESObjectConstants.TISSUE_RECORDS_ID, map);
            }
        }
        logger.info("Finished exporting tissue records for instance with id " + instanceId);
    }
}
