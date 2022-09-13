package org.broadinstitute.dsm.model.defaultvalues;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.pubsub.study.osteo.OsteoWorkflowStatusUpdate.NEW_OSTEO_COHORT_TAG_NAME;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.export.ElasticDataExportAdapter;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class NewOsteoDefaultValues extends BasicDefaultDataMaker {

    private CohortTagDao cohortTagDao;
    private ElasticDataExportAdapter elasticDataExportAdapter;


    public NewOsteoDefaultValues() {
        this.cohortTagDao = new CohortTagDaoImpl();
        this.elasticDataExportAdapter = new ElasticDataExportAdapter();
    }


    @Override
    protected boolean setDefaultData() {
        Dsm dsm = elasticSearchParticipantDto.getDsm().get();
        int newOsteoInstanceId = ddpInstanceDao.getDDPInstanceIdByInstanceName(NEW_OSTEO_INSTANCE_NAME);
        CohortTag newCohortTag = new CohortTag(
                NEW_OSTEO_COHORT_TAG_NAME,
                elasticSearchParticipantDto.getParticipantId(),
                newOsteoInstanceId);
        int newCohortTagId = cohortTagDao.create(newCohortTag);
        newCohortTag.setCohortTagId(newCohortTagId);
        logger.info("Attempting to update `dsm` object in ES");
        dsm.setCohortTag(Stream.concat(dsm.getCohortTag().stream(), Stream.of(newCohortTag)).collect(Collectors.toList()));
        Map<String, Object> dsmAsMap =
                ObjectMapperSingleton.readValue(ObjectMapperSingleton.writeValueAsString(dsm),
                        new TypeReference<Map<String, Object>>() {});
        writeDataToES(Map.of(ESObjectConstants.DSM, dsmAsMap));
        return true;
    }

    private void writeDataToES(Map<String, Object> esPtDtoAsMap) {
        logger.info("Attempting to write `dsm` object in ES");
        elasticDataExportAdapter.setSource(esPtDtoAsMap);
        elasticDataExportAdapter.export();
    }
}
