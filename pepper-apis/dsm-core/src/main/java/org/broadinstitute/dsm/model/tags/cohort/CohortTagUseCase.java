package org.broadinstitute.dsm.model.tags.cohort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Data;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.exception.DuplicateException;
import org.broadinstitute.dsm.model.elastic.export.painless.ScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class CohortTagUseCase {

    private static final Logger logger = LoggerFactory.getLogger(CohortTagUseCase.class);
    private ScriptBuilder scriptBuilder;
    private CohortTag cohortTagPayload;
    private DDPInstanceDto ddpInstanceDto;
    private CohortTagDao cohortTagDao;
    private ElasticSearchable elasticSearchable;
    private UpsertPainlessFacade upsertPainlessFacade;
    private BulkCohortTag bulkCohortTag;

    public CohortTagUseCase(CohortTag cohortTagPayload, DDPInstanceDto ddpInstanceDto, CohortTagDao cohortTagDao,
                            ElasticSearchable elasticSearchable, UpsertPainlessFacade upsertPainlessFacade, ScriptBuilder scriptBuilder) {
        this(ddpInstanceDto, cohortTagDao, elasticSearchable, upsertPainlessFacade, scriptBuilder);
        this.cohortTagPayload = cohortTagPayload;
        this.cohortTagPayload.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
    }

    public CohortTagUseCase(BulkCohortTag bulkCohortTag, DDPInstanceDto ddpInstanceDto, CohortTagDao cohortTagDao,
                            ElasticSearchable elasticSearchable, UpsertPainlessFacade upsertPainlessFacade, ScriptBuilder scriptBuilder) {
        this(ddpInstanceDto, cohortTagDao, elasticSearchable, upsertPainlessFacade, scriptBuilder);
        this.bulkCohortTag = bulkCohortTag;
    }

    private CohortTagUseCase(DDPInstanceDto ddpInstanceDto, CohortTagDao cohortTagDao, ElasticSearchable elasticSearchable,
                            UpsertPainlessFacade upsertPainlessFacade, ScriptBuilder scriptBuilder) {
        this.ddpInstanceDto = ddpInstanceDto;
        this.cohortTagDao = cohortTagDao;
        this.elasticSearchable = elasticSearchable;
        this.upsertPainlessFacade = upsertPainlessFacade;
        this.scriptBuilder = scriptBuilder;
    }

    public int insert() throws DuplicateException {
        if (participantHasTag()) {
            throw new DuplicateException(String.format("Participant %s Already has tag %s", cohortTagPayload.getDdpParticipantId(),
                    cohortTagPayload.getCohortTagName()));
        }
        logger.info("Inserting cohort tag with tag name: " + getCohortTagName()
                + " for participant with id: " + getDdpParticipantId());
        cohortTagPayload.setDdpParticipantId(getGuidIfLegacyAltPid(ddpInstanceDto, cohortTagPayload));
        int justCreatedCohortTagId = cohortTagDao.create(cohortTagPayload);
        cohortTagPayload.setCohortTagId(justCreatedCohortTagId);

        prepareUpsertPainlessFacade(cohortTagPayload, ESObjectConstants.DSM_COHORT_TAG_ID,
                ESObjectConstants.DOC_ID, getGuidIfLegacyAltPid(ddpInstanceDto, Objects.requireNonNull(cohortTagPayload)));
        upsertPainlessFacade.export();
        logger.info("Inserted cohort tag: " + getCohortTagName() + " for participant with id: " + getDdpParticipantId());
        return justCreatedCohortTagId;
    }

    public List<CohortTag> bulkInsert() {
        logger.info("Inserting cohort tags: " + bulkCohortTag.getCohortTags());
        List<CohortTag> cohortTagsToCreate = createCohortTagObjectsFromStringTags();
        if (cohortTagsToCreate.size() == 0) {
            return new ArrayList<>();
        }
        List<Integer> createdCohortTagsIds = cohortTagDao.bulkCohortCreate(cohortTagsToCreate);
        setCohortTagIdsToCohortTags(cohortTagsToCreate, createdCohortTagsIds);

        prepareUpsertPainlessFacade(cohortTagsToCreate, ESObjectConstants.DDP_PARTICIPANT_ID,
                String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.PROFILE, ESObjectConstants.GUID),
                Objects.requireNonNull(bulkCohortTag.getSelectedPatients()));
        upsertPainlessFacade.export();
        logger.info("Inserted cohort tags: " + bulkCohortTag.getCohortTags());
        return cohortTagsToCreate;
    }

    private List<CohortTag> createCohortTagObjectsFromStringTags() {
        List<CohortTag> cohortTagsToCreate = new ArrayList<>();
        String createdBy = bulkCohortTag.getCreatedBy();
        for (String participantId: bulkCohortTag.getSelectedPatients()) {
            for (String tag: bulkCohortTag.getCohortTags()) {
                if (!cohortTagDao.participantHasTag(participantId, tag) && !isDuplicateTag(participantId, tag, cohortTagsToCreate)) {
                    CohortTag cohortTag = new CohortTag(tag, participantId, ddpInstanceDto.getDdpInstanceId());
                    cohortTag.setCreatedBy(createdBy);
                    cohortTagsToCreate.add(cohortTag);
                }
            }
        }
        return cohortTagsToCreate;
    }

    private boolean isDuplicateTag(String participantId, String tag, List<CohortTag> cohortTagsToCreate) {
        return cohortTagsToCreate.stream().filter(cohortTag ->
            cohortTag.getCohortTagName().equals(tag) && cohortTag.getDdpParticipantId().equals(participantId)).findAny().isPresent();
    }

    private void setCohortTagIdsToCohortTags(List<CohortTag> cohortTagsToCreate, List<Integer> createdCohortTagsIds) {
        for (int i = 0; i < cohortTagsToCreate.size(); i++) {
            cohortTagsToCreate.get(i).setCohortTagId(createdCohortTagsIds.get(i));
        }
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

    private void prepareUpsertPainlessFacade(Object source, String uniqueIdentifier, String fieldName, Object fieldValue) {
        upsertPainlessFacade.setFieldName(fieldName);
        upsertPainlessFacade.setFieldValue(fieldValue);
        upsertPainlessFacade.setSource(source);
        upsertPainlessFacade.setUniqueIdentifier(uniqueIdentifier);
        upsertPainlessFacade.setGeneratorElseLogError(ddpInstanceDto);
        upsertPainlessFacade.buildAndSetFieldTypeExtractor(ddpInstanceDto);
        upsertPainlessFacade.buildAndSetUpsertPainless(ddpInstanceDto, scriptBuilder);
    }

    public void delete() {
        logger.info("Deleting cohort tag with id: " + getCohortTagId());
        prepareUpsertPainlessFacade(cohortTagPayload, ESObjectConstants.DSM_COHORT_TAG_ID,
                ESObjectConstants.DSM_COHORT_TAG_ID, cohortTagPayload.getCohortTagId());
        cohortTagDao.delete(cohortTagPayload.getCohortTagId());
        upsertPainlessFacade.export();
        logger.info("Deleted cohort tag with id: " + getCohortTagId());
    }

    private int getCohortTagId() {
        return cohortTagPayload.getCohortTagId();
    }

    private String getDdpParticipantId() {
        return cohortTagPayload.getDdpParticipantId();
    }

    private String getCohortTagName() {
        return cohortTagPayload.getCohortTagName();
    }

    public boolean participantHasTag() {
        return cohortTagDao.participantHasTag(this.getDdpParticipantId(), this.getCohortTagName());
    }
}

