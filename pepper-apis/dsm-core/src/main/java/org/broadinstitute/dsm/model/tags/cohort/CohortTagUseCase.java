package org.broadinstitute.dsm.model.tags.cohort;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ParticipantUtil;

public class CohortTagUseCase {
    private CohortTag cohortTagPayload;
    private DDPInstanceDto ddpInstanceDto;
    private CohortTagDao cohortTagDao;
    private ElasticSearchable elasticSearchable;
    private UpsertPainlessFacade upsertPainlessFacade;

    public CohortTagUseCase(CohortTag cohortTagPayload, DDPInstanceDto ddpInstanceDto, CohortTagDao cohortTagDao,
                            ElasticSearchable elasticSearchable, UpsertPainlessFacade upsertPainlessFacade) {
        this.cohortTagPayload = cohortTagPayload;
        this.ddpInstanceDto = ddpInstanceDto;
        this.cohortTagDao = cohortTagDao;
        this.elasticSearchable = elasticSearchable;
        prepareUpsertPainlessFacade(upsertPainlessFacade);
        fillCohortTag();
    }

    private void prepareUpsertPainlessFacade(UpsertPainlessFacade upsertPainlessFacade) {
        upsertPainlessFacade.setSource(cohortTagPayload);
        upsertPainlessFacade.setUniqueIdentifier(ESObjectConstants.DSM_COHORT_TAG_ID);
        upsertPainlessFacade.setFieldName(ESObjectConstants.DOC_ID);
        upsertPainlessFacade.setFieldValue(getGuidIfLegacyAltPid(ddpInstanceDto, cohortTagPayload));
        upsertPainlessFacade.setGeneratorElseLogError(ddpInstanceDto);
        upsertPainlessFacade.buildAndSetFieldTypeExtractor(ddpInstanceDto);
        upsertPainlessFacade.buildAndSetUpsertPainless(ddpInstanceDto, new PutToNestedScriptBuilder());
        this.upsertPainlessFacade = upsertPainlessFacade;
    }

    private void fillCohortTag() {
        cohortTagPayload.setDdpParticipantId(getGuidIfLegacyAltPid(ddpInstanceDto, cohortTagPayload));
        cohortTagPayload.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
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

    public int insert() {
        int justCreatedCohortTagId = cohortTagDao.create(cohortTagPayload);
        cohortTagPayload.setCohortTagId(justCreatedCohortTagId);
        upsertPainlessFacade.export();
        return justCreatedCohortTagId;
    }
}
