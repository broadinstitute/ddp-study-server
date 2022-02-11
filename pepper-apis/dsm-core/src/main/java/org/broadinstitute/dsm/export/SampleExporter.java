package org.broadinstitute.dsm.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.ESSamplesDto;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Map;

public class SampleExporter implements Exporter {

    private static final Logger logger = LoggerFactory.getLogger(SampleExporter.class);
    private static final ObjectMapper oMapper = new ObjectMapper();
    private static final KitRequestDao kitRequestDao = new KitRequestDao();

    @Override
    public void export(DDPInstance instance) {
        int instanceId = instance.getDdpInstanceIdAsInt();
        logger.info("Started exporting samples for instance with id " + instanceId);
        ArrayDeque<ESSamplesDto> esSamples = new ArrayDeque<>(kitRequestDao.getESSamplesByInstanceId(instanceId));
        while (!esSamples.isEmpty()) {
            ESSamplesDto sample = esSamples.pop();
            Map<String, Object> map = oMapper.convertValue(sample, Map.class);
            if (sample.getKitRequestId() != null && sample.getDdpParticipantId() != null) {
                ElasticSearchUtil.writeSample(instance, sample.getKitRequestId(), sample.getDdpParticipantId(),
                        ESObjectConstants.SAMPLES, ESObjectConstants.KIT_REQUEST_ID, map);
            }
        }
        logger.info("Finished exporting samples for instance with id " + instanceId);
    }
}
