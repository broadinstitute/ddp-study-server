package org.broadinstitute.dsm.pubsub.study.osteo;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.ElasticDataExportAdapter;
import org.broadinstitute.dsm.model.elastic.export.RequestPayload;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.pubsub.study.HasWorkflowStatusUpdate;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsteoWorkflowStatusUpdate implements HasWorkflowStatusUpdate {

    private static final Logger logger = LoggerFactory.getLogger(OsteoWorkflowStatusUpdate.class);
    private static final Gson GSON = new Gson();
    public static final String NEW_OSTEO_COHORT_TAG_NAME = "OS PE-CGS";

    private final DDPInstanceDto instance;
    private final String ddpParticipantId;
    private final int newOsteoInstanceId;
    private final CohortTag newCohortTag;

    private final ParticipantDao participantDao;
    private final ParticipantRecordDao participantRecordDao;
    private final DDPInstitutionDao ddpInstitutionDao;
    private final MedicalRecordDao medicalRecordDao;
    private final CohortTagDao cohortTagDao;

    private final ElasticDataExportAdapter elasticDataExportAdapter;
    private final ElasticSearchable elasticSearch;


    private OsteoWorkflowStatusUpdate(DDPInstanceDto instance, String ddpParticipantId) {
        this.instance = instance;
        this.ddpParticipantId = ddpParticipantId;
        this.participantDao = ParticipantDao.of();
        this.participantRecordDao = ParticipantRecordDao.of();
        this.ddpInstitutionDao = DDPInstitutionDao.of();
        this.medicalRecordDao = MedicalRecordDao.of();
        this.cohortTagDao = new CohortTagDaoImpl();
        this.elasticSearch = new ElasticSearch();
        this.elasticDataExportAdapter = new ElasticDataExportAdapter();
        elasticDataExportAdapter.setRequestPayload(new RequestPayload(instance.getEsParticipantIndex(), ddpParticipantId));
        this.newOsteoInstanceId = DDPInstanceDao.of().getDDPInstanceIdByInstanceName(NEW_OSTEO_INSTANCE_NAME);
        this.newCohortTag = new CohortTag(NEW_OSTEO_COHORT_TAG_NAME, ddpParticipantId, newOsteoInstanceId);
    }


    public static OsteoWorkflowStatusUpdate of(DDPInstanceDto instance, String ddpParticipantId) {
        return new OsteoWorkflowStatusUpdate(instance, ddpParticipantId);
    }

    @Override
    public void update() {
        logger.info(String.format("Running workflow updates for %s", NEW_OSTEO_INSTANCE_NAME));
        int ddpInstanceId = instance.getDdpInstanceId();
        boolean isParticipantInDb = MedicalRecordUtil.isParticipantInDB(ddpParticipantId, String.valueOf(ddpInstanceId));
        if (isParticipantInDb) {
            logger.info(String.format("Updating values for existing participant in db for %s", NEW_OSTEO_INSTANCE_NAME));
            Optional<ParticipantDto> maybeOldOsteoParticipant = participantDao
                    .getParticipantByDdpParticipantIdAndDdpInstanceId(ddpParticipantId, ddpInstanceId);
            Optional<Integer> maybeOldOsteoParticipantId = maybeOldOsteoParticipant.flatMap(ParticipantDto::getParticipantId);
            Optional<Integer> maybeNewOsteoParticipantId = maybeOldOsteoParticipant
                    .map(this::updateParticipantDto)
                    .map(participantDao::create);
            int newCohortTagId = cohortTagDao.create(newCohortTag);
            newCohortTag.setCohortTagId(newCohortTagId);
            Optional<ParticipantRecordDto> maybeOldOsteoParticipantRecord = maybeOldOsteoParticipantId
                    .flatMap(participantRecordDao::getParticipantRecordByParticipantId);
            maybeOldOsteoParticipantRecord.ifPresent(participantRecord -> maybeNewOsteoParticipantId
                    .ifPresent(participantId -> updateAndThenSaveNewParticipantRecord(participantRecord, participantId)));
            List<MedicalRecord> newOsteoMedicalRecords = maybeNewOsteoParticipantId
                    .map(this::updateAndThenSaveInstitutionsAndMedicalRecords).orElseThrow();
            String oldOsteoDdpParticipantId = maybeOldOsteoParticipant.flatMap(ParticipantDto::getDdpParticipantId).orElseThrow();
            ElasticSearchParticipantDto esPtDto = elasticSearch
                    .getParticipantById(instance.getEsParticipantIndex(), oldOsteoDdpParticipantId);
            int newOsteoParticipantId = maybeNewOsteoParticipantId.orElseThrow();
            esPtDto.getDsm().ifPresentOrElse(
                    esDsm -> updateDsmAndWriteToES(newOsteoParticipantId, newOsteoMedicalRecords, esDsm),
                    ()    -> logger.warn(String.format("Could not find participant in ES with guid %s", ddpParticipantId))
            );
        }
    }

    private ParticipantDto updateParticipantDto(ParticipantDto participantDto) {
        ParticipantDto clonedParticipantDto = participantDto.clone();
        clonedParticipantDto.setDdpInstanceId(newOsteoInstanceId);
        clonedParticipantDto.setAssigneeIdMr(Util.orElseNull(participantDto.getAssigneeIdMr(), 0));
        clonedParticipantDto.setAssigneeIdTissue(Util.orElseNull(participantDto.getAssigneeIdTissue(), 0));
        return clonedParticipantDto;
    }

    private void writeDataToES(Map<String, Object> esPtDtoAsMap) {
        logger.info("Attempting to write `dsm` object in ES");
        elasticDataExportAdapter.setSource(esPtDtoAsMap);
        elasticDataExportAdapter.export();
    }

    private void updateDsmAndWriteToES(long newOsteoParticipantId, List<MedicalRecord> newOsteoMedicalRecords, Dsm dsm) {
        logger.info("Attempting to update the `dsm` object in ES");
        dsm.getParticipant().ifPresent(oldOsteoPt -> updateNewOsteoParticipant(newOsteoParticipantId, dsm, oldOsteoPt));
        dsm.setCohortTag(Stream.concat(dsm.getCohortTag().stream(), Stream.of(newCohortTag)).collect(Collectors.toList()));
        List<MedicalRecord> oldOsteoMedicalRecords = dsm.getMedicalRecord();
        List<MedicalRecord> updatedMedicalRecords = Stream.concat(oldOsteoMedicalRecords.stream(), newOsteoMedicalRecords.stream())
                .collect(Collectors.toList());
        dsm.setMedicalRecord(updatedMedicalRecords);
        logger.info("`dsm` object was updated successfully");
        Map<String, Object> dsmAsMap =
                ObjectMapperSingleton.readValue(ObjectMapperSingleton.writeValueAsString(dsm),
                        new TypeReference<Map<String, Object>>() {});
        writeDataToES(Map.of(ESObjectConstants.DSM, dsmAsMap));
    }

    private void updateNewOsteoParticipant(long newOsteoParticipantId, Dsm dsm, Participant oldOsteoPt) {
        Participant newOsteoPt = oldOsteoPt.clone();
        newOsteoPt.setParticipantId(newOsteoParticipantId);
        newOsteoPt.setDdpInstanceId(newOsteoInstanceId);
        dsm.setNewOsteoParticipant(newOsteoPt);
    }

    private List<MedicalRecord> updateAndThenSaveInstitutionsAndMedicalRecords(int newOsteoParticipantId) {
        List<MedicalRecord> newOsteoMedicalRecords = new ArrayList<>();
        List<MedicalRecord> oldOsteoMedicalRecords = MedicalRecord.getMedicalRecordsByInstanceNameAndDdpParticipantId(
                instance.getInstanceName(),
                ddpParticipantId);
        oldOsteoMedicalRecords.forEach(medicalRecord -> {
            int newOsteoInstitutionId = updateAndThenSaveNewInstitution(newOsteoParticipantId, medicalRecord.getInstitutionId());
            medicalRecord.setInstitutionId(newOsteoInstitutionId);
            medicalRecord.setDdpInstanceId(newOsteoInstanceId);
            int newMedicalRecordId = medicalRecordDao.create(medicalRecord);
            medicalRecord.setMedicalRecordId(newMedicalRecordId);
            newOsteoMedicalRecords.add(medicalRecord);
        });
        return newOsteoMedicalRecords;
    }

    private int updateAndThenSaveNewInstitution(int newOsteoParticipantId, long institutionId) {
        return ddpInstitutionDao.get(institutionId)
                .map(oldOsteoInstitution -> updateInstitutionDto(newOsteoParticipantId, oldOsteoInstitution))
                .map(ddpInstitutionDao::create)
                .orElseThrow();
    }

    private DDPInstitutionDto updateInstitutionDto(int newOsteoParticipantId, DDPInstitutionDto oldOsteoInstitution) {
        DDPInstitutionDto clonedDdpInstitutionDto = oldOsteoInstitution.clone();
        clonedDdpInstitutionDto.setParticipantId(newOsteoParticipantId);
        return clonedDdpInstitutionDto;
    }

    private void updateAndThenSaveNewParticipantRecord(ParticipantRecordDto oldOsteoParticipantRecord, int newOsteoParticipantId) {
        ParticipantRecordDto clonedParticipantRecordDto = oldOsteoParticipantRecord.clone();
        clonedParticipantRecordDto.setParticipantId(newOsteoParticipantId);
        participantRecordDao.create(clonedParticipantRecordDto);
    }

}
