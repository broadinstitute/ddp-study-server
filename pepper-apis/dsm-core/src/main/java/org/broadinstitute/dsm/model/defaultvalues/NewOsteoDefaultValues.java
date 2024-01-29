package org.broadinstitute.dsm.model.defaultvalues;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.pubsub.study.osteo.OsteoWorkflowStatusUpdate.NEW_OSTEO_COHORT_TAG_NAME;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.export.ElasticDataExportAdapter;
import org.broadinstitute.dsm.model.elastic.export.RequestPayload;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Slf4j
public class NewOsteoDefaultValues extends BasicDefaultDataMaker {

    private CohortTagDao cohortTagDao;
    private ElasticDataExportAdapter elasticDataExportAdapter;


    public NewOsteoDefaultValues() {
        this.cohortTagDao = new CohortTagDaoImpl();
        this.elasticDataExportAdapter = new ElasticDataExportAdapter();
    }


    @Override
    protected boolean setDefaultData() {
        if (elasticSearchParticipantDto.getDsm().isEmpty()) {
            // TODO: coding it this way since the existing behavior was to retry elastic until the data shows up
            // but I'm not sure that is applicable here since the code is looking for DSM data - DC
            throw new ESMissingParticipantDataException("Participant dsm ES data missing");
        }

        Dsm dsm = elasticSearchParticipantDto.getDsm().get();
        DDPInstanceDto ddpInstanceByInstanceName =
                ddpInstanceDao.getDDPInstanceByInstanceName(NEW_OSTEO_INSTANCE_NAME).orElseThrow();
        CohortTag newCohortTag = new CohortTag(
                NEW_OSTEO_COHORT_TAG_NAME,
                elasticSearchParticipantDto.getParticipantId(),
                ddpInstanceByInstanceName.getDdpInstanceId());
        int newCohortTagId = cohortTagDao.create(newCohortTag);
        newCohortTag.setCohortTagId(newCohortTagId);
        log.info("Attempting to update `dsm` object in ES");
        dsm.setCohortTag(List.of(newCohortTag));
        Map<String, Object> dsmAsMap =
                ObjectMapperSingleton.readValue(ObjectMapperSingleton.writeValueAsString(dsm),
                        new TypeReference<Map<String, Object>>() {});
        this.elasticDataExportAdapter.setRequestPayload(new RequestPayload(ddpInstanceByInstanceName.getEsParticipantIndex(),
                elasticSearchParticipantDto.getParticipantId()));
        writeDataToES(Map.of(ESObjectConstants.DSM, dsmAsMap));
        return true;
    }

    private void writeDataToES(Map<String, Object> esPtDtoAsMap) {
        log.info("Attempting to write `dsm` object in ES");
        elasticDataExportAdapter.setSource(esPtDtoAsMap);
        elasticDataExportAdapter.export();
    }
}
