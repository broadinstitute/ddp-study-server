package org.broadinstitute.dsm.model.tags.cohort;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.ScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CohortTagUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CohortTagUseCase.class);
    private ScriptBuilder scriptBuilder;
    private CohortTag cohortTagPayload;
    private DDPInstanceDto ddpInstanceDto;
    private CohortTagDao cohortTagDao;
    private ElasticSearchable elasticSearchable;
    private UpsertPainlessFacade upsertPainlessFacade;

    public CohortTagUseCase(CohortTag cohortTagPayload, DDPInstanceDto ddpInstanceDto, CohortTagDao cohortTagDao,
                            ElasticSearchable elasticSearchable, UpsertPainlessFacade upsertPainlessFacade, ScriptBuilder scriptBuilder) {
        this.cohortTagPayload = cohortTagPayload;
        this.ddpInstanceDto = ddpInstanceDto;
        this.cohortTagDao = cohortTagDao;
        this.elasticSearchable = elasticSearchable;
        this.upsertPainlessFacade = upsertPainlessFacade;
        this.scriptBuilder = scriptBuilder;
        this.cohortTagPayload.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
    }

    public int insert() {
        logger.info("Inserting cohort tag with tag name: " + getCohortTagName()
                + " for participant with id: " + getDdpParticipantId());
        upsertPainlessFacade.setFieldName(ESObjectConstants.DOC_ID);
        upsertPainlessFacade.setFieldValue(getGuidIfLegacyAltPid(ddpInstanceDto, cohortTagPayload));
        prepareUpsertPainlessFacade();
        cohortTagPayload.setDdpParticipantId(getGuidIfLegacyAltPid(ddpInstanceDto, cohortTagPayload));
        int justCreatedCohortTagId = cohortTagDao.create(cohortTagPayload);
        cohortTagPayload.setCohortTagId(justCreatedCohortTagId);
        upsertPainlessFacade.export();
        logger.info("Inserted cohort tag: " + getCohortTagName() + " for participant with id: " + getDdpParticipantId());
        return justCreatedCohortTagId;
    }

    private String getGuidIfLegacyAltPid(DDPInstanceDto ddpInstanceDto, CohortTag cohortTagPayload) {
        String ddpParticipantId = cohortTagPayload.getDdpParticipantId();
        boolean isLegacyAltPid = ParticipantUtil.isLegacyAltPid(ddpParticipantId);
        if (isLegacyAltPid) {
            ElasticSearchParticipantDto esDto =
                    elasticSearchable.getParticipantById(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
            ddpParticipantId = esDto.getParticipantId();
        }
        return ddpParticipantId;
    }

    private void prepareUpsertPainlessFacade() {
        upsertPainlessFacade.setSource(cohortTagPayload);
        upsertPainlessFacade.setUniqueIdentifier(ESObjectConstants.DSM_COHORT_TAG_ID);
        upsertPainlessFacade.setGeneratorElseLogError(ddpInstanceDto);
        upsertPainlessFacade.buildAndSetFieldTypeExtractor(ddpInstanceDto);
        upsertPainlessFacade.buildAndSetUpsertPainless(ddpInstanceDto, scriptBuilder);
    }

    public void delete() {
        logger.info("Deleting cohort tag: " + getCohortTagName() + " from participant with id: " + getDdpParticipantId());
        upsertPainlessFacade.setFieldName(ESObjectConstants.DSM_COHORT_TAG_ID);
        upsertPainlessFacade.setFieldValue(cohortTagPayload.getCohortTagId());
        prepareUpsertPainlessFacade();
        cohortTagDao.delete(cohortTagPayload.getCohortTagId());
        upsertPainlessFacade.export();
        logger.info("Deleted cohort tag: " + getCohortTagName() + " from participant with id: " + getDdpParticipantId());
    }

    private String getDdpParticipantId() {
        return cohortTagPayload.getDdpParticipantId();
    }

    private String getCohortTagName() {
        return cohortTagPayload.getCohortTagName();
    }
}

